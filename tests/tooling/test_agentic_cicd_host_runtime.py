import subprocess
import json
import os
import shutil
import tempfile
import time
import unittest
from pathlib import Path

import yaml

from scripts.agentic_cicd.host_credentials import prepare_host_credentials


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
HOST_DEPLOYMENT = REPOSITORY_ROOT / "deploy" / "host" / "agentic-cicd"
KUBERNETES_BASE = (
    REPOSITORY_ROOT / "deploy" / "kubernetes" / "agentic-cicd" / "base"
)


def render(path: Path) -> list[dict]:
    result = subprocess.run(
        ["kubectl", "kustomize", str(path)],
        cwd=REPOSITORY_ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return [document for document in yaml.safe_load_all(result.stdout) if document]


class AgenticCicdHostRuntimeTest(unittest.TestCase):
    def test_host_credential_preparer_reduces_config_and_never_copies_personal_home(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            token = root / "token"
            auth = root / "auth.json"
            config = root / "config.toml"
            destination = root / "credentials"
            token.write_text("ghs_fixture_token_without_whitespace", encoding="utf-8")
            auth.write_text(
                json.dumps({"OPENAI_API_KEY": "sk-fixture-not-a-real-platform-key"}),
                encoding="utf-8",
            )
            config.write_text(
                'model = "gpt-fixture"\n'
                'model_provider = "fixture"\n'
                'model_reasoning_effort = "high"\n'
                'approval_policy = "never"\n\n'
                '[model_providers.fixture]\n'
                'name = "Fixture"\n'
                'base_url = "https://fixture.example/v1"\n'
                'wire_api = "responses"\n'
                'requires_openai_auth = true\n\n'
                '[mcp_servers.untrusted]\ncommand = "/tmp/untrusted"\n',
                encoding="utf-8",
            )
            for path in (token, auth, config):
                path.chmod(0o600)

            prepare_host_credentials(
                token_file=token,
                expires_at_epoch_seconds=int(time.time()) + 3600,
                auth_file=auth,
                config_file=config,
                destination=destination,
                now_epoch_seconds=int(time.time()),
            )

            reduced = (destination / "codex-config.toml").read_text(encoding="utf-8")
            self.assertIn('base_url = "https://fixture.example/v1"', reduced)
            self.assertNotIn("approval_policy", reduced)
            self.assertNotIn("mcp_servers", reduced)
            self.assertEqual(
                {"OPENAI_API_KEY": "sk-fixture-not-a-real-platform-key"},
                json.loads((destination / "codex-auth.json").read_text(encoding="utf-8")),
            )
            self.assertEqual(
                {"github-token", "github-token-expires-at", "codex-auth.json", "codex-config.toml"},
                {path.name for path in destination.iterdir()},
            )
            self.assertTrue(
                all((path.stat().st_mode & 0o777) == 0o400 for path in destination.iterdir())
            )

            token.write_text("ghs_fixture_token_without_whitespace\n", encoding="utf-8")
            token.chmod(0o600)
            with self.assertRaisesRegex(ValueError, "whitespace"):
                prepare_host_credentials(
                    token_file=token,
                    expires_at_epoch_seconds=int(time.time()) + 3600,
                    auth_file=auth,
                    config_file=config,
                    destination=root / "rejected",
                    now_epoch_seconds=int(time.time()),
                )
            self.assertFalse((root / "rejected").exists())

    def test_kubernetes_base_contains_storage_but_no_symphony_execution_plane(self) -> None:
        documents = render(KUBERNETES_BASE)
        identities = {
            (document.get("kind"), document.get("metadata", {}).get("name"))
            for document in documents
        }
        self.assertNotIn(("Deployment", "symphony"), identities)
        self.assertNotIn(("Service", "symphony"), identities)
        self.assertFalse(
            any(
                document.get("kind") == "ConfigMap"
                and document.get("metadata", {}).get("name", "").startswith(
                    "symphony-workflow-"
                )
                for document in documents
            )
        )
        self.assertIn(("PersistentVolume", "agentic-cicd-symphony-state"), identities)
        self.assertIn(("PersistentVolumeClaim", "symphony-state"), identities)
        for retired in (
            KUBERNETES_BASE / "deployment.yaml",
            KUBERNETES_BASE / "service.yaml",
            KUBERNETES_BASE / "service-account.yaml",
        ):
            self.assertEqual(
                0,
                len([item for item in yaml.safe_load_all(retired.read_text(encoding="utf-8")) if item]),
            )

    def test_systemd_service_is_static_non_root_and_keeps_codex_user_namespaces(self) -> None:
        unit = (HOST_DEPLOYMENT / "jstore-agentic-cicd.service").read_text(
            encoding="utf-8"
        )
        self.assertIn("User=jstore-agentic-cicd", unit)
        self.assertIn("Group=jstore-agentic-cicd", unit)
        self.assertIn("NoNewPrivileges=yes", unit)
        self.assertIn("ProtectSystem=strict", unit)
        self.assertIn("PrivateTmp=yes", unit)
        self.assertIn("Restart=no", unit)
        self.assertIn("LoadCredential=github-token:", unit)
        self.assertIn("LoadCredential=codex-auth.json:", unit)
        self.assertIn("LoadCredential=codex-config.toml:", unit)
        self.assertNotIn("RestrictNamespaces=", unit)
        self.assertNotIn("CapabilityBoundingSet=CAP_SYS_ADMIN", unit)
        self.assertNotIn("[Install]", unit)

    def test_runtime_wrapper_uses_credentials_without_emitting_or_inheriting_secrets(self) -> None:
        wrapper = (HOST_DEPLOYMENT / "run-symphony.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn('CREDENTIALS_DIRECTORY', wrapper)
        self.assertIn('JSTORE_SYMPHONY_GITHUB_TOKEN', wrapper)
        self.assertIn('codex sandbox -- /bin/true', wrapper)
        self.assertIn('codex login status >/dev/null 2>&1', wrapper)
        self.assertIn('host: 127.0.0.1', (HOST_DEPLOYMENT / "WORKFLOW.md").read_text(encoding="utf-8"))
        self.assertNotIn('--host', wrapper)
        self.assertNotIn('set -x', wrapper)
        for inherited in ("GITHUB_TOKEN", "GH_TOKEN", "CODEX_API_KEY"):
            self.assertIn(f"unset {inherited}", wrapper)

    def test_host_install_is_immutable_and_does_not_start_the_service(self) -> None:
        install = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-host-install.sh"
        ).read_text(encoding="utf-8")
        self.assertIn("--bundle-sha256", install)
        self.assertIn("sha256sum --check --strict", install)
        self.assertIn("manifest.sha256", install)
        self.assertIn("install_root=/opt/jstore-agentic-cicd", install)
        self.assertIn('"$install_root/releases"', install)
        self.assertIn("systemctl daemon-reload", install)
        self.assertNotIn("systemctl enable", install)
        self.assertNotIn("systemctl start", install)
        self.assertNotIn("kubectl apply", install)

    def test_host_credential_cli_has_read_only_validation_and_fixed_install_target(self) -> None:
        script = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-host-credentials.py"
        ).read_text(encoding="utf-8")
        self.assertIn('mode.add_argument("--check-only"', script)
        self.assertIn('mode.add_argument("--install"', script)
        self.assertIn('/etc/jstore-agentic-cicd/credentials', script)
        self.assertIn("os.geteuid()", script)
        self.assertIn("systemctl", script)
        self.assertNotIn("kubectl", script)

    def test_host_bundle_build_pins_sources_and_contains_no_credentials(self) -> None:
        build = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-host-build.sh"
        ).read_text(encoding="utf-8")
        packager = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-package-codex.sh"
        ).read_text(encoding="utf-8")
        self.assertIn("symphony.lock.json", build)
        self.assertIn("git -C \"$symphony_source\" status --porcelain", build)
        self.assertIn("git -C \"$repo_root\" status --porcelain", build)
        self.assertIn("symphony-phase-bridge.patch", build)
        self.assertIn("symphony-phase-routing.patch", build)
        self.assertIn("symphony-mix.lock", build)
        self.assertIn("test_fixture_relative=$(read_lock test_fixture)", build)
        self.assertIn(
            'verify_sha256 "$repo_root/$test_fixture_relative" "$test_fixture_sha256"',
            build,
        )
        self.assertIn("mix compile --warnings-as-errors", build)
        self.assertIn("mix hex.audit", build)
        self.assertIn("mix test", build)
        self.assertIn("bwrap --die-with-parent", build)
        self.assertIn("--dev-bind /dev /dev", build)
        self.assertIn(
            '--ro-bind "$test_fixture_dir" /opt/jstore-agentic-controller', build
        )
        self.assertIn("agentic-cicd-package-codex.sh", build)
        self.assertNotIn("codex sandbox -- /bin/true", build)
        self.assertIn("packageJson.optionalDependencies", packager)
        self.assertIn("createRequire", packager)
        self.assertIn('"$payload/bin/codex" --version', packager)
        self.assertIn('"$payload/bin/codex" sandbox -- /bin/true', packager)
        self.assertIn("manifest.sha256", build)
        self.assertIn("tar --sort=name", build)
        for secret in ("auth.json", "config.toml", "OPENAI_API_KEY"):
            self.assertNotIn(f'cp "$HOME/.codex/{secret}"', build)

    def test_codex_packager_builds_a_self_contained_isolated_payload(self) -> None:
        node = shutil.which("node")
        self.assertIsNotNone(node)
        assert node is not None
        version = "0.148.0"
        platform = subprocess.run(
            [node, "-p", "`${process.platform}:${process.arch}`"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        platform_packages = {
            "linux:x64": "@openai/codex-linux-x64",
            "linux:arm64": "@openai/codex-linux-arm64",
            "darwin:x64": "@openai/codex-darwin-x64",
            "darwin:arm64": "@openai/codex-darwin-arm64",
            "win32:x64": "@openai/codex-win32-x64",
            "win32:arm64": "@openai/codex-win32-arm64",
        }
        platform_package = platform_packages[platform]
        platform_directory_name = platform_package.removeprefix("@openai/")
        vendor_binary = "vendor/test/bin/codex"

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source" / "node_modules" / "@openai"
            codex_module = source / "codex"
            platform_module = source / platform_directory_name
            codex_bin = codex_module / "bin" / "codex.js"
            platform_bin = platform_module / vendor_binary
            codex_bin.parent.mkdir(parents=True)
            platform_bin.parent.mkdir(parents=True)
            (codex_module / "package.json").write_text(
                json.dumps(
                    {
                        "name": "@openai/codex",
                        "version": version,
                        "type": "module",
                        "optionalDependencies": {
                            platform_package: f"npm:@openai/codex@{version}-{platform}"
                        },
                    }
                ),
                encoding="utf-8",
            )
            (platform_module / "package.json").write_text(
                json.dumps(
                    {"name": platform_package, "version": f"{version}-{platform}"}
                ),
                encoding="utf-8",
            )
            codex_bin.write_text(
                "#!/usr/bin/env node\n"
                'import { createRequire } from "node:module";\n'
                'import { spawnSync } from "node:child_process";\n'
                'import path from "node:path";\n'
                "const require = createRequire(import.meta.url);\n"
                "const packageJson = require.resolve(\n"
                f'  "{platform_package}/package.json"\n'
                ");\n"
                "const binary = path.join(\n"
                "  path.dirname(packageJson),\n"
                f'  "{vendor_binary}"\n'
                ");\n"
                "const result = spawnSync(binary, process.argv.slice(2), { stdio: \"inherit\" });\n"
                "process.exit(result.status ?? 1);\n",
                encoding="utf-8",
            )
            codex_bin.chmod(0o755)
            platform_bin.write_text(
                "#!/usr/bin/env bash\n"
                "set -euo pipefail\n"
                f"if [[ ${{1:-}} == --version ]]; then echo 'codex-cli {version}'; exit 0; fi\n"
                "if [[ ${1:-} == sandbox && ${2:-} == -- && ${3:-} == /bin/true ]]; then exit 0; fi\n"
                "exit 2\n",
                encoding="utf-8",
            )
            platform_bin.chmod(0o755)
            codex = root / "source" / "bin" / "codex"
            codex.parent.mkdir()
            codex.symlink_to(codex_bin)
            portable_node = root / "source" / "bin" / "node"
            portable_node.write_text(
                "#!/usr/bin/env bash\n" f'exec "{node}" "$@"\n', encoding="utf-8"
            )
            portable_node.chmod(0o755)

            payload = root / "payload"
            subprocess.run(
                [
                    str(REPOSITORY_ROOT / "scripts" / "agentic-cicd-package-codex.sh"),
                    "--codex-command",
                    codex,
                    "--node-command",
                    portable_node,
                    "--payload",
                    str(payload),
                    "--expected-version",
                    version,
                ],
                cwd=root,
                check=True,
            )

            packaged_environment = os.environ.copy()
            packaged_environment["PATH"] = f"{payload / 'bin'}:/usr/bin:/bin"
            packaged_version = subprocess.run(
                [str(payload / "bin" / "codex"), "--version"],
                cwd=root,
                env=packaged_environment,
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip()
            self.assertEqual(f"codex-cli {version}", packaged_version)
            self.assertTrue(
                any(
                    path.name == platform_directory_name
                    for path in (payload / "lib" / "node_modules" / "@openai").iterdir()
                )
            )
            subprocess.run(
                [str(payload / "bin" / "codex"), "sandbox", "--", "/bin/true"],
                cwd=root,
                env=packaged_environment,
                check=True,
            )

    def test_host_start_fails_closed_on_kubernetes_double_active_and_runs_no_model_preflight(self) -> None:
        start = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-host-start.sh"
        ).read_text(encoding="utf-8")
        self.assertIn('deployment/symphony', start)
        self.assertIn('jsonpath={.spec.replicas}', start)
        self.assertIn('run-symphony --preflight-only', start)
        self.assertLess(
            start.index('run-symphony --preflight-only'),
            start.index('systemctl start jstore-agentic-cicd.service'),
        )
        self.assertNotIn("kubectl scale", start)
        self.assertNotIn("kubectl delete", start)

    def test_host_stop_and_status_target_only_the_static_service(self) -> None:
        control = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-host-control.sh"
        ).read_text(encoding="utf-8")
        self.assertIn("stop|status", control)
        self.assertIn("systemctl stop jstore-agentic-cicd.service", control)
        self.assertIn("systemctl status jstore-agentic-cicd.service", control)
        self.assertNotIn("rm -", control)
        self.assertNotIn("kubectl", control)


if __name__ == "__main__":
    unittest.main()

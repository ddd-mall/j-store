import subprocess
import unittest
import json
import hashlib
import os
import shutil
import tempfile
from pathlib import Path

import yaml


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
BASE = REPOSITORY_ROOT / "deploy" / "kubernetes" / "agentic-cicd" / "base"
DEVELOPMENT = (
    REPOSITORY_ROOT
    / "deploy"
    / "kubernetes"
    / "agentic-cicd"
    / "overlays"
    / "development-local-image"
)
CREDENTIALED_OBSERVER = (
    REPOSITORY_ROOT
    / "deploy"
    / "kubernetes"
    / "agentic-cicd"
    / "overlays"
    / "development-credentialed-observer"
)
NETWORK_POLICY_ENGINE = (
    REPOSITORY_ROOT
    / "deploy"
    / "kubernetes"
    / "agentic-cicd"
    / "network-policy-engine"
)
GATES = (
    REPOSITORY_ROOT / "deploy" / "kubernetes" / "agentic-cicd" / "gates"
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


def by_kind_name(documents: list[dict], kind: str, name: str) -> dict:
    return next(
        document
        for document in documents
        if document.get("kind") == kind
        and document.get("metadata", {}).get("name") == name
    )


def by_kind_prefix(documents: list[dict], kind: str, prefix: str) -> dict:
    return next(
        document
        for document in documents
        if document.get("kind") == kind
        and document.get("metadata", {}).get("name", "").startswith(prefix)
    )


class AgenticCicdKubernetesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.base = render(BASE)
        cls.development = render(DEVELOPMENT)
        cls.credentialed_observer = render(CREDENTIALED_OBSERVER)
        cls.network_policy_engine = render(NETWORK_POLICY_ENGINE)
        cls.gates = render(GATES)

    def test_resources_are_isolated_and_do_not_embed_secrets(self) -> None:
        namespace = by_kind_name(self.base, "Namespace", "agentic-cicd")
        labels = namespace["metadata"]["labels"]
        self.assertEqual("restricted", labels["pod-security.kubernetes.io/enforce"])
        self.assertFalse(any(document.get("kind") == "Secret" for document in self.base))
        for document in self.base:
            if document.get("kind") not in {"Namespace", "PersistentVolume"}:
                self.assertEqual("agentic-cicd", document["metadata"].get("namespace"))
        volumes = [
            document
            for document in self.base
            if document.get("kind") == "PersistentVolume"
        ]
        self.assertEqual(5, len(volumes))
        self.assertTrue(
            all(volume["spec"]["persistentVolumeReclaimPolicy"] == "Retain" for volume in volumes)
        )
        self.assertEqual(
            {
                "/var/lib/jstore-agentic-cicd",
                "/var/lib/jstore-agentic-candidates",
                "/var/lib/jstore-agentic-gate-requests",
                "/var/lib/jstore-agentic-gate-receipts",
                "/var/lib/jstore-agentic-artifact-leases",
            },
            {volume["spec"]["local"]["path"] for volume in volumes},
        )

    def test_deployment_is_single_instance_and_cannot_call_kubernetes_api(self) -> None:
        deployment = by_kind_name(self.base, "Deployment", "symphony")
        self.assertEqual(1, deployment["spec"]["replicas"])
        self.assertEqual("Recreate", deployment["spec"]["strategy"]["type"])
        pod = deployment["spec"]["template"]["spec"]
        self.assertEqual("symphony", pod["serviceAccountName"])
        self.assertFalse(pod["automountServiceAccountToken"])
        self.assertFalse(
            any(document.get("kind") in {"Role", "RoleBinding"} for document in self.base)
        )

    def test_credentialed_observer_mounts_only_fixed_credential_secrets(self) -> None:
        base_deployment = by_kind_name(self.base, "Deployment", "symphony")
        base_container = base_deployment["spec"]["template"]["spec"]["containers"][0]
        base_environment = {item["name"]: item for item in base_container["env"]}
        self.assertEqual(
            "level0-no-github-access",
            base_environment["JSTORE_SYMPHONY_GITHUB_TOKEN"]["value"],
        )

        self.assertFalse(
            any(document.get("kind") == "Secret" for document in self.credentialed_observer)
        )
        deployment = by_kind_name(
            self.credentialed_observer, "Deployment", "symphony"
        )
        pod = deployment["spec"]["template"]["spec"]
        container = pod["containers"][0]
        environment = {item["name"]: item for item in container["env"]}
        self.assertEqual(
            {
                "name": "symphony-github-token",
                "key": "token",
                "optional": False,
            },
            environment["JSTORE_SYMPHONY_GITHUB_TOKEN"]["valueFrom"][
                "secretKeyRef"
            ],
        )
        self.assertNotIn("value", environment["JSTORE_SYMPHONY_GITHUB_TOKEN"])
        self.assertEqual(
            {
                "name": "symphony-github-token",
                "key": "expires-at-epoch-seconds",
                "optional": False,
            },
            environment["JSTORE_GITHUB_TOKEN_EXPIRES_AT_EPOCH_SECONDS"][
                "valueFrom"
            ]["secretKeyRef"],
        )
        self.assertFalse(pod["automountServiceAccountToken"])
        self.assertEqual("k8s-master", pod["nodeSelector"]["kubernetes.io/hostname"])
        self.assertEqual("Never", container["imagePullPolicy"])
        self.assertEqual(1, len(pod["initContainers"]))
        prepare_home = pod["initContainers"][0]
        self.assertEqual("prepare-codex-home", prepare_home["name"])
        self.assertEqual(
            [
                "/usr/bin/install",
                "-d",
                "-m",
                "0700",
                "/var/lib/symphony/home/.codex",
            ],
            prepare_home["command"],
        )
        self.assertTrue(prepare_home["securityContext"]["readOnlyRootFilesystem"])
        self.assertFalse(prepare_home["securityContext"]["allowPrivilegeEscalation"])
        self.assertEqual(
            ["ALL"], prepare_home["securityContext"]["capabilities"]["drop"]
        )
        codex_mounts = {
            mount["subPath"]: mount
            for mount in container["volumeMounts"]
            if mount["name"] == "codex-auth"
        }
        self.assertEqual({"auth.json", "config.toml"}, set(codex_mounts))
        self.assertEqual(
            "/var/lib/symphony/home/.codex/auth.json",
            codex_mounts["auth.json"]["mountPath"],
        )
        self.assertEqual(
            "/var/lib/symphony/home/.codex/config.toml",
            codex_mounts["config.toml"]["mountPath"],
        )
        self.assertTrue(all(mount["readOnly"] for mount in codex_mounts.values()))
        codex_volume = next(
            volume for volume in pod["volumes"] if volume["name"] == "codex-auth"
        )
        self.assertEqual("symphony-codex-auth", codex_volume["secret"]["secretName"])
        self.assertFalse(codex_volume["secret"]["optional"])
        self.assertEqual(0o440, codex_volume["secret"]["defaultMode"])

    def test_runtime_is_non_root_read_only_and_persistent(self) -> None:
        deployment = by_kind_name(self.base, "Deployment", "symphony")
        pod = deployment["spec"]["template"]["spec"]
        container = pod["containers"][0]
        self.assertTrue(pod["securityContext"]["runAsNonRoot"])
        self.assertEqual("RuntimeDefault", pod["securityContext"]["seccompProfile"]["type"])
        security = container["securityContext"]
        self.assertTrue(security["readOnlyRootFilesystem"])
        self.assertFalse(security["allowPrivilegeEscalation"])
        self.assertEqual(["ALL"], security["capabilities"]["drop"])
        mounts = {mount["mountPath"]: mount["name"] for mount in container["volumeMounts"]}
        self.assertEqual("state", mounts["/var/lib/symphony"])
        self.assertEqual("workflow", mounts["/etc/symphony"])
        pvc = by_kind_name(self.base, "PersistentVolumeClaim", "symphony-state")
        self.assertIn("ReadWriteOnce", pvc["spec"]["accessModes"])
        self.assertEqual("agentic-cicd-symphony-state", pvc["spec"]["volumeName"])

    def test_workflow_is_the_trusted_level_zero_contract(self) -> None:
        generated = by_kind_prefix(self.base, "ConfigMap", "symphony-workflow-")
        deployed_workflow = generated["data"]["WORKFLOW.md"]
        trusted_workflow = (REPOSITORY_ROOT / "WORKFLOW.md").read_text(encoding="utf-8")
        kubernetes_server_binding = "server:\n  host: 0.0.0.0\n"
        self.assertEqual(1, deployed_workflow.count(kubernetes_server_binding))
        self.assertEqual(
            trusted_workflow,
            deployed_workflow.replace(kubernetes_server_binding, ""),
        )
        for contract in (
            "max_concurrent_agents: 1",
            "max_turns: 1",
            "thread_sandbox: read-only",
            "type: readOnly",
            "sandbox_approval: true",
            "不得修改文件",
            "不得自动合并、发布或写生产",
        ):
            self.assertIn(contract, deployed_workflow)
        self.assertIn(
            "/usr/bin/python3 /opt/jstore-agentic-controller/controller.py bootstrap-workspace",
            deployed_workflow,
        )
        self.assertIn(
            "/usr/bin/python3 /opt/jstore-agentic-controller/controller.py complete-turn",
            deployed_workflow,
        )
        for binding in (
            "--expected-phase \"$JSTORE_INVOCATION_PHASE\"",
            "--expected-role \"$JSTORE_INVOCATION_ROLE\"",
            "--expected-head-sha \"$JSTORE_INVOCATION_HEAD_SHA\"",
            "--expected-candidate-revision \"$JSTORE_INVOCATION_CANDIDATE_REVISION\"",
            "--outcome \"$JSTORE_TURN_OUTCOME\"",
            "--token-usage-observed \"$JSTORE_TURN_TOKEN_USAGE_OBSERVED\"",
            "--wall-clock-seconds \"$JSTORE_TURN_WALL_CLOCK_SECONDS\"",
            "--input-tokens \"$JSTORE_TURN_INPUT_TOKENS\"",
            "--output-tokens \"$JSTORE_TURN_OUTPUT_TOKENS\"",
        ):
            self.assertIn(binding, deployed_workflow)
        self.assertIn('{% if agentic_cicd.role == "reviewer" %}', deployed_workflow)
        self.assertIn('{% elsif agentic_cicd.role == "implementer" %}', deployed_workflow)
        self.assertIn("submit_review_proposal", deployed_workflow)
        self.assertNotIn("git clone --filter=blob:none", deployed_workflow)

        deployment = by_kind_name(self.base, "Deployment", "symphony")
        workflow_volume = next(
            volume
            for volume in deployment["spec"]["template"]["spec"]["volumes"]
            if volume["name"] == "workflow"
        )
        self.assertEqual(generated["metadata"]["name"], workflow_volume["configMap"]["name"])

    def test_image_placeholder_and_development_node_are_explicit(self) -> None:
        deployment = by_kind_name(self.development, "Deployment", "symphony")
        pod = deployment["spec"]["template"]["spec"]
        container = pod["containers"][0]
        self.assertEqual("jstore-agentic-cicd:development-placeholder", container["image"])
        self.assertEqual("Never", container["imagePullPolicy"])
        self.assertEqual("k8s-master", pod["nodeSelector"]["kubernetes.io/hostname"])
        tolerations = pod["tolerations"]
        self.assertTrue(
            any(
                item.get("key") == "node-role.kubernetes.io/control-plane"
                and item.get("effect") == "NoSchedule"
                for item in tolerations
            )
        )

    def test_image_build_uses_pinned_official_sources(self) -> None:
        dockerfile = (
            REPOSITORY_ROOT
            / "deploy"
            / "kubernetes"
            / "agentic-cicd"
            / "image"
            / "Dockerfile"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "hexpm/elixir:1.19.5-erlang-28.3-debian-bookworm-20260202-slim@sha256:09279250196a9ad971ebe4673ec2df47bc760c0409a055df8ea283954ac6a099",
            dockerfile,
        )
        self.assertIn(
            "node:22-bookworm-slim@sha256:d649c27dae7ba0137b3cef5dd75baa422c08dc3d9e3fc0c23dfb172dc3cc6436",
            dockerfile,
        )
        self.assertIn("ARG CODEX_VERSION", dockerfile)
        self.assertNotIn("ARG CODEX_VERSION=", dockerfile)
        self.assertIn('test -n "${CODEX_VERSION}"', dockerfile)
        self.assertIn(
            "COPY --from=symphony-source /elixir /build/symphony/elixir",
            dockerfile,
        )
        self.assertIn("ARG JSTORE_CONTROLLER_REVISION", dockerfile)
        self.assertIn(
            "COPY scripts/agentic_cicd /opt/jstore-agentic-controller/agentic_cicd",
            dockerfile,
        )
        self.assertIn(
            "COPY scripts/agentic-cicd-controller.py /opt/jstore-agentic-controller/controller.py",
            dockerfile,
        )
        self.assertIn("COPY scripts/agentic-artifact-broker.py", dockerfile)
        self.assertIn("COPY scripts/agentic-gate-dispatcher.py", dockerfile)
        self.assertIn("COPY config/agentic-cicd /opt/config/agentic-cicd", dockerfile)
        self.assertIn("from agentic_cicd.protocol import GateReceipt, GateRequest", dockerfile)
        self.assertIn("chmod -R a-w /opt/config/agentic-cicd", dockerfile)
        self.assertIn(
            "COPY config/agentic-cicd/state-contract.json /opt/jstore-agentic-controller/state-contract.json",
            dockerfile,
        )
        self.assertNotIn(
            "state-contract.level2-disposable.example.json "
            "/opt/jstore-agentic-controller/state-contract.json",
            dockerfile,
        )
        self.assertIn("FROM level0-runtime AS disposable-level2", dockerfile)
        self.assertIn(
            "COPY --from=runtime-profile --chmod=0444 /state-contract.json",
            dockerfile,
        )
        self.assertIn(
            "COPY --from=runtime-profile --chmod=0444 /runtime-binding.json",
            dockerfile,
        )
        self.assertIn("FROM level0-runtime AS default", dockerfile)
        self.assertIn("io.jstore.controller.revision", dockerfile)
        for label in (
            "io.jstore.symphony.patch.sha256",
            "io.jstore.symphony.routing-patch.sha256",
            "io.jstore.symphony.dependency-lock.sha256",
            "io.jstore.workflow.sha256",
            "io.jstore.base.elixir",
            "io.jstore.base.node",
        ):
            self.assertIn(label, dockerfile)
        self.assertIn(
            "COPY deploy/kubernetes/agentic-cicd/patches/symphony-phase-bridge.patch",
            dockerfile,
        )
        self.assertIn(
            "COPY deploy/kubernetes/agentic-cicd/patches/symphony-phase-routing.patch",
            dockerfile,
        )
        self.assertIn(
            "COPY deploy/kubernetes/agentic-cicd/patches/symphony-mix.lock",
            dockerfile,
        )
        self.assertIn("git apply --recount --check", dockerfile)
        self.assertIn("git apply --recount /tmp/symphony-phase-bridge.patch", dockerfile)
        self.assertIn("/tmp/symphony-mix.lock /build/symphony/elixir/mix.lock", dockerfile)
        self.assertNotIn(":latest", dockerfile)

        audit_script = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-symphony-audit.sh"
        ).read_text(encoding="utf-8")
        for check in (
            'archive "$symphony_revision"',
            "git apply --recount --check",
            '--iidfile "$audit_toolchain_iidfile"',
            "audit_toolchain_dockerfile_sha256",
            "audit_toolchain_image_id",
            "Acquire::Retries=2",
            "Acquire::http::Timeout=30",
            "Acquire::https::Timeout=30",
            "mix compile --warnings-as-errors",
            "mix test",
            "mix hex.audit",
            "mix escript.build",
            "symphony-dependencies.tsv",
            "controller_fixture_sha256",
            '"$audit_root/controller-fixture"',
            '"$audit_root/evidence"',
            'verify_sha256 "$source_tree/elixir/mix.lock"',
            'chown -R $(id -u):$(id -g) /cleanup',
            "codex-cli $codex_version",
        ):
            self.assertIn(check, audit_script)
        self.assertEqual(
            audit_script.count(
                "install --yes --no-install-recommends build-essential cmake "
                "git ca-certificates python3"
            ),
            1,
        )
        self.assertGreaterEqual(audit_script.count('"$audit_toolchain_image_id"'), 2)
        self.assertIn("--network host", audit_script)
        self.assertIn("--build-arg HTTP_PROXY", audit_script)
        self.assertIn(
            '"audit_toolchain_dockerfile_sha256": '
            '"$audit_toolchain_dockerfile_sha256"',
            audit_script,
        )
        self.assertIn(
            '"audit_toolchain_image_id": "$audit_toolchain_image_id"',
            audit_script,
        )
        self.assertIn(
            "hexpm/elixir:1.19.5-erlang-28.3-debian-bookworm-20260202-slim@sha256:",
            audit_script,
        )
        self.assertIn(
            "node:22-bookworm-slim@sha256:d649c27dae7ba0137b3cef5dd75baa422c08dc3d9e3fc0c23dfb172dc3cc6436",
            audit_script,
        )
        self.assertIn("@openai/codex@$codex_version", audit_script)
        self.assertIn("codex_output=$(codex --version", audit_script)

        build_script = (
            REPOSITORY_ROOT
            / "scripts"
            / "agentic-cicd-controller-image-build.sh"
        ).read_text(encoding="utf-8")
        for evidence in (
            "--sbom=true",
            "--provenance=mode=max",
            "--build-arg HTTP_PROXY",
            "--network host",
            "runtime_manifest_digest",
            "phase_bridge_patch_sha256",
            "phase_routing_patch_sha256",
            "dependency_lock_sha256",
            "workflow_sha256",
            "CONTROLLER_IMAGE_ARCHIVE_SHA256",
            '"archive": pathlib.Path("$archive_path").name',
            'verify_sha256 "$repo_root/$patch_relative"',
            'verify_sha256 "$repo_root/$routing_patch_relative"',
            'verify_sha256 "$repo_root/$dependency_lock_relative"',
            'archive "$symphony_revision"',
            'symphony-source=$symphony_context',
        ):
            self.assertIn(evidence, build_script)
        self.assertNotIn(":latest", build_script)
        self.assertIn("codex_output=$(codex --version", build_script)
        self.assertIn('--build-arg "CODEX_VERSION=$codex_version"', build_script)
        self.assertNotIn('symphony-source=$symphony_source', build_script)
        for profile_boundary in (
            "--disposable-level2-repository",
            "state-contract.level2-disposable.example.json",
            "prepare_disposable_runtime_profile",
            "--target disposable-level2",
            "runtime-profile=$runtime_profile_context",
            "JSTORE_STATE_CONTRACT_SHA256",
            "JSTORE_RUNTIME_BINDING_SHA256",
            "io.jstore.target.repository",
            "CONTROLLER_TARGET_REPOSITORY",
            'profile_suffix="-level2-${state_contract_sha256:0:16}-binding-${runtime_binding_sha256:0:16}"',
        ):
            self.assertIn(profile_boundary, build_script)
        self.assertNotIn("--capability-contract", build_script)
        self.assertIn("HEX_HTTP_CONCURRENCY=1", dockerfile)
        self.assertIn("HEX_HTTP_TIMEOUT=120", dockerfile)
        self.assertIn(
            "apt-get upgrade --yes --no-install-recommends",
            dockerfile,
        )
        self.assertIn(
            "ca-certificates git bash python3",
            dockerfile,
        )
        self.assertNotIn("openssh-client", dockerfile)
        self.assertNotIn(" bash curl python3", dockerfile)

        dockerignore = (REPOSITORY_ROOT / ".dockerignore").read_text(encoding="utf-8")
        self.assertTrue(dockerignore.startswith("**\n"))
        self.assertIn("!scripts/agentic_cicd/**", dockerignore)
        self.assertIn("!scripts/agentic-cicd-controller.py", dockerignore)
        self.assertIn("!scripts/agentic-artifact-broker.py", dockerignore)
        self.assertIn("!scripts/agentic-gate-dispatcher.py", dockerignore)
        self.assertIn("!config/agentic-cicd/*.json", dockerignore)
        self.assertIn(
            "config/agentic-cicd/state-contract.level2-disposable.example.json",
            dockerignore,
        )
        self.assertIn(
            "!deploy/kubernetes/agentic-cicd/patches/symphony-phase-bridge.patch",
            dockerignore,
        )
        self.assertIn(
            "!deploy/kubernetes/agentic-cicd/patches/symphony-phase-routing.patch",
            dockerignore,
        )
        self.assertIn(
            "!deploy/kubernetes/agentic-cicd/patches/symphony-mix.lock",
            dockerignore,
        )
        self.assertNotIn("!.git", dockerignore)

    def test_symphony_patch_is_pinned_and_exposes_only_trusted_bridge_inputs(self) -> None:
        patch_path = (
            REPOSITORY_ROOT
            / "deploy"
            / "kubernetes"
            / "agentic-cicd"
            / "patches"
            / "symphony-phase-bridge.patch"
        )
        patch = patch_path.read_text(encoding="utf-8")
        lock = json.loads(
            (REPOSITORY_ROOT / "config" / "agentic-cicd" / "symphony.lock.json").read_text(
                encoding="utf-8"
            )
        )
        self.assertEqual(
            hashlib.sha256(patch_path.read_bytes()).hexdigest(),
            lock["patch_sha256"],
        )
        self.assertIn("submit_review_proposal", patch)
        self.assertIn("JSTORE_TURN_SESSION_ID", patch)
        self.assertIn("JSTORE_TURN_THREAD_ID", patch)
        self.assertIn("JSTORE_TURN_ID", patch)
        self.assertIn("JSTORE_ISSUE_TITLE", patch)
        self.assertIn("JSTORE_ISSUE_BODY", patch)
        self.assertIn("issue_environment(issue_context)", patch)
        self.assertNotIn("codex app-server", patch)

        routing_patch_path = patch_path.with_name("symphony-phase-routing.patch")
        routing_patch = routing_patch_path.read_text(encoding="utf-8")
        self.assertIn("JSTORE_TURN_WALL_CLOCK_SECONDS", routing_patch)
        self.assertIn("JSTORE_TURN_INPUT_TOKENS", routing_patch)
        self.assertIn("JSTORE_TURN_OUTPUT_TOKENS", routing_patch)
        self.assertEqual(
            hashlib.sha256(routing_patch_path.read_bytes()).hexdigest(),
            lock["routing_patch_sha256"],
        )
        dependency_lock_path = routing_patch_path.with_name("symphony-mix.lock")
        self.assertEqual(
            hashlib.sha256(dependency_lock_path.read_bytes()).hexdigest(),
            lock["dependency_lock_sha256"],
        )
        fixture_path = REPOSITORY_ROOT / lock["test_fixture"]
        self.assertEqual(
            hashlib.sha256(fixture_path.read_bytes()).hexdigest(),
            lock["test_fixture_sha256"],
        )
        self.assertIn('"run_model" => false', routing_patch)
        self.assertIn('"thread_sandbox"', routing_patch)
        self.assertIn("model_workspace", routing_patch)
        self.assertIn("candidate_revision", routing_patch)
        self.assertIn("runtime_policy", routing_patch)
        self.assertIn(":agentic_cicd_context", routing_patch)
        self.assertIn("defmodule SymphonyElixir.AgenticCicd do", routing_patch)
        self.assertIn(":remote_phase_context_not_supported", routing_patch)
        self.assertIn('@allowed_methods ["GET"]', routing_patch)
        self.assertIn(
            'Enum.each(["POST", "PATCH", "PUT", "DELETE"]', routing_patch
        )
        self.assertIn(
            "read-only github_api must reject write methods before calling the client",
            routing_patch,
        )
        for binding in (
            "JSTORE_INVOCATION_PHASE",
            "JSTORE_INVOCATION_ROLE",
            "JSTORE_INVOCATION_HEAD_SHA",
            "JSTORE_INVOCATION_CANDIDATE_REVISION",
        ):
            self.assertIn(binding, routing_patch)
        self.assertNotIn("codex app-server", routing_patch)

    def test_dashboard_is_internal_and_probed(self) -> None:
        service = by_kind_name(self.base, "Service", "symphony")
        self.assertEqual("ClusterIP", service["spec"].get("type", "ClusterIP"))
        self.assertFalse(any(document.get("kind") == "Ingress" for document in self.base))
        deployment = by_kind_name(self.base, "Deployment", "symphony")
        container = deployment["spec"]["template"]["spec"]["containers"][0]
        self.assertEqual("/api/v1/state", container["readinessProbe"]["httpGet"]["path"])
        self.assertEqual("/api/v1/state", container["livenessProbe"]["httpGet"]["path"])

    def test_gate_runner_is_immutable_offline_and_has_no_runtime_dependency_download(self) -> None:
        image_root = (
            REPOSITORY_ROOT / "deploy" / "kubernetes" / "agentic-cicd" / "image"
        )
        dockerfile = (image_root / "GateRunner.Dockerfile").read_text(encoding="utf-8")
        entrypoint = (image_root / "run-quality-gate.sh").read_text(encoding="utf-8")
        targets = (image_root / "write-spotless-targets.sh").read_text(encoding="utf-8")
        self.assertIn("eclipse-temurin:25-jdk-noble@sha256:", dockerfile)
        self.assertIn("KUBECTL_VERSION=v1.28.15", dockerfile)
        self.assertIn(
            "KUBECTL_SHA256=1f7651ad0b50ef4561aa82e77f3ad06599b5e6b0b2a5fb6c4f474d95a77e41c5",
            dockerfile,
        )
        self.assertIn("sha256sum --check --strict", dockerfile)
        self.assertIn("gradle.startParameter.offline = true", dockerfile)
        self.assertIn("--requirement requirements-quality.txt", dockerfile)
        self.assertIn("USER 65532:65532", dockerfile)
        self.assertIn("GRADLE_USER_HOME", entrypoint)
        self.assertIn("ORG_GRADLE_PROJECT_spotlessFilesFile", entrypoint)
        self.assertIn("JSTORE_QUALITY_TOOL_ROOT", entrypoint)
        self.assertIn("JSTORE_REPOSITORY_FILES_FILE", targets)
        self.assertIn("trusted/quality-gate.sh", entrypoint)
        self.assertIn("agentic_cicd/capabilities.py", dockerfile)
        self.assertNotIn("./scripts/quality-gate.sh", entrypoint)
        self.assertIn("known_example_sha256", targets)
        self.assertIn("sha256sum", targets)
        self.assertIn("serviceaccount/token", entrypoint)
        self.assertNotIn("curl ", entrypoint)
        self.assertNotIn("wget ", entrypoint)

    def test_gate_runner_trusted_governance_layout_is_self_contained(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trusted = Path(directory) / "trusted"
            package = trusted / "agentic_cicd"
            package.mkdir(parents=True)
            shutil.copy2(
                REPOSITORY_ROOT / "scripts" / "check-agentic-cicd.py",
                trusted / "check-agentic-cicd.py",
            )
            for name in ("__init__.py", "capabilities.py"):
                shutil.copy2(
                    REPOSITORY_ROOT / "scripts" / "agentic_cicd" / name,
                    package / name,
                )
            environment = dict(os.environ)
            environment["JSTORE_REPOSITORY_ROOT"] = str(REPOSITORY_ROOT)
            environment.pop("PYTHONPATH", None)
            result = subprocess.run(
                ["python3", "-s", str(trusted / "check-agentic-cicd.py")],
                cwd=Path(directory),
                env=environment,
                capture_output=True,
                text=True,
            )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_scripts_are_context_bound_and_cannot_touch_application_namespaces(self) -> None:
        deploy = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-kubernetes-deploy.sh"
        ).read_text(encoding="utf-8")
        smoke = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-kubernetes-smoke.sh"
        ).read_text(encoding="utf-8")
        stop = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-kubernetes-stop.sh"
        ).read_text(encoding="utf-8")
        gate_deploy = (
            REPOSITORY_ROOT
            / "scripts"
            / "agentic-cicd-kubernetes-gate-deploy.sh"
        ).read_text(encoding="utf-8")
        for script in (deploy, smoke, stop, gate_deploy):
            self.assertIn('"$(kubectl config current-context)" != "$context"', script)
            self.assertNotIn("-n jstore", script)
            self.assertNotIn("-n postgresql", script)
        for script in (deploy, smoke, stop):
            self.assertNotIn("get secret", script)
        self.assertIn('namespace="agentic-cicd"', deploy)
        self.assertIn('namespace="agentic-cicd"', smoke)
        self.assertIn('namespace="agentic-cicd"', stop)
        self.assertIn("ctr --namespace k8s.io images import", deploy)
        self.assertNotIn("sudo -n", deploy)
        self.assertIn("--dry-run=server", deploy)
        self.assertIn("--build-arg HTTP_PROXY=", deploy)
        self.assertIn("--build-arg http_proxy=", deploy)
        self.assertIn('--build-context "symphony-source=$symphony_source"', deploy)
        self.assertIn("git -C \"$symphony_source\" status --porcelain", deploy)
        self.assertIn("git -C \"$repo_root\" status --porcelain", deploy)
        self.assertIn("JSTORE_CONTROLLER_REVISION", deploy)
        self.assertIn("old_pod_uid", deploy)
        self.assertIn("new_pod_uid", deploy)
        self.assertIn("image: $image", deploy)
        self.assertIn("io.jstore.controller.revision", deploy)
        self.assertIn('expected_image="docker.io/library/jstore-agentic-cicd:', deploy)
        self.assertIn('images tag "$image" "$image_ref"', deploy)
        self.assertIn('image_ref="$image_repository@$image_digest"', deploy)
        self.assertIn("org.opencontainers.image.revision", deploy)
        self.assertIn("symphony-phase-bridge.patch", deploy)
        self.assertIn("symphony-phase-routing.patch", deploy)
        self.assertIn("--credentialed-observer", deploy)
        self.assertIn("development-credentialed-observer", deploy)
        self.assertIn("patch_sha256", deploy)
        self.assertIn("dependency_lock_sha256", deploy)
        self.assertIn('lock_file="$repo_root/config/agentic-cicd/symphony.lock.json"', deploy)
        self.assertIn("routing_patch_sha256=$(read_lock routing_patch_sha256)", deploy)
        self.assertIn("codex_output=$(codex --version", deploy)
        self.assertIn('--build-arg "CODEX_VERSION=$codex_version"', deploy)
        self.assertNotIn("codex-0.146.0", deploy)
        self.assertIn(
            '[[ "$codex_version" =~ ^codex-cli\\ [0-9]+\\.[0-9]+\\.[0-9]+$ ]]',
            smoke,
        )
        source_preflight = (
            '"$repo_root/scripts/check-agentic-cicd-runtime.py" \\\n'
            '    --symphony-source "$symphony_source" \\\n'
            "    --source-only"
        )
        self.assertIn(source_preflight, deploy)
        self.assertLess(deploy.index(source_preflight), deploy.index("sudo ctr"))
        self.assertIn('[[ "$revision" == "$expected_symphony_revision" ]]', deploy)
        self.assertNotIn(
            '[[ "$revision" == "8001b52e3062495a16e520e4ceaf8f9de868c4d0" ]]',
            deploy,
        )
        self.assertNotIn(
            "b60be30500e95f7fd8d61ea4f73cab4b618e646f541ede6f67e8e0f3eac27535",
            deploy,
        )
        self.assertIn("--dry-run=server", gate_deploy)
        self.assertIn("@sha256:", gate_deploy)
        self.assertIn("run-quality-gate", gate_deploy)
        self.assertIn("capability and all GitHub writes remain disabled", gate_deploy)
        self.assertIn("auth can-i get secrets", gate_deploy)
        self.assertIn("if supervisor_token=$(kubectl", gate_deploy)
        self.assertIn("supervisor_status=$?", gate_deploy)
        self.assertIn('"$supervisor_status" -ne 1', gate_deploy)
        self.assertIn("if dispatcher_secret=$(kubectl", gate_deploy)
        self.assertIn("dispatcher_status=$?", gate_deploy)
        self.assertIn('"$dispatcher_status" -ne 1', gate_deploy)
        self.assertIn("runtime-revisions", smoke)
        self.assertIn("scale deployment/symphony --replicas=0", stop)
        self.assertNotIn("delete pvc", stop)
        for state_field in ('\"running\"', '\"counts\"', '\"codex_totals\"'):
            self.assertIn(state_field, smoke)

    def test_gate_image_distribution_is_single_archive_and_digest_bound(self) -> None:
        build = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-gate-image-build.sh"
        ).read_text(encoding="utf-8")
        image_import = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-gate-image-import.sh"
        ).read_text(encoding="utf-8")
        self.assertIn("--platform linux/amd64", build)
        self.assertIn("--provenance=false", build)
        self.assertIn("type=oci,dest=", build)
        self.assertIn("containerimage.digest", build)
        self.assertIn('tag="docker.io/library/jstore-agentic-gate:', build)
        self.assertIn('"GATE_IMAGE_REF=$repository@$digest"', build)
        self.assertIn("source repository must be clean", build)
        self.assertIn('"$actual_sha256" != "$expected_sha256"', image_import)
        self.assertIn("ctr --namespace k8s.io images import", image_import)
        self.assertIn("--image-tag", image_import)
        self.assertIn("--image-ref", image_import)
        self.assertIn("ctr --namespace k8s.io images list", image_import)
        self.assertIn("$3 == digest", image_import)
        self.assertIn('containerd_image_tag=$image_tag', image_import)

    def test_gate_image_import_binds_build_tag_to_canonical_digest_ref(self) -> None:
        script = REPOSITORY_ROOT / "scripts" / "agentic-cicd-gate-image-import.sh"
        digest = "sha256:" + "a" * 64
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "gate.oci.tar"
            archive.write_bytes(b"reviewed archive")
            archive_sha256 = hashlib.sha256(archive.read_bytes()).hexdigest()
            bin_dir = root / "bin"
            bin_dir.mkdir()
            (bin_dir / "sudo").write_text(
                "#!/bin/sh\nexec \"$@\"\n", encoding="utf-8"
            )
            (bin_dir / "tar").write_text(
                "#!/bin/sh\nprintf 'oci-layout\\nindex.json\\n'\n", encoding="utf-8"
            )
            (bin_dir / "ctr").write_text(
                "#!/bin/sh\n"
                "printf '%s\\n' \"$*\" >> \"$CTR_LOG\"\n"
                "case \"$*\" in\n"
                "  *'content list') printf '%s\\n' '"
                + digest
                + "' ;;\n"
                "  *'images list') printf '%s\\n' "
                "'REF TYPE DIGEST SIZE PLATFORMS LABELS' "
                "'docker.io/library/jstore-agentic-gate:test application/vnd.oci.image.manifest.v1+json "
                + digest
                + " 1B linux/amd64 -' "
                "'docker.io/library/jstore-agentic-gate@"
                + digest
                + " application/vnd.oci.image.manifest.v1+json "
                + digest
                + " 1B linux/amd64 io.cri-containerd.image=managed' ;;\n"
                "esac\n",
                encoding="utf-8",
            )
            for executable in bin_dir.iterdir():
                executable.chmod(0o755)
            environment = os.environ.copy()
            environment["PATH"] = f"{bin_dir}:{environment['PATH']}"
            ctr_log = root / "ctr.log"
            environment["CTR_LOG"] = str(ctr_log)
            result = subprocess.run(
                [
                    str(script),
                    "--archive",
                    str(archive),
                    "--sha256",
                    archive_sha256,
                    "--image-tag",
                    "docker.io/library/jstore-agentic-gate:test",
                    "--image-ref",
                    f"docker.io/library/jstore-agentic-gate@{digest}",
                ],
                cwd=REPOSITORY_ROOT,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )
            mismatch = subprocess.run(
                [
                    str(script),
                    "--archive",
                    str(archive),
                    "--sha256",
                    archive_sha256,
                    "--image-tag",
                    "docker.io/library/other-gate:test",
                    "--image-ref",
                    f"docker.io/library/jstore-agentic-gate@{digest}",
                ],
                cwd=REPOSITORY_ROOT,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )
            ctr_calls = ctr_log.read_text(encoding="utf-8")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(f"docker.io/library/jstore-agentic-gate@{digest}", result.stdout)
        self.assertIn(
            "images tag docker.io/library/jstore-agentic-gate:test "
            f"docker.io/library/jstore-agentic-gate@{digest}",
            ctr_calls,
        )
        self.assertIn(
            f"images label docker.io/library/jstore-agentic-gate@{digest} "
            "io.cri-containerd.image=managed",
            ctr_calls,
        )
        self.assertEqual(2, mismatch.returncode)
        self.assertIn("use different repositories", mismatch.stderr)

    def test_policy_engine_is_pinned_and_has_read_only_cluster_rbac(self) -> None:
        daemonset = by_kind_name(
            self.network_policy_engine, "DaemonSet", "kube-router-firewall"
        )
        pod = daemonset["spec"]["template"]["spec"]
        container = pod["containers"][0]
        self.assertTrue(pod["hostNetwork"])
        self.assertEqual(
            "docker.m.daocloud.io/cloudnativelabs/kube-router@sha256:0991f2cc7aaabe107b51c0c554d6b843f0483fd319b94f437fab638470c47c22",
            container["image"],
        )
        self.assertNotIn(":latest", container["image"])
        self.assertIn("--run-router=false", container["args"])
        self.assertIn("--run-firewall=true", container["args"])
        self.assertIn("--run-service-proxy=false", container["args"])
        self.assertFalse(any(volume["name"] == "cni-conf-dir" for volume in pod["volumes"]))
        self.assertNotIn("initContainers", pod)
        self.assertTrue(container["securityContext"]["privileged"])

        role = by_kind_name(
            self.network_policy_engine, "ClusterRole", "jstore-kube-router-firewall"
        )
        self.assertEqual(
            {"get", "list", "watch"},
            {verb for rule in role["rules"] for verb in rule["verbs"]},
        )
        resources = {resource for rule in role["rules"] for resource in rule["resources"]}
        self.assertEqual(
            {
                "endpoints",
                "endpointslices",
                "namespaces",
                "networkpolicies",
                "nodes",
                "pods",
                "services",
            },
            resources,
        )

    def test_gate_namespace_is_restricted_bounded_and_default_denied(self) -> None:
        namespace = by_kind_name(self.gates, "Namespace", "agentic-cicd-gates")
        self.assertEqual(
            "restricted",
            namespace["metadata"]["labels"]["pod-security.kubernetes.io/enforce"],
        )
        quota = by_kind_name(self.gates, "ResourceQuota", "gate-budget")
        self.assertEqual("2", quota["spec"]["hard"]["count/pods"])
        self.assertEqual("4", quota["spec"]["hard"]["limits.cpu"])
        self.assertEqual("8Gi", quota["spec"]["hard"]["limits.memory"])

        policy = by_kind_name(self.gates, "NetworkPolicy", "default-deny")
        self.assertEqual({}, policy["spec"]["podSelector"])
        self.assertEqual({"Ingress", "Egress"}, set(policy["spec"]["policyTypes"]))
        self.assertNotIn("ingress", policy["spec"])
        self.assertNotIn("egress", policy["spec"])

        runner = by_kind_name(self.gates, "ServiceAccount", "gate-runner")
        self.assertFalse(runner["automountServiceAccountToken"])
        self.assertFalse(any(document.get("kind") == "Secret" for document in self.gates))

    def test_gate_dispatcher_rbac_cannot_read_secrets_or_exec(self) -> None:
        role = by_kind_name(self.gates, "Role", "gate-dispatcher")
        resources = {resource for rule in role["rules"] for resource in rule["resources"]}
        self.assertEqual({"jobs", "pods", "pods/log"}, resources)
        self.assertNotIn("secrets", resources)
        self.assertNotIn("pods/exec", resources)
        binding = by_kind_name(self.gates, "RoleBinding", "gate-dispatcher")
        self.assertEqual("agentic-cicd", binding["subjects"][0]["namespace"])

    def test_broker_and_dispatcher_are_separate_disabled_control_plane_pods(self) -> None:
        broker = by_kind_name(self.gates, "Deployment", "artifact-broker")
        dispatcher = by_kind_name(self.gates, "Deployment", "gate-dispatcher")
        self.assertEqual(0, broker["spec"]["replicas"])
        self.assertEqual(0, dispatcher["spec"]["replicas"])

        broker_pod = broker["spec"]["template"]["spec"]
        dispatcher_pod = dispatcher["spec"]["template"]["spec"]
        self.assertFalse(broker_pod["automountServiceAccountToken"])
        self.assertFalse(dispatcher_pod["automountServiceAccountToken"])
        self.assertEqual("artifact-broker", broker_pod["serviceAccountName"])
        self.assertEqual("gate-dispatcher", dispatcher_pod["serviceAccountName"])
        for pod in (broker_pod, dispatcher_pod):
            self.assertEqual("k8s-master", pod["nodeSelector"]["kubernetes.io/hostname"])
            self.assertIn(
                {
                    "key": "node-role.kubernetes.io/control-plane",
                    "operator": "Exists",
                    "effect": "NoSchedule",
                },
                pod["tolerations"],
            )
        self.assertEqual(1, len(broker_pod["containers"]))
        self.assertEqual(1, len(dispatcher_pod["containers"]))

        broker_mounts = {
            value["name"] for value in broker_pod["containers"][0]["volumeMounts"]
        }
        dispatcher_mounts = {
            value["name"] for value in dispatcher_pod["containers"][0]["volumeMounts"]
        }
        self.assertIn("candidate-artifacts", broker_mounts)
        self.assertNotIn("gate-exchange", broker_mounts)
        self.assertNotIn("candidate-artifacts", dispatcher_mounts)
        self.assertIn("dispatcher-token", dispatcher_mounts)
        projected = next(
            volume["projected"] for volume in dispatcher_pod["volumes"]
            if volume["name"] == "dispatcher-token"
        )
        token_projection = projected["sources"][0]["serviceAccountToken"]
        self.assertEqual(3600, token_projection["expirationSeconds"])
        self.assertEqual(
            "https://kubernetes.default.svc.cluster.local",
            token_projection["audience"],
        )

    def test_gate_network_allows_only_broker_endpoint(self) -> None:
        egress = by_kind_name(self.gates, "NetworkPolicy", "gate-to-artifact-broker")
        rule = egress["spec"]["egress"][0]
        self.assertEqual(8081, rule["ports"][0]["port"])
        self.assertEqual(
            "artifact-broker",
            rule["to"][0]["podSelector"]["matchLabels"]["app.kubernetes.io/name"],
        )
        ingress = by_kind_name(self.gates, "NetworkPolicy", "artifact-broker-from-gates")
        self.assertEqual(
            "agentic-cicd-gates",
            ingress["spec"]["ingress"][0]["from"][0]["namespaceSelector"]["matchLabels"]["kubernetes.io/metadata.name"],
        )

    def test_offline_gate_smoke_has_no_token_or_network_and_is_bounded(self) -> None:
        job_path = GATES / "offline-smoke-job.yaml"
        job = yaml.safe_load(job_path.read_text(encoding="utf-8"))
        self.assertEqual("Job", job["kind"])
        self.assertEqual(0, job["spec"]["backoffLimit"])
        self.assertEqual(60, job["spec"]["activeDeadlineSeconds"])
        pod = job["spec"]["template"]["spec"]
        self.assertFalse(pod["automountServiceAccountToken"])
        self.assertEqual("gate-runner", pod["serviceAccountName"])
        self.assertEqual("k8s-worker1", pod["nodeSelector"]["kubernetes.io/hostname"])
        container = pod["containers"][0]
        self.assertTrue(container["securityContext"]["readOnlyRootFilesystem"])
        self.assertFalse(container["securityContext"]["allowPrivilegeEscalation"])
        self.assertEqual(["ALL"], container["securityContext"]["capabilities"]["drop"])
        self.assertIn("GATE_OFFLINE_SMOKE_PASS", container["args"][0])

    def test_preflight_requires_policy_engine_by_default(self) -> None:
        preflight = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-kubernetes-preflight.sh"
        ).read_text(encoding="utf-8")
        self.assertIn('"$(kubectl config current-context)" != "$context"', preflight)
        self.assertIn('allow_missing_engine=false', preflight)
        self.assertIn('network-policy-engine=', preflight)
        self.assertIn('symphony-rbac=create-jobs:no', preflight)
        self.assertNotIn("get secret", preflight)
        self.assertNotIn("-n postgresql", preflight)

    def test_policy_engine_scripts_are_context_bound_and_rollback_cleans_nodes(self) -> None:
        deploy = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-network-policy-deploy.sh"
        ).read_text(encoding="utf-8")
        rollback = (
            REPOSITORY_ROOT / "scripts" / "agentic-cicd-network-policy-rollback.sh"
        ).read_text(encoding="utf-8")
        for script in (deploy, rollback):
            self.assertIn('"$(kubectl config current-context)" != "$context"', script)
            self.assertIn('engine="kube-router-firewall"', script)
            self.assertNotIn("get secret", script)
            self.assertNotIn("delete namespace", script)
        self.assertIn("agentic-cicd-network-policy-smoke.sh", deploy)
        self.assertIn("agentic-cicd-network-policy-rollback.sh", deploy)
        self.assertIn("--cleanup-config", (NETWORK_POLICY_ENGINE / "cleanup-jobs.yaml").read_text(encoding="utf-8"))
        self.assertIn("kube-router-firewall-cleanup-master", rollback)
        self.assertIn("kube-router-firewall-cleanup-worker1", rollback)


if __name__ == "__main__":
    unittest.main()

import copy
import json
import os
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
APPLICATION_ROOT = REPOSITORY_ROOT / "deploy" / "kubernetes" / "application"
ENVIRONMENTS = ("development", "integration", "canary", "production")
DIGEST = "sha256:" + "a" * 64
IMAGE = f"registry.example.test/j-store/application@{DIGEST}"


def load_overlay(environment: str) -> list[dict]:
    result = subprocess.run(
        [
            "kubectl",
            "kustomize",
            str(APPLICATION_ROOT / "overlays" / environment),
        ],
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


class ImmutableMultiClusterDeliveryTest(unittest.TestCase):
    def test_all_environment_overlays_render_the_same_oci_application_contract(self) -> None:
        for environment in ENVIRONMENTS:
            with self.subTest(environment=environment):
                documents = load_overlay(environment)
                deployment = by_kind_name(documents, "Deployment", "j-store")
                pod_template = deployment["spec"]["template"]
                pod_spec = pod_template["spec"]
                container = pod_spec["containers"][0]

                self.assertEqual("jstore", deployment["metadata"]["namespace"])
                self.assertEqual(
                    environment,
                    pod_template["metadata"]["labels"][
                        "app.kubernetes.io/environment"
                    ],
                )
                self.assertRegex(
                    container["image"],
                    r"^j-store/application@sha256:[0-9a-f]{64}$",
                )
                self.assertEqual("IfNotPresent", container["imagePullPolicy"])
                self.assertTrue(container["securityContext"]["readOnlyRootFilesystem"])
                self.assertFalse(pod_spec["automountServiceAccountToken"])
                config_map_names = {
                    reference["configMapRef"]["name"]
                    for reference in container["envFrom"]
                    if "configMapRef" in reference
                }
                secret_names = {
                    reference["secretRef"]["name"]
                    for reference in container["envFrom"]
                    if "secretRef" in reference
                }
                self.assertEqual(
                    {"jstore-deployment", "jstore-runtime"}, config_map_names
                )
                self.assertEqual({"jstore-runtime"}, secret_names)

                kinds = {document["kind"] for document in documents}
                self.assertNotIn("Namespace", kinds)
                self.assertNotIn("PersistentVolumeClaim", kinds)
                self.assertNotIn("StatefulSet", kinds)
                self.assertFalse(any(document["kind"] == "Secret" for document in documents))
                serialized = yaml.safe_dump_all(documents)
                self.assertNotIn("/opt/jstore/app.jar", serialized)
                self.assertNotIn("daocloud.io", serialized)
                self.assertNotIn(":latest", serialized)

                runtime = by_kind_name(documents, "ConfigMap", "jstore-deployment")
                expected_profiles = (
                    "local,observability,outbox-observability"
                    if environment == "development"
                    else "production"
                )
                self.assertEqual(
                    expected_profiles,
                    runtime["data"]["SPRING_PROFILES_ACTIVE"],
                )
                self.assertEqual(
                    environment,
                    runtime["data"]["JSTORE_DEPLOYMENT_ENVIRONMENT"],
                )

                application_policy = by_kind_name(
                    documents, "NetworkPolicy", "application"
                )
                redis_destinations = [
                    destination
                    for rule in application_policy["spec"]["egress"]
                    if any(port["port"] == 6379 for port in rule["ports"])
                    for destination in rule["to"]
                ]
                self.assertEqual(
                    [
                        {
                            "podSelector": {
                                "matchLabels": {"app.kubernetes.io/name": "redis"}
                            }
                        }
                    ],
                    redis_destinations,
                )

    def test_canary_and_production_are_high_availability_rollouts(self) -> None:
        for environment in ("canary", "production"):
            with self.subTest(environment=environment):
                documents = load_overlay(environment)
                deployment = by_kind_name(documents, "Deployment", "j-store")
                strategy = deployment["spec"]["strategy"]
                self.assertGreaterEqual(deployment["spec"]["replicas"], 2)
                self.assertEqual("RollingUpdate", strategy["type"])
                self.assertEqual(0, strategy["rollingUpdate"]["maxUnavailable"])
                self.assertEqual(1, strategy["rollingUpdate"]["maxSurge"])
                self.assertIn(
                    "topologySpreadConstraints", deployment["spec"]["template"]["spec"]
                )
                pdb = by_kind_name(documents, "PodDisruptionBudget", "j-store")
                self.assertEqual(1, pdb["spec"]["minAvailable"])
                runtime = by_kind_name(documents, "ConfigMap", "jstore-deployment")
                self.assertEqual("false", runtime["data"]["SPRING_FLYWAY_ENABLED"])
                explicit_environment = {
                    item["name"]: item["value"]
                    for item in deployment["spec"]["template"]["spec"]["containers"][0][
                        "env"
                    ]
                }
                self.assertEqual("false", explicit_environment["SPRING_FLYWAY_ENABLED"])

    def test_renderer_requires_and_injects_an_immutable_digest(self) -> None:
        renderer = REPOSITORY_ROOT / "scripts" / "render-kubernetes-application.sh"
        invalid_environment = subprocess.run(
            [
                "bash",
                str(renderer),
                "--environment",
                "staging",
                "--image",
                IMAGE,
            ],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(2, invalid_environment.returncode)
        self.assertIn("unsupported environment", invalid_environment.stderr)

        rejected = subprocess.run(
            [
                "bash",
                str(renderer),
                "--environment",
                "production",
                "--image",
                "registry.example.test/j-store/application:latest",
            ],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("repository@sha256:digest", rejected.stderr)

        tagged_digest = subprocess.run(
            [
                "bash",
                str(renderer),
                "--environment",
                "production",
                "--image",
                f"registry.example.test/j-store/application:release@{DIGEST}",
            ],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(0, tagged_digest.returncode)
        self.assertIn("without a tag", tagged_digest.stderr)

        rendered = subprocess.run(
            [
                "bash",
                str(renderer),
                "--environment",
                "production",
                "--image",
                IMAGE,
            ],
            cwd=REPOSITORY_ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        documents = [
            document for document in yaml.safe_load_all(rendered.stdout) if document
        ]
        deployment = by_kind_name(documents, "Deployment", "j-store")
        actual_image = deployment["spec"]["template"]["spec"]["containers"][0][
            "image"
        ]
        self.assertEqual(IMAGE, actual_image)
        self.assertNotIn("j-store/application@sha256:" + "0" * 64, rendered.stdout)

    def test_runtime_and_container_build_do_not_default_to_development_inputs(self) -> None:
        application = (
            REPOSITORY_ROOT
            / "j-store-boot"
            / "src"
            / "main"
            / "resources"
            / "application.properties"
        ).read_text(encoding="utf-8")
        production = (
            REPOSITORY_ROOT
            / "j-store-boot"
            / "src"
            / "main"
            / "resources"
            / "application-production.properties"
        ).read_text(encoding="utf-8")
        dockerfile = (REPOSITORY_ROOT / "j-store-boot" / "Dockerfile").read_text(
            encoding="utf-8"
        )

        self.assertNotIn("spring.profiles.active=local", application)
        for required in (
            "${JSTORE_DB_URL}",
            "${JSTORE_DB_USER}",
            "${JSTORE_DB_PASSWORD}",
            "${JSTORE_REDIS_HOST}",
            "${JSTORE_REDIS_PASSWORD}",
        ):
            self.assertIn(required, production)
        first_line = dockerfile.splitlines()[0]
        self.assertEqual(
            "FROM amazoncorretto:25-alpine3.24@sha256:"
            "027310590da693629c2cf704d2f87e9359c33ee2f02bcaa777680b2f4b94f4c7",
            first_line,
        )

    def test_legacy_latest_manifest_is_removed(self) -> None:
        self.assertFalse((REPOSITORY_ROOT / "j-store-boot" / "k8s-deployment.yaml").exists())
        deployment_files = [
            path
            for path in REPOSITORY_ROOT.rglob("*.yaml")
            if ".qoder" not in path.parts and "build" not in path.parts
        ]
        for path in deployment_files:
            text = path.read_text(encoding="utf-8")
            self.assertIsNone(
                re.search(r"^\s*image:\s*\S+:latest\s*$", text, re.MULTILINE),
                path,
            )

    def test_cluster_target_configuration_has_no_credentials(self) -> None:
        schema = json.loads(
            (
                REPOSITORY_ROOT / "config" / "cicd" / "cluster-target.schema.json"
            ).read_text(encoding="utf-8")
        )
        example = json.loads(
            (
                REPOSITORY_ROOT
                / "config"
                / "cicd"
                / "cluster-targets.example.json"
            ).read_text(encoding="utf-8")
        )
        validator = Draft202012Validator(schema)
        validator.validate(example)
        self.assertEqual(set(ENVIRONMENTS), set(example["targets"]))
        self.assertTrue(
            all("registry" not in target for target in example["targets"].values())
        )
        serialized = json.dumps(example).lower()
        for forbidden in ("password", "token", "kubeconfig", "privatekey"):
            self.assertNotIn(forbidden, serialized)

        unsafe_configs = []

        production_without_approval = copy.deepcopy(example)
        production_without_approval["targets"]["production"]["requiresApproval"] = False
        unsafe_configs.append(
            ("production approval disabled", production_without_approval)
        )

        canary_without_approval = copy.deepcopy(example)
        canary_without_approval["targets"]["canary"]["requiresApproval"] = False
        unsafe_configs.append(("canary approval disabled", canary_without_approval))

        tagged_repository = copy.deepcopy(example)
        tagged_repository["targets"]["production"]["repository"] += ":latest"
        unsafe_configs.append(("tagged repository", tagged_repository))

        embedded_credentials = copy.deepcopy(example)
        embedded_credentials["targets"]["production"]["registry"] = (
            "https://user:password@registry.production.internal"
        )
        unsafe_configs.append(("embedded registry credentials", embedded_credentials))

        for name, candidate in unsafe_configs:
            with self.subTest(name=name):
                self.assertTrue(list(validator.iter_errors(candidate)))

    def run_deployer(
        self,
        *,
        current_context: str = "jstore-production",
        actual_cluster_uid: str = "11111111-1111-1111-1111-111111111111",
        config_map_exists: bool = True,
        secret_exists: bool = True,
    ) -> tuple[subprocess.CompletedProcess[str], list[str]]:
        real_kubectl = shutil.which("kubectl")
        self.assertIsNotNone(real_kubectl)

        with tempfile.TemporaryDirectory() as directory:
            temp_root = Path(directory)
            bin_dir = temp_root / "bin"
            bin_dir.mkdir()
            log_path = temp_root / "kubectl.log"
            fake_kubectl = bin_dir / "kubectl"
            fake_kubectl.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    set -euo pipefail

                    if [[ "${1:-}" == "kustomize" ]]; then
                      exec "$REAL_KUBECTL" "$@"
                    fi

                    printf '%s\n' "$*" >>"$FAKE_KUBECTL_LOG"
                    if [[ "$*" == "config current-context" ]]; then
                      printf '%s\n' "$FAKE_CURRENT_CONTEXT"
                    elif [[ "$*" == *"get namespace kube-system"* ]]; then
                      printf '%s' "$FAKE_CLUSTER_UID"
                    elif [[ "$*" == *"get configmap jstore-runtime"* ]]; then
                      [[ "$FAKE_CONFIG_MAP_EXISTS" == "true" ]]
                    elif [[ "$*" == *"get secret jstore-runtime"* ]]; then
                      [[ "$FAKE_SECRET_EXISTS" == "true" ]]
                    elif [[ "$*" == *" apply "* || "$*" == *" rollout status "* ]]; then
                      exit 0
                    else
                      printf 'unexpected fake kubectl invocation: %s\n' "$*" >&2
                      exit 2
                    fi
                    """
                ),
                encoding="utf-8",
            )
            fake_kubectl.chmod(0o755)

            environment = os.environ.copy()
            environment.update(
                {
                    "PATH": f"{bin_dir}{os.pathsep}{environment['PATH']}",
                    "REAL_KUBECTL": str(real_kubectl),
                    "FAKE_KUBECTL_LOG": str(log_path),
                    "FAKE_CURRENT_CONTEXT": current_context,
                    "FAKE_CLUSTER_UID": actual_cluster_uid,
                    "FAKE_CONFIG_MAP_EXISTS": (
                        "true" if config_map_exists else "false"
                    ),
                    "FAKE_SECRET_EXISTS": "true" if secret_exists else "false",
                }
            )
            result = subprocess.run(
                [
                    "bash",
                    str(REPOSITORY_ROOT / "scripts" / "deploy-kubernetes-application.sh"),
                    "--context",
                    "jstore-production",
                    "--expected-cluster-uid",
                    "11111111-1111-1111-1111-111111111111",
                    "--environment",
                    "production",
                    "--namespace",
                    "jstore",
                    "--image",
                    IMAGE,
                ],
                cwd=REPOSITORY_ROOT,
                env=environment,
                capture_output=True,
                text=True,
            )
            calls = log_path.read_text(encoding="utf-8").splitlines()
            return result, calls

    def test_deployer_rejects_wrong_context_before_apply(self) -> None:
        result, calls = self.run_deployer(current_context="jstore-development")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("must equal the current kubectl context", result.stderr)
        self.assertFalse(any(" apply " in f" {call} " for call in calls))

    def test_deployer_rejects_wrong_cluster_uid_before_apply(self) -> None:
        result, calls = self.run_deployer(
            actual_cluster_uid="22222222-2222-2222-2222-222222222222"
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("target cluster UID mismatch", result.stderr)
        self.assertFalse(any(" apply " in f" {call} " for call in calls))

    def test_deployer_requires_the_external_secret_before_apply(self) -> None:
        result, calls = self.run_deployer(secret_exists=False)

        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(" apply " in f" {call} " for call in calls))

    def test_deployer_requires_the_external_runtime_config_before_apply(self) -> None:
        result, calls = self.run_deployer(config_map_exists=False)

        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(" apply " in f" {call} " for call in calls))

    def test_deployer_dry_runs_before_apply_and_rollout(self) -> None:
        result, calls = self.run_deployer()

        self.assertEqual(0, result.returncode, result.stderr)
        dry_run_index = next(
            index for index, call in enumerate(calls) if "--dry-run=server" in call
        )
        apply_index = next(
            index
            for index, call in enumerate(calls)
            if " apply " in f" {call} " and "--dry-run=server" not in call
        )
        rollout_index = next(
            index for index, call in enumerate(calls) if " rollout status " in f" {call} "
        )
        self.assertLess(dry_run_index, apply_index)
        self.assertLess(apply_index, rollout_index)

    def test_candidate_builder_builds_once_with_sbom_and_provenance(self) -> None:
        builder_path = REPOSITORY_ROOT / "scripts" / "build-oci-candidate.sh"
        builder = builder_path.read_text(encoding="utf-8")
        self.assertIn("docker buildx build", builder)
        self.assertIn("--provenance=mode=max", builder)
        self.assertIn("--sbom=true", builder)
        self.assertIn("containerimage.digest", builder)
        self.assertIn("JSTORE_IMAGE_REF", builder)
        self.assertNotIn("kubectl apply", builder)

        tagged_repository = subprocess.run(
            [
                "bash",
                str(builder_path),
                "--repository",
                "registry.example.test/j-store/application:release",
                "--output-dir",
                "/tmp/jstore-candidate-test",
            ],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(2, tagged_repository.returncode)
        self.assertIn("without tag or digest", tagged_repository.stderr)

    def test_security_workflow_scans_an_attested_oci_archive(self) -> None:
        workflow_text = (
            REPOSITORY_ROOT / ".github" / "workflows" / "security.yml"
        ).read_text(encoding="utf-8")
        workflow = yaml.safe_load(workflow_text)
        steps = workflow["jobs"]["dependency-vulnerability-scan"]["steps"]
        buildx_setup_index = next(
            index
            for index, step in enumerate(steps)
            if step.get("uses") == "docker/setup-buildx-action@v4"
        )
        attested_build_index = next(
            index
            for index, step in enumerate(steps)
            if step.get("name")
            == "Build attested OCI archive for container security scanning"
        )
        archive_conversion_index = next(
            index
            for index, step in enumerate(steps)
            if step.get("name")
            == "Convert attested OCI archive for OSV Scanner"
        )
        container_scan = next(
            step
            for step in steps
            if step.get("name")
            == "Scan container operating-system and application packages"
        )
        attested_build = steps[attested_build_index]
        self.assertLess(buildx_setup_index, attested_build_index)
        self.assertIn(
            "--output type=oci,dest=j-store-security.tar", attested_build["run"]
        )
        self.assertNotIn("type=docker", attested_build["run"])
        archive_conversion = steps[archive_conversion_index]
        self.assertLess(attested_build_index, archive_conversion_index)
        self.assertLess(archive_conversion_index, steps.index(container_scan))
        self.assertRegex(
            archive_conversion["env"]["SKOPEO_IMAGE"],
            r"^quay\.io/skopeo/stable@sha256:[0-9a-f]{64}$",
        )
        self.assertIn(
            "oci-archive:/work/j-store-security.tar",
            archive_conversion["run"],
        )
        self.assertIn(
            "docker-archive:/output/j-store-security-docker.tar",
            archive_conversion["run"],
        )
        self.assertIn('--network none', archive_conversion["run"])
        self.assertIn('${GITHUB_WORKSPACE}:/work:ro', archive_conversion["run"])
        self.assertIn('${RUNNER_TEMP}:/output', archive_conversion["run"])
        self.assertIn("BUILDX_METADATA_PROVENANCE: max", workflow_text)
        self.assertIn(
            'scan image --archive "$RUNNER_TEMP/j-store-security-docker.tar"',
            container_scan["run"],
        )
        self.assertNotIn("--load", workflow_text)


if __name__ == "__main__":
    unittest.main()

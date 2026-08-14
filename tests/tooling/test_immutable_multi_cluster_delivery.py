import json
import re
import subprocess
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
                env_from_names = {
                    reference.get("configMapRef", reference.get("secretRef"))["name"]
                    for reference in container["envFrom"]
                }
                self.assertEqual(
                    {"jstore-deployment", "jstore-runtime"}, env_from_names
                )

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
                self.assertEqual(
                    "runtime,observability",
                    runtime["data"]["SPRING_PROFILES_ACTIVE"],
                )
                self.assertEqual(
                    environment,
                    runtime["data"]["JSTORE_DEPLOYMENT_ENVIRONMENT"],
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

    def test_renderer_requires_and_injects_an_immutable_digest(self) -> None:
        renderer = REPOSITORY_ROOT / "scripts" / "render-kubernetes-application.sh"
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
        runtime = (
            REPOSITORY_ROOT
            / "j-store-boot"
            / "src"
            / "main"
            / "resources"
            / "application-runtime.properties"
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
            self.assertIn(required, runtime)
        first_line = dockerfile.splitlines()[0]
        self.assertRegex(first_line, r"^FROM .+@sha256:[0-9a-f]{64}$")

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
        Draft202012Validator(schema).validate(example)
        self.assertEqual(set(ENVIRONMENTS), set(example["targets"]))
        serialized = json.dumps(example).lower()
        for forbidden in ("password", "token", "kubeconfig", "privatekey"):
            self.assertNotIn(forbidden, serialized)

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
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / "security.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("--output type=oci,dest=j-store-security.tar", workflow)
        self.assertIn("BUILDX_METADATA_PROVENANCE: max", workflow)
        self.assertIn("scan image --archive", workflow)
        self.assertNotIn("--load", workflow)


if __name__ == "__main__":
    unittest.main()

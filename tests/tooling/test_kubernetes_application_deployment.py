import subprocess
import unittest
from pathlib import Path

import yaml


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
BASE = REPOSITORY_ROOT / "deploy" / "kubernetes" / "application" / "base"


def load_documents() -> list[dict]:
    result = subprocess.run(
        ["kubectl", "kustomize", str(BASE)],
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


class KubernetesApplicationDeploymentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.documents = load_documents()

    def test_namespace_and_repository_do_not_embed_secrets(self) -> None:
        namespace = by_kind_name(self.documents, "Namespace", "jstore")
        labels = namespace["metadata"]["labels"]
        self.assertEqual("restricted", labels["pod-security.kubernetes.io/enforce"])
        self.assertFalse(any(document.get("kind") == "Secret" for document in self.documents))

    def test_application_uses_java_25_artifact_pvc_and_hardened_runtime(self) -> None:
        deployment = by_kind_name(self.documents, "Deployment", "j-store")
        pod_spec = deployment["spec"]["template"]["spec"]
        container = pod_spec["containers"][0]
        serialized = yaml.safe_dump(deployment)

        self.assertEqual(
            "docker.m.daocloud.io/library/amazoncorretto:25-al2023-headless",
            container["image"],
        )
        self.assertIn("/opt/jstore/app.jar", container["args"])
        self.assertIn("jstore-artifact", serialized)
        self.assertIn("jstore-runtime", serialized)
        for key in ("startupProbe", "readinessProbe", "livenessProbe", "resources"):
            self.assertIn(key, container)
        security = container["securityContext"]
        self.assertTrue(security["readOnlyRootFilesystem"])
        self.assertFalse(security["allowPrivilegeEscalation"])
        self.assertEqual(["ALL"], security["capabilities"]["drop"])
        self.assertFalse(pod_spec["automountServiceAccountToken"])

    def test_redis_is_authenticated_persistent_and_not_public(self) -> None:
        redis = by_kind_name(self.documents, "StatefulSet", "redis")
        container = redis["spec"]["template"]["spec"]["containers"][0]
        serialized = yaml.safe_dump(redis)
        self.assertEqual(
            "docker.m.daocloud.io/library/redis:7.4.5-alpine",
            container["image"],
        )
        self.assertIn("jstore-runtime", serialized)
        self.assertIn("requirepass", serialized)
        self.assertIn("volumeClaimTemplates", redis["spec"])
        service = by_kind_name(self.documents, "Service", "redis")
        self.assertEqual("ClusterIP", service["spec"].get("type", "ClusterIP"))

    def test_existing_prometheus_and_grafana_discover_application(self) -> None:
        monitor = by_kind_name(self.documents, "ServiceMonitor", "j-store")
        self.assertEqual("/actuator/prometheus", monitor["spec"]["endpoints"][0]["path"])
        dashboard = by_kind_name(self.documents, "ConfigMap", "j-store-grafana-dashboard")
        self.assertEqual("1", dashboard["metadata"]["labels"]["grafana_dashboard"])
        self.assertIn('"uid": "j-store-runtime"', dashboard["data"]["j-store-runtime.json"])

    def test_services_are_internal_and_default_deny_is_declared(self) -> None:
        for service in (
            document for document in self.documents if document.get("kind") == "Service"
        ):
            self.assertEqual("ClusterIP", service["spec"].get("type", "ClusterIP"))
        self.assertFalse(
            any(document.get("kind") in {"Ingress", "IngressClass"} for document in self.documents)
        )
        policy = by_kind_name(self.documents, "NetworkPolicy", "default-deny")
        self.assertEqual({}, policy["spec"]["podSelector"])
        self.assertEqual({"Ingress", "Egress"}, set(policy["spec"]["policyTypes"]))

    def test_deploy_script_is_context_bound_and_uses_isolated_database(self) -> None:
        script = (
            REPOSITORY_ROOT / "scripts" / "kubernetes-development-deploy.sh"
        ).read_text(encoding="utf-8")
        self.assertIn('"$(kubectl config current-context)" != "$context"', script)
        self.assertIn('[[ "$namespace" != "jstore" ]]', script)
        self.assertIn("j_store_codex", script)
        self.assertIn("jstore_app", script)
        self.assertIn("sha256sum /opt/jstore/app.jar", script)
        self.assertIn("get secret jstore-runtime", script)
        self.assertIn("existing Secret key is missing or too short", script)
        self.assertNotIn("j_store OWNER", script)


if __name__ == "__main__":
    unittest.main()

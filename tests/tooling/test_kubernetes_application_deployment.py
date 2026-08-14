import json
import subprocess
import unittest
from pathlib import Path

import yaml


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
BASE = (
    REPOSITORY_ROOT
    / "deploy"
    / "kubernetes"
    / "application"
    / "overlays"
    / "development"
)


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

    def test_namespace_and_secrets_are_owned_by_the_platform(self) -> None:
        self.assertFalse(any(document.get("kind") == "Namespace" for document in self.documents))
        self.assertFalse(any(document.get("kind") == "Secret" for document in self.documents))
        namespaced = [
            document
            for document in self.documents
            if document.get("kind")
            not in {"Namespace", "ClusterRole", "ClusterRoleBinding"}
        ]
        self.assertTrue(namespaced)
        self.assertTrue(
            all(document["metadata"].get("namespace") == "jstore" for document in namespaced)
        )

    def test_application_uses_immutable_oci_image_and_hardened_runtime(self) -> None:
        deployment = by_kind_name(self.documents, "Deployment", "j-store")
        pod_spec = deployment["spec"]["template"]["spec"]
        container = pod_spec["containers"][0]
        serialized = yaml.safe_dump(deployment)

        self.assertRegex(
            container["image"], r"^j-store/application@sha256:[0-9a-f]{64}$"
        )
        self.assertNotIn("/opt/jstore/app.jar", serialized)
        self.assertNotIn("jstore-artifact", serialized)
        self.assertIn("jstore-runtime", serialized)
        for key in ("startupProbe", "readinessProbe", "livenessProbe", "resources"):
            self.assertIn(key, container)
        security = container["securityContext"]
        self.assertTrue(security["readOnlyRootFilesystem"])
        self.assertFalse(security["allowPrivilegeEscalation"])
        self.assertEqual(["ALL"], security["capabilities"]["drop"])
        self.assertFalse(pod_spec["automountServiceAccountToken"])

    def test_platform_infrastructure_is_not_owned_by_application_manifests(self) -> None:
        kinds = {document["kind"] for document in self.documents}
        self.assertNotIn("StatefulSet", kinds)
        self.assertNotIn("PersistentVolumeClaim", kinds)

    def test_existing_prometheus_and_grafana_discover_application(self) -> None:
        monitor = by_kind_name(self.documents, "ServiceMonitor", "j-store")
        self.assertEqual("/actuator/prometheus", monitor["spec"]["endpoints"][0]["path"])
        dashboard = by_kind_name(self.documents, "ConfigMap", "j-store-grafana-dashboard")
        self.assertEqual("1", dashboard["metadata"]["labels"]["grafana_dashboard"])
        self.assertIn('"uid": "j-store-runtime"', dashboard["data"]["j-store-runtime.json"])

    def test_runtime_dashboard_covers_application_jvm_and_pod_signals(self) -> None:
        dashboard_config = by_kind_name(
            self.documents, "ConfigMap", "j-store-grafana-dashboard"
        )
        dashboard = json.loads(dashboard_config["data"]["j-store-runtime.json"])
        panels = {panel["title"]: panel for panel in dashboard["panels"]}

        expected_titles = {
            "Overall QPS",
            "Business QPS",
            "Average request duration",
            "Maximum request duration",
            "QPS by endpoint",
            "Request duration by endpoint",
            "Tomcat threads",
            "Heap pools used",
            "GC count rate",
            "GC pause time",
            "JVM process CPU",
            "Pod CPU usage",
            "Pod memory working set",
            "Pod resource utilization",
            "Pod restarts",
        }
        self.assertEqual(set(), expected_titles - panels.keys())

        expressions = "\n".join(
            target["expr"]
            for panel in dashboard["panels"]
            for target in panel.get("targets", [])
        )
        for metric in (
            "http_server_requests_seconds_count",
            "http_server_requests_seconds_sum",
            "http_server_requests_seconds_max",
            "tomcat_threads_busy_threads",
            "tomcat_threads_current_threads",
            "tomcat_threads_config_max_threads",
            "jvm_memory_used_bytes",
            "jvm_gc_pause_seconds_count",
            "jvm_gc_pause_seconds_sum",
            "process_cpu_usage",
            "container_cpu_usage_seconds_total",
            "container_memory_working_set_bytes",
            "kube_pod_container_resource_limits",
            "kube_pod_container_status_restarts_total",
        ):
            self.assertIn(metric, expressions)
        self.assertIn('uri!~"/actuator.*"', expressions)
        self.assertIn("by (method, uri)", expressions)
        self.assertIn("by (id)", expressions)

    def test_observability_profile_enables_tomcat_thread_metrics(self) -> None:
        properties = (
            REPOSITORY_ROOT
            / "j-store-boot"
            / "src"
            / "main"
            / "resources"
            / "application-observability.properties"
        ).read_text(encoding="utf-8")
        self.assertIn("server.tomcat.mbeanregistry.enabled=true", properties)
        runtime = by_kind_name(self.documents, "ConfigMap", "jstore-deployment")
        self.assertEqual(
            "true", runtime["data"]["SERVER_TOMCAT_MBEANREGISTRY_ENABLED"]
        )

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
        application_policy = by_kind_name(self.documents, "NetworkPolicy", "application")
        ingress_sources = application_policy["spec"]["ingress"][0]["from"]
        self.assertIn(
            {"namespaceSelector": {"matchLabels": {"jstore.network/ingress-access": "true"}}},
            ingress_sources,
        )

    def test_deploy_script_is_context_and_cluster_identity_bound(self) -> None:
        script = (
            REPOSITORY_ROOT / "scripts" / "deploy-kubernetes-application.sh"
        ).read_text(encoding="utf-8")
        self.assertIn('"$(kubectl config current-context)" != "$context"', script)
        self.assertIn("expected_cluster_uid", script)
        self.assertIn("actual_cluster_uid", script)
        self.assertIn("--dry-run=server", script)
        self.assertIn('"$namespace" != "jstore"', script)
        self.assertIn("get secret jstore-runtime", script)
        self.assertNotIn("create secret", script)
        self.assertNotIn("CREATE ROLE", script)
        self.assertNotIn("get --raw", script)


if __name__ == "__main__":
    unittest.main()

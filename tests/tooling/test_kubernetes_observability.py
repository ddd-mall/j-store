import re
import subprocess
import unittest
from pathlib import Path

import yaml


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
BASE = REPOSITORY_ROOT / "deploy" / "kubernetes" / "observability" / "base"


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


def containers(workload: dict) -> list[dict]:
    return workload["spec"]["template"]["spec"]["containers"]


class KubernetesObservabilityManifestTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.documents = load_documents()

    def test_alloy_is_a_node_sharded_daemonset_with_bounded_wal(self) -> None:
        alloy = by_kind_name(self.documents, "DaemonSet", "alloy")
        pod_spec = alloy["spec"]["template"]["spec"]
        config = by_kind_name(self.documents, "ConfigMap", "alloy-config")["data"][
            "config.alloy"
        ]

        self.assertIn('field = "spec.nodeName=" + sys.env("HOSTNAME")', config)
        self.assertIn('label = "jstore.logs/enabled=true"', config)
        self.assertIn("wal {", config)
        self.assertIn('capacity          = "32MiB"', config)
        self.assertIn("block_on_overflow = false", config)
        storage = next(volume for volume in pod_spec["volumes"] if volume["name"] == "storage")
        self.assertEqual("1Gi", storage["emptyDir"]["sizeLimit"])
        self.assertNotIn("hostPath", yaml.safe_dump(pod_spec["volumes"]))
        self.assertEqual("alloy", pod_spec["serviceAccountName"])
        self.assertEqual({"kubernetes.io/os": "linux"}, pod_spec["nodeSelector"])

        namespace = by_kind_name(self.documents, "Namespace", "jstore-observability")
        self.assertEqual(
            "restricted",
            namespace["metadata"]["labels"]["pod-security.kubernetes.io/enforce"],
        )

    def test_high_cardinality_fields_are_structured_metadata_not_labels(self) -> None:
        config = by_kind_name(self.documents, "ConfigMap", "alloy-config")["data"][
            "config.alloy"
        ]
        labels_block = config.split("stage.labels", 1)[1].split("stage.structured_metadata", 1)[0]
        metadata_block = config.split("stage.structured_metadata", 1)[1]

        for field in ("pod", "node", "trace_id", "correlation_id", "message_id"):
            self.assertNotIn(f"{field} =", labels_block)
            self.assertIn(field, metadata_block)
        for field in ("service_name", "environment", "namespace", "container"):
            self.assertIn(field, labels_block)

    def test_alloy_rbac_is_read_only_and_cannot_read_secrets(self) -> None:
        role = by_kind_name(self.documents, "ClusterRole", "alloy-log-reader")
        rules = role["rules"]
        serialized = yaml.safe_dump(rules)

        self.assertNotIn("secrets", serialized)
        self.assertNotIn("'*'", serialized)
        self.assertNotIn('"*"', serialized)
        self.assertEqual({"get", "list", "watch"}, {verb for rule in rules for verb in rule["verbs"]})
        self.assertTrue(any("pods/log" in rule["resources"] for rule in rules))

        prometheus_role = by_kind_name(
            self.documents, "ClusterRole", "jstore-prometheus-discovery"
        )
        self.assertEqual(
            [
                {
                    "apiGroups": [""],
                    "resources": ["pods"],
                    "verbs": ["get", "list", "watch"],
                }
            ],
            prometheus_role["rules"],
        )

    def test_gateway_requires_tls_and_auth_without_committed_secret(self) -> None:
        gateway = by_kind_name(self.documents, "Deployment", "loki-gateway")
        config = by_kind_name(self.documents, "ConfigMap", "loki-gateway-config")["data"][
            "nginx.conf"
        ]
        alloy_config = by_kind_name(self.documents, "ConfigMap", "alloy-config")["data"][
            "config.alloy"
        ]

        self.assertIn("ssl_protocols TLSv1.2 TLSv1.3", config)
        self.assertIn("auth_basic_user_file", config)
        self.assertRegex(
            alloy_config,
            r'url\s*=\s*"https://loki-gateway/loki/api/v1/push"',
        )
        self.assertIn("basic_auth {", alloy_config)
        self.assertRegex(
            alloy_config,
            r'ca_file\s*=\s*"/var/run/secrets/loki/ca.crt"',
        )
        self.assertNotIn("insecure_skip_verify", alloy_config)
        self.assertFalse(any(document.get("kind") == "Secret" for document in self.documents))
        self.assertTrue(containers(gateway)[0]["securityContext"]["readOnlyRootFilesystem"])

    def test_stateful_backends_have_pvc_resources_and_health_probes(self) -> None:
        expected_claims = {"loki": "loki-data", "prometheus": "prometheus-data", "grafana": "grafana-data"}
        for name, claim_name in expected_claims.items():
            workload = by_kind_name(self.documents, "StatefulSet", name)
            container = containers(workload)[0]
            claims = workload["spec"]["volumeClaimTemplates"]
            self.assertIn(claim_name, {claim["metadata"]["name"] for claim in claims})
            self.assertIn("requests", container["resources"])
            self.assertIn("limits", container["resources"])
            self.assertIn("startupProbe", container)
            self.assertIn("readinessProbe", container)
            self.assertIn("livenessProbe", container)
            self.assertTrue(container["securityContext"]["readOnlyRootFilesystem"])

        prometheus_config = by_kind_name(
            self.documents, "ConfigMap", "prometheus-config"
        )["data"]["prometheus.yml"]
        alert_config = by_kind_name(
            self.documents, "ConfigMap", "prometheus-alerts"
        )["data"]["observability-alerts.yml"]
        self.assertIn("/etc/prometheus/rules/*.yml", prometheus_config)
        for alert in (
            "JStoreAlloyTargetDown",
            "JStoreAlloyDroppedLogs",
            "JStoreAlloyWriteRetries",
            "JStoreLokiUnavailable",
            "JStoreApplicationMetricsTargetDown",
            "JStoreOutboxReadyLagHigh",
            "JStoreOutboxDeadLettersPresent",
            "JStoreOutboxExpiredLocksPresent",
            "JStoreOutboxSchedulerFailing",
        ):
            self.assertIn(f"alert: {alert}", alert_config)

        rules = {
            rule["alert"]: rule
            for group in yaml.safe_load(alert_config)["groups"]
            for rule in group["rules"]
        }
        expected_outbox_metrics = {
            "JStoreOutboxReadyLagHigh": "jstore_outbox_alert",
            "JStoreOutboxDeadLettersPresent": "jstore_outbox_alert",
            "JStoreOutboxExpiredLocksPresent": "jstore_outbox_alert",
            "JStoreOutboxSchedulerFailing": "jstore_outbox_alert",
        }
        for alert, metric in expected_outbox_metrics.items():
            expression = rules[alert]["expr"]
            self.assertIn(metric, expression)
            self.assertNotRegex(
                expression,
                r"eventType|aggregateType|aggregateId|messageId|correlationId|userId",
            )
        for alert in expected_outbox_metrics:
            self.assertIn('transportId="all"', rules[alert]["expr"])

    def test_services_are_cluster_internal_and_network_policies_default_deny(self) -> None:
        for service in (document for document in self.documents if document.get("kind") == "Service"):
            self.assertEqual("ClusterIP", service["spec"].get("type", "ClusterIP"))
        self.assertFalse(
            any(document.get("kind") in {"Ingress", "IngressClass"} for document in self.documents)
        )

        policy = by_kind_name(self.documents, "NetworkPolicy", "default-deny")
        self.assertEqual({}, policy["spec"]["podSelector"])
        self.assertEqual({"Ingress", "Egress"}, set(policy["spec"]["policyTypes"]))
        self.assertEqual([], policy["spec"]["ingress"])
        self.assertEqual([], policy["spec"]["egress"])

    def test_every_workload_has_hardened_security_and_pinned_images(self) -> None:
        workloads = [
            document
            for document in self.documents
            if document.get("kind") in {"DaemonSet", "Deployment", "StatefulSet"}
        ]
        self.assertGreaterEqual(len(workloads), 5)
        for workload in workloads:
            pod_spec = workload["spec"]["template"]["spec"]
            self.assertFalse(pod_spec.get("hostNetwork", False))
            for container in containers(workload):
                image = container["image"]
                self.assertIn(":", image)
                self.assertNotIn(":latest", image)
                security = container["securityContext"]
                self.assertFalse(security["allowPrivilegeEscalation"])
                self.assertFalse(security.get("privileged", False))
                self.assertEqual(["ALL"], security["capabilities"]["drop"])

    def test_development_registry_overlay_changes_only_registry_names(self) -> None:
        overlay = (
            REPOSITORY_ROOT
            / "deploy"
            / "kubernetes"
            / "observability"
            / "overlays"
            / "development-registry-mirror"
        )
        result = subprocess.run(
            ["kubectl", "kustomize", str(overlay)],
            cwd=REPOSITORY_ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        documents = [document for document in yaml.safe_load_all(result.stdout) if document]
        images = {
            container["image"]
            for document in documents
            if document.get("kind") in {"DaemonSet", "Deployment", "StatefulSet"}
            for container in containers(document)
        }
        self.assertEqual(
            {
                "docker.m.daocloud.io/grafana/alloy:v1.18.0",
                "docker.m.daocloud.io/grafana/grafana:13.1.0",
                "docker.m.daocloud.io/grafana/loki:3.6.12",
                "docker.m.daocloud.io/nginxinc/nginx-unprivileged:1.29.1-alpine",
                "quay.m.daocloud.io/prometheus/prometheus:v3.13.2",
            },
            images,
        )


if __name__ == "__main__":
    unittest.main()

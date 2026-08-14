import subprocess
import unittest
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


class AgenticCicdKubernetesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.base = render(BASE)
        cls.development = render(DEVELOPMENT)

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
        self.assertEqual(1, len(volumes))
        self.assertEqual("Retain", volumes[0]["spec"]["persistentVolumeReclaimPolicy"])
        self.assertEqual(
            "/var/lib/jstore-agentic-cicd", volumes[0]["spec"]["local"]["path"]
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
        generated = by_kind_name(self.base, "ConfigMap", "symphony-workflow")
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
            "max_turns: 12",
            "thread_sandbox: read-only",
            "type: readOnly",
            "sandbox_approval: true",
            "不得修改文件",
            "不得自动合并、发布或写生产",
        ):
            self.assertIn(contract, deployed_workflow)

    def test_image_and_development_node_are_explicitly_pinned(self) -> None:
        deployment = by_kind_name(self.development, "Deployment", "symphony")
        pod = deployment["spec"]["template"]["spec"]
        container = pod["containers"][0]
        self.assertEqual(
            "jstore-agentic-cicd:8001b52e-codex-0.146.0", container["image"]
        )
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
        self.assertIn("ARG CODEX_VERSION=0.146.0", dockerfile)
        self.assertIn(
            "COPY --from=symphony-source /elixir /build/symphony/elixir",
            dockerfile,
        )
        self.assertNotIn(":latest", dockerfile)

    def test_dashboard_is_internal_and_probed(self) -> None:
        service = by_kind_name(self.base, "Service", "symphony")
        self.assertEqual("ClusterIP", service["spec"].get("type", "ClusterIP"))
        self.assertFalse(any(document.get("kind") == "Ingress" for document in self.base))
        deployment = by_kind_name(self.base, "Deployment", "symphony")
        container = deployment["spec"]["template"]["spec"]["containers"][0]
        self.assertEqual("/api/v1/state", container["readinessProbe"]["httpGet"]["path"])
        self.assertEqual("/api/v1/state", container["livenessProbe"]["httpGet"]["path"])

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
        for script in (deploy, smoke, stop):
            self.assertIn('"$(kubectl config current-context)" != "$context"', script)
            self.assertIn('namespace="agentic-cicd"', script)
            self.assertNotIn("-n jstore", script)
            self.assertNotIn("-n postgresql", script)
            self.assertNotIn("get secret", script)
        self.assertIn("ctr --namespace k8s.io images import", deploy)
        self.assertNotIn("sudo -n", deploy)
        self.assertIn("--dry-run=server", deploy)
        self.assertIn("--build-arg HTTP_PROXY=", deploy)
        self.assertIn("--build-arg http_proxy=", deploy)
        self.assertIn('--build-context "symphony-source=$symphony_source"', deploy)
        self.assertIn("git -C \"$symphony_source\" status --porcelain", deploy)
        self.assertIn("scale deployment/symphony --replicas=0", stop)
        self.assertNotIn("delete pvc", stop)
        for state_field in ('\"running\"', '\"counts\"', '\"codex_totals\"'):
            self.assertIn(state_field, smoke)


if __name__ == "__main__":
    unittest.main()

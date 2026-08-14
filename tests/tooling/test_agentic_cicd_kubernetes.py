import subprocess
import unittest
import json
import hashlib
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
        self.assertIn("ARG JSTORE_CONTROLLER_REVISION", dockerfile)
        self.assertIn(
            "COPY scripts/agentic_cicd /opt/jstore-agentic-controller/agentic_cicd",
            dockerfile,
        )
        self.assertIn(
            "COPY scripts/agentic-cicd-controller.py /opt/jstore-agentic-controller/controller.py",
            dockerfile,
        )
        self.assertIn(
            "COPY config/agentic-cicd/state-contract.json /opt/jstore-agentic-controller/state-contract.json",
            dockerfile,
        )
        self.assertIn("io.jstore.controller.revision", dockerfile)
        self.assertIn(
            "COPY deploy/kubernetes/agentic-cicd/patches/symphony-phase-bridge.patch",
            dockerfile,
        )
        self.assertIn(
            "COPY deploy/kubernetes/agentic-cicd/patches/symphony-phase-routing.patch",
            dockerfile,
        )
        self.assertIn("git apply --recount --check", dockerfile)
        self.assertIn("git apply --recount /tmp/symphony-phase-bridge.patch", dockerfile)
        self.assertNotIn(":latest", dockerfile)

        dockerignore = (REPOSITORY_ROOT / ".dockerignore").read_text(encoding="utf-8")
        self.assertTrue(dockerignore.startswith("**\n"))
        self.assertIn("!scripts/agentic_cicd/**", dockerignore)
        self.assertIn("!scripts/agentic-cicd-controller.py", dockerignore)
        self.assertIn("!config/agentic-cicd/state-contract.json", dockerignore)
        self.assertIn(
            "!deploy/kubernetes/agentic-cicd/patches/symphony-phase-bridge.patch",
            dockerignore,
        )
        self.assertIn(
            "!deploy/kubernetes/agentic-cicd/patches/symphony-phase-routing.patch",
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
        self.assertNotIn("codex app-server", patch)

        routing_patch_path = patch_path.with_name("symphony-phase-routing.patch")
        routing_patch = routing_patch_path.read_text(encoding="utf-8")
        self.assertEqual(
            hashlib.sha256(routing_patch_path.read_bytes()).hexdigest(),
            lock["routing_patch_sha256"],
        )
        self.assertIn('"run_model" => false', routing_patch)
        self.assertIn('"thread_sandbox"', routing_patch)
        self.assertIn("runtime_policy", routing_patch)
        self.assertNotIn("codex app-server", routing_patch)

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
        self.assertIn("git -C \"$repo_root\" status --porcelain", deploy)
        self.assertIn("JSTORE_CONTROLLER_REVISION", deploy)
        self.assertIn("old_pod_uid", deploy)
        self.assertIn("new_pod_uid", deploy)
        self.assertIn("image: $image", deploy)
        self.assertIn("io.jstore.controller.revision", deploy)
        self.assertIn("org.opencontainers.image.revision", deploy)
        self.assertIn("symphony-phase-bridge.patch", deploy)
        self.assertIn("symphony-phase-routing.patch", deploy)
        self.assertIn("patch_sha256", deploy)
        self.assertIn("runtime-revisions", smoke)
        self.assertIn("scale deployment/symphony --replicas=0", stop)
        self.assertNotIn("delete pvc", stop)
        for state_field in ('\"running\"', '\"counts\"', '\"codex_totals\"'):
            self.assertIn(state_field, smoke)


if __name__ == "__main__":
    unittest.main()

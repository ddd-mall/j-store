from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.agentic_cicd.artifact_broker import ArtifactLeaseStore
from scripts.agentic_cicd.candidate import CandidateRevision
from scripts.agentic_cicd.gate_dispatcher import GateDispatcher
from scripts.agentic_cicd.kubernetes_gate import (
    GateLogLimitExceeded,
    GateJobImages,
    GateJobSpecBuilder,
    KubernetesGateJobClient,
)
from scripts.agentic_cicd.protocol import GateRequest


FETCH_IMAGE = "registry.internal/fetch@sha256:" + "3" * 64
RUNNER_IMAGE = "registry.internal/gate@sha256:" + "4" * 64


def request() -> GateRequest:
    base, tree, artifact, policy = "a" * 40, "b" * 40, "1" * 64, "2" * 64
    candidate = CandidateRevision(
        base,
        tree,
        artifact,
        policy,
        CandidateRevision.calculate_revision(base, tree, artifact, policy),
    )
    commands = ("/opt/jstore-gate/run-quality-gate",)
    return GateRequest(
        "gate-gh-123-1",
        "GH-123",
        candidate,
        RUNNER_IMAGE,
        GateRequest.calculate_command_policy_sha256(commands),
        commands,
        600,
        "2026-08-15T00:00:00Z",
    )


class FakeKubernetesApi:
    def __init__(self) -> None:
        self.job: dict | None = None
        self.posts = 0
        self.deletions = 0

    def get_json(self, path: str) -> dict | None:
        if "/pods?" in path:
            return {
                "items": [
                    {
                        "metadata": {"name": "gate-pod", "uid": "pod-uid"},
                        "status": {
                            "containerStatuses": [
                                {
                                    "name": "gate",
                                    "imageID": "containerd://" + RUNNER_IMAGE,
                                    "state": {
                                        "terminated": {
                                            "exitCode": 0,
                                            "reason": "Completed",
                                            "startedAt": "2026-08-15T00:00:01Z",
                                            "finishedAt": "2026-08-15T00:00:02Z",
                                        }
                                    },
                                }
                            ]
                        },
                    }
                ]
            }
        return self.job

    def post_json(self, path: str, payload: dict) -> dict:
        self.posts += 1
        self.job = payload
        self.job["metadata"]["uid"] = "job-uid"
        self.job["metadata"]["creationTimestamp"] = "2026-08-15T00:00:00Z"
        self.job["status"] = {"succeeded": 1}
        return self.job

    def get_text(self, path: str) -> bytes:
        return b"quality gate passed\n"

    def delete(self, path: str) -> None:
        self.deletions += 1
        self.job = None


class OversizedLogKubernetesApi(FakeKubernetesApi):
    def get_text(self, path: str) -> bytes:
        raise GateLogLimitExceeded("too large")


class FailingKubernetesApi(FakeKubernetesApi):
    def __init__(self, *, fail_logs: bool = False) -> None:
        super().__init__()
        self.fail_logs = fail_logs

    def get_json(self, path: str) -> dict | None:
        if not self.fail_logs:
            raise OSError("API unavailable")
        return super().get_json(path)

    def get_text(self, path: str) -> bytes:
        raise OSError("log endpoint unavailable")


class KubernetesGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.builder = GateJobSpecBuilder(
            images=GateJobImages(FETCH_IMAGE, RUNNER_IMAGE),
            broker_url="http://10.96.10.20:8081",
            lease_store=ArtifactLeaseStore(
                Path(self.temporary_directory.name) / "leases"
            ),
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_job_is_bounded_tokenless_and_only_init_receives_fetch_capability(self) -> None:
        job = self.builder.build(request())
        pod = job["spec"]["template"]["spec"]

        self.assertEqual(0, job["spec"]["backoffLimit"])
        self.assertEqual(600, job["spec"]["activeDeadlineSeconds"])
        self.assertFalse(pod["automountServiceAccountToken"])
        self.assertEqual("k8s-worker1", pod["nodeSelector"]["kubernetes.io/hostname"])
        self.assertNotIn("ARTIFACT_TOKEN", str(pod["containers"][0]))
        fetch = pod["initContainers"][1]
        self.assertEqual("ARTIFACT_TOKEN", fetch["env"][0]["name"])
        self.assertEqual("verify-network-policy", pod["initContainers"][0]["name"])
        self.assertEqual(
            ["/usr/bin/python3", "/opt/jstore-gate/fetch-candidate.py"],
            fetch["command"],
        )
        self.assertEqual("/tmp", fetch["workingDir"])
        self.assertNotIn("ARTIFACT_TOKEN", str(pod["containers"][0]))
        self.assertEqual("Never", pod["containers"][0]["imagePullPolicy"])
        self.assertEqual("Never", pod["initContainers"][0]["imagePullPolicy"])
        self.assertNotIn("hostPath", str(pod))
        self.assertNotIn("secret", str(pod).lower())
        self.assertTrue(
            pod["containers"][0]["volumeMounts"][-1]["readOnly"]
        )
        self.assertEqual(65531, fetch["securityContext"]["runAsUser"])
        for container in pod["initContainers"] + pod["containers"]:
            security = container["securityContext"]
            self.assertTrue(security["readOnlyRootFilesystem"])
            self.assertFalse(security["allowPrivilegeEscalation"])
            self.assertEqual(["ALL"], security["capabilities"]["drop"])

    def test_real_client_and_dispatcher_resume_the_same_job(self) -> None:
        api = FakeKubernetesApi()
        dispatcher = GateDispatcher(KubernetesGateJobClient(api, self.builder))

        first = dispatcher.dispatch(request())
        second = dispatcher.dispatch(request())

        self.assertEqual(1, api.posts)
        self.assertEqual(first, second)
        self.assertEqual("PASS", first.verdict)
        self.assertEqual("job-uid", first.job_uid)
        self.assertEqual("pod-uid", first.pod_uid)
        dispatcher.cleanup(request().gate_id)
        self.assertEqual(1, api.deletions)

    def test_builder_rejects_mismatched_or_sentinel_runner(self) -> None:
        payload = request().to_json()
        payload["runner_image"] = "registry.internal/other@sha256:" + "5" * 64
        with self.assertRaisesRegex(RuntimeError, "runner"):
            self.builder.build(GateRequest.from_json(payload))

        with self.assertRaisesRegex(ValueError, "non-sentinel"):
            GateJobSpecBuilder(
                images=GateJobImages(FETCH_IMAGE, "gate@sha256:" + "0" * 64),
                broker_url="http://broker:8081",
                lease_store=ArtifactLeaseStore(
                    Path(self.temporary_directory.name) / "other-leases"
                ),
            )

        payload = request().to_json()
        commands = ("./scripts/quality-gate.sh",)
        payload["validation_commands"] = list(commands)
        payload["command_policy_sha256"] = (
            GateRequest.calculate_command_policy_sha256(commands)
        )
        with self.assertRaisesRegex(RuntimeError, "trusted gate command"):
            self.builder.build(GateRequest.from_json(payload))

    def test_api_transport_failures_are_classified_as_infrastructure(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "status lookup"):
            KubernetesGateJobClient(FailingKubernetesApi(), self.builder).await_result(
                request().gate_id, 1
            )

        log_api = FailingKubernetesApi(fail_logs=True)
        log_api.job = self.builder.build(request())
        log_api.job["metadata"]["uid"] = "job-uid"
        log_api.job["metadata"]["creationTimestamp"] = "2026-08-15T00:00:00Z"
        log_api.job["status"] = {"succeeded": 1}
        with self.assertRaisesRegex(RuntimeError, "log retrieval"):
            KubernetesGateJobClient(log_api, self.builder).await_result(
                request().gate_id, 1
            )

    def test_deadline_and_oversized_logs_are_infrastructure_failures(self) -> None:
        deadline_api = FakeKubernetesApi()
        deadline_dispatcher = GateDispatcher(
            KubernetesGateJobClient(deadline_api, self.builder)
        )
        deadline_job = self.builder.build(request())
        deadline_job["metadata"]["uid"] = "deadline-job"
        deadline_job["metadata"]["creationTimestamp"] = "2026-08-15T00:00:00Z"
        deadline_job["status"] = {
            "failed": 1,
            "conditions": [{"type": "Failed", "reason": "DeadlineExceeded"}],
        }
        deadline_api.job = deadline_job
        self.assertEqual(
            "INFRASTRUCTURE_FAILURE", deadline_dispatcher.dispatch(request()).verdict
        )

        log_api = OversizedLogKubernetesApi()
        log_dispatcher = GateDispatcher(KubernetesGateJobClient(log_api, self.builder))
        log_dispatcher.dispatch(request())
        self.assertEqual(
            "INFRASTRUCTURE_FAILURE", log_dispatcher.dispatch(request()).verdict
        )


if __name__ == "__main__":
    unittest.main()

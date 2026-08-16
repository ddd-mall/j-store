from __future__ import annotations

import base64
import json
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from .artifact_broker import ArtifactLeaseStore
from .gate_dispatcher import GateInfrastructureError, GateJobIdentity, GateJobResult
from .gate_runtime import TRUSTED_VALIDATION_COMMANDS
from .protocol import GateRequest


GATE_NAMESPACE = "agentic-cicd-gates"
MAX_GATE_LOG_BYTES = 4 * 1024 * 1024


class GateLogLimitExceeded(RuntimeError):
    pass


class KubernetesApi(Protocol):
    def get_json(self, path: str) -> dict[str, Any] | None: ...

    def post_json(self, path: str, payload: dict[str, Any]) -> dict[str, Any]: ...

    def get_text(self, path: str) -> bytes: ...

    def delete(self, path: str) -> None: ...


class ServiceAccountKubernetesApi:
    """Minimal in-cluster Kubernetes API transport for the Dispatcher."""

    def __init__(
        self,
        *,
        api_server: str = "https://kubernetes.default.svc",
        token_path: str = "/var/run/secrets/dispatcher/token",
        ca_path: str = "/var/run/secrets/dispatcher/ca.crt",
    ):
        self.api_server = api_server.rstrip("/")
        self.token_path = token_path
        self.context = ssl.create_default_context(cafile=ca_path)

    def get_json(self, path: str) -> dict[str, Any] | None:
        try:
            payload = self._request("GET", path)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            raise
        decoded = json.loads(payload)
        if not isinstance(decoded, dict):
            raise RuntimeError("Kubernetes API returned a non-object")
        return decoded

    def post_json(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        decoded = json.loads(
            self._request(
                "POST",
                path,
                json.dumps(payload, separators=(",", ":")).encode(),
                "application/json",
            )
        )
        if not isinstance(decoded, dict):
            raise RuntimeError("Kubernetes API returned a non-object")
        return decoded

    def get_text(self, path: str) -> bytes:
        return self._request("GET", path, maximum_bytes=MAX_GATE_LOG_BYTES)

    def delete(self, path: str) -> None:
        try:
            self._request("DELETE", path)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise

    def _request(
        self,
        method: str,
        path: str,
        body: bytes | None = None,
        content_type: str | None = None,
        maximum_bytes: int | None = None,
    ) -> bytes:
        token = Path(self.token_path).read_text(encoding="utf-8").strip()
        headers = {"Authorization": f"Bearer {token}"}
        if content_type is not None:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(
            self.api_server + path,
            data=body,
            headers=headers,
            method=method,
        )
        with urllib.request.urlopen(request, context=self.context, timeout=10) as response:
            payload = response.read(None if maximum_bytes is None else maximum_bytes + 1)
        if maximum_bytes is not None and len(payload) > maximum_bytes:
            raise GateLogLimitExceeded("gate log exceeds the trusted size limit")
        return payload


@dataclass(frozen=True)
class GateJobImages:
    fetch_image: str
    runner_image: str


class GateJobSpecBuilder:
    """Builds the restricted, one-shot Kubernetes Job for an exact request."""

    def __init__(
        self,
        *,
        images: GateJobImages,
        broker_url: str,
        lease_store: ArtifactLeaseStore,
    ):
        for image in (images.fetch_image, images.runner_image):
            if "@sha256:" not in image or image.endswith("0" * 64):
                raise ValueError("gate images must use non-sentinel immutable digests")
        if not broker_url.startswith("http://"):
            raise ValueError("broker_url must be an internal HTTP endpoint")
        self.images = images
        self.broker_url = broker_url.rstrip("/")
        self.lease_store = lease_store

    def build(self, request: GateRequest) -> dict[str, Any]:
        if request.runner_image != self.images.runner_image:
            raise RuntimeError("request runner does not match deployed runner image")
        if request.validation_commands != TRUSTED_VALIDATION_COMMANDS:
            raise RuntimeError("request must invoke the trusted gate command")
        lease = self.lease_store.issue(
            request.candidate_revision.artifact_sha256,
            ttl_seconds=min(request.timeout_seconds, 300),
        )
        annotations = self._identity_annotations(request)
        job_name = request.gate_id
        command = " && ".join(f"({value})" for value in request.validation_commands)
        artifact = request.candidate_revision.artifact_sha256
        return {
            "apiVersion": "batch/v1",
            "kind": "Job",
            "metadata": {
                "name": job_name,
                "namespace": GATE_NAMESPACE,
                "labels": self._labels(),
                "annotations": annotations,
            },
            "spec": {
                "backoffLimit": 0,
                "activeDeadlineSeconds": request.timeout_seconds,
                "ttlSecondsAfterFinished": 300,
                "template": {
                    "metadata": {
                        "labels": self._labels(),
                        "annotations": annotations,
                    },
                    "spec": {
                        "restartPolicy": "Never",
                        "serviceAccountName": "gate-runner",
                        "automountServiceAccountToken": False,
                        "nodeSelector": {"kubernetes.io/hostname": "k8s-worker1"},
                        "securityContext": {
                            "runAsNonRoot": True,
                            "runAsUser": 65532,
                            "runAsGroup": 65532,
                            "fsGroup": 65532,
                            "seccompProfile": {"type": "RuntimeDefault"},
                        },
                        "initContainers": [
                            {
                                "name": "verify-network-policy",
                                "image": self.images.fetch_image,
                                "imagePullPolicy": "Never",
                                "command": [
                                    "/usr/bin/python3",
                                    "/opt/jstore-gate/network-probe.py",
                                ],
                                "args": [
                                    "--broker-url",
                                    self.broker_url,
                                    "--forbidden-endpoint",
                                    "10.96.0.1:443",
                                    "--forbidden-endpoint",
                                    "10.96.0.10:53",
                                    "--forbidden-endpoint",
                                    "1.1.1.1:443",
                                    "--forbidden-dns-endpoint",
                                    "10.96.0.10:53",
                                ],
                                "resources": self._fetch_resources(),
                                "securityContext": self._init_security(),
                            },
                            {
                                "name": "fetch-candidate",
                                "image": self.images.fetch_image,
                                "imagePullPolicy": "Never",
                                "command": [
                                    "/usr/bin/python3",
                                    "/opt/jstore-gate/fetch-candidate.py",
                                ],
                                "workingDir": "/tmp",
                                "args": [
                                    "--artifact-url",
                                    f"{self.broker_url}/artifacts/{artifact}",
                                    "--artifact-sha256",
                                    artifact,
                                    "--repository-files-output",
                                    "/gate-metadata/repository-files",
                                ],
                                "env": [
                                    {"name": "ARTIFACT_TOKEN", "value": lease.token},
                                ],
                                "resources": self._fetch_resources(),
                                "securityContext": self._init_security(),
                                "volumeMounts": [
                                    {"name": "workspace", "mountPath": "/workspace"},
                                    {"name": "tmp", "mountPath": "/tmp"},
                                    {"name": "gate-metadata", "mountPath": "/gate-metadata"},
                                ],
                            }
                        ],
                        "containers": [
                            {
                                "name": "gate",
                                "image": self.images.runner_image,
                                "imagePullPolicy": "Never",
                                "workingDir": "/workspace/source",
                                "command": ["/bin/bash", "-lc", command],
                                "resources": {
                                    "requests": {
                                        "cpu": "2",
                                        "memory": "4Gi",
                                        "ephemeral-storage": "4Gi",
                                    },
                                    "limits": {
                                        "cpu": "4",
                                        "memory": "8Gi",
                                        "ephemeral-storage": "12Gi",
                                    },
                                },
                                "securityContext": self._container_security(),
                                "env": [
                                    {
                                        "name": "JSTORE_REPOSITORY_FILES_FILE",
                                        "value": "/gate-metadata/repository-files",
                                    }
                                ],
                                "volumeMounts": [
                                    {"name": "workspace", "mountPath": "/workspace"},
                                    {"name": "tmp", "mountPath": "/tmp"},
                                    {
                                        "name": "gate-metadata",
                                        "mountPath": "/gate-metadata",
                                        "readOnly": True,
                                    },
                                ],
                            }
                        ],
                        "volumes": [
                            {"name": "workspace", "emptyDir": {"sizeLimit": "12Gi"}},
                            {"name": "tmp", "emptyDir": {"sizeLimit": "4Gi"}},
                            {"name": "gate-metadata", "emptyDir": {"sizeLimit": "1Mi"}},
                        ],
                    },
                },
            },
        }

    @staticmethod
    def _labels() -> dict[str, str]:
        return {
            "app.kubernetes.io/name": "candidate-gate",
            "app.kubernetes.io/component": "gate-runner",
            "app.kubernetes.io/part-of": "j-store-agentic-cicd",
        }

    @staticmethod
    def _container_security() -> dict[str, Any]:
        return {
            "allowPrivilegeEscalation": False,
            "readOnlyRootFilesystem": True,
            "capabilities": {"drop": ["ALL"]},
        }

    @staticmethod
    def _init_security() -> dict[str, Any]:
        security = GateJobSpecBuilder._container_security()
        security.update({"runAsUser": 65531, "runAsGroup": 65532})
        return security

    @staticmethod
    def _fetch_resources() -> dict[str, Any]:
        return {
            "requests": {
                "cpu": "50m",
                "memory": "64Mi",
                "ephemeral-storage": "128Mi",
            },
            "limits": {
                "cpu": "250m",
                "memory": "128Mi",
                "ephemeral-storage": "512Mi",
            },
        }

    @staticmethod
    def _identity_annotations(request: GateRequest) -> dict[str, str]:
        commands = base64.urlsafe_b64encode(
            json.dumps(list(request.validation_commands), separators=(",", ":")).encode()
        ).decode()
        return {
            "agentic.jstore.io/gate-id": request.gate_id,
            "agentic.jstore.io/issue": request.issue_identifier,
            "agentic.jstore.io/candidate": request.candidate_revision.candidate_revision,
            "agentic.jstore.io/runner": request.runner_image,
            "agentic.jstore.io/command-policy": request.command_policy_sha256,
            "agentic.jstore.io/commands": commands,
            "agentic.jstore.io/timeout": str(request.timeout_seconds),
            "agentic.jstore.io/requested-at": request.requested_at,
        }


class KubernetesGateJobClient:
    def __init__(self, api: KubernetesApi, builder: GateJobSpecBuilder):
        self.api = api
        self.builder = builder

    def get(self, gate_id: str) -> GateJobIdentity | None:
        try:
            job = self.api.get_json(self._job_path(gate_id))
        except (OSError, urllib.error.HTTPError) as error:
            raise GateInfrastructureError("Kubernetes Job lookup failed") from error
        return None if job is None else self._identity(job)

    def create(self, request: GateRequest) -> GateJobIdentity:
        try:
            job = self.api.post_json(
                f"/apis/batch/v1/namespaces/{GATE_NAMESPACE}/jobs",
                self.builder.build(request),
            )
        except (OSError, urllib.error.HTTPError) as error:
            raise GateInfrastructureError("Kubernetes Job creation failed") from error
        return self._identity(job)

    def delete(self, gate_id: str) -> None:
        try:
            self.api.delete(self._job_path(gate_id) + "?propagationPolicy=Foreground")
        except (OSError, urllib.error.HTTPError) as error:
            raise GateInfrastructureError("Kubernetes Job cleanup failed") from error
        deadline = time.monotonic() + 15
        while time.monotonic() <= deadline:
            if self._get_json(
                self._job_path(gate_id), "Kubernetes Job cleanup lookup failed"
            ) is None:
                return
            time.sleep(0.25)
        raise GateInfrastructureError("Kubernetes Job cleanup did not complete")

    def await_result(self, gate_id: str, timeout_seconds: int) -> GateJobResult:
        deadline = time.monotonic() + timeout_seconds + 15
        while time.monotonic() <= deadline:
            job = self._get_json(
                self._job_path(gate_id), "Kubernetes Job status lookup failed"
            )
            if job is None:
                raise RuntimeError("dispatched gate Job disappeared")
            status = job.get("status", {})
            if status.get("succeeded") == 1 or status.get("failed") == 1:
                return self._terminal_result(job)
            time.sleep(1)
        return self._infrastructure_result(gate_id, "dispatcher-timeout")

    def _terminal_result(self, job: dict[str, Any]) -> GateJobResult:
        gate_id = job["metadata"]["name"]
        pods = self._get_json(
            f"/api/v1/namespaces/{GATE_NAMESPACE}/pods?labelSelector="
            + urllib.parse.quote(f"job-name={gate_id}"),
            "Kubernetes gate Pod lookup failed",
        )
        items = [] if pods is None else pods.get("items", [])
        if len(items) != 1:
            return self._infrastructure_result(gate_id, "missing-or-duplicate-pod")
        pod = items[0]
        statuses = pod.get("status", {}).get("containerStatuses", [])
        gate_status = next(
            (value for value in statuses if value.get("name") == "gate"), None
        )
        terminated = None if gate_status is None else gate_status.get("state", {}).get("terminated")
        if terminated is None:
            return self._infrastructure_result(gate_id, "gate-container-not-terminated")
        exit_code = terminated.get("exitCode")
        reason = terminated.get("reason", "")
        job_reasons = {
            condition.get("reason")
            for condition in job.get("status", {}).get("conditions", [])
            if condition.get("type") == "Failed"
        }
        infrastructure = reason in {"ContainerCannotRun", "OOMKilled"} or (
            "DeadlineExceeded" in job_reasons
        )
        try:
            logs = self.api.get_text(
                f"/api/v1/namespaces/{GATE_NAMESPACE}/pods/{pod['metadata']['name']}/log?container=gate"
            )
        except GateLogLimitExceeded:
            return self._infrastructure_result(gate_id, "gate-log-limit-exceeded")
        except (OSError, urllib.error.HTTPError) as error:
            raise GateInfrastructureError("Kubernetes gate log retrieval failed") from error
        requested_image = job["metadata"]["annotations"]["agentic.jstore.io/runner"]
        image_id = gate_status.get("imageID", "")
        runtime_image = requested_image if requested_image.split("@", 1)[1] in image_id else image_id
        return GateJobResult(
            status="INFRASTRUCTURE_FAILURE" if infrastructure else "COMPLETED",
            started_at=terminated.get("startedAt", job["metadata"]["creationTimestamp"]),
            finished_at=terminated.get("finishedAt", job["metadata"]["creationTimestamp"]),
            exit_code=exit_code,
            logs=logs,
            job_uid=job["metadata"]["uid"],
            pod_uid=pod["metadata"]["uid"],
            runtime_image=runtime_image,
        )

    def _infrastructure_result(self, gate_id: str, reason: str) -> GateJobResult:
        job = self._get_json(
            self._job_path(gate_id), "Kubernetes Job status lookup failed"
        )
        if job is None:
            raise RuntimeError("gate Job is missing")
        timestamp = job["metadata"].get("creationTimestamp", "1970-01-01T00:00:00Z")
        return GateJobResult(
            "INFRASTRUCTURE_FAILURE",
            timestamp,
            timestamp,
            None,
            reason.encode(),
            job["metadata"]["uid"],
            None,
            job["metadata"]["annotations"]["agentic.jstore.io/runner"],
        )

    @staticmethod
    def _identity(job: dict[str, Any]) -> GateJobIdentity:
        metadata = job["metadata"]
        annotations = metadata["annotations"]
        commands = tuple(
            json.loads(base64.urlsafe_b64decode(annotations["agentic.jstore.io/commands"]))
        )
        return GateJobIdentity(
            gate_id=annotations["agentic.jstore.io/gate-id"],
            issue_identifier=annotations["agentic.jstore.io/issue"],
            candidate_revision=annotations["agentic.jstore.io/candidate"],
            runner_image=annotations["agentic.jstore.io/runner"],
            command_policy_sha256=annotations["agentic.jstore.io/command-policy"],
            validation_commands=commands,
            timeout_seconds=int(annotations["agentic.jstore.io/timeout"]),
            requested_at=annotations["agentic.jstore.io/requested-at"],
            job_uid=metadata["uid"],
        )

    @staticmethod
    def _job_path(gate_id: str) -> str:
        return f"/apis/batch/v1/namespaces/{GATE_NAMESPACE}/jobs/{gate_id}"

    def _get_json(self, path: str, message: str) -> dict[str, Any] | None:
        try:
            return self.api.get_json(path)
        except (OSError, urllib.error.HTTPError) as error:
            raise GateInfrastructureError(message) from error

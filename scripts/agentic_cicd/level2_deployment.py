from __future__ import annotations

import hashlib
import json
import re
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .capabilities import GITHUB_REPOSITORY
from .github_adapter import validate_handoff_logins
from .process_environment import trusted_process_environment


SHA256 = re.compile(r"[0-9a-f]{64}")
MANIFEST_DIGEST = re.compile(r"sha256:[0-9a-f]{64}")
SOURCE_RECORD_KEYS = {
    "schema_version",
    "created_at",
    "image",
    "runtime_manifest_digest",
    "archive",
    "archive_sha256",
    "symphony_revision",
    "controller_revision",
    "phase_bridge_patch_sha256",
    "phase_routing_patch_sha256",
    "dependency_lock_sha256",
    "workflow_sha256",
    "capability_level",
    "target_repository",
    "state_contract_sha256",
    "runtime_binding_sha256",
    "codex_version",
    "elixir_image",
    "node_image",
    "buildx_version",
    "sbom",
    "provenance",
}
IMAGE_REPOSITORY = "docker.io/library/jstore-agentic-cicd"
NAMESPACE = "agentic-cicd"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _require_digest(value: Any, label: str) -> str:
    if not isinstance(value, str) or SHA256.fullmatch(value) is None:
        raise ValueError(f"{label} must be a lowercase SHA-256")
    return value


def _artifact_path(directory: Path, value: Any, label: str) -> Path:
    if (
        not isinstance(value, str)
        or not value
        or Path(value).name != value
        or value in {".", ".."}
    ):
        raise ValueError(f"{label} must be a local artifact filename")
    path = directory / value
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"{label} artifact is missing: {value}")
    return path


def _verify_attestation(
    path: Path, runtime_digest: str, label: str, predicate_fragment: str
) -> None:
    try:
        statement = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"{label} attestation is not valid JSON") from error
    if (
        not isinstance(statement, dict)
        or predicate_fragment not in str(statement.get("predicateType", ""))
    ):
        raise ValueError(f"{label} attestation predicate type is invalid")
    subjects = statement.get("subject")
    expected = runtime_digest.removeprefix("sha256:")
    if not isinstance(subjects, list) or not any(
        isinstance(subject, dict)
        and isinstance(subject.get("digest"), dict)
        and subject["digest"].get("sha256") == expected
        for subject in subjects
    ):
        raise ValueError(f"{label} attestation subject does not bind the runtime digest")


@dataclass(frozen=True)
class LevelTwoSourceRecord:
    repository: str
    image_tag: str
    image_ref: str
    runtime_manifest_digest: str
    state_contract_sha256: str
    runtime_binding_sha256: str
    archive_sha256: str
    controller_revision: str
    symphony_revision: str

    @classmethod
    def load_verified(
        cls,
        *,
        path: Path,
        expected_sha256: str,
        repository: str,
    ) -> LevelTwoSourceRecord:
        expected_sha256 = _require_digest(
            expected_sha256, "expected source record digest"
        )
        if path.is_symlink() or not path.is_file():
            raise ValueError("controller source record is missing")
        if _sha256(path) != expected_sha256:
            raise ValueError("controller source record digest does not match")
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ValueError("controller source record is not valid JSON") from error
        if not isinstance(value, dict) or set(value) != SOURCE_RECORD_KEYS:
            raise ValueError("controller source record fields do not match schema version 1")
        if value.get("schema_version") != 1:
            raise ValueError("controller source record schema version is unsupported")
        if GITHUB_REPOSITORY.fullmatch(repository) is None:
            raise ValueError("deployment repository must use owner/name form")
        if repository.casefold() == "ddd-mall/j-store":
            raise ValueError("disposable Level 2 deployment must not target ddd-mall/j-store")
        if value.get("target_repository") != repository:
            raise ValueError(
                "source record target repository does not match deployment target"
            )
        if value.get("capability_level") != 2:
            raise ValueError("source record capability level must be 2")

        runtime_digest = value.get("runtime_manifest_digest")
        if (
            not isinstance(runtime_digest, str)
            or MANIFEST_DIGEST.fullmatch(runtime_digest) is None
        ):
            raise ValueError("runtime manifest digest must be sha256:<lowercase SHA-256>")
        contract_digest = _require_digest(
            value.get("state_contract_sha256"), "state contract digest"
        )
        binding_digest = _require_digest(
            value.get("runtime_binding_sha256"), "runtime binding digest"
        )
        archive_digest = _require_digest(
            value.get("archive_sha256"), "archive digest"
        )
        image = value.get("image")
        expected_suffix = (
            f"-level2-{contract_digest[:16]}-binding-{binding_digest[:16]}"
        )
        if (
            not isinstance(image, str)
            or "@" in image
            or not image.startswith(IMAGE_REPOSITORY + ":")
            or not image.endswith(expected_suffix)
        ):
            raise ValueError("source record image tag does not match its Level 2 binding")

        artifact_directory = path.parent
        archive = _artifact_path(artifact_directory, value.get("archive"), "archive")
        if _sha256(archive) != archive_digest:
            raise ValueError("controller archive digest does not match source record")
        sbom = _artifact_path(artifact_directory, value.get("sbom"), "SBOM")
        provenance = _artifact_path(
            artifact_directory, value.get("provenance"), "provenance"
        )
        _verify_attestation(sbom, runtime_digest, "SBOM", "spdx")
        _verify_attestation(
            provenance, runtime_digest, "provenance", "slsa.dev/provenance"
        )

        controller_revision = value.get("controller_revision")
        symphony_revision = value.get("symphony_revision")
        if not isinstance(controller_revision, str) or re.fullmatch(
            r"[0-9a-f]{40}", controller_revision
        ) is None:
            raise ValueError("controller revision must be a full Git SHA")
        if not isinstance(symphony_revision, str) or re.fullmatch(
            r"[0-9a-f]{40}", symphony_revision
        ) is None:
            raise ValueError("Symphony revision must be a full Git SHA")
        return cls(
            repository=repository,
            image_tag=image,
            image_ref=f"{IMAGE_REPOSITORY}@{runtime_digest}",
            runtime_manifest_digest=runtime_digest,
            state_contract_sha256=contract_digest,
            runtime_binding_sha256=binding_digest,
            archive_sha256=archive_digest,
            controller_revision=controller_revision,
            symphony_revision=symphony_revision,
        )


def _render_manifest(
    *,
    record: LevelTwoSourceRecord,
    repository_root: Path,
    source_record_sha256: str,
    github_app_login: str,
    reviewer: str,
) -> str:
    overlays_root = (
        repository_root
        / "deploy"
        / "kubernetes"
        / "agentic-cicd"
        / "overlays"
    ).resolve()
    credentialed_overlay = overlays_root / "development-credentialed-observer"
    if not (credentialed_overlay / "kustomization.yaml").is_file():
        raise ValueError("credentialed observer overlay is missing")
    annotations = {
        "agentic-cicd.jstore.io/source-record-sha256": source_record_sha256,
        "agentic-cicd.jstore.io/state-contract-sha256": record.state_contract_sha256,
        "agentic-cicd.jstore.io/runtime-binding-sha256": record.runtime_binding_sha256,
        "agentic-cicd.jstore.io/target-repository": record.repository,
        "agentic-cicd.jstore.io/github-app-login": github_app_login,
        "agentic-cicd.jstore.io/reviewer": reviewer,
    }
    patch = {
        "apiVersion": "apps/v1",
        "kind": "Deployment",
        "metadata": {
            "name": "symphony",
            "namespace": NAMESPACE,
            "annotations": annotations,
        },
        "spec": {
            "template": {
                "metadata": {"annotations": annotations},
                "spec": {
                    "containers": [
                        {
                            "name": "symphony",
                            "env": [
                                {
                                    "name": "JSTORE_SYMPHONY_REPOSITORY",
                                    "value": record.repository,
                                },
                                {
                                    "name": "JSTORE_SYMPHONY_REPOSITORY_URL",
                                    "value": f"https://github.com/{record.repository}.git",
                                },
                                {
                                    "name": "JSTORE_GITHUB_APP_LOGIN",
                                    "value": github_app_login,
                                },
                                {
                                    "name": "JSTORE_GITHUB_REVIEWER",
                                    "value": reviewer,
                                },
                            ],
                            "volumeMounts": [
                                {
                                    "name": "gate-policy",
                                    "mountPath": "/etc/agentic-cicd",
                                    "readOnly": True,
                                }
                            ],
                        }
                    ],
                    "volumes": [
                        {
                            "name": "gate-policy",
                            "configMap": {"name": "gate-policy"},
                        }
                    ],
                },
            }
        },
    }
    kustomization = {
        "apiVersion": "kustomize.config.k8s.io/v1beta1",
        "kind": "Kustomization",
        "resources": ["../development-credentialed-observer"],
        "patches": [{"path": "deployment-patch.json"}],
        "images": [
            {
                "name": "jstore-agentic-cicd",
                "newName": IMAGE_REPOSITORY,
                "digest": record.runtime_manifest_digest,
            }
        ],
    }
    with tempfile.TemporaryDirectory(
        prefix=".level2-render-", dir=overlays_root
    ) as temporary:
        overlay = Path(temporary)
        (overlay / "deployment-patch.json").write_text(
            json.dumps(patch, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        (overlay / "kustomization.yaml").write_text(
            json.dumps(kustomization, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        result = subprocess.run(
            [
                "kubectl",
                "kustomize",
                str(overlay),
            ],
            check=False,
            capture_output=True,
            text=True,
            env=trusted_process_environment(),
        )
    if result.returncode != 0:
        detail = result.stderr.strip().splitlines()
        raise RuntimeError(
            "Level 2 deployment candidate render failed"
            + (f": {detail[-1]}" if detail else "")
        )
    manifest = result.stdout
    if manifest.count("image: " + record.image_ref) < 2:
        raise RuntimeError("rendered manifest does not bind every controller container image")
    for expected in (
        record.repository,
        f"https://github.com/{record.repository}.git",
        source_record_sha256,
        record.runtime_binding_sha256,
        github_app_login,
        reviewer,
        "symphony-github-token",
        "symphony-codex-auth",
        "gate-policy",
        "/etc/agentic-cicd",
    ):
        if expected not in manifest:
            raise RuntimeError(f"rendered manifest is missing required binding: {expected}")
    return manifest


def prepare_level2_deployment_candidate(
    *,
    source_record_path: Path,
    expected_source_record_sha256: str,
    repository: str,
    github_app_login: str,
    reviewer: str,
    output_directory: Path,
    repository_root: Path,
) -> None:
    if output_directory.exists():
        raise FileExistsError(
            f"deployment candidate output already exists: {output_directory}"
        )
    raise RuntimeError(
        "Kubernetes Symphony deployment candidates are retired; build the "
        "host-native execution bundle with scripts/agentic-cicd-host-build.sh"
    )
    validate_handoff_logins(
        github_app_login=github_app_login,
        reviewer=reviewer,
        require_app_login=True,
        require_reviewer=True,
    )
    record = LevelTwoSourceRecord.load_verified(
        path=source_record_path,
        expected_sha256=expected_source_record_sha256,
        repository=repository,
    )
    manifest = _render_manifest(
        record=record,
        repository_root=repository_root,
        source_record_sha256=expected_source_record_sha256,
        github_app_login=github_app_login,
        reviewer=reviewer,
    )
    profile = {
        "schema_version": 1,
        "mode": "render-only",
        "namespace": NAMESPACE,
        "repository": record.repository,
        "repository_url": f"https://github.com/{record.repository}.git",
        "image_tag": record.image_tag,
        "image_ref": record.image_ref,
        "runtime_manifest_digest": record.runtime_manifest_digest,
        "archive_sha256": record.archive_sha256,
        "state_contract_sha256": record.state_contract_sha256,
        "runtime_binding_sha256": record.runtime_binding_sha256,
        "source_record": source_record_path.name,
        "source_record_sha256": expected_source_record_sha256,
        "controller_revision": record.controller_revision,
        "symphony_revision": record.symphony_revision,
        "github_token_secret": "symphony-github-token",
        "codex_auth_secret": "symphony-codex-auth",
        "github_app_login": github_app_login,
        "reviewer": reviewer,
    }
    output_directory.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(
        prefix=f".{output_directory.name}.", dir=output_directory.parent
    ) as temporary:
        staging = Path(temporary) / "candidate"
        staging.mkdir(mode=0o750)
        manifest_path = staging / "manifest.yaml"
        profile_path = staging / "deployment-profile.json"
        manifest_path.write_text(manifest, encoding="utf-8")
        profile_path.write_text(
            json.dumps(profile, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        manifest_path.chmod(0o444)
        profile_path.chmod(0o444)
        staging.rename(output_directory)

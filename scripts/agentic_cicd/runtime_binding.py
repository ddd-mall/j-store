from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .capabilities import GITHUB_REPOSITORY, validate_disposable_github_e2e


RUNTIME_BINDING_KEYS = {
    "schema_version",
    "repository",
    "repository_url",
    "capability_level",
    "state_contract_sha256",
}


@dataclass(frozen=True)
class RuntimeBinding:
    repository: str
    repository_url: str
    capability_level: int
    state_contract_sha256: str
    schema_version: int = 1

    @classmethod
    def create(
        cls, *, repository: str, capability_level: int, contract_bytes: bytes
    ) -> RuntimeBinding:
        return cls.from_json(
            {
                "schema_version": 1,
                "repository": repository,
                "repository_url": f"https://github.com/{repository}.git",
                "capability_level": capability_level,
                "state_contract_sha256": hashlib.sha256(contract_bytes).hexdigest(),
            }
        )

    @classmethod
    def from_json(cls, value: Any) -> RuntimeBinding:
        if not isinstance(value, dict) or set(value) != RUNTIME_BINDING_KEYS:
            raise ValueError("runtime binding must contain only the required fields")
        repository = value.get("repository")
        repository_url = value.get("repository_url")
        capability_level = value.get("capability_level")
        contract_sha256 = value.get("state_contract_sha256")
        if not isinstance(repository, str) or GITHUB_REPOSITORY.fullmatch(repository) is None:
            raise ValueError("runtime binding repository must use owner/name form")
        if repository_url != f"https://github.com/{repository}.git":
            raise ValueError("runtime binding repository URL does not match repository")
        if capability_level not in {0, 1, 2}:
            raise ValueError("runtime binding capability level is invalid")
        if (
            not isinstance(contract_sha256, str)
            or len(contract_sha256) != 64
            or any(character not in "0123456789abcdef" for character in contract_sha256)
        ):
            raise ValueError("runtime binding contract digest must be lowercase SHA-256")
        if value.get("schema_version") != 1:
            raise ValueError("runtime binding schema version is unsupported")
        return cls(
            repository=repository,
            repository_url=repository_url,
            capability_level=capability_level,
            state_contract_sha256=contract_sha256,
        )

    @classmethod
    def load(cls, path: Path) -> RuntimeBinding:
        return cls.from_json(json.loads(path.read_text(encoding="utf-8")))

    def verify_contract(self, path: Path) -> None:
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != self.state_contract_sha256:
            raise RuntimeError("runtime state contract does not match its image binding")

    def to_json(self) -> dict[str, Any]:
        return {
            "schema_version": self.schema_version,
            "repository": self.repository,
            "repository_url": self.repository_url,
            "capability_level": self.capability_level,
            "state_contract_sha256": self.state_contract_sha256,
        }

    def write(self, path: Path) -> None:
        path.write_text(
            json.dumps(self.to_json(), indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )


def validate_runtime_authority(
    *,
    contract_path: Path,
    binding_path: Path,
    configured_repository: str,
    configured_repository_url: str,
    unbound_repository: str,
) -> str:
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    if not isinstance(contract, dict):
        raise ValueError("runtime state contract must be a JSON object")
    if binding_path.is_file():
        binding = RuntimeBinding.load(binding_path)
        binding.verify_contract(contract_path)
        if contract.get("capability_level") != binding.capability_level:
            raise RuntimeError("runtime capability level does not match its image binding")
        if configured_repository != binding.repository:
            raise RuntimeError("configured repository does not match its image binding")
        if configured_repository_url != binding.repository_url:
            raise RuntimeError("configured repository URL does not match its image binding")
        return binding.repository
    if contract.get("capability_level") != 0:
        raise RuntimeError("non-Level 0 runtime requires an immutable image binding")
    if configured_repository != unbound_repository:
        raise RuntimeError(f"unbound Level 0 runtime is fixed to {unbound_repository}")
    expected_url = f"https://github.com/{unbound_repository}.git"
    if configured_repository_url != expected_url:
        raise RuntimeError("unbound Level 0 runtime repository URL is invalid")
    return unbound_repository


def prepare_disposable_runtime_profile(
    *,
    authoritative_contract_path: Path,
    candidate_contract_path: Path,
    repository: str,
    output_directory: Path,
) -> tuple[str, str]:
    authoritative = json.loads(
        authoritative_contract_path.read_text(encoding="utf-8")
    )
    candidate_bytes = candidate_contract_path.read_bytes()
    candidate = json.loads(candidate_bytes)
    failures = validate_disposable_github_e2e(
        repository=repository,
        repository_url=f"https://github.com/{repository}.git",
        contract=candidate,
        authoritative_contract=authoritative,
    )
    if failures:
        raise ValueError("invalid disposable Level 2 profile: " + "; ".join(failures))
    output_directory.mkdir(parents=True, exist_ok=False)
    contract_target = output_directory / "state-contract.json"
    contract_target.write_bytes(candidate_bytes)
    binding = RuntimeBinding.create(
        repository=repository,
        capability_level=2,
        contract_bytes=candidate_bytes,
    )
    binding_target = output_directory / "runtime-binding.json"
    binding.write(binding_target)
    return (
        hashlib.sha256(candidate_bytes).hexdigest(),
        hashlib.sha256(binding_target.read_bytes()).hexdigest(),
    )

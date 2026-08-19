from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.agentic_cicd.runtime_binding import (
    RuntimeBinding,
    prepare_disposable_runtime_profile,
    validate_runtime_authority,
)


REPO_ROOT = Path(__file__).resolve().parents[2]
LEVEL_ZERO_CONTRACT = (
    REPO_ROOT / "config" / "agentic-cicd" / "state-contract.json"
)
LEVEL_TWO_CONTRACT = (
    REPO_ROOT
    / "config"
    / "agentic-cicd"
    / "state-contract.level2-disposable.example.json"
)


class RuntimeBindingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.contract = self.root / "state-contract.json"
        self.binding_path = self.root / "runtime-binding.json"
        self.repository = "ddd-mall/agentic-cicd-disposable"
        self.contract.write_bytes(LEVEL_TWO_CONTRACT.read_bytes())
        self.binding = RuntimeBinding.create(
            repository=self.repository,
            capability_level=2,
            contract_bytes=self.contract.read_bytes(),
        )
        self.binding.write(self.binding_path)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_bound_level_two_contract_accepts_only_its_repository(self) -> None:
        self.assertEqual(
            self.repository,
            validate_runtime_authority(
                contract_path=self.contract,
                binding_path=self.binding_path,
                configured_repository=self.repository,
                configured_repository_url=f"https://github.com/{self.repository}.git",
                unbound_repository="ddd-mall/j-store",
            ),
        )

        with self.assertRaisesRegex(RuntimeError, "configured repository"):
            validate_runtime_authority(
                contract_path=self.contract,
                binding_path=self.binding_path,
                configured_repository="ddd-mall/other",
                configured_repository_url="https://github.com/ddd-mall/other.git",
                unbound_repository="ddd-mall/j-store",
            )

        with self.assertRaisesRegex(RuntimeError, "repository URL"):
            validate_runtime_authority(
                contract_path=self.contract,
                binding_path=self.binding_path,
                configured_repository=self.repository,
                configured_repository_url="https://github.com/ddd-mall/other.git",
                unbound_repository="ddd-mall/j-store",
            )

    def test_binding_rejects_contract_or_capability_drift(self) -> None:
        payload = json.loads(self.contract.read_text(encoding="utf-8"))
        payload["required_checks"].append("unexpected")
        self.contract.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(RuntimeError, "does not match its image binding"):
            validate_runtime_authority(
                contract_path=self.contract,
                binding_path=self.binding_path,
                configured_repository=self.repository,
                configured_repository_url=f"https://github.com/{self.repository}.git",
                unbound_repository="ddd-mall/j-store",
            )

        self.contract.write_bytes(LEVEL_TWO_CONTRACT.read_bytes())
        wrong_level = RuntimeBinding.create(
            repository=self.repository,
            capability_level=1,
            contract_bytes=self.contract.read_bytes(),
        )
        wrong_level.write(self.binding_path)
        with self.assertRaisesRegex(RuntimeError, "capability level"):
            validate_runtime_authority(
                contract_path=self.contract,
                binding_path=self.binding_path,
                configured_repository=self.repository,
                configured_repository_url=f"https://github.com/{self.repository}.git",
                unbound_repository="ddd-mall/j-store",
            )

    def test_unbound_runtime_accepts_only_authoritative_level_zero_identity(self) -> None:
        self.contract.write_bytes(LEVEL_ZERO_CONTRACT.read_bytes())
        self.binding_path.unlink()
        self.assertEqual(
            "ddd-mall/j-store",
            validate_runtime_authority(
                contract_path=self.contract,
                binding_path=self.binding_path,
                configured_repository="ddd-mall/j-store",
                configured_repository_url="https://github.com/ddd-mall/j-store.git",
                unbound_repository="ddd-mall/j-store",
            ),
        )
        with self.assertRaisesRegex(RuntimeError, "unbound Level 0"):
            validate_runtime_authority(
                contract_path=self.contract,
                binding_path=self.binding_path,
                configured_repository=self.repository,
                configured_repository_url=f"https://github.com/{self.repository}.git",
                unbound_repository="ddd-mall/j-store",
            )

        self.contract.write_bytes(LEVEL_TWO_CONTRACT.read_bytes())
        with self.assertRaisesRegex(RuntimeError, "requires an immutable image binding"):
            validate_runtime_authority(
                contract_path=self.contract,
                binding_path=self.binding_path,
                configured_repository="ddd-mall/j-store",
                configured_repository_url="https://github.com/ddd-mall/j-store.git",
                unbound_repository="ddd-mall/j-store",
            )

    def test_binding_parser_rejects_url_and_unknown_field_drift(self) -> None:
        payload = self.binding.to_json()
        payload["repository_url"] = "https://github.com/ddd-mall/other.git"
        with self.assertRaisesRegex(ValueError, "URL"):
            RuntimeBinding.from_json(payload)

        payload = self.binding.to_json()
        payload["unexpected"] = True
        with self.assertRaisesRegex(ValueError, "only the required fields"):
            RuntimeBinding.from_json(payload)

    def test_profile_preparation_preserves_contract_bytes_and_binds_repository(self) -> None:
        output = self.root / "prepared"
        contract_sha256, binding_sha256 = prepare_disposable_runtime_profile(
            authoritative_contract_path=LEVEL_ZERO_CONTRACT,
            candidate_contract_path=LEVEL_TWO_CONTRACT,
            repository=self.repository,
            output_directory=output,
        )

        self.assertEqual(LEVEL_TWO_CONTRACT.read_bytes(), (output / "state-contract.json").read_bytes())
        prepared = RuntimeBinding.load(output / "runtime-binding.json")
        self.assertEqual(self.repository, prepared.repository)
        self.assertEqual(2, prepared.capability_level)
        self.assertEqual(contract_sha256, prepared.state_contract_sha256)
        self.assertEqual(64, len(binding_sha256))

    def test_profile_preparation_rejects_production_repository_before_writing(self) -> None:
        output = self.root / "rejected"
        with self.assertRaisesRegex(ValueError, "must not target"):
            prepare_disposable_runtime_profile(
                authoritative_contract_path=LEVEL_ZERO_CONTRACT,
                candidate_contract_path=LEVEL_TWO_CONTRACT,
                repository="DDD-MALL/J-STORE",
                output_directory=output,
            )
        self.assertFalse(output.exists())

    def test_profile_binding_digest_distinguishes_repositories(self) -> None:
        first_contract, first_binding = prepare_disposable_runtime_profile(
            authoritative_contract_path=LEVEL_ZERO_CONTRACT,
            candidate_contract_path=LEVEL_TWO_CONTRACT,
            repository="ddd-mall/disposable-a",
            output_directory=self.root / "profile-a",
        )
        second_contract, second_binding = prepare_disposable_runtime_profile(
            authoritative_contract_path=LEVEL_ZERO_CONTRACT,
            candidate_contract_path=LEVEL_TWO_CONTRACT,
            repository="ddd-mall/disposable-b",
            output_directory=self.root / "profile-b",
        )

        self.assertEqual(first_contract, second_contract)
        self.assertNotEqual(first_binding, second_binding)


if __name__ == "__main__":
    unittest.main()

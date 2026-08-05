from __future__ import annotations

import copy
import importlib.util
import json
import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
SKILL_ROOT = REPO_ROOT / ".codex" / "skills" / "spec-dev"
VALIDATOR_PATH = SKILL_ROOT / "scripts" / "validate_evals.py"
MODULE_SPEC = importlib.util.spec_from_file_location("validate_evals", VALIDATOR_PATH)
assert MODULE_SPEC and MODULE_SPEC.loader
validate_evals = importlib.util.module_from_spec(MODULE_SPEC)
sys.modules[MODULE_SPEC.name] = validate_evals
MODULE_SPEC.loader.exec_module(validate_evals)


class BehavioralEvalValidationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.cases = {
            data["id"]: data
            for path in sorted((SKILL_ROOT / "evals" / "cases").glob("*.json"))
            for data in [json.loads(path.read_text(encoding="utf-8"))]
        }
        cls.run_records = {
            path: json.loads(path.read_text(encoding="utf-8"))
            for path in sorted((SKILL_ROOT / "evals" / "runs").glob("*.json"))
        }
        cls.run_record = cls.run_records[SKILL_ROOT / "evals" / "runs" / "release-forward-analyze.json"]

    def test_all_recorded_runs_match_current_cases_candidate_and_traces(self) -> None:
        errors = []
        for path, run_record in self.run_records.items():
            errors.extend(validate_evals.schema_errors(run_record, "eval-run.schema.json", path.name))
            errors.extend(validate_evals.validate_run(run_record, path.name, self.cases, SKILL_ROOT))
        self.assertEqual([], errors)

    def test_run_schema_requires_durable_provenance(self) -> None:
        run = copy.deepcopy(self.run_record)
        run.pop("trace")
        run["unexpected"] = True
        errors = validate_evals.schema_errors(run, "eval-run.schema.json", "run.json")
        self.assertTrue(any("trace" in error for error in errors))
        self.assertTrue(any("unexpected" in error for error in errors))

    def test_tampered_trace_digest_is_rejected(self) -> None:
        run = copy.deepcopy(self.run_record)
        run["traceDigest"] = "0" * 64
        errors = validate_evals.validate_run(run, "run.json", self.cases, SKILL_ROOT)
        self.assertTrue(any("traceDigest" in error for error in errors))

    def test_stale_candidate_digest_is_rejected(self) -> None:
        run = copy.deepcopy(self.run_record)
        run["candidate"]["digest"] = "0" * 64
        errors = validate_evals.validate_run(run, "run.json", self.cases, SKILL_ROOT)
        self.assertTrue(any("candidate digest" in error for error in errors))

    def test_stale_case_digest_is_rejected(self) -> None:
        run = copy.deepcopy(self.run_record)
        run["caseDigest"] = "0" * 64
        errors = validate_evals.validate_run(run, "run.json", self.cases, SKILL_ROOT)
        self.assertTrue(any("caseDigest" in error for error in errors))


if __name__ == "__main__":
    unittest.main()

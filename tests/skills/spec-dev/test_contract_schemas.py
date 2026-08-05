from __future__ import annotations

import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


REPO_ROOT = Path(__file__).resolve().parents[3]
SKILL_ROOT = REPO_ROOT / ".codex" / "skills" / "spec-dev"


class ContractSchemaTest(unittest.TestCase):
    def test_all_schema_documents_are_valid(self) -> None:
        for path in sorted((SKILL_ROOT / "schemas").glob("*.json")):
            with self.subTest(schema=path.name):
                Draft202012Validator.check_schema(json.loads(path.read_text(encoding="utf-8")))

    def test_behavior_cases_match_their_schema(self) -> None:
        schema = json.loads((SKILL_ROOT / "schemas" / "eval-case.schema.json").read_text(encoding="utf-8"))
        validator = Draft202012Validator(schema)
        cases = sorted((SKILL_ROOT / "evals" / "cases").glob("*.json"))
        self.assertGreaterEqual(len(cases), 7)
        for path in cases:
            with self.subTest(case=path.name):
                validator.validate(json.loads(path.read_text(encoding="utf-8")))

    def test_recorded_runs_match_their_schema(self) -> None:
        schema = json.loads((SKILL_ROOT / "schemas" / "eval-run.schema.json").read_text(encoding="utf-8"))
        validator = Draft202012Validator(schema)
        runs = sorted((SKILL_ROOT / "evals" / "runs").glob("*.json"))
        self.assertGreaterEqual(len(runs), 7)
        for path in runs:
            with self.subTest(run=path.name):
                validator.validate(json.loads(path.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()

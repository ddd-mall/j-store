from __future__ import annotations

import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
SPECCTL_PATH = REPO_ROOT / ".codex" / "skills" / "spec-dev" / "scripts" / "specctl.py"
MODULE_SPEC = importlib.util.spec_from_file_location("specctl", SPECCTL_PATH)
assert MODULE_SPEC and MODULE_SPEC.loader
specctl = importlib.util.module_from_spec(MODULE_SPEC)
sys.modules[MODULE_SPEC.name] = specctl
MODULE_SPEC.loader.exec_module(specctl)


class SpecCtlTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.spec_root = self.root / "docs" / "spec"
        (self.spec_root / "current").mkdir(parents=True)
        (self.spec_root / "current" / "orders.md").write_text("# Orders\n\nORDER-CANCEL-01 is terminal.\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_change(
        self,
        change_id: str,
        *,
        current_outcome: str = "ORDER-CANCEL-01",
        evidence_result: str | None = "passed",
        impact: str = "delta",
        rationale: str | None = None,
    ) -> specctl.LocatedChange:
        directory = self.spec_root / "changes" / change_id
        directory.mkdir(parents=True)
        artifacts = {"delta": "delta.md", "evidence": "evidence.json"} if impact == "delta" else {"evidence": "evidence.json"}
        metadata = {
            "schemaVersion": 1,
            "id": change_id,
            "kind": "feature",
            "specImpact": impact,
            "currentRefs": ["docs/spec/current/orders.md"],
            "artifacts": artifacts,
            "outcomes": [
                {
                    "id": "ORDER_CANCEL_NEW",
                    "summary": "Define cancellation behavior",
                    "operation": "modified" if impact == "delta" else "unchanged",
                    **({"currentOutcomeId": current_outcome} if impact == "delta" else {}),
                }
            ],
        }
        if rationale is not None:
            metadata["specImpactRationale"] = rationale
        (directory / "change.json").write_text(json.dumps(metadata), encoding="utf-8")
        if impact == "delta":
            (directory / "delta.md").write_text("# MODIFIED\n\nORDER_CANCEL_NEW\n", encoding="utf-8")
        evidence = {
            "schemaVersion": 1,
            "changeId": change_id,
            "evidence": []
            if evidence_result is None
            else [
                {
                    "id": "test-1",
                    "type": "test",
                    "outcomeIds": ["ORDER_CANCEL_NEW"],
                    "result": evidence_result,
                    "summary": "Focused behavior test",
                }
            ],
        }
        (directory / "evidence.json").write_text(json.dumps(evidence), encoding="utf-8")
        return specctl.locate_change(self.spec_root, change_id)

    def test_valid_change_is_convergence_candidate(self) -> None:
        located = self.write_change("change-one")
        result = specctl.validate_change(located, self.root, strict=True)
        self.assertEqual([], result["findings"])
        self.assertEqual([], specctl.evidence_gaps(result))
        self.assertEqual("candidate", specctl.summarize_status(result)["state"])

    def test_missing_passed_evidence_is_a_gap(self) -> None:
        located = self.write_change("change-one", evidence_result="partial")
        result = specctl.validate_change(located, self.root, strict=True)
        gaps = specctl.evidence_gaps(result)
        self.assertEqual(1, len(gaps))
        self.assertIn("no passed evidence", gaps[0]["message"])
        self.assertEqual("verifying", specctl.summarize_status(result)["state"])

    def test_removed_outcome_requires_evidence_by_default(self) -> None:
        located = self.write_change("change-one", evidence_result=None)
        metadata = json.loads(located.metadata_path.read_text(encoding="utf-8"))
        metadata["outcomes"][0]["operation"] = "removed"
        located.metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
        result = specctl.validate_change(located, self.root, strict=True)
        gaps = specctl.evidence_gaps(result)
        self.assertEqual(1, len(gaps))
        self.assertIn("ORDER_CANCEL_NEW", gaps[0]["message"])

    def test_parallel_modifications_conflict(self) -> None:
        first = specctl.validate_change(self.write_change("change-one"), self.root, strict=True)
        second = specctl.validate_change(self.write_change("change-two"), self.root, strict=True)
        conflicts = specctl.conflict_findings([first, second])
        self.assertEqual(1, len(conflicts["change-one"]))
        self.assertEqual(1, len(conflicts["change-two"]))
        self.assertEqual(["ORDER_CANCEL_NEW"], conflicts["change-one"][0]["outcomeIds"])

    def test_generated_conflict_finding_round_trips_through_findings_json(self) -> None:
        first = specctl.validate_change(self.write_change("change-one"), self.root, strict=True)
        second = specctl.validate_change(self.write_change("change-two"), self.root, strict=True)
        generated = specctl.conflict_findings([first, second])["change-one"][0]
        located = specctl.locate_change(self.spec_root, "change-one")
        metadata = json.loads(located.metadata_path.read_text(encoding="utf-8"))
        metadata["artifacts"]["findings"] = "findings.json"
        located.metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
        (located.directory / "findings.json").write_text(
            json.dumps({"schemaVersion": 1, "changeId": "change-one", "findings": [generated]}),
            encoding="utf-8",
        )

        result = specctl.validate_change(located, self.root, strict=True)
        checks = {item["check"] for item in result["findings"]}
        self.assertNotIn("finding.unknown-outcome", checks)

    def test_targeted_converge_detects_sibling_delta_conflict(self) -> None:
        self.write_change("change-one")
        self.write_change("change-two")
        code, payload = self.run_cli("converge", "change-one", "--strict")
        self.assertEqual(1, code)
        self.assertEqual("blocked", payload["results"][0]["result"])
        checks = {item["check"] for item in payload["results"][0]["findings"]}
        self.assertTrue(any(check.startswith("change.conflict.") for check in checks))

    def test_no_impact_requires_rationale(self) -> None:
        located = self.write_change("refactor-one", impact="none", rationale=None)
        result = specctl.validate_change(located, self.root, strict=True)
        checks = {item["check"] for item in result["findings"]}
        self.assertIn("metadata.impact-rationale", checks)

    def test_no_impact_with_rationale_is_valid(self) -> None:
        located = self.write_change("refactor-one", impact="none", rationale="Private refactor only")
        result = specctl.validate_change(located, self.root, strict=True)
        self.assertEqual([], result["findings"])

    def test_modified_outcome_must_exist_in_declared_current_specs(self) -> None:
        located = self.write_change("change-one", current_outcome="DOES_NOT_EXIST")
        result = specctl.validate_change(located, self.root, strict=True)
        checks = {item["check"] for item in result["findings"]}
        self.assertIn("outcome.current-id-not-found", checks)

    def test_modified_outcome_requires_current_refs(self) -> None:
        located = self.write_change("change-one")
        metadata = json.loads(located.metadata_path.read_text(encoding="utf-8"))
        metadata.pop("currentRefs")
        located.metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
        result = specctl.validate_change(located, self.root, strict=True)
        checks = {item["check"] for item in result["findings"]}
        self.assertIn("outcome.current-ref-required", checks)

    def test_no_impact_rejects_changed_outcome(self) -> None:
        located = self.write_change("refactor-one", impact="none", rationale="No behavior change")
        metadata = json.loads(located.metadata_path.read_text(encoding="utf-8"))
        metadata["outcomes"][0]["operation"] = "added"
        located.metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
        result = specctl.validate_change(located, self.root, strict=True)
        checks = {item["check"] for item in result["findings"]}
        self.assertIn("metadata.no-impact-changes", checks)

    def test_delta_requires_a_changed_outcome(self) -> None:
        located = self.write_change("change-one")
        metadata = json.loads(located.metadata_path.read_text(encoding="utf-8"))
        metadata["outcomes"][0]["operation"] = "unchanged"
        metadata["outcomes"][0].pop("currentOutcomeId")
        located.metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
        result = specctl.validate_change(located, self.root, strict=True)
        checks = {item["check"] for item in result["findings"]}
        self.assertIn("metadata.delta-outcome-required", checks)

    def run_cli(self, *arguments: str) -> tuple[int, dict]:
        output = io.StringIO()
        with redirect_stdout(output):
            code = specctl.main([*arguments, "--root", str(self.root), "--json"])
        return code, json.loads(output.getvalue())

    def test_converge_reports_candidate_without_overclaiming(self) -> None:
        self.write_change("change-one")
        code, payload = self.run_cli("converge", "change-one", "--strict")
        self.assertEqual(0, code)
        self.assertEqual("candidate", payload["results"][0]["result"])
        self.assertTrue(payload["results"][0]["semanticReviewRequired"])

    def test_analyze_reports_candidate_without_semantic_clean_claim(self) -> None:
        self.write_change("change-one")
        code, payload = self.run_cli("analyze", "change-one", "--strict")
        self.assertEqual(0, code)
        self.assertEqual("candidate", payload["results"][0]["result"])
        self.assertTrue(payload["results"][0]["semanticReviewRequired"])

    def test_converge_exit_code_reports_evidence_gap(self) -> None:
        self.write_change("change-one", evidence_result=None)
        code, payload = self.run_cli("converge", "change-one", "--strict")
        self.assertEqual(1, code)
        self.assertEqual("gaps", payload["results"][0]["result"])

    def test_archive_is_read_only_dry_run(self) -> None:
        located = self.write_change("change-one")
        before = sorted(path.relative_to(located.directory) for path in located.directory.rglob("*"))
        code, payload = self.run_cli("archive", "change-one", "--dry-run", "--strict")
        after = sorted(path.relative_to(located.directory) for path in located.directory.rglob("*"))
        self.assertEqual(0, code)
        self.assertEqual("awaiting-semantic-convergence", payload["results"][0]["status"])
        self.assertFalse(payload["results"][0]["archiveReady"])
        self.assertEqual("passed", payload["results"][0]["deterministicPreconditions"])
        self.assertFalse(payload["results"][0]["mutationPerformed"])
        self.assertEqual(before, after)

    def test_missing_jsonschema_is_a_tool_error(self) -> None:
        self.write_change("change-one")
        original = specctl.Draft202012Validator
        specctl.Draft202012Validator = None
        try:
            code, payload = self.run_cli("validate", "change-one", "--strict")
        finally:
            specctl.Draft202012Validator = original
        self.assertEqual(2, code)
        self.assertIn("jsonschema", payload["error"])

    def test_json_schema_rejects_unknown_metadata_fields(self) -> None:
        located = self.write_change("change-one")
        metadata = json.loads(located.metadata_path.read_text(encoding="utf-8"))
        metadata["unexpected"] = True
        located.metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
        result = specctl.validate_change(located, self.root, strict=True)
        schema_checks = [item for item in result["findings"] if item["check"].startswith("schema.change.schema.json")]
        self.assertTrue(schema_checks)

    def test_repository_native_outcome_identifiers_are_supported(self) -> None:
        located = self.write_change("change-one")
        (self.spec_root / "current" / "orders.md").write_text(
            "# Orders\n\norder.cancel.01 is terminal.\n", encoding="utf-8"
        )
        metadata = json.loads(located.metadata_path.read_text(encoding="utf-8"))
        metadata["outcomes"][0]["id"] = "order.cancel.01.delta"
        metadata["outcomes"][0]["currentOutcomeId"] = "order.cancel.01"
        located.metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
        evidence_path = located.directory / "evidence.json"
        evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
        evidence["evidence"][0]["outcomeIds"] = ["order.cancel.01.delta"]
        evidence_path.write_text(json.dumps(evidence), encoding="utf-8")
        result = specctl.validate_change(located, self.root, strict=True)
        self.assertEqual([], result["findings"])

    def test_schema_enforces_recorded_at_date_time_format(self) -> None:
        located = self.write_change("change-one")
        evidence_path = located.directory / "evidence.json"
        evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
        evidence["evidence"][0]["recordedAt"] = "not-a-date"
        evidence_path.write_text(json.dumps(evidence), encoding="utf-8")
        result = specctl.validate_change(located, self.root, strict=True)
        schema_checks = [item for item in result["findings"] if item["check"].startswith("schema.evidence.schema.json")]
        self.assertTrue(schema_checks)


if __name__ == "__main__":
    unittest.main()

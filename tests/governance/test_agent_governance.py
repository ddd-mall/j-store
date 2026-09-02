from __future__ import annotations

import re
import shutil
import subprocess
import tomllib
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


def repository_bash() -> str:
    if shutil.which("git"):
        git = Path(shutil.which("git") or "")
        candidates = (git.with_name("bash.exe"), git.parent.parent / "bin" / "bash.exe")
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
    return shutil.which("bash") or "bash"


class AgentGovernanceContractTest(unittest.TestCase):
    def test_kotlin_plugin_versions_are_not_hardcoded_in_gradle_scripts(self) -> None:
        hardcoded_plugin_version = re.compile(
            r'(?:kotlin\("[^"]+"\)|id\("org\.jetbrains\.kotlin\.[^"]+"\))'
            r'\s+version\s+"[^"]+"'
        )

        offenders = []
        for path in REPO_ROOT.rglob("*.gradle.kts"):
            if hardcoded_plugin_version.search(path.read_text(encoding="utf-8")):
                offenders.append(path.relative_to(REPO_ROOT).as_posix())

        self.assertEqual([], offenders, f"hardcoded Kotlin plugin versions: {offenders}")

    def test_domain_model_is_indexed_and_scheduled_for_long_term_maintenance(self) -> None:
        domain_model = REPO_ROOT / "docs" / "domain-modeling.md"
        self.assertTrue(domain_model.is_file())

        agents = (REPO_ROOT / "AGENTS.md").read_text(encoding="utf-8")
        overview = (REPO_ROOT / "docs" / "project-overview.md").read_text(encoding="utf-8")
        runbook = (
            REPO_ROOT / "docs" / "operations" / "agent-automation-runbook.md"
        ).read_text(encoding="utf-8")

        self.assertIn("docs/domain-modeling.md", agents)
        self.assertIn("domain-modeling.md", overview)
        self.assertIn("docs/domain-modeling.md", runbook)
        self.assertIn("每月", runbook)

    def test_repository_governance_contract(self) -> None:
        result = subprocess.run(
            [repository_bash(), "scripts/check-agent-governance.sh"],
            cwd=REPO_ROOT,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_governance_agents_reference_canonical_policy(self) -> None:
        expected_names = {
            "maintenance_orchestrator",
            "product_steward",
            "quality_gate",
            "security_supply_chain",
            "sre_incident",
            "release_migration",
        }
        agent_paths = sorted((REPO_ROOT / ".codex" / "agents").glob("*.toml"))
        governance_agents: dict[str, Path] = {}

        for path in agent_paths:
            data = tomllib.loads(path.read_text(encoding="utf-8"))
            name = data.get("name")
            if name in expected_names:
                governance_agents[name] = path
                instructions = data.get("developer_instructions", "")
                self.assertIn("AGENTS.md", instructions, path.name)
                self.assertIn("docs/steering/agent-governance.md", instructions, path.name)

        self.assertEqual(expected_names, set(governance_agents))

    def test_quality_gate_uses_a_supported_python_version(self) -> None:
        quality_gate = (REPO_ROOT / "scripts/quality-gate.sh").read_text(
            encoding="utf-8"
        )

        self.assertIn("uv run --python 3.13", quality_gate)
        self.assertIn("Python 3.11 or newer is required", quality_gate)

    def test_governance_search_fallback_translates_ripgrep_globs(self) -> None:
        governance = (REPO_ROOT / "scripts" / "check-agent-governance.sh").read_text(
            encoding="utf-8"
        )

        self.assertIn('"--glob")', governance)
        self.assertIn('grep_options+=("--include=$1")', governance)
        self.assertIn('grep -ERq "${grep_options[@]}" -- "$pattern" "${grep_paths[@]}"', governance)
        self.assertIn('grep -ERq -- "$pattern" "${grep_paths[@]}"', governance)
        self.assertNotIn('grep -ERq -- "$pattern" "$@"', governance)


if __name__ == "__main__":
    unittest.main()

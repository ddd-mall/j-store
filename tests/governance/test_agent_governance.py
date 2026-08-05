from __future__ import annotations

import subprocess
import tomllib
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


class AgentGovernanceContractTest(unittest.TestCase):
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
            ["bash", "scripts/check-agent-governance.sh"],
            cwd=REPO_ROOT,
            check=False,
            capture_output=True,
            text=True,
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


if __name__ == "__main__":
    unittest.main()

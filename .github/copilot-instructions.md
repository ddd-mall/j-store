Read and follow `/AGENTS.md` for every task. For automated maintenance, code review, security, release, migration, or production-related work, also read `/docs/steering/agent-governance.md`.

Do not treat current code as authority for intended product behavior: approved requirements and deltas own intent. Report conflicts as drift findings. Run `./scripts/quality-gate.sh` before claiming repository-wide verification, and report skipped checks explicitly.

Never add credentials or production data, merge automatically, bypass required checks, deploy, execute production migrations, or mutate production without explicit authorization for the exact action.

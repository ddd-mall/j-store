# Adaptive Coordination Reference

Use this reference when resuming existing specification work, coordinating specialized roles, or preparing the final handoff. The canonical outcome and autonomy rules live in `../../SKILL.md`.

## Resume Existing Work

Establish the current state from:

- the latest user intent and accepted decisions;
- relevant current specifications, active change deltas, or legacy files under `docs/spec/`;
- affected code, tests, and repository guidance;
- current working-tree changes and available verification evidence.

Determine semantic gaps rather than routing solely by which file exists. When `change.json` is present, use `specctl status` or `validate` for deterministic state without mistaking it for semantic completion. Continue with the capability that closes the highest-value gap: intent clarification, analysis, design, planning, implementation, convergence, or verification. Work directly when delegation would not improve quality or independence.

## Detect And Reconcile Drift

Treat user-declared edits, content differences, timestamps, and hashes as signals to inspect. Classify the change:

- **No semantic impact:** formatting, wording, or unrelated guidance; preserve downstream work.
- **Localized impact:** update affected decisions, tasks, code, or tests only.
- **Material intent or contract impact:** reconcile all affected downstream behavior and evidence before implementation continues or completion is claimed.

Record significant reconciliation decisions. Do not equate a newer timestamp with stale content.

## Coordinate Reviews

Choose review scope from risk:

- use self-review and final verification for routine low-risk work;
- use an independent evaluator for broad, uncertain, security-sensitive, compatibility-sensitive, concurrent, migration, or public-contract changes;
- review the integrated feature when interactions across slices may fail even though individual slices pass.

On a blocking finding, correct the owning source and re-evaluate the affected scope. Escalate when the decision exceeds agent authority or meaningful safe progress is no longer possible; do not rely on a fixed retry count.

## Final Handoff

Report:

- delivered observable behavior and scope;
- material artifacts and changed paths;
- significant design decisions and assumptions;
- verification commands or other evidence and their results;
- acceptance and quality-goal coverage;
- skipped checks, deviations, residual risks, and follow-ups.

Write or update `summary.md` when the feature is substantial, the repository expects a durable handoff, or the user requests one. A summary does not replace feature-level evidence.

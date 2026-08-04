# Converge Capability

## Outcome

Determine whether the integrated implementation and evidence satisfy the accepted desired state, or expose the concrete gaps that remain.

For behavior-affecting work, evaluate against the applicable current specifications plus the accepted delta. Include feature-level interactions when separately correct slices can still fail together.

## Evidence Basis

Use evidence appropriate to the accepted outcomes and risks, such as executable tests, contract checks, migration results, static analysis, manual scenarios, operational checks, code inspection, or independent review. Evidence MUST identify what it substantiates and disclose material limitations.

Do not infer convergence from completed tasks, artifact presence, a green build unrelated to the change, or a prior review of a narrower slice.

## Assessment

Establish whether:

- every accepted outcome has implementation coverage and identifiable evidence;
- selected quality goals and unchanged behavior are preserved;
- compatibility, migration, rollout, rollback, security, and operational concerns are resolved where applicable;
- skipped checks, deviations, and residual risks are honestly represented;
- current specifications, delta, implementation, tests, and final documentation agree on material behavior;
- blocking analysis or review findings are resolved at their owning source.

Use `schemas/evidence.schema.json` and `schemas/finding.schema.json` when structured state is useful. Deterministic coverage checks may establish readiness for semantic judgment, but MUST NOT claim semantic convergence by themselves.

## Verdict

Return one of:

- `converged`: accepted outcomes are satisfied with sufficient evidence and no unresolved blocker;
- `gaps`: one or more concrete, resolvable gaps remain;
- `blocked`: a required decision, environment, authority, or unavailable evidence prevents a defensible verdict.

Keep the convergence assessment read-only. Report gaps with ownership, evidence, and resolution conditions; do not rewrite code, specifications, or task status while evaluating. After the assessment ends, an authorized delivery agent may decide how to resolve each gap as a separate action, then request reassessment until a terminal verdict is justified.

Use independent convergence for high-risk or broad changes when separation of context materially improves confidence.

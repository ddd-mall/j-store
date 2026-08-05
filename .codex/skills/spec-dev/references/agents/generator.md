# Implementation Capability

## Outcome

Produce a working change that satisfies accepted behavior, significant design decisions, applicable repository constraints, and selected quality goals, with evidence proportionate to risk.

## Establish Readiness

Before changing code, understand the relevant intent, constraints, current implementation, and verification expectations. Use existing `requirement.md`, `design.md`, and `tasks.md` when present, but do not treat an artifact as authoritative when it visibly conflicts with a later accepted decision.

Resolve low-risk implementation gaps autonomously. Use `clarification.md` only for decisions that cross the escalation threshold. If upstream content is materially inconsistent, correct or route the owning decision and reconcile affected downstream work.

## Implement Adaptively

- Choose coherent slices, order, tools, and local code structure according to the repository and task risk.
- Preserve unrelated user and concurrent changes.
- Keep public contracts, domain behavior, data semantics, error behavior, and security boundaries consistent with accepted decisions.
- Add small enabling changes when necessary and low-risk; report them. Escalate material scope expansion.
- Update the plan or design when discoveries change significant decisions, rather than forcing code to follow a stale mechanical instruction.

Task checkboxes MAY track progress, but mark a task complete only when its intended outcome and completion evidence are satisfied. Do not require an independent evaluator for every code or test edit.

## Verify The Change

Choose focused and broad checks based on where defects could escape:

- focused tests for local behavior;
- contract or integration tests for boundaries;
- end-to-end scenarios for user-visible flows;
- migration and compatibility checks for data or public contracts;
- security, concurrency, performance, or recovery checks when those risks are material;
- build, static analysis, formatting, or manual inspection as relevant.

Do not silently skip necessary evidence because a harness is heavyweight or unavailable. Attempt an equivalent safe check when possible. Otherwise classify and report the limitation; treat it as blocking when confidence in an accepted outcome would be materially insufficient.

Perform feature-level verification after integrated behavior exists. Slice-level success cannot prove cross-slice correctness by itself.

## Review Strategy

Use self-review for every change. Invoke an independent evaluator when breadth, uncertainty, risk, or user instruction warrants separation of context. Review the integrated feature as well as individual slices when interactions matter.

On a blocking finding, fix the owning cause and re-verify affected behavior. Escalate when the decision exceeds authority or meaningful safe progress is no longer possible; do not stop merely because a fixed retry count was reached.

Maintain `review-log.md` only when the feature already uses it, strict audit is requested, or repeated independent review needs a durable history.

## Completion Evidence

Report:

- observable behavior delivered;
- material changed paths and enabling changes;
- acceptance and quality-goal evidence;
- verification commands and results;
- skipped checks, deviations, assumptions, residual risks, and follow-ups.

Do not claim completion until the skill's feature-level Definition of Done is satisfied.

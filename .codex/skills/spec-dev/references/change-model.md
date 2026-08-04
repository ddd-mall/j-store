# Current State And Change Model

## Outcome

Keep durable specifications honest about two distinct states:

- **current**: accepted behavior that the system is expected to provide now;
- **change**: a bounded proposal to add, modify, or remove behavior.

For behavior-affecting work, assess the implementation against:

`desired state = applicable current specifications + accepted change delta`

Do not require this model for a trivial change when it would only restate obvious information.

## Current Specifications

Organize current specifications by stable business capability rather than by historical ticket. A current specification should describe externally observable behavior, important invariants, boundaries, and selected quality goals that remain relevant after one change is finished.

Use `docs/spec/current/<capability>.md` when the repository has adopted this convention. Existing repositories MAY retain legacy feature folders and migrate a capability when it is next materially changed; do not require a speculative whole-repository rewrite.

## Active Changes

Keep a substantial active change under `docs/spec/changes/<change-id>/` when durable separation is useful. Use the minimum artifacts that make the change understandable and verifiable. A typical change MAY contain:

- `change.json`: identity, kind, spec impact, artifact paths, and stable outcome summaries;
- `delta.md`: additions, modifications, and removals to current behavior;
- `requirement.md`, `design.md`, or `tasks.md` when their distinct content improves delivery;
- `evidence.json` and `findings.json` when machine-readable verification or review state is useful.

The agent decides which human-readable artifacts are warranted. Machine-readable metadata MUST reference them without duplicating their full semantics.

## Delta Semantics

Describe only the behavior that changes. Prefer the headings `ADDED`, `MODIFIED`, and `REMOVED`; use stable outcome identifiers only where they materially improve matching, conflict detection, or evidence traceability.

A modified outcome MUST identify the current outcome it replaces. A removal MUST preserve enough context to show what is no longer expected. If two active changes modify or remove the same current outcome, surface a conflict before claiming either change is ready.

For refactors, tooling, or documentation work with no intended behavior change, declare `specImpact` as `none` and provide a concise rationale. Do not create an empty delta merely to satisfy a template.

## Reconciliation And Archive

Implementation discoveries MAY change the proposal, delta, design, or tasks. Update the owning source and re-evaluate affected downstream evidence rather than treating the first draft as immutable.

Archive only when:

- convergence has no unresolved blocking gap;
- the delta still applies cleanly to current specifications;
- accepted behavior and material evidence are synchronized;
- remaining limitations are explicitly accepted or recorded as residual risk.

Archiving folds the accepted delta into current specifications and preserves the change as dated history. Perform the merge deliberately and review the resulting current specification; do not infer that moving a folder proves semantic convergence.

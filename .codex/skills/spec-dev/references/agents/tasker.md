# Task Decomposition Capability

## Outcome

Create an implementation plan that exposes dependencies, reduces risk, and lets each meaningful slice be completed and verified without losing feature-level coherence.

Use `docs/spec/<feature-slug>/tasks.md` when written decomposition improves coordination, recovery, review, or execution. Keep a simple change in a concise plan or proceed directly when a separate task artifact would be redundant.

## Task Contract

Each executable task SHOULD communicate:

- the outcome or behavior it advances;
- relevant dependencies or prerequisites;
- the accepted requirements, design decisions, or risks it covers;
- completion evidence or verification expectations;
- any material compatibility, migration, rollout, or rollback concern.

Include concrete paths, symbols, commands, or code details when they make execution more reliable, not as mandatory decoration. Allow the implementing agent to refine local mechanics while preserving the task outcome.

## Decomposition Strategy

Choose task boundaries and order from the work itself. Valid strategies include vertical user-value slices, dependency order, risk-first exploration, contract-first work, migration sequencing, or repository-defined layers.

Prefer slices that are:

- coherent and independently reviewable;
- small enough to reason about and verify;
- large enough to deliver meaningful progress;
- safe to revise or roll back;
- explicit about interactions with other slices.

Do not enforce universal file-count, line-count, task-count, checkpoint-frequency, or layer-order thresholds. Split when complexity, reviewability, context, ownership, or risk warrants it.

Sequence schema and data changes according to compatibility, deployability, rollback, and repository migration practices. Do not assume that DDL always comes first or backfill always comes last.

## Validation Planning

Plan evidence at the level where a failure could occur. Component tests may accompany a slice, while contract, integration, end-to-end, migration, security, or performance verification may span several slices.

Ensure every accepted outcome and selected quality goal has an evidence path. Do not duplicate tests merely to mirror the task hierarchy.

## Adaptive Planning

Treat the plan as a maintained decision aid, not an immutable command list. The implementing agent MAY reorder, merge, split, or refine tasks when discoveries make the change safer or simpler. Reconcile material scope, contract, or verification changes with upstream artifacts.

Use `clarification.md` only when a planning decision exceeds technical authority. Otherwise choose a defensible plan and record consequential assumptions.

## Readiness Evidence

The task outcome is ready when implementation can begin with clear next steps, dependencies, completion evidence, and feature-level coverage. User approval is required only when requested or when the plan exposes an escalation-grade decision.

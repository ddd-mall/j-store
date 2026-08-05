# Design Capability

## Outcome

Define a technically viable approach that satisfies accepted intent and applicable repository constraints while leaving local, reversible implementation choices to the implementer.

Use `docs/spec/<feature-slug>/design.md` when significant decisions need to persist across implementation or review. Combine the design with another artifact for a narrow change when a separate document would add no durable value.

## Design Coverage

Capture only applicable concerns, with detail proportional to their risk:

- system context, affected boundaries, responsibilities, and interactions;
- public or cross-component contracts;
- domain and persistence models, data ownership, and migration implications;
- transaction, consistency, concurrency, idempotency, and failure behavior;
- authorization, privacy, trust boundaries, abuse cases, and other security concerns;
- compatibility, rollout, rollback, recovery, and operational behavior;
- observability, capacity, performance, and resource constraints;
- verification strategy for functional outcomes and selected quality goals.

Document significant alternatives and why the selected option best fits the evidence. Do not manufacture a decision table, diagram, or section when there is no material decision to communicate.

## Appropriate Precision

Make public contracts and cross-boundary behavior precise enough to implement and review. Include schemas, examples, state transitions, signatures, or diagrams when they remove consequential ambiguity.

Avoid prescribing imports, annotations, constructors, private fields, exact file paths, or internal method signatures unless they are an accepted contract, repository requirement, or necessary to prevent a material error. Let the implementing agent choose local code structure consistent with the repository.

## Quality And Risk

Translate applicable quality goals into design mechanisms and verification targets. For example, a latency goal needs a measurement boundary; a recovery goal needs failure behavior; an authorization goal needs an explicit policy and trust boundary.

Surface risks with consequence, mitigation, and verification—not generic checklists. Omit inapplicable quality categories instead of filling empty sections.

## Decision Behavior

Choose reversible technical options autonomously when repository evidence supports them. Record the rationale. Use `clarification.md` only for decisions that affect product behavior, public contracts, security/privacy, irreversible data, material cost, or another escalation condition.

If an accepted requirement must change, route that product decision to its owner. If implementation details evolve without changing intent or material contracts, update the design directly and reconcile affected evidence.

## Readiness Evidence

The design outcome is ready when an implementer can proceed safely and a reviewer can determine:

- how each accepted outcome is covered;
- which significant contracts and decisions must hold;
- how relevant failure and quality risks are handled;
- what evidence will demonstrate success.

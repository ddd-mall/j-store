# Analyze Capability

## Outcome

Produce a read-only assessment of whether accepted intent and delivery artifacts are sufficiently complete, consistent, traceable, and verifiable to support the next material decision.

Use analysis when ambiguity, breadth, risk, cross-component behavior, parallel deltas, or user instruction makes inconsistency consequential. A small, coherent change MAY proceed without a separate analysis artifact.

## Assessment Scope

Inspect the applicable sources of truth rather than assuming a fixed file set:

- accepted intent, decisions, exclusions, and quality goals;
- relevant current specifications and the proposed delta;
- requirement, design, and task artifacts when present;
- repository constraints and known implementation evidence.

Assess at least the dimensions that apply:

- ambiguity, contradictions, missing boundary behavior, and unstated material assumptions;
- coverage from accepted outcomes to design, implementation slices, and planned evidence;
- testability of acceptance and quality goals;
- compatibility, migration, security, operational, and rollback implications;
- conflicts between active changes or between a delta and current specifications;
- obsolete downstream content after an upstream decision changed.

## Findings

For every material finding, identify:

- a stable finding identifier and severity;
- whether it blocks safe progress;
- the owning source: intent, current spec, delta, design, tasks, implementation, tests, or repository policy;
- concrete artifact location and evidence;
- affected outcome identifiers when available;
- impact if unresolved and the condition that would resolve it.

Use `schemas/finding.schema.json` when emitting machine-readable findings. Do not fail work for preferred formatting, optional artifacts, or unsupported speculation.

## Result

Return one of:

- `clean`: no material inconsistency was found in the assessed scope;
- `findings`: resolvable gaps or conflicts were found;
- `blocked`: required evidence or a decision outside agent authority prevents a defensible assessment.

State the assessed scope and limitations. `clean` means clean within that scope; it is not proof that the implementation is complete. Route findings to the source that owns the correction and re-analyze affected scope after material changes.

# Spec consistency analysis

**Result:** `findings`  
**Safe implementation status:** Blocked until the artifacts are reconciled.

## Assessed scope

- `requirement.md` requires cancelled orders to be reopenable.
- `design.md` rejects every transition from cancelled.
- `tasks.md` implements the design’s terminal-cancellation behavior.

This is a read-only semantic review. No implementation or test evidence was provided, so the assessment covers requirement-to-design-to-task consistency rather than implementation completeness.

## Findings

### ORDER-REOPEN-001 — High severity, blocking

- **Owner:** `design.md`
- **Evidence:** Its cancelled-state transition rule rejects all outgoing transitions, while `requirement.md` requires at least one valid transition that reopens a cancelled order.
- **Affected outcome:** Cancelled orders can be reopened.
- **Impact:** An implementation conforming to the design must reject behavior required by the accepted intent. The requirement and design cannot both be satisfied.
- **Resolution condition:** Confirm the requirement as the controlling product decision, then revise the design to define the reopen transition. If cancellation is intended to remain terminal, changing the requirement instead requires explicit stakeholder authorization because that changes externally observable behavior.

The corrected design should specify which cancelled orders are eligible, the destination state, affected order-line states, required inventory or payment checks, emitted events, failure behavior, and atomicity.

### ORDER-REOPEN-002 — High severity, blocking

- **Owner:** `tasks.md`
- **Evidence:** The tasks implement the design’s blanket rejection of transitions from cancelled rather than the reopen behavior in `requirement.md`.
- **Affected outcome:** Cancelled orders can be reopened.
- **Impact:** Completing the current tasks would provide evidence for the wrong behavior. Task completion cannot establish feature completion because the accepted outcome remains uncovered.
- **Resolution condition:** After the design is corrected, revise or replace the affected tasks with a verifiable reopen implementation slice. Any task that asserts universal terminal behavior for cancelled orders must be narrowed so it permits the explicitly authorized reopen transition.

### ORDER-REOPEN-003 — High severity, blocking

- **Owner:** `tasks.md` and planned tests
- **Evidence:** A plan implementing universal rejection necessarily lacks positive evidence for a successful reopen path and may encode the opposite behavior as a regression test.
- **Affected outcome:** Cancelled orders can be reopened.
- **Impact:** The test suite could pass while preventing the required behavior from ever being implemented.
- **Resolution condition:** Add verification for:
  - every eligible cancellation category;
  - exact before-and-after aggregate and line-item states;
  - prohibited reopen attempts;
  - failure atomicity with no partial state or event mutation;
  - persistence round trips;
  - public API and domain-event behavior where applicable;
  - repeated or concurrent reopen requests when relevant.

## Traceability

| Accepted outcome | Design | Tasks | Planned verification |
| --- | --- | --- | --- |
| Cancelled orders can be reopened | Contradicted | Contradicted | Missing or proves the opposite |

`design.md` and `tasks.md` are mutually consistent, but that downstream consistency does not satisfy the requirement.

## Required convergence

1. Confirm the intended externally observable cancellation behavior.
2. Make the reopen acceptance criteria precise if eligibility and resulting state are not already defined.
3. Update `design.md` to model the accepted transition and preserve all applicable invariants.
4. Update `tasks.md` and its verification plan to implement the corrected design.
5. Re-analyze the affected traceability before implementation proceeds.
6. If code already follows the current tasks, classify it as implementation drift and revise it only after the artifacts converge.

## Verdict

The change is not implementation-ready and must not be declared complete. The downstream artifacts currently implement the opposite of the accepted requirement. Safe progress resumes when requirement, design, tasks, and planned evidence express one consistent reopen contract.

No artifacts or workspace files were changed.

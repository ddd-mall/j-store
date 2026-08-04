# Bugfix Outcome Contract

## Outcome

Restore expected behavior with a change proportionate to the defect while preserving behavior that should remain unchanged.

Use this contract for a defect, regression, or incorrect implementation. If investigation shows that the requested result is materially new product behavior, treat that part as a feature change instead of disguising it as a bugfix.

## Required Understanding

Capture enough durable information to establish:

- the observed symptom, impact, affected scope, and triggering conditions;
- current incorrect behavior and expected correct behavior;
- unchanged behavior and boundaries that protect against regression;
- reproduction evidence, or why reliable reproduction is unavailable;
- root-cause evidence and remaining causal uncertainty;
- the verification needed to demonstrate the defect is fixed and protected from recurrence.

Do not present a plausible code location as a proven root cause. Record the observations that connect the cause to the symptom.

## Relationship To Current Specifications

Classify the result before updating durable specifications:

- If implementation violates an existing current specification, the fix restores current truth and normally needs no behavior delta.
- If expected behavior was previously unstated, add or modify the current specification through a delta so the corrected expectation becomes durable.
- If stakeholders choose behavior different from the prior accepted expectation, model it as a behavior change with an explicit delta.

## Verification

Use the most direct available evidence to show:

- the original defect or its causal condition existed before the fix when reproducible;
- the corrected implementation provides expected behavior;
- identified unchanged behavior still holds;
- the test or check would detect a meaningful regression rather than merely execute the changed line.

Property-based, example-based, integration, contract, or manual verification are all valid when they fit the defect. Do not require a particular technique.

## Artifact Proportionality

A concise `bugfix.md` plus implementation and evidence MAY be sufficient for a well-understood repair. Add separate design, tasks, rollout, monitoring, or rollback material only when complexity, risk, compliance, or coordination makes it valuable.

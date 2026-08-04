# Independent Evaluation Capability

## Outcome

Assess whether a change has sufficient evidence to satisfy accepted intent, applicable quality goals, significant design decisions, and repository constraints. Keep review independent: report findings and do not modify implementation while acting as evaluator.

Choose review scope according to the risk. Evaluate a slice when it is independently meaningful; evaluate the integrated feature when interactions, migrations, public contracts, or cross-cutting quality goals can fail across slices.

For read-only pre-implementation consistency analysis, follow `../analyze.md`. For an integrated completion assessment, follow `../converge.md` and use its `converged`, `gaps`, or `blocked` verdict. Use the generic `PASS` or `FAIL` verdict below for other bounded reviews.

## Evidence Baseline

Inspect the relevant request, accepted decisions, specification artifacts, changed code, tests, repository guidance, and verification results. Obtain enough context to judge outcomes without requiring a fixed handoff template.

Do not assume that document structure or task completion proves correctness. Separate:

- implementation defects;
- requirement, design, or planning defects;
- environmental or harness limitations;
- non-blocking improvement suggestions.

## Evaluation Dimensions

Assess applicable dimensions:

- **Outcome compliance:** observable acceptance behavior is implemented.
- **Quality-goal evidence:** selected performance, reliability, security, privacy, compatibility, usability, maintainability, or operational goals have credible evidence.
- **Design integrity:** significant contracts, data semantics, boundaries, and recorded decisions hold.
- **Repository fit:** applicable steering rules and established conventions are respected.
- **Verification adequacy:** tests and other checks exercise the failure surfaces that matter.
- **Regression and integration:** combined behavior and affected existing behavior remain sound.
- **Completeness:** no material placeholders, hidden skips, or unresolved blockers remain.

Use only dimensions relevant to the reviewed change. Do not manufacture findings for inapplicable categories.

## Verdict Rules

Return `PASS` when no blocking issue remains and the evidence is sufficient for the reviewed scope. Return `FAIL` when an accepted outcome is unmet, a material contract or boundary is violated, required evidence is missing, the change is broken, or residual uncertainty is too high to claim completion.

Treat style preferences, optional refactors, and unsupported speculation as non-blocking. Cite concrete evidence for every blocking finding. If uncertainty has no concrete evidence of violation, state the limitation and its risk rather than inventing a failure.

An upstream contradiction is owned by its source, but it may still block the feature from passing. Route it using `clarification.md` and explain the downstream impact.

## Response Contract

Provide a concise, evidence-backed response containing:

- scope reviewed;
- verdict;
- acceptance and quality-goal coverage;
- blocking findings with location, consequence, and actionable correction;
- non-blocking suggestions when useful;
- upstream issues and environmental limitations;
- residual risks and recommended re-verification scope.

Do not require every response to reproduce a fixed heading tree when a smaller structure communicates the same evidence clearly.

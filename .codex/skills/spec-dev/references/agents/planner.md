# Planning Capability

## Outcome

Create or update a durable expression of stakeholder intent that is clear enough to design, implement, and verify without inventing product behavior.

Use `docs/spec/<feature-slug>/requirement.md` for a substantial feature or when the repository/user expects a dedicated requirement artifact. For a narrow change, contribute the same information to the smallest suitable existing artifact.

## Required Content

Capture, when applicable:

- the problem, motivation, stakeholders, and intended value;
- in-scope outcomes and explicit exclusions;
- system or actor boundaries and important domain terms;
- observable functional acceptance criteria, including meaningful edge and failure cases;
- prioritized quality goals such as performance, reliability, security, privacy, compatibility, usability, accessibility, or operability;
- external constraints, dependencies, assumptions, and unresolved high-impact decisions;
- rollout or compatibility expectations when they are part of stakeholder intent.

Assign stable identifiers when they materially improve traceability. Do not require every statement to be a user story or every term to appear in a glossary.

## Requirement Quality

Ensure accepted requirements are:

- necessary and within scope;
- unambiguous enough for the affected decision;
- feasible within known constraints;
- observable or otherwise objectively verifiable;
- mutually consistent;
- as implementation-independent as the stakeholder need allows;
- measurable when a threshold matters.

Describe outcomes rather than code structure. Record a technology, architecture, or file-level constraint only when it is imposed by the user, repository, integration contract, or another accepted boundary.

## Notation Choice

Choose the notation that communicates the domain most clearly. EARS, Given/When/Then, examples, state transitions, decision tables, invariants, API examples, or concise prose are all valid. Use one or several; do not force a format that obscures intent.

Prefer acceptance criteria that expose user- or system-observable results. Include representative examples where examples communicate boundaries better than abstract prose.

## Decision Behavior

Resolve ordinary terminology and low-risk assumptions from repository evidence. Use `clarification.md` only when an unresolved choice reaches the escalation threshold. Record accepted choices in the requirement artifact rather than only in chat.

## Readiness Evidence

The planning outcome is ready when downstream work can identify:

- what success looks like;
- what is explicitly outside scope;
- which outcomes and quality targets must be verified;
- which high-impact decisions remain unresolved.

Report the artifact path, major assumptions, and any unresolved decision. Do not require a ceremonial approval when the user has already authorized implementation and no escalation condition exists.

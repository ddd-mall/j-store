---
name: spec-dev
description: Deliver feature changes, bugfixes, and requirement revisions through outcome-focused spec-driven development. Use when Codex needs to clarify intent, create or evolve current or change specifications, analyze or converge artifacts, implement from specifications, verify observable acceptance and quality goals, or review consistency among specifications, code, tests, evidence, and repository constraints. Adapt the workflow, artifacts, tools, task granularity, approval points, and use of sub-agents to the change's risk and complexity.
---

# Spec-Driven Delivery

Deliver a working, verified change whose behavior matches stakeholder intent, applicable repository constraints, and explicitly selected quality goals. Treat specifications as durable expressions of intent and evidence, not as mandatory ceremony.

Use the user's language for communication and artifacts. Follow the repository's established language when the user has not expressed a preference.

Interpret `MUST` as a non-negotiable outcome or safety boundary, `SHOULD` as a default that may be changed for a documented reason, and `MAY` as an optional technique.

Within this skill package, this `SKILL.md` is normative. `references/agents/common.md` supplies subordinate shared guidance; role references specialize a capability without overriding this file; prompt references describe optional coordination techniques.

## Outcome Contract

A completed change MUST provide enough durable information and evidence to establish that:

- the problem, intended outcomes, scope, exclusions, constraints, and material assumptions are understood;
- acceptance criteria describe observable behavior and are verifiable;
- applicable quality goals are identified and addressed;
- implementation, tests, and documentation are semantically consistent with accepted intent;
- repository rules and existing user changes are respected;
- relevant regression, compatibility, migration, security, and operational risks are addressed;
- skipped verification, deviations, and residual risks are visible in the final handoff.

Judge completeness at feature level. Completed tasks or a successful review of the latest slice are supporting evidence, not sufficient completion criteria by themselves.

## Agent Decision Authority

Choose the workflow that best achieves the outcome contract. The agent MAY:

- work directly or use available sub-agents;
- combine, reorder, repeat, or omit phases that add no value;
- choose artifact structure and notation;
- choose task boundaries, implementation order, tools, and validation strategy;
- make reversible technical decisions that preserve product intent and repository constraints;
- update a plan when implementation reveals a safer or simpler route.

Do not ask the user to decide ordinary implementation details that can be resolved safely from the codebase, project guidance, or sound engineering judgment. Record architecturally significant decisions and consequential assumptions in the most relevant artifact.

Ask for user direction before proceeding only when a decision would materially affect one or more of:

- externally observable product behavior or accepted scope;
- a public contract or backward compatibility;
- security, privacy, permissions, compliance, or tenant isolation;
- irreversible or destructive data state;
- material external cost, authority, or coordination;
- a trade-off for which the repository and prior user intent provide no defensible default.

When ambiguity is low-risk and the decision is reversible, choose a conservative default, continue, and disclose the assumption.

## Repository Contract

Inspect the code and project guidance relevant to the affected area. Satisfy applicable steering rules and established conventions for architecture, language, framework, persistence, errors, tests, logging, naming, and file placement.

Do not require every role to rediscover all repository facts on every pass. Reuse trustworthy context and reload information when scope changes, evidence is stale, or independent verification benefits from fresh discovery.

Preserve unrelated user changes. Do not broaden the product scope silently. Small enabling changes MAY be included when they are necessary, low-risk, and reported; escalate material scope expansion.

## Adaptive Lifecycle

Use these capabilities as needed rather than as a mandatory linear pipeline:

1. Establish intent, constraints, success criteria, and open risks.
2. Inspect relevant repository context and existing artifacts.
3. Create or update the minimum durable specification needed for the change.
4. Plan coherent, verifiable implementation slices when a written plan improves reliability.
5. Implement while preserving semantic consistency with the accepted intent.
6. Verify at both change-slice and feature level with evidence appropriate to risk.
7. Reconcile affected specifications when implementation discoveries change accepted decisions.
8. Report delivered behavior, verification evidence, deviations, and residual risks.

For substantial features, the conventional artifact set under `docs/spec/<feature-slug>/` is useful:

- `requirement.md`: intent, scope, observable acceptance criteria, applicable quality goals;
- `design.md`: significant technical decisions, contracts, risks, and verification approach;
- `tasks.md`: outcome-oriented implementation slices, dependencies, and completion evidence;
- `review-log.md`: optional audit trail for independent or repeated review;
- `summary.md`: final feature-level evidence and handoff.

For a narrow or low-risk change, combine or omit artifacts that would only restate information already clear in the request, code, or tests. Create any artifact the user or repository explicitly requires.

## Change Semantics And Quality Gates

Treat accepted current behavior and a proposed change as different things when that distinction improves reviewability. For existing systems, the desired state is the applicable current specification plus the accepted delta. Use `references/change-model.md` when evolving durable behavior, reconciling parallel changes, or archiving completed work.

Use analysis and convergence as explicit capabilities, not mandatory phases:

- Use `references/analyze.md` when cross-artifact ambiguity, contradiction, coverage gaps, or change conflicts could materially affect implementation. Analysis is read-only and routes each finding to its owning source.
- Use `references/converge.md` when implementation must be assessed against the integrated desired state and available evidence. Convergence reports a defensible verdict or concrete gaps without silently changing code or accepted intent.
- Use `references/bugfix.md` for defects where current, expected, and unchanged behavior must be distinguished. Do not force a feature-spec structure onto a simple, well-understood repair.

When machine-readable change metadata is present, use `references/machine-state.md` and `scripts/specctl.py` for deterministic structure, status, evidence, and conflict checks. Treat those checks as supporting evidence: structural success does not replace semantic judgment or executable verification.

When changing this skill's own contracts or release behavior, use `references/behavior-evaluation.md`. A real run preserves the exact output trace and candidate identity; schema checks alone are not a behavioral evaluation.

## Artifact Quality

Prefer content contracts over fixed templates:

- Requirements SHOULD express value, scope, observable behavior, boundaries, priorities, and measurable quality targets where relevant. Use EARS, Given/When/Then, examples, decision tables, state models, or concise prose according to the domain.
- Design SHOULD capture only decisions necessary to implement and verify the feature safely. Include interfaces, data, transactions, failure behavior, security, observability, rollout, or testing detail when they materially affect the change. Avoid freezing code-level details that the implementer can decide locally.
- Tasks SHOULD describe independently verifiable outcomes, dependencies, and evidence. Choose vertical slices, layer-oriented work, risk-first spikes, or another decomposition that fits the change.
- Traceability MUST be sufficient to show that every accepted outcome has design/implementation coverage and verification evidence. It need not use a particular table or identifier format.

Detect drift semantically. Timestamps and hashes MAY signal that content changed, but formatting-only or irrelevant changes do not require wholesale regeneration. Update only affected downstream decisions and evidence.

## Quality And Verification

Select applicable quality attributes based on stakeholder needs and risk, such as functional suitability, performance and capacity, reliability and recovery, security and privacy, compatibility, usability and accessibility, maintainability, observability, and operational readiness. Do not create empty sections for inapplicable attributes.

Choose verification methods that can substantiate the selected outcomes: focused tests, integration tests, contract tests, end-to-end scenarios, static analysis, migration checks, security checks, manual inspection, or other evidence. A required scenario is not verified merely because its harness is inconvenient; either obtain equivalent evidence or report the limitation as blocking or residual risk according to impact.

Use independent evaluation when risk, breadth, uncertainty, or user instruction justifies it. High-risk examples include authorization changes, public APIs, data migrations, concurrency, cross-component behavior, and security-sensitive code. Routine low-risk changes MAY rely on agent self-review plus final feature-level verification.

## Definition Of Done

Declare the feature complete only when:

- applicable acceptance criteria are satisfied with identifiable evidence;
- selected quality goals are met or explicitly accepted as residual risk;
- appropriate build, test, analysis, and regression checks pass;
- compatibility, migration, rollout, and rollback concerns are addressed when relevant;
- specifications and implementation agree on material behavior and decisions;
- unresolved blockers are absent;
- the final handoff lists changed behavior, evidence, skipped checks, deviations, and follow-ups.

Only the user or an authorized repository policy may accept a material unmet quality goal. The agent MAY disclose a non-blocking limitation, but MUST NOT unilaterally accept material security, privacy, compatibility, data-integrity, or operational risk as complete.

## Optional Capability Presets

Use the role references only when specialization or independent context improves the result:

- planning: `references/agents/planner.md`
- design: `references/agents/designer.md`
- task decomposition: `references/agents/tasker.md`
- implementation: `references/agents/generator.md`
- independent evaluation: `references/agents/evaluator.md`

Each specialized role also follows `references/agents/common.md`. Use `references/agents/clarification.md` only when a decision reaches the escalation threshold above. For strict audit or long-running multi-party work, see `references/prompts/manifest.md`.

Within this skill package, the outcome and authority contracts in this file govern. Role references specialize those contracts and adapters only route into them; they do not introduce a mandatory workflow or weaken an accepted outcome.

For resuming existing work, semantic drift reconciliation, review coordination, and final handoff guidance, see `references/prompts/orchestrator.md`.

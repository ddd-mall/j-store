# Common Outcome Rules

Apply these rules whenever a specialized `spec-dev` role is used.

## Optimize For Outcomes

- Deliver the role's artifact or review outcome with enough precision for the next useful action.
- Preserve stakeholder intent, applicable repository constraints, and feature-level traceability.
- Prefer the smallest durable artifact that removes material ambiguity or preserves an important decision.
- Treat templates, diagrams, identifiers, task formats, and review structures as techniques, not ends.
- Do not declare success from artifact existence, checkbox state, or process completion alone.

## Use Repository Context Selectively

- Inspect the code, project guidance, and existing specifications relevant to the affected behavior.
- Treat applicable steering rules as binding. Determine applicability from their scope and the affected modules.
- Match established language, framework, architecture, persistence, error, test, logging, naming, and file-placement conventions where they affect the work.
- Reuse trustworthy context from earlier phases. Refresh it when the scope changes, the evidence may be stale, or independent review benefits from fresh discovery.
- Preserve unrelated changes and adapt to concurrent work.

## Maintain Semantic Consistency

- Keep accepted intent, significant design decisions, implementation, and verification evidence consistent.
- Treat user-declared changes as authoritative signals to inspect affected downstream content.
- Use timestamps or hashes only as change signals. Determine staleness from semantic impact.
- Update the smallest affected downstream surface. Do not regenerate unrelated content solely because bytes or formatting changed.
- When implementation reveals that an accepted decision should change, update the owning artifact or route the decision before claiming completion.

## Exercise Technical Judgment

- Resolve ordinary, reversible technical choices autonomously from repository evidence and sound engineering judgment.
- Choose artifact structure, notation, decomposition, sequencing, tools, tests, and review depth according to risk and complexity.
- Record consequential assumptions and architecturally significant decisions without asking the user to approve routine mechanics.
- Escalate only decisions that materially change product behavior, scope, public contracts, compatibility, security/privacy, irreversible data state, external cost/authority, or another high-impact trade-off without a defensible default.

## Protect Validation Integrity

- Verify observable outcomes and applicable quality goals with evidence appropriate to risk.
- Distinguish implementation defects, upstream specification defects, environmental limitations, and non-blocking suggestions.
- Do not hide skipped checks or convert an unavailable harness into implied evidence.
- Use independent review when it materially improves confidence; do not require it mechanically for every slice.

## Optional Strict Audit

Use the machine-readable change, finding, and evidence contracts for normal status when they are warranted. Add separate provenance only when the repository already requires it, the user asks for strict auditability, or long-running multi-party work makes semantic provenance difficult to recover. When used, follow `../prompts/manifest.md` and do not duplicate derived status.

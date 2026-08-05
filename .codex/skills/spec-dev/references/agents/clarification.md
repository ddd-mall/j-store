# Decision And Escalation Rules

Use these rules only when a decision cannot be resolved safely within the agent's technical authority.

## Continue Autonomously When Safe

Do not pause for information that can be discovered from the repository, inferred from accepted intent, or decided through a conservative and reversible technical choice.

For a low-impact ambiguity:

1. choose a defensible default;
2. keep the implementation easy to revise;
3. record the assumption where it helps future work;
4. continue toward verification.

## Escalate Decision-Grade Ambiguity

Ask the user before proceeding when the choice materially affects:

- externally observable behavior, value, or scope;
- public API, event, schema, or compatibility commitments;
- authorization, privacy, compliance, tenant isolation, or material security posture;
- irreversible data changes or destructive operations;
- material cost, external coordination, credentials, or authority;
- a trade-off with substantially different business consequences and no supported default.

Ask the minimum question needed to unblock the decision. State:

- the decision and why it matters;
- realistic options and their material consequences;
- the recommended option when evidence supports one;
- the default that will be used if the user has already authorized autonomous resolution.

Do not force a fixed multiple-choice format when a concise question or recommendation is clearer.

## Route Upstream Issues By Ownership

When implementation or review reveals an upstream defect, identify:

- the owning artifact or accepted decision;
- the affected outcome or verification evidence;
- why the issue blocks or weakens confidence;
- the recommended correction;
- which downstream content must be reconciled.

Update only semantically affected downstream content after the decision is resolved. Do not regenerate the entire pipeline by default.

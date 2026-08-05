# Optional Semantic Provenance Manifest

Use a separate provenance record only when strict auditability or long-running multi-party coordination justifies the maintenance cost. When the current/change model is active, keep it beside the change artifacts and follow `../machine-state.md` for normal status and evidence state.

## Purpose

Record provenance so an agent can determine which accepted inputs informed an artifact or verification result. A digest proves byte-level identity, not semantic freshness.

## Suggested Data

Record only what the workflow needs, for example:

- manifest schema version;
- relevant steering files and digests;
- artifact digests and modification times;
- declared upstream dependencies;
- the semantic decision or evidence item for which provenance is recorded;
- concise reason that a changed input is or is not semantically relevant.

Preserve unrelated entries written by other tools.

## Semantic Drift Rules

- Use a digest mismatch as a prompt to inspect the change, not as automatic proof that every downstream artifact is stale.
- Ignore formatting-only or irrelevant changes after recording that they were reviewed.
- Reconcile the smallest affected downstream surface and update its provenance.
- Require re-verification when an accepted behavior, public contract, quality goal, significant design decision, or applicable repository constraint changes.
- If provenance cannot establish which inputs informed an artifact, mark it `review-needed` rather than regenerating blindly.

## Trade-Off

Provenance tracking improves recovery and auditability but adds noise. Do not duplicate derived change status, open findings, or evidence coverage already represented by `change.json`, `findings.json`, `evidence.json`, and `specctl`. Prefer semantic inspection and normal version control for ordinary single-agent or short-lived work.

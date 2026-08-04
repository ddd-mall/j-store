# Behavioral Evaluation

## Outcome

Demonstrate that the skill produces the intended decisions and safeguards on realistic prompts, not merely that its files are well formed.

Use behavioral evaluation when changing the skill's contracts, routing, autonomy boundaries, result semantics, or machine-readable state. Select cases that exercise the changed capability and its highest-consequence failure modes.

## Evidence Contract

A recorded run is credible only when it preserves:

- the exact case identity and prompt-content digest;
- an exact, durable trace of the agent's output;
- a content digest identifying the evaluated skill candidate;
- assertion-by-assertion results with concrete evidence;
- failures and uncertainty as faithfully as passes.

Do not treat a paraphrased answer, an ephemeral conversation locator, or self-asserted pass entries without a trace as a real run. Do not rewrite the trace after grading it.

The candidate digest is `sha256-tree-v1`: sort all files below the skill root by POSIX-style relative path, excluding `evals/runs/`, `evals/traces/`, `__pycache__/`, and `*.pyc`; hash each relative path, a null byte, its raw bytes, and a null byte in sequence. The case digest is SHA-256 over canonical JSON using UTF-8, sorted keys, and compact separators. The trace digest is SHA-256 over the trace artifact's exact bytes.

## Evaluation Set

Keep cases under `evals/cases/` and durable outputs under `evals/traces/`. Prefer prompts that expose consequential behavior such as ambiguity escalation, read-only Analyze, evidence-based Converge, bugfix root-cause integrity, current/delta conflicts, unavailable verification, and public-contract risk.

A skill change should pass the cases affected by that change. A release-level assessment should cover the representative suite and compare material regressions with a baseline when one exists. Case count alone is not a quality signal; coverage of intended outcomes and prohibited failures is.

## Validation

From the repository root, run:

```text
python .codex/skills/spec-dev/scripts/validate_evals.py --runs .codex/skills/spec-dev/evals/runs --json
```

Validation proves schema integrity, assertion coverage, durable trace integrity, and candidate/case identity. It does not prove that a semantic grader judged an assertion correctly. Inspect the trace and assertion evidence, or use an independent evaluator, before accepting the behavioral verdict.

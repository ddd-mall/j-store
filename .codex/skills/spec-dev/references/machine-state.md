# Machine-Readable State

## Outcome

Make structural validity, artifact state, evidence coverage, and open findings consumable by agents and CI without duplicating the human specification.

Use machine-readable state when a change is substantial, long-running, reviewed by multiple parties, or automated checks benefit from stable output. Do not impose it on a trivial change unless repository policy requires it.

## Sources And Derived State

- `change.json` is declared metadata: identity, kind, spec impact, artifact paths, and outcome summaries.
- `evidence.json` records verification evidence and its outcome coverage.
- `findings.json` records analysis or review findings and their resolution state.
- Command output is derived state. Do not commit a second hand-maintained status file that can drift from these sources.

Validate these files with the schemas under `schemas/`. Keep detailed intent, design reasoning, and test output in their natural artifacts; store references and concise summaries in JSON.

## Deterministic Checks

From the repository root, prefix each command with `python .codex/skills/spec-dev/scripts/specctl.py`; pass `--root` when validating another repository:

- `status [change] --json`: derive artifact, evidence, finding, and readiness state;
- `validate [change] --all --strict --json`: validate schemas, paths, references, and change conflicts;
- `analyze <change> --json`: produce deterministic pre-implementation findings and semantic review inputs;
- `converge <change> --json`: identify unresolved findings and evidence coverage gaps;
- `archive <change> --dry-run --json`: report archive preconditions without mutating files.

Commands are read-only. Exit code `0` means the requested deterministic contract passed, `1` means findings or gaps exist, and `2` means input or tool failure.

The deterministic scripts require Python 3.10 or newer and the `jsonschema` package. If the validator is unavailable, stop with exit code `2`; never silently skip schema enforcement.

For evaluation of the skill itself, use the separate evidence contract in `behavior-evaluation.md`. Evaluation run records must identify the exact case and skill candidate and preserve a durable output trace; assertion records without that provenance are not accepted as real runs.

## Semantic Boundary

Schema validity and identifier coverage are necessary evidence, not proof of correct behavior. `analyze` reports only deterministic findings plus the scope an agent must assess semantically. `converge` reports `candidate` when machine-checkable preconditions pass; an agent or authorized reviewer must still compare behavior and evidence before issuing the semantic `converged` verdict.

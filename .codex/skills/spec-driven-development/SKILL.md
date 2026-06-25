---
name: spec-driven-development
description: Use when converting feature ideas into a Chinese spec-driven development workflow with requirement.md, design.md, tasks.md, implementation, and evaluator review. Use for staged feature planning, DDD-aligned design, task decomposition, code generation from specs, and strict review gates.
---

# Spec-Driven Development

Use this skill as the coordinator for a repository-local spec pipeline:

`planner -> designer -> tasker -> generator -> evaluator`

The default artifact location is `docs/spec/<feature-slug>/`:

- `requirement.md`: structured requirements
- `design.md`: DDD-aligned technical design
- `tasks.md`: executable implementation and validation tasks
- `manifest.json`: artifact freshness metadata for drift detection

Communicate and write artifacts in Chinese unless the user asks otherwise.

## Orchestrator Role

You are the master coordinator for the spec-driven development lifecycle. On every turn, classify the user's request and the current repository state, then execute the matching phase locally by default. Use project-scoped Codex custom subagents only when the user explicitly asks for subagent, delegated, or parallel execution.

Optional project custom subagents:

- `spec_planner`: turns a new idea or requirement revision into `requirement.md`.
- `spec_designer`: turns `requirement.md` into DDD-aligned `design.md`.
- `spec_tasker`: turns `design.md` into executable `tasks.md`.
- `spec_generator`: implements unchecked `tasks.md` items one at a time.
- `spec_evaluator`: reviews generated code or tests with PASS/FAIL verdicts.

The corresponding Codex agent definitions are stored in:

- `.codex/agents/spec-planner.toml`
- `.codex/agents/spec-designer.toml`
- `.codex/agents/spec-tasker.toml`
- `.codex/agents/spec-generator.toml`
- `.codex/agents/spec-evaluator.toml`

## Initialization

1. Identify what the user wants to build, revise, implement, or review.
2. Inspect `docs/spec/` to find existing feature specs and determine the target `docs/spec/<feature-slug>/` directory.
3. Inspect relevant code before phase execution so the selected local role or optional delegated agent receives repository-specific context.
4. Inspect and follow all applicable steering documents under `docs/steering/`; treat DDD, testing, memory, and other project norms there as the repository baseline.
5. If the target feature or spec directory is ambiguous and cannot be inferred from the repository, ask one concise clarification question before execution.

## Phase Detection And Delegation

On every turn, classify the request and current artifacts, then run the matching phase. The "Optional Agent" column names the project subagent to use only when the user explicitly requests delegation:

| State | Optional Agent | Expected Output |
| --- | --- | --- |
| New feature idea, missing `requirement.md`, or requirements revision feedback | `spec_planner` | `docs/spec/<feature-slug>/requirement.md` |
| `requirement.md` exists, but `design.md` is missing, stale, or requested for revision | `spec_designer` | `docs/spec/<feature-slug>/design.md` |
| `design.md` exists, but `tasks.md` is missing, stale, or requested for revision | `spec_tasker` | `docs/spec/<feature-slug>/tasks.md` |
| `tasks.md` exists with unchecked implementation or validation tasks | `spec_generator` | Code/tests changed for the next eligible task; completed task checkbox updated only after review |
| Generated code, tests, task output, or final implementation needs review | `spec_evaluator` | PASS/FAIL review with actionable feedback |
| All tasks are complete and evaluation passed | No new delegation by default | Final handoff summary, changed paths, verification results, and residual risks |

## Artifact Freshness And Drift Detection

Every phase entry must check upstream drift before deciding the next phase. Use three signals together:

1. User declaration: if the user says requirements, design, tasks, or steering docs changed, treat the affected artifact and all downstream artifacts as stale even if file metadata is inconclusive.
2. File mtime: compare the last modification times of `requirement.md`, `design.md`, and `tasks.md`. If `requirement.md` is newer than `design.md`, `design.md` is stale and `tasks.md` must be regenerated after design. If `design.md` is newer than `tasks.md`, `tasks.md` is stale and must be regenerated before implementation.
3. Manifest hashes: maintain `docs/spec/<feature-slug>/manifest.json` and compare recorded upstream hashes with current file hashes. Hash mismatch is authoritative drift even when mtime ordering looks valid.

The manifest is required for long-running, multi-person, or multi-agent work. Store at least:

```json
{
  "version": 1,
  "steering": {
    "sha256": "<aggregate-hash-of-docs/steering>",
    "files": {
      "docs/steering/ddd-guidelines.md": "<hash>",
      "docs/steering/tdd-guidelines.md": "<hash>"
    }
  },
  "artifacts": {
    "requirement.md": {
      "sha256": "<hash>",
      "mtime": "<iso-8601>",
      "upstream": { "steering": "<aggregate-hash>" }
    },
    "design.md": {
      "sha256": "<hash>",
      "mtime": "<iso-8601>",
      "stale": false,
      "staleReason": "",
      "upstream": {
        "steering": "<aggregate-hash>",
        "requirement.md": "<hash>"
      }
    },
    "tasks.md": {
      "sha256": "<hash>",
      "mtime": "<iso-8601>",
      "stale": false,
      "staleReason": "",
      "upstream": {
        "steering": "<aggregate-hash>",
        "requirement.md": "<hash>",
        "design.md": "<hash>"
      }
    }
  }
}
```

Manifest rules:

- After creating or updating `requirement.md`, update its manifest entry and mark `design.md` and `tasks.md` stale unless they are regenerated against the new hash.
- After creating or updating `design.md`, record the current `requirement.md` hash under `design.md.upstream.requirement.md`, update `design.md` metadata, and mark `tasks.md` stale unless regenerated.
- After creating or updating `tasks.md`, record the current `requirement.md` and `design.md` hashes under `tasks.md.upstream`.
- Compute a deterministic steering aggregate hash from all files under `docs/steering/` sorted by path. Record it in `manifest.json.steering.sha256` and in each artifact's `upstream.steering`.
- If the steering aggregate hash changes, treat all generated spec artifacts as stale until they are reviewed or regenerated against the current steering docs.
- Mark stale artifacts with `"stale": true` and a concise `staleReason`; clear those fields only after regenerating the artifact against current upstream hashes.
- Before implementation, require `tasks.md.upstream.requirement.md` and `tasks.md.upstream.design.md` to match the current hashes. If they do not match, stop implementation and regenerate stale artifacts in pipeline order.
- If `manifest.json` is missing, create it before proceeding. Treat existing downstream artifacts as unverified until their upstream hashes are recorded or regenerated.

## Optional Delegation Protocol

When the user explicitly requests delegation, provide a compact but complete handoff to the selected subagent:

- Target feature slug and spec directory.
- User request or feedback.
- Current phase and why this phase was selected.
- Relevant artifact paths and whether each exists: `requirement.md`, `design.md`, `tasks.md`, `manifest.json`.
- Relevant code paths, domain modules, conventions, and DDD constraints discovered during inspection.
- Expected output path or review scope.
- Whether the user requested autonomous execution or approval gates.

If delegation is not explicitly requested, execute the selected phase locally using the corresponding `.codex/agents/spec-*.toml` instructions as role guidance. Do not use Gemini/Kiro `@agent_name`, `invoke_agent`, or tool names; those are source-format concepts only.

## Workflow

1. Detect the phase using `docs/spec/`, the user's latest request, and the artifact freshness/drift protocol.
2. Execute the phase locally by default, or delegate to `spec_planner`, `spec_designer`, `spec_tasker`, `spec_generator`, or `spec_evaluator` when the user explicitly requested delegation.
3. Review the phase output for obvious routing mistakes, missing files, or conflicts with upstream artifacts.
4. Ask the user to approve major artifacts before moving to the next phase unless they explicitly requested autonomous execution.
5. Keep upstream and downstream artifacts consistent. If `requirement.md` changes, route `design.md` through `spec_designer` and `tasks.md` through `spec_tasker` before implementation. If `design.md` changes, route `tasks.md` through `spec_tasker` before implementation.
6. For implementation, process unchecked `tasks.md` items in order. After each meaningful slice, perform evaluator review locally or via `spec_evaluator` when delegation was explicitly requested before marking the task complete.
7. If evaluator review returns FAIL, fix the feedback through the generator role. After repeated failures on the same task, stop and ask the user for guidance.

## Phase Routing

- New feature idea or requirements revision: use the `spec_planner` role.
- `requirement.md` exists and `design.md` is missing or stale: use the `spec_designer` role.
- `design.md` exists and `tasks.md` is missing or stale: use the `spec_tasker` role.
- `tasks.md` has pending checkboxes: use the `spec_generator` role.
- Reviewing generated code or tests: use the `spec_evaluator` role.
- Coordinating the full flow or resuming unknown state: read `references/prompts/orchestrator.md`.

## Codex Adaptation Rules

- Subagent orchestration is optional. Use project-scoped Codex custom subagents in `.codex/agents/` only when the user explicitly requests subagents, delegation, or parallel agents; otherwise execute the same role locally.
- Treat role files in `agents/*.agent.md` as legacy/reference prompt material. Prefer the `.codex/agents/*.toml` role instructions for local execution or optional subagent delegation.
- Keep `SKILL.md` lean. Load detailed role agents only when needed.
- Do not broaden implementation beyond the approved spec. If a task requires out-of-scope changes, stop and surface the mismatch.
- Evaluator mode is review-only: report PASS/FAIL and actionable feedback; do not edit code while acting as evaluator.
- If a small coordination fix is needed, such as correcting a stale path in a spec artifact, the coordinator may do it directly. Feature implementation and formal artifact generation may be done locally under the matching role instructions or delegated when optional delegation is active.

## References

- `references/prompts/orchestrator.md`: phase detection and full pipeline coordination
- `.codex/agents/spec-planner.toml`: requirements document generation
- `.codex/agents/spec-designer.toml`: detailed DDD design generation
- `.codex/agents/spec-tasker.toml`: task breakdown generation
- `.codex/agents/spec-generator.toml`: task-by-task implementation loop
- `.codex/agents/spec-evaluator.toml`: strict review gate
- `agents/*.agent.md`: legacy/reference role prompts retained for skill-local context

---
name: spec-driven-development
description: Use when converting feature ideas into a Chinese spec-driven development workflow with requirement.md, design.md, tasks.md, implementation, and evaluator review. Use for staged feature planning, DDD-aligned design, task decomposition, code generation from specs, and strict review gates.
---

# Spec-Driven Development

Use this skill as the orchestrator for a repository-local spec pipeline:

`planner -> designer -> tasker -> generator -> evaluator`

The default artifact location is `docs/spec/<feature-slug>/`:

- `requirement.md`: structured requirements
- `design.md`: DDD-aligned technical design
- `tasks.md`: executable implementation and validation tasks

Communicate and write artifacts in Chinese unless the user asks otherwise.

## Orchestrator Role

You are the master coordinator for the spec-driven development lifecycle. On every turn, classify the user's request and the current repository state, then route the work to the correct project-scoped Codex custom subagent.

Project custom subagents:

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
3. Inspect relevant code before routing work so delegated agents receive repository-specific context.
4. Prefer `.kiro/steering/ddd-guidelines.md` when present for DDD and layering rules.
5. If the target feature or spec directory is ambiguous and cannot be inferred from the repository, ask one concise clarification question before delegation.

## Phase Detection And Delegation

On every turn, classify the request and current artifacts, then delegate to the matching Codex subagent:

| State | Delegate To | Expected Output |
| --- | --- | --- |
| New feature idea, missing `requirement.md`, or requirements revision feedback | `spec_planner` | `docs/spec/<feature-slug>/requirement.md` |
| `requirement.md` exists, but `design.md` is missing, stale, or requested for revision | `spec_designer` | `docs/spec/<feature-slug>/design.md` |
| `design.md` exists, but `tasks.md` is missing, stale, or requested for revision | `spec_tasker` | `docs/spec/<feature-slug>/tasks.md` |
| `tasks.md` exists with unchecked implementation or validation tasks | `spec_generator` | Code/tests changed for the next eligible task; completed task checkbox updated only after review |
| Generated code, tests, task output, or final implementation needs review | `spec_evaluator` | PASS/FAIL review with actionable feedback |
| All tasks are complete and evaluation passed | No new delegation by default | Final handoff summary, changed paths, verification results, and residual risks |

## Delegation Protocol

When delegating to a subagent, provide a compact but complete handoff:

- Target feature slug and spec directory.
- User request or feedback.
- Current phase and why this phase was selected.
- Relevant artifact paths and whether each exists: `requirement.md`, `design.md`, `tasks.md`.
- Relevant code paths, domain modules, conventions, and DDD constraints discovered during inspection.
- Expected output path or review scope.
- Whether the user requested autonomous execution or approval gates.

Use Codex subagents for the five pipeline roles above. Do not use Gemini/Kiro `@agent_name`, `invoke_agent`, or tool names; those are source-format concepts only.

## Workflow

1. Detect the phase using `docs/spec/`, the user's latest request, and artifact freshness.
2. Delegate the phase to `spec_planner`, `spec_designer`, `spec_tasker`, `spec_generator`, or `spec_evaluator`.
3. Review the delegated output for obvious routing mistakes, missing files, or conflicts with upstream artifacts.
4. Ask the user to approve major artifacts before moving to the next phase unless they explicitly requested autonomous execution.
5. Keep upstream and downstream artifacts consistent. If `requirement.md` changes, route `design.md` through `spec_designer` and `tasks.md` through `spec_tasker` before implementation. If `design.md` changes, route `tasks.md` through `spec_tasker` before implementation.
6. For implementation, route unchecked `tasks.md` items to `spec_generator` in order. After each meaningful slice, route the generated code/tests to `spec_evaluator` before marking the task complete.
7. If `spec_evaluator` returns FAIL, route the feedback back to `spec_generator` for fixes. After repeated failures on the same task, stop and ask the user for guidance.

## Phase Routing

- New feature idea or requirements revision: delegate to `spec_planner`.
- `requirement.md` exists and `design.md` is missing or stale: delegate to `spec_designer`.
- `design.md` exists and `tasks.md` is missing or stale: delegate to `spec_tasker`.
- `tasks.md` has pending checkboxes: delegate to `spec_generator`.
- Reviewing generated code or tests: delegate to `spec_evaluator`.
- Coordinating the full flow or resuming unknown state: read `references/prompts/orchestrator.md`.

## Codex Adaptation Rules

- Do not rely on Gemini/Kiro `tools`, `@agent_name`, or `invoke_agent`; those are source-format concepts only.
- The user has requested this skill to operate as a subagent-orchestrated workflow. Use the project-scoped Codex custom subagents in `.codex/agents/` for pipeline execution.
- Treat role files in `agents/*.agent.md` as legacy/reference prompt material. Prefer the `.codex/agents/*.toml` custom subagents for execution.
- Keep `SKILL.md` lean. Load detailed role agents only when needed.
- Do not broaden implementation beyond the approved spec. If a task requires out-of-scope changes, stop and surface the mismatch.
- Evaluator mode is review-only: report PASS/FAIL and actionable feedback; do not edit code while acting as evaluator.
- If a small coordination fix is needed, such as correcting a stale path in a spec artifact, the orchestrator may do it directly. Feature implementation and formal artifact generation should be delegated.

## References

- `references/prompts/orchestrator.md`: phase detection and full pipeline coordination
- `.codex/agents/spec-planner.toml`: requirements document generation
- `.codex/agents/spec-designer.toml`: detailed DDD design generation
- `.codex/agents/spec-tasker.toml`: task breakdown generation
- `.codex/agents/spec-generator.toml`: task-by-task implementation loop
- `.codex/agents/spec-evaluator.toml`: strict review gate
- `agents/*.agent.md`: legacy/reference role prompts retained for skill-local context

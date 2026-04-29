---
name: spec-driven-development
description: Use when converting feature ideas into a Chinese spec-driven development workflow with requirement.md, design.md, tasks.md, implementation, and evaluator review. Use for staged feature planning, DDD-aligned design, task decomposition, code generation from specs, and strict review gates.
---

# Spec-Driven Development

Use this skill to run a repository-local spec pipeline:

`planner -> designer -> tasker -> generator -> evaluator`

The default artifact location is `docs/spec/<feature-slug>/`:

- `requirement.md`: structured requirements
- `design.md`: DDD-aligned technical design
- `tasks.md`: executable implementation and validation tasks

Communicate and write artifacts in Chinese unless the user asks otherwise.

## Workflow

1. Identify the current phase by inspecting `docs/spec/` and the user's request.
2. Use the relevant agent definition from `agents/` before doing that phase.
3. Keep upstream and downstream artifacts consistent. If `requirement.md` changes, regenerate or revise `design.md` and `tasks.md` before implementation.
4. Before drafting or coding, inspect the codebase for existing domain models, naming, modules, tests, and DDD conventions. In this repository, prefer `.kiro/steering/ddd-guidelines.md` when present.
5. Ask the user to approve major artifacts before moving to the next phase, unless they explicitly request autonomous execution.
6. For implementation, process unchecked `tasks.md` items in order. After each meaningful slice, use the evaluator agent as a review gate before marking tasks complete.

## Phase Routing

- New feature idea or requirements revision: use `agents/planner.agent.md`.
- `requirement.md` exists and `design.md` is missing or stale: use `agents/designer.agent.md`.
- `design.md` exists and `tasks.md` is missing or stale: use `agents/tasker.agent.md`.
- `tasks.md` has pending checkboxes: use `agents/generator.agent.md`.
- Reviewing generated code or tests: use `agents/evaluator.agent.md`.
- Coordinating the full flow or resuming unknown state: read `references/prompts/orchestrator.md`.

## Codex Adaptation Rules

- Do not rely on Gemini/Kiro `tools`, `@agent_name`, or `invoke_agent`; those are source-format concepts only.
- Treat each role agent as procedural guidance for Codex. Use Codex sub-agents only when the user explicitly asks for delegation or parallel agent work.
- Keep `SKILL.md` lean. Load detailed role agents only when needed.
- Do not broaden implementation beyond the approved spec. If a task requires out-of-scope changes, stop and surface the mismatch.
- Evaluator mode is review-only: report PASS/FAIL and actionable feedback; do not edit code while acting as evaluator.

## References

- `references/prompts/orchestrator.md`: phase detection and full pipeline coordination
- `agents/planner.agent.md`: requirements document generation
- `agents/designer.agent.md`: detailed DDD design generation
- `agents/tasker.agent.md`: task breakdown generation
- `agents/generator.agent.md`: task-by-task implementation loop
- `agents/evaluator.agent.md`: strict review gate

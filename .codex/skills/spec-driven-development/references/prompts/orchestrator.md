# Orchestrator Reference

The canonical orchestration rules live in `../../SKILL.md`. Use this reference only as a short checklist when resuming an unknown state or preparing a handoff.

## Resume Checklist

- Identify the target `docs/spec/<feature-slug>/` directory.
- Confirm which artifacts exist: `requirement.md`, `design.md`, `tasks.md`.
- Check whether upstream artifacts changed and downstream artifacts need regeneration.
- Inspect relevant code before delegation so the selected subagent receives repository-specific context.
- Keep communication and generated artifacts in Chinese unless the user asks otherwise.

## Handoff Checklist

When pausing or completing a phase, report:

- current spec directory
- artifact created or revised
- subagent used
- next phase
- blockers, failed evaluations, or upstream inconsistencies

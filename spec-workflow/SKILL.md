---
name: spec-workflow
description: Full-lifecycle spec-driven development workflow orchestrating specialist agents (planner, designer, tasker, generator, evaluator) for high-quality, document-driven feature implementation.
---

# Spec Workflow

## Overview

The `spec-workflow` is a structured, document-driven pipeline that orchestrates five specialist agents to turn feature ideas into production-ready code. It ensures high quality through formal requirements, detailed designs, and rigorous evaluation at every step.

## Pipeline Order

```
planner ──► designer ──► tasker ──► generator ◄──► evaluator
(requirement.md) (design.md) (tasks.md)  (code)     (per-task review)
```

## Workflow Stages

### Stage 0: Intake & Initialization
- Define a concise, kebab-case `<feature-slug>`.
- Create the spec directory: `docs/spec/<feature-slug>/`.
- All artifacts (requirement.md, design.md, tasks.md) are co-located here.

### Stage 1: Requirements (Planner)
- **Agent**: `planner`
- **Output**: `docs/spec/<feature-slug>/requirement.md`
- **Gate**: Requires explicit user approval before proceeding.

### Stage 2: Design (Designer)
- **Agent**: `designer`
- **Output**: `docs/spec/<feature-slug>/design.md`
- **Gate**: Requires explicit user approval.
- **Cascade**: If requirements change, route back to Stage 1.

### Stage 3: Task Breakdown (Tasker)
- **Agent**: `tasker`
- **Output**: `docs/spec/<feature-slug>/tasks.md`
- **Gate**: Requires explicit user approval.

### Stage 4: Implementation (Generator & Evaluator)
- **Agents**: `generator` and `evaluator`
- **Process**: Generator implements tasks from `tasks.md` one by one.
- **Verification**: Each task must pass `evaluator` review before being marked complete.
- **Retry Limit**: If a task fails evaluation >3 times, pause for user guidance.

## Core Rules

### Gate Enforcement
Each major stage (1, 2, 3) must be approved by the user. Do not advance silently.

### Cascade Rule
Any upstream change must flow forward. If `requirement.md` changes, `design.md` and `tasks.md` must be regenerated. Never "patch" a downstream document to fix an upstream issue.

### Traceability
Tasks must reference requirement serial numbers. Nothing is marked complete without evidence of evaluation PASS.

## Usage

Use this skill when the user says:
- "走 spec 流程"
- "Plan this feature using spec workflow"
- "Create a requirement and design for X"
- "Spec this"

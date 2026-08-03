The docs directory is a flat collection of Markdown-based artifacts grouped into logical subdirectories rather than code modules:
- `ai/`: prompt templates for designer, evaluator, generator, planner, spec-workflow, and tasker agents, plus meta-prompts that generate those prompts.
- `requirement/202604需求/`: monthly requirement notes for warehouse, goods, infrastructure, multi-merchant, factory, order, and accounting modules.
- `spec/`: per-feature specification directories each following a consistent triple-file pattern (`requirement.md`, `design.md`, `tasks.md`) with optional `review-log.md` and `summary.md`.
- `steering/`: cross-cutting guidelines (agent memory, DDD, TDD).
- `technic/`: technical deep-dives on DDD restructuring, Gradle audit, framework scaffolding, and domain event infrastructure.
- Root-level files: project overview, DDD quick reference and implementation guide, Order model walkthroughs, Spring Modulith guides, and phase summaries.
The structure is purely documentary — no build manifests or executable code — and serves as the single source of truth for design decisions, requirements, and implementation guidance across the multi-module Gradle project.
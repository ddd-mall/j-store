The docs directory is a flat collection of Markdown artifacts grouped by purpose rather than code modules:
- `ai/` contains reusable design and requirement templates plus Chinese-language prompt files for generating designer/evaluator/generator/planner/spec-workflow/tasker agents.
- `spec/<feature>/` holds one self-contained specification per feature with a fixed triad of `design.md`, `requirement.md`, `tasks.md` (plus optional `summary.md`, `review-log.md`, `delta.md`), following the templates in `ai/`.
- `steering/` stores long-lived governance and style guidelines (agent governance, DDD, TDD, agent memory).
- `operations/` holds runbooks for agent automation execution.
- `technic/` records technical deep-dives (DDD restructuring, Gradle audit, framework scaffolding, event infrastructure).
- Root-level Markdown files provide project overviews, quick references, phase summaries, and Spring-Modulith guides.
- `文档索引.md` acts as the master index linking all documents by role and task.
There is no build system or runtime; the structure itself is the artifact — each spec folder is an independent unit of work that can be reviewed and merged independently.
参照 C:\source\j-store\docs\ai\创建tasker agent 的提示词.md，docs\ai\创建planner agent 的提示词.md， docs\ai\创建designer agent 的提示词.md 中的内容，填充docs\ai\创建generator agent的提示词.md
任务目标：参照按理中的三个文件中的内容，写一个用于生成agent 的提示词模板。
gernerator angent的设计目标: 根据planer, designer, tasker生成的requirements.md, design.md, tasks.md 这三个文件中的内容生成代码。
generator的工作方式：从tasks.md中领取一个待办任务，在一个循环中生成对应的代码，循环流程如下：

1. 生成/修改此次任务对应的代码
2. 生成结束后后触发evaluator对本轮生成内容进行评估
3. 接收evaluator提供的反馈，如果存在问题则回到第一步继续生成/修改代码，直到evaluator评估通过为止。

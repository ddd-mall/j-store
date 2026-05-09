参照 C:\source\j-store\docs\ai\创建tasker agent 的提示词.md，docs\ai\创建planner agent 的提示词.md， docs\ai\创建designer agent 的提示词.md，C:\source\j-store\docs\ai\创建generator agent的提示词.md 中的内容，填充C:\source\j-store\docs\ai\创建evaluator agent的提示词.md

任务目标：参照上述四个三个文件中的内容，写一个用于生成agent 的提示词模板。
evaluator angent的设计目标: 根据planer, designer, tasker生成的requirements.md, design.md, tasks.md 这三个文件中的内容，评估 generator 生成的代码内容。

evaluator的工作方式：根据generator提供的上下文内容，包括相关的需求描述，设计方案，任务描述，以及generator生成的代码内容，评估代码的正确性和质量。评估流程如下：

1. 接收generator提供的上下文内容，包括相关的需求描述，设计方案，任务描述，以及generator生成的代码内容。
2. 根据设计文档中的正确性属性和需求文档中的验收标准，对generator生成的代码内容进行评估，判断其是否满足设计文档中的正确性属性和需求文档中的验收标准。
3. 如果评估结果显示代码内容存在问题，提供具体的反馈信息，包括不满足设计文档中的正确性属性和需求文档中的验收标准的具体方面，以及改进建议。
4. 如果评估结果显示代码内容满足设计文档中的正确性属性和需求文档中的验收标准，提供评估通过的反馈信息。

# Agentic CI/CD Level 2 GitHub 候选闭环需求

## 背景

Level 1只在本地形成不可变候选、隔离Gate和独立评审，不向GitHub写入。下一阶段要把已通过本地门禁的候选维护为唯一Draft PR，消费GitHub CI与review事实，在全部当前head门禁通过后转为Ready，并通过GitHub原生信号交给人工审核。

本变更实现总规格AC-06至AC-09的远端候选部分，不改变“自动化不得approve、merge、release或deploy”的永久边界。

## 范围

- 定义Level 2机器能力：Level 1本地能力保持开启，并按依赖关系和独立授权逐步开放远端branch、push、Draft PR、Issue Workpad/label、Ready和review request能力；最终目标profile覆盖全部这些能力。
- 由host-side Reconciler创建或复用唯一Draft PR；Coding Agent不持有GitHub token。
- 聚合绑定最新head SHA的required checks、额外checks和actionable review threads。
- 只有PR元数据、CI、review、独立Reviewer和PR模板全部满足时才允许Draft转Ready。
- Ready后幂等尝试Workpad、`agent:human-review`标签和配置的review request；至少一种成功才完成handoff。
- Supervisor重启后从GitHub事实和持久TaskSnapshot恢复，不重复PR或已成功的handoff副作用。

## 非目标

- 不自动approve、merge、release、deploy或写生产。
- 不授予Administration、Secrets、Environments、Deployments或Workflows write。
- 不让模型直接调用带写权限的GitHub工具。
- 不以LLM判断覆盖GitHub check conclusion、review thread状态或独立评审结果。
- 本变更的组件开发不自动授权真实GitHub App权限、push、PR、Ready或review request操作。

## 验收标准

### AC-GH-01 Level 2能力边界

Level 2必须开启全部Level 1本地能力，并允许按独立授权启用host-side GitHub能力。push依赖remote branch，Draft PR依赖branch和push，Ready依赖Draft PR，review request依赖Ready；Issue Workpad/label可独立灰度。最终目标profile覆盖全部上述能力。机器校验必须永久拒绝approve、merge、release和production write，Level 0/1继续拒绝全部GitHub写入。

### AC-GH-02 唯一Draft PR

Reconciler只能为任务记录的branch创建目标为`develop`的Draft PR。重复事件或重启必须复用同一开放PR；同一branch出现多个开放PR、base/head不匹配或exact-candidate独立评审未PASS时必须fail closed。

### AC-GH-03 当前head CI与review事实

所有required checks必须在当前head上为SUCCESS；其它适用check不得失败或等待；所有actionable review thread必须已解决。head变化必须使旧CI、review和Ready资格失效。

### AC-GH-04 Ready门禁

Ready转换还必须要求PR模板的Intent、Branch policy、Evidence、Independent review和Residual risk均已填写，不存在未勾选项、pending finding或blocked/fused/cancelled任务状态。重复Ready事件不得产生第二次副作用。

### AC-GH-05 GitHub人工交接

Ready后，Reconciler按已启用能力分别更新唯一Workpad、迁移互斥状态标签和请求配置reviewer。每个动作以`repository + PR + head + signal`幂等；至少一种成功即完成handoff，其余失败作为可重试运维finding保留。三种信号全部失败时handoff保持pending。

### AC-GH-06 恢复与审计

TaskSnapshot必须持久化PR编号、绑定真实远端资源与观察状态的GitHub审计回执、当前handoff head和失败增强项。GitHub未提供mutation event ID时不得用本地拼接字符串伪装事件ID；回执必须区分mutation与幂等恢复时的observation。进程重启后跳过已成功信号，只重试失败信号；不得保存token、Authorization header或未经脱敏的远端错误正文。

### AC-GH-07 真实验收

开启j-store写能力前，必须在disposable仓库完成重复事件、失败CI、review返工、base前移、Ready、handoff部分失败和Supervisor重启E2E，并独立验证GitHub App最小权限。随后在j-store只读观察期记录按任务类型拆分的`pass@1`、`success@budget`、unsafe-action、false-success、人工修改量、成本和恢复时间。

## 人工审批点

- GitHub App Contents、Pull requests和Issues write权限；
- 任何真实branch、push、PR、Issue comment/label、Ready或review request写入；
- 真实模型调用及其费用；
- 从disposable仓库扩大到j-store的灰度决定。

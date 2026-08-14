# Symphony 可信阶段桥变更需求

## 背景

当前 `develop` 已有 Coordinator、IterationPacket、独立评审决定和固定运行时预检，但这些组件尚未接入唯一 Symphony 生命周期。旧原型从 `after_run` 再启动 `codex app-server`，会形成第二套模型生命周期，不能作为 I3-08 的实现。

对固定 Symphony 源码的核对表明：同一 Supervisor 在一次正常 Agent invocation 结束且 Issue 仍可路由时会重新调度；将每次 invocation 限制为一个 turn，可让实现与评审获得不同的 Symphony App Server 会话。当前缺口是 Symphony 没有把完成 turn 的可信 `session_id`、`thread_id` 和 `turn_id` 交给仓库侧状态协调器。

## 目标

建立不启动模型的 host-side 阶段桥合同，使实现、确定性门禁、独立评审和返工状态能够跨 Symphony invocation 原子恢复，并确保最终 ReviewDecision 的运行时身份只能来自 Symphony 回执，不能来自模型自述。

## 范围

- 定义实现/评审/完成三个内部阶段及持久化字段。
- 允许首次实现包在会话尚未创建时不携带 implementer session。
- 将模型输出缩减为不含运行时身份的 ReviewProposal。
- 用可信 TurnReceipt 将 ReviewProposal 绑定为 ReviewDecision。
- 验证 exact-head、独立会话、失败 finding 回流、新 head 失效和重启恢复。
- 设计后续固定 Symphony 源码适配器与真实只读演练。

## 非目标

- 本变更不启动 Symphony 或模型 turn。
- 不创建 Issue、提交、推送、PR、邮件或 Kubernetes 资源。
- 不开放 workspace-write、GitHub 写权限、自动合并、发布或生产写入。
- 不把组件合同完成误记为 I3-08、I3-04 或 I3-07 的端到端完成。

## 验收标准

- `AC-PB-01`：首次实现 IterationPacket 的 implementer session 可为 `null`，评审 turn 必须已有可信 implementer session。
- `AC-PB-02`：ReviewProposal schema 不允许 `reviewer_session_id`、`implementer_session_id`、`thread_id` 或 `turn_id`。
- `AC-PB-03`：阶段桥只接受 exact-head、正确角色的 TurnReceipt，并拒绝实现者与评审者复用同一 session。
- `AC-PB-04`：PASS 只对精确 head 生效；FAIL findings 原样进入下一次实现包；新 head 回到实现阶段并使旧流程状态失效。
- `AC-PB-05`：阶段和最后可信回执可经原子 TaskSnapshot 保存/恢复。
- `AC-PB-06`：后续 Symphony 适配不得启动第二个 App Server；模型生命周期仍由唯一 Symphony Supervisor 管理。
- `AC-PB-07`：新 workspace 必须从本次 fetch 得到的精确 `origin/develop` SHA 检出，不得停留在 GitHub 默认 `master`；候选分支和 metadata 必须绑定该 SHA。
- `AC-PB-08`：host hook 只能执行镜像内只读、带 j-store revision 的可信控制器，不得执行 Implementer 可修改的 workspace 脚本。
- `AC-PB-09`：Reviewer 通过 host-side 受限工具提交无运行时身份的 ReviewProposal；工具只在 review 阶段接受 exact-head payload，after hook 再用同一 turn receipt 绑定 ReviewDecision。
- `AC-PB-10`：Symphony patch、可信控制器、WORKFLOW 或镜像内容变化必须改变 Pod template；部署 smoke 必须证明新 Pod UID、image ID、配置摘要和代码 revision 与候选一致。
- `AC-PB-11`：构建必须验证 Symphony 和 j-store 两个源码输入的精确 commit 与洁净状态，并将二者写入 OCI label；不得从任意 dirty workspace 生成特权控制器。
- `AC-PB-12`：真实 GitHub token 只能通过短期 Secret 引用注入；哨兵模式、依赖安全升级和受影响凭据轮换未完成前不得启动 Issue 或模型 turn。
- `AC-PB-13`：Implementer turn 完成后必须进入不启动模型的 `validate` 阶段；Supervisor/after hook 不得直接执行可由 Implementer 修改的 workspace 程序，也不得把 tracker token 传给候选验证进程。
- `AC-PB-14`：只有 host-owned、exact-head 的 GateReceipt PASS 才能从 `validate` 进入 `review`；FAIL 必须携带确定性 finding 回到 `implement`，旧 head 或重复 receipt 不得推进状态。
- `AC-PB-15`：Symphony 每次 invocation 最多一个 turn；`implement` 使用 workspace-write、`review` 使用 read-only，`validate` 与 `complete` 必须在创建 App Server session 前短路为无模型动作。
- `AC-PB-16`：阶段上下文只能由镜像内可信控制器从 host-owned snapshot 和实际 Git head 生成；WORKFLOW 模板不得信任 workspace 自报阶段或模型自报 gate/review 完成。

## 人工边界

应用 Symphony 补丁/派生构建、部署 Kubernetes、创建 disposable Issue、注入 GitHub 凭据和执行模型 turn，均是后续独立动作；其中外部写操作仍需精确授权。

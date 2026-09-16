# 交付记录

## 结果

公共发布者和路由 SPI 使用不可变 OutboxMessage；写入通过 OutboxWriter，装配通过 OutboxBackend。原 polling 模型/状态/仓储移入 spring.polling，writer 负责初始化和提交后唤醒。公共装配与条件 polling 装配分开，默认 mode=polling，缺失/重复/不匹配 backend 启动失败。

当前仍在 j-store-outbox-core / j-store-outbox-spring 两个 Gradle 模块中组织；本次完成模型、端口和装配边界，不新增 CDC 模块或运行时。新实现可复用公共发布者，并独立提供 writer、allocator、消费、运维和投递运行时。

## 验收映射

| 需求 | 证据 |
|---|---|
| OB-R1 | 业务 domain/application 与消息契约无本变更；Boot 回归 |
| OB-R2 | core 模型/路由编译与 test_outbox_backend_boundary.py |
| OB-R3 | OutboxEventPublisherTest、OutboxIntegrationMessagePublisherTest、PollingOutboxWriterTest |
| OB-R4 | OutboxBackendSelectionTest、OutboxAutoConfigurationTest |
| OB-R5 | PostgreSQL business/outbox/sequence 回滚、writer 无事务拒绝、提交后唤醒，以及原聚合确认测试 |
| OB-R6 | 原 PostgreSQL claim/fencing/顺序/幂等/死信/清理与本地完成事务回归；metadata 往返 |
| OB-R7 | 无 JPA mock、无 polling 仓储的 recording backend 接受领域事件和集成消息 |

## 验证

- RED：新增边界治理测试初次执行 2 项失败，定位 core 的 polling 类型泄漏及发布者对任务仓储/唤醒的依赖；改造后通过。
- 最终执行 `scripts/gradlew-windows.ps1 :j-store-common-core:test :j-store-messaging-core:test :j-store-messaging-local-spring:test :j-store-outbox-core:test :j-store-outbox-spring:test :j-store-boot:test spotlessCheck -PspotlessFilesFile=build/outbox-format-files.txt --console=plain`：BUILD SUCCESSFUL。六模块报告分别为 145、5、14、13、138、24 项，共 339 项、零失败；common-core 使用有效的 Gradle up-to-date 结果，其余相关模块已执行回归。
- Spotless 使用仓库提供的 `-PspotlessFilesFile=build/outbox-format-files.txt` 只格式化本次模块，避免修改并行工作区内容。
- 额外执行不带目标限制的 `spotlessCheck`：失败。违规文件为现有 CartApplicationService、CartBootConfiguration、Cart、OfferAuthorizationService 和 CheckoutController；均非本次代码改动。未自动格式化这些其它工作区文件，本次 Outbox 目标文件检查通过。
- 文件版权检查通过：1661 个当前工作区文件，含 4 个第三方文件。
- 全仓门禁首次因尚未暂存的文件移动仍被 Git 索引列举而停止。使用 `JSTORE_REPOSITORY_FILES_FILE` 指定完整现存工作区清单后，规格测试 28 项、治理测试 68 项通过；工具链测试执行 341 项，5 failures / 23 errors，门禁退出 1。
- 工具链失败位于未修改的 candidate snapshot、gate fetch、credentials、不可变交付等测试：Windows 缺少 `termios`，无法创建带换行的文件名，POSIX executable mode 断言不适用，Bash/WSL 执行失败，以及只读归档/缓存路径权限问题。没有放宽检查或修改这些测试。
- 门禁因此没有执行其后续阶段；全仓格式已单独执行并报告失败，依赖解析/许可证审计、全模块 test 和发布制品许可证检查未运行。本变更的相关模块测试、目标文件 Spotless 和版权检查另行完成，不能据此声称全仓门禁通过。

Git Bash 中该清单变量使用 `/c/source/j-store/build/outbox-repository-files.txt`；传入 Windows Python 时会转换为 Windows 绝对路径。未修改用户 Git 暂存区。

## 边界

没有修改表结构、依赖版本、业务消息名称/版本、业务用例；没有部署、提交或切换运行环境。仓库已有 Market/Cart/Pricing 和素材改动保留。CDC 实现仍需单独验证事务原子性、消费顺序/幂等、保留与恢复、运维控制面及交接协议；测试 recording backend 仅证明可替换边界，不构成 CDC 实现。

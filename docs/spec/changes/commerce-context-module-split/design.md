# 设计：核心上下文统一四层架构

每个上下文采用：

```text
{context}-boot -> {context}-application -> {context}-domain -> common-core
               -> {context}-infrastructure -> {context}-domain
```

application 定义用例端口和纯编排实现；boot 使用 Spring `TransactionTemplate` 装饰端口。写装饰器覆盖 repository 与 `DomainEventPublisher`，查询使用只读事务。基础设施仓储只持久化，并以 `MANDATORY` 防止写操作绕过用例事务。

事件发布统一复用 common-core 中的非破坏性 `publishPendingEvents` 扩展：复制队列快照、全部发布成功后删除对应事件；异常时保留队列并由外层数据库事务回滚。

用户上下文中的 Redis token 操作不加入数据库原子承诺。数据库状态与 Outbox 先提交；登录后的 refresh token 写入、账号禁用/强制下线后的 token 撤销作为提交成功后的副作用执行。token 刷新由 Redis 状态主导，不包装成数据库写事务。未来需要强一致恢复时改为消费 Outbox 的幂等 token-revocation handler。

根 boot 保留跨上下文 translator、整站启动、迁移和运维入口；各上下文 Controller/config 下沉到对应 boot 模块。

## Spring Modulith 决策

本次不引入 Spring Modulith。当前最重要的边界是可独立构建的 Gradle 物理模块，以及既有 `local`/`broker`/`hybrid` Outbox 协议；直接引入 Modulith 的 package-based application-module 模型和 Event Publication Registry 会与现有模块门禁、Outbox 表及投递状态形成两套权威来源。

Spring Modulith 仍适合作为后续可选的测试/文档工具：当各上下文对外 package API 收敛后，可在根 boot 试点 `ApplicationModules.verify()` 检查循环依赖和内部包访问。试点不得替换现有 Outbox，除非另立迁移规格，明确事件表、重试、归档、broker externalization 以及回滚方案。

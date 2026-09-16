# Outbox 实现中立边界

用户要求：先改造现有 Outbox，统一语义，使后续独立 CDC 实现可由装配层选择，业务无感。

## 验收

- OB-R1：业务继续只使用 common-core 的 DomainEventPublisher 和 messaging-core 的 IntegrationMessagePublisher；业务用例和消息 payload 不变。
- OB-R2：outbox-core 只拥有不可变消息、追加端口、顺序分配和目标规划/投递契约，不拥有 polling 状态、租约或任务仓储。
- OB-R3：公共发布者只依赖追加端口；polling 适配器拥有 PENDING 初始化、批量持久化及提交后唤醒。
- OB-R4：装配默认选 polling。显式选择其它实现时不得启动 polling 的 JPA、调度、清理、监控或本地消费恢复逻辑。未提供匹配实现、重复实现必须启动失败，不得静默回退。
- OB-R5：业务写入、序号和消息在同一数据库事务中提交/回滚；批量失败、提交失败不得确认聚合事件。
- OB-R6：保留稳定消息 ID、冻结目标、每 transport 独立投递、同流 sequence、至少一次、前驱阻塞、幂等、死信审计和现有本地完成事务。
- OB-R7：用无 polling 仓储依赖的测试实现证明发布端口可替换；数据库集成测试继续覆盖 polling 可靠性。

不实施 CDC、Broker 客户端、表结构迁移、多目标合并、运行时热切换或双 owner。

本变更修订 OTM-R3 的模块职责：polling 运行状态移动到 Spring 实现侧。其它投递规格继续适用。

# 设计

OutboxMessage 是不可变投递意图；保留当前 eventId/eventType 命名与完整元数据，避免顺带修改消息协议。OutboxWriter 只接受消息批次，正常返回表示加入调用方事务，不表示已经投递。序号仍由 OutboxStreamSequenceAllocator 在同事务分配。

现有 OutboxEntry、OutboxEntryStatus、OutboxEntryRepository 移入 spring.polling。Entry 是 polling 持久化任务；通过显式映射转换为公共 OutboxMessage，公共路由器和 channel 不接收任务状态。数据库物理结构保持原样。

公共 Spring 装配负责序列化、注册、目标规划与发布者。Polling 配置负责 JPA、writer、allocator、本地消费/保留、relay、调度、死信和监控。OutboxBackend 是装配描述，包含实现 ID、writer 和 allocator；启动要求唯一实现且 ID 与 jstore.outbox.mode 匹配。默认 polling；未来 CDC 配置提供自身 backend 并显式选择，不能仅凭 classpath 或 @Primary 隐藏多实现。

完成语义分三层：append 返回仅是事务内接受；源事务 commit 是持久接受；本地 handler 事务完成或外部 transport 确认才是投递完成。远端业务处理完成另由消费者负责。CDC 未来必须用消费者事务/幂等和顺序控制证明等价保证，不要求复制 polling 的 claim/lease。

local-domain 是目标而非 relay 技术名称；仍只在所属服务调用领域 listener，不能因使用 CDC 自动公开为跨上下文事件。当前本地消费的水位恢复查询依赖 polling 表，因此整体归 polling 装配所有，后续实现必须提供自己的消费恢复策略。

保留期之外的任意重放去重不在保证内。未来 CDC 清理必须明确快照/恢复窗口及持久捕获边界；WAL 读取进度与表行保留是不同机制，本次不设计 CDC 清理协议。

验证：边界治理检查、不可变消息校验、替换实现装配、错误/重复选择、writer 元数据往返及失败不唤醒、既有 PostgreSQL 事务/顺序/fencing/重入队测试、boot 与质量门禁。

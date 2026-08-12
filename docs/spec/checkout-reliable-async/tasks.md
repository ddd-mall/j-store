# 结账可靠异步基础任务

- [x] CRA-T1：以失败测试定义命令 `acceptBefore`、版本感知稳定 ID 和按 destination 的发布规划。
- [x] CRA-T2：实现路由模型、Spring 配置绑定和启动时 transport 完整性校验。
- [x] CRA-T3：扩展 Outbox/Envelope 的逻辑 destination、delivery profile、acceptBefore 和 publishedAt。
- [x] CRA-T4：新增 Flyway migration，并验证字段、约束与索引；旧开发 schema 直接重建。
- [x] CRA-T5：迁移 Commerce 消息稳定 ID，给 Checkout 关键命令补充 Deadline。
- [x] CRA-T6：让库存预留成功事实携带 Reservation 过期时间并完成序列化/Translator 测试。
- [x] CRA-T7：相关模块、迁移与格式检查已通过；仓库全量 `test` 运行至 10 分钟工具上限未返回失败，未据此宣称完整通过。
- [x] CRA-T8：收敛规格与交付摘要，记录未实现的 Broker/Process Manager 后续边界。
- [x] CRA-T9：修复审查发现的分隔符 ID 碰撞、库存锁等待过期竞态和 Outbox 时间元数据非法状态，并增加领域、持久化及迁移回归测试。

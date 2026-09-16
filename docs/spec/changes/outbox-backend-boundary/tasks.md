# 实施与证据

- [x] 将不可变消息、追加端口与 backend 契约放入 core，移动 polling 状态/仓储到实现侧。
- [x] 发布者只使用 writer，polling writer 负责初始化任务、批量保存与提交后唤醒。
- [x] 条件拆分公共发布与 polling 运行时，装配验证唯一 backend 和 mode 匹配。
- [x] 增加替换实现、无实现、重复实现、错误实现及禁用场景测试；迁移原有模型与路由测试。
- [x] 治理边界测试先失败（core 泄漏状态、publisher 依赖 polling），改造后通过。
- [x] 执行 PostgreSQL、Boot 和全仓门禁；相关回归通过，门禁的 Windows 工具链失败及未执行阶段见 summary.md，不表示全仓门禁通过。

未改变消息版本、业务模块、数据库结构或依赖版本。工作区原有 Market/Cart/Pricing 等修改不属于本变更。

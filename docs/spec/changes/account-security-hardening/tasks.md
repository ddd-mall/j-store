# 实施任务

- [x] 增加 Controller 越权、匿名接口和 `/me` 契约测试。
- [x] 增加 PostgreSQL 用户仓储 ID 往返测试并修复 ID 生成策略。
- [x] 增加多会话、摘要存储、并发 rotation、全用户撤销测试。
- [x] 增加手机号 challenge 一次性消费、过期、手机号绑定和发送限流测试。
- [x] 增加登录统一错误与失败限流测试。
- [x] 增加 JWT issuer/audience/kid/双密钥与 session claims 测试。
- [x] 删除重复认证 Filter，更新 SDK 为服务端会话校验。
- [x] 新增数据库迁移和迁移测试。
- [x] 运行用户、认证 SDK、根 boot、全仓测试与质量门禁（全仓测试通过；门禁命中与本变更无关的既有缺失 `.qoder` 文档错误，详见 summary）。
- [x] 完成 summary，记录验收证据、外部适配边界和残余风险。

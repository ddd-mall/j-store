# 实施任务

- [x] 建立 Merchant、MerchantMembership、角色、权限和仓储端口，并以领域测试验证权限不变量。
- [x] 实现 MerchantService 和授权服务，并验证创建者 OWNER、跨商户隔离及成员管理规则。
- [x] 在 shop infrastructure 中实现 JPA 映射、转换和仓储。
- [x] 新增严格商户表迁移，不做历史推导，并用 PostgreSQL 验证商户外键与唯一约束。
- [x] 装配商户服务并提供商户/成员管理 API。
- [x] 将支付、履约和商家售后操作迁移到商户成员权限校验。
- [x] 修正受影响控制器使用 SDK 支持的 `UserId` 参数类型。
- [x] 执行 shop、shop-infrastructure、boot 及完整回归测试，记录结果与残余风险。
- [x] 删除售后旧 actor 查询入口和商户 owner 重复字段，完成无兼容层收敛。

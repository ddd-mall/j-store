# 改造总结

## 交付状态

已完成“一套用户身份 + 商户组织成员关系 + 商户内角色权限”的账号体系改造。`user_accounts` 继续表示自然人身份，不增加 C 端/B 端用户类型；一个用户可加入多个商户，一个商户可拥有多个成员。

## 已交付能力

- 新增 `Merchant`、`MerchantMembership`、`MerchantRole` 和 `MerchantPermission` 领域模型。
- 创建商户时原子创建 OWNER 成员关系；OWNER 不可被普通成员管理操作降级或停用。
- 提供商户创建、我的商户、添加成员、修改角色和停用成员 API。
- 支付、履约和商家售后接口按实际资源所属商户校验成员权限，不再使用 `userId == merchantId` 作为授权条件。
- 商家售后领域命令使用资源的真实 `merchantId`，不把操作员工的 `userId` 冒充商户主体。
- 新增严格 PostgreSQL 模型，商户、成员和角色只由显式业务用例创建，业务资源通过外键引用真实商户。
- 删除历史商户推导、`user_id = merchant_id` OWNER 回填和旧售后 actor 查询入口。
- 删除 `merchants.owner_user_id`，以 OWNER 成员角色作为商户所有权的唯一事实源。

## 验证证据

- `./gradlew.bat :j-store-shop:test :j-store-shop-infrastructure:test :j-store-order:test :j-store-boot:test --no-daemon`：通过。
- `./gradlew.bat test --no-daemon`：通过，`BUILD SUCCESSFUL`，84 个任务完成或复用缓存。
- PostgreSQL 迁移测试覆盖空库无隐式回填、真实商户外键和成员唯一约束。
- 控制器契约测试覆盖商户员工成功操作、ID 数值相同但无成员关系时拒绝访问，以及售后命令使用真实商户身份。
- 静态扫描未发现 `@CurrentUserId Long`、`merchantId == userId` 或用当前用户构造商户操作主体的残留实现。
- 本次账号改造涉及的已跟踪文件执行 `git diff --check`：通过；全仓检查被并发格式化产生的无关 Outbox 行尾改动污染，未擅自处理。

## 使用边界与残余风险

- 平台管理员身份域、JWT portal/audience 隔离和管理员 MFA 不在本次范围内，应作为独立安全变更实施。
- 本次采用破坏性迁移，不支持已有开发数据升级或旧应用回滚；本地环境必须从空库重建。
- 数据库唯一约束负责处理并发添加同一成员的最终一致性；若后续需要稳定的冲突错误码，可在基础设施异常映射层补充转换。

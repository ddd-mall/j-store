# 统一账号与商户成员关系设计

## 核心决策

采用“一套身份、多个业务身份”的模型：

```text
UserAccount (全局登录身份)
    │ userId
    ├── 买家行为
    └── MerchantMembership ── Merchant
            │ roles              │ merchantId
            └── permissions      └── status
```

`MerchantId` 与 `UserId` 是不同概念，不共享编号语义，也不得依赖数值相等授权。

## 模块边界

- `j-store-shop`：Merchant、MerchantMembership、角色/权限、仓储端口和应用服务。
- `j-store-shop-infrastructure`：JPA PO、Spring Data Repository 和领域仓储实现。
- `j-store-boot`：Bean 装配、商户管理 API、跨上下文接口鉴权和 Flyway 迁移。
- `j-store-user`：继续负责全局登录身份，不依赖 shop 上下文。

## 权限模型

成员可以有多个角色；有效权限是全部角色权限的并集。

| 角色 | 关键权限 |
|---|---|
| OWNER / ADMIN | 全部商户权限 |
| PRODUCT_MANAGER | 商品读写 |
| ORDER_MANAGER | 订单读取、售后处理、履约读写 |
| CUSTOMER_SERVICE | 订单读取、售后读取与处理 |
| FINANCE | 订单读取、支付读写、财务读取 |
| VIEWER | 商户、商品、订单、售后、支付、履约只读 |

授权流程必须先确认商户 ACTIVE，再确认成员 ACTIVE，最后校验权限。

## 数据模型

- `merchants(id, name, status, create_time, update_time)`
- `merchant_memberships(id, merchant_id, user_id, status, create_time, update_time)`
- `merchant_membership_roles(membership_id, role)`

跨限界上下文不为 `user_id` 建数据库外键，账号存在性由 shop 上下文的 `UserAccountLookup` ACL 在写入成员关系前验证。

## 接口

- `POST /api/merchants`：当前用户创建商户。
- `GET /api/merchants`：查询当前用户加入的商户。
- `POST /api/merchants/{merchantId}/members`：新增成员。
- `PUT /api/merchants/{merchantId}/members/{userId}/roles`：调整非 OWNER 成员角色。
- `DELETE /api/merchants/{merchantId}/members/{userId}`：禁用非 OWNER 成员。

OWNER 只存在于 `merchant_membership_roles`，`merchants` 不保存重复的 owner 字段。创建商户与初始 OWNER 成员关系必须在同一事务中完成。

## 破坏性采用

本项目尚未上线，只支持从空库建立新模型。迁移不回填旧商户、不合成 OWNER，也不支持回滚到按 ID 相等授权的旧应用。商品、订单、售后、支付和履约表通过外键引用 `merchants`；存在旧的孤立业务数据时迁移应失败，开发环境应重建数据库。

## 验证

- 领域测试覆盖角色权限、禁用状态和 OWNER 保护。
- 应用服务测试覆盖创建商户、跨商户隔离、成员管理授权和账号存在性。
- PostgreSQL 迁移测试覆盖空库结构、无隐式回填、商户外键和唯一约束。
- Boot/controller 测试覆盖 B 端接口使用成员权限而非 ID 相等。

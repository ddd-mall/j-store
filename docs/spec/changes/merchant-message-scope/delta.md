# 集成消息商户与部署作用域语义 Delta

## 背景

当前集成消息 metadata 使用 `tenantId`，部分 Commerce 消息直接以 `merchantId` 填充。
该名称把当前商户隔离事实误导为通用租户身份，并可能在未来把 Site、部署单元和 Merchant
三个独立维度压入同一个字段。

## 目标语义

- `merchantScopeId` 是可空的商户隔离 metadata；当前有值时只表示 `merchantId`。
- `deploymentScopeId` 是与商户独立的可空部署/站点路由扩展；当前消息可以不提供。
- 两个字段只服务于消息路由、隔离和观测，消费者不得仅依赖它们完成业务授权。
- 核心消息、Outbox 和传输模型不使用 `tenantId`，也不提前赋予部署范围真实 Site 领域语义。

## 变更范围

- 破坏性重命名消息接口、metadata、Outbox 模型、持久化列和 transport envelope。
- Commerce 消息原来由 `merchantId` 生成的 metadata 改为 `merchantScopeId`。
- 新增独立 `deploymentScopeId` 贯穿 metadata、Outbox 持久化和 transport envelope，当前默认
  为 `null`。
- 同步当前数据库基线、契约测试、持久化测试和长期架构文档。

## 不在范围

- 不新增真实 `siteId`、站点聚合或跨站点路由策略。
- 不把 metadata 当作认证或商户授权事实。
- 项目仍处于内部开发期，不保留 `tenantId` 兼容字段、双列或消息双版本。

## 验收

- 商户相关 Commerce 消息将 `merchantId` 映射为 `merchantScopeId`，部署范围保持独立可空。
- 两个可选范围字段分别拒绝空白值，并完整经过 Outbox 持久化和 transport envelope。
- 当前代码、数据库基线和规范中不再使用 `tenantId` / `tenant_id` 表示消息范围。
- Messaging、Outbox、Integration Contracts 及组合运行时相关测试通过。

## 回滚

若候选在合并前失败，整体回滚本次契约、持久化基线和文档变更；不得只恢复旧列名而保留新
消息接口。该变更尚未进入公开稳定版本，不提供运行时双读或历史数据迁移。

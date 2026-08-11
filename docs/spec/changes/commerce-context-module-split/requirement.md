# 需求：核心电商上下文四层拆分与事务一致性修复

## 目标

将商品/库存、支付、履约、用户、会计五个活跃上下文统一拆分为 domain、application、infrastructure、boot 四层 Gradle 模块，并修复业务写入、Outbox 事件和本地多仓储更新缺少用例级事务的问题。

## 验收标准

1. 删除 `j-store-goods`、`j-store-payment`、`j-store-fulfillment`、`j-store-user`、`j-store-accounting` 混合模块，分别建立五组四层模块；保留 `j-store-goods-api` 与认证 SDK 等独立公共契约。
2. 所有 domain/application 生产源码不得引用 Spring、Jakarta Persistence 或 Hibernate。
3. Controller 与集成消息 handler 只依赖上下文用例端口；boot 提供 Spring 事务装饰器和 Bean 装配。
4. 每个写用例在同一数据库事务中完成业务持久化与 Outbox 写入；事件全部写入成功后才清除队列。
5. 商品上架的 SPU、快照与 Outbox 原子提交；库存与预占记录原子更新，释放库存必须持久化库存变化。
6. 支付、履约、用户和会计领域事件不得因应用服务遗漏或仓储转换而丢失。
7. 用户数据库事务不宣称覆盖 Redis；token 撤销等 Redis 副作用必须在数据库事务成功后执行，或由可靠消息驱动。
8. 根 `j-store-boot` 继续作为模块化单体启动器，外部 HTTP、集成消息和数据库结构保持不变。

## 质量目标

- 数据完整性：禁止业务数据与 Outbox 部分提交。
- 可维护性：框架和部署技术不能进入 domain/application。
- 可迁移性：每个 boot 模块可被单体启动器或未来独立服务启动器复用。
- 可验证性：依赖门禁、事件队列测试和 PostgreSQL 事务测试提供确定性证据。

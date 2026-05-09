# 需求文档：goods-api-contract-rename

## 简介

当前仓库中存在 `j-store-goods-acl` 模块，包名为 `com.jstore.goods.acl`，用于承载商品上下文对外发布的查询契约，例如 `GoodsSnapshotQueryService`、`GoodsSnapshotInfo` 和 `GoodsSkuSnapshotInfo`。按照 `docs/steering/ddd-guidelines.md` 的约定，ACL 接口应位于消费方领域模块的 `acl` 包中，表达消费方本地防腐层端口；而当前 provider 侧也使用 `acl` 命名，容易与订单上下文的 `com.jstore.order.acl.GoodsService` 混淆。

本需求的目标是将商品 provider 侧发布契约从 ACL 语义中剥离出来，统一命名为商品对外 API 契约：将 `j-store-goods-acl` 重命名为 `j-store-goods-api`，并将 provider 侧发布契约包名改为 `com.jstore.goods.api`。订单上下文仍保留自己的消费方防腐层端口 `com.jstore.order.acl.GoodsService`；订单领域只依赖本地 ACL 端口，订单基础设施通过适配商品发布语言来调用 `j-store-goods-api`。

范围内行为包括：Gradle 模块命名迁移、商品 provider 侧契约包名迁移、相关 import 与依赖关系更新、订单消费方 ACL 语义保留、商品领域与订单上下文依赖边界保持清晰，以及项目编译和相关测试通过。范围外行为包括：不重命名 `com.jstore.order.acl.GoodsService`，不改写订单领域建模，不新增商品查询能力，不改变商品快照契约字段语义，不引入远程调用协议或 HTTP/RPC 接口实现。

## 术语表

- **Goods_Api_Module**：商品上下文对外发布查询契约的 Gradle 模块，目标模块名为 `j-store-goods-api`。
- **Goods_Acl_Module**：当前已存在的商品 provider 侧契约模块，模块名为 `j-store-goods-acl`，本需求完成后不应继续作为项目依赖入口。
- **Goods_Api_Package**：商品 provider 侧发布契约的 Kotlin 包，目标包名为 `com.jstore.goods.api`。
- **Goods_Acl_Package**：当前商品 provider 侧契约包，包名为 `com.jstore.goods.acl`，本需求完成后不应继续承载商品 provider 侧发布契约。
- **Goods_Snapshot_Query_Service**：商品上下文对外发布的商品快照查询契约，对应当前 `GoodsSnapshotQueryService`。
- **Goods_Snapshot_Info**：商品快照查询返回的 SPU 级快照数据，对应当前 `GoodsSnapshotInfo`。
- **Goods_Sku_Snapshot_Info**：商品快照查询返回的 SKU 级快照数据，对应当前 `GoodsSkuSnapshotInfo`。
- **Order_Goods_Service**：订单上下文本地消费方 ACL 端口，对应 `com.jstore.order.acl.GoodsService`。
- **Order_Domain_Module**：订单领域与应用模块，对应 `j-store-order`，只能依赖自身本地 ACL 端口表达对商品能力的需求。
- **Order_Infrastructure_Module**：订单基础设施模块，对应 `j-store-order-infrastructure`，负责实现订单消费方 ACL 并适配外部商品发布语言。
- **Goods_Application_Module**：商品领域与应用模块，对应 `j-store-goods`，可实现和发布 `Goods_Api_Module` 中定义的商品查询契约。
- **Goods_Domain_Layer**：商品上下文的领域模型、领域服务、仓储接口和值对象所在层，不应依赖订单上下文或外部消费方 ACL。
- **Boot_Module**：应用启动与装配模块，对应 `j-store-boot`，负责 Spring Bean 装配和上下文集成。
- **Project_Build**：全仓库 Gradle 编译与测试验证过程。
- **Provider_Contract_Consumer_Source_Files**：所有引用商品 provider 侧发布契约的生产源码文件，包括商品应用、订单基础设施和启动装配中的相关文件。
- **Provider_Contract_Consumer_Tests**：所有引用商品 provider 侧发布契约的测试文件，包括商品、订单基础设施和启动装配相关测试。
- **Provider_Side_Goods_Contract_Rename**：本需求定义的商品 provider 侧发布契约模块名与包名迁移动作。

## 需求

### 需求 1：商品发布契约模块重命名

**用户故事：** 作为 开发者，我希望 将商品 provider 侧契约模块从 `j-store-goods-acl` 重命名为 `j-store-goods-api`，以便 模块名准确表达商品上下文对外发布 API 契约而不是消费方防腐层。

#### 验收标准

1. THE Goods_Api_Module SHALL be registered in Gradle settings with module name `j-store-goods-api`.
2. THE Goods_Acl_Module SHALL NOT remain as a referenced project dependency in production or test Gradle dependency declarations.
3. WHEN Project_Build resolves module dependencies, THE Goods_Api_Module SHALL provide the former goods snapshot query contract dependency surface required by downstream modules.

### 需求 2：商品 provider 侧契约包名迁移

**用户故事：** 作为 开发者，我希望 将商品 provider 侧发布契约包名改为 `com.jstore.goods.api`，以便 代码语义与 DDD 中消费方 ACL 的语义区分清楚。

#### 验收标准

1. THE Goods_Api_Package SHALL contain Goods_Snapshot_Query_Service, Goods_Snapshot_Info, and Goods_Sku_Snapshot_Info.
2. THE Goods_Acl_Package SHALL NOT contain provider side goods snapshot query contract declarations after the migration.
3. FOR ALL Provider_Contract_Consumer_Source_Files, THE Goods_Api_Package SHALL be the imported package for provider side goods snapshot query contracts.

### 需求 3：订单消费方 ACL 语义保留

**用户故事：** 作为 订单开发者，我希望 保留 `com.jstore.order.acl.GoodsService` 作为订单领域本地端口，以便 订单领域继续使用自己的防腐层语言表达对商品能力的需求。

#### 验收标准

1. THE Order_Goods_Service SHALL remain in package `com.jstore.order.acl`.
2. THE Order_Goods_Service SHALL keep its role as the order context local ACL port for querying goods information.
3. WHEN Provider_Side_Goods_Contract_Rename is performed, THE Order_Goods_Service SHALL NOT be renamed to `com.jstore.goods.api` or moved into the goods context.

### 需求 4：订单侧依赖方向符合 DDD 分层

**用户故事：** 作为 架构维护者，我希望 订单领域不直接依赖商品发布 API，而由订单基础设施完成适配，以便 保持订单领域模型与外部上下文契约解耦。

#### 验收标准

1. THE Order_Domain_Module SHALL NOT depend on Goods_Api_Module.
2. THE Order_Domain_Module SHALL use Order_Goods_Service as its goods capability port.
3. THE Order_Infrastructure_Module SHALL depend on Goods_Api_Module to implement Order_Goods_Service.
4. WHEN Order_Infrastructure_Module adapts goods snapshot contracts, THE Order_Infrastructure_Module SHALL translate Goods_Snapshot_Info and Goods_Sku_Snapshot_Info into order-local ACL data types.

### 需求 5：商品侧发布契约与领域边界清晰

**用户故事：** 作为 商品开发者，我希望 商品应用层发布商品查询契约且商品领域不依赖订单上下文，以便 商品上下文保持独立并以明确契约对外提供能力。

#### 验收标准

1. THE Goods_Application_Module SHALL be allowed to depend on Goods_Api_Module and implement Goods_Snapshot_Query_Service.
2. THE Goods_Domain_Layer SHALL NOT depend on Order_Domain_Module or Order_Goods_Service.
3. THE Goods_Domain_Layer SHALL NOT import consumer side ACL packages from other bounded contexts.
4. WHEN Goods_Application_Module publishes snapshot query behavior, THE Goods_Application_Module SHALL preserve the existing semantics of Goods_Snapshot_Query_Service, Goods_Snapshot_Info, and Goods_Sku_Snapshot_Info.

### 需求 6：启动装配与全项目验证

**用户故事：** 作为 系统维护者，我希望 更新启动装配、测试与构建引用并验证通过，以便 模块重命名不会造成编译、Bean 装配或测试回归。

#### 验收标准

1. THE Boot_Module SHALL import provider side goods snapshot query contracts from Goods_Api_Package.
2. FOR ALL Provider_Contract_Consumer_Tests, THE Goods_Api_Package SHALL be the referenced package for provider side goods snapshot query contracts.
3. WHEN Project_Build runs compilation, THE Project_Build SHALL complete successfully.
4. WHEN Project_Build runs relevant goods and order tests, THE Project_Build SHALL complete successfully.

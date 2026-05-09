# 实现计划：订单商品快照

## 概述

本实现计划将订单模块与商品快照集成，使订单创建时能够从商品快照中读取完整的商品信息（名称、SKU 描述、销售属性、价格、快照版本号），并将这些信息冻结在订单行项中。实现按依赖顺序排列：先扩展领域模型和 ACL 接口，再实现基础设施层，最后完成持久化层和集成验证。

## Tasks

- [x] 1. 扩展 GoodsService ACL 接口和 GoodsInfo 数据类
  - [x] 1.1 修改 `GoodsInfo` 数据类，移除 `version` 字段，新增 `snapshotVersion: Long`、`spuName: String`、`skuName: String`、`attributes: List<Pair<String, String>>` 字段
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/acl/GoodsService.kt`
    - `attributes` 使用 `List<Pair<String, String>>` 而非商品上下文的 `Attribute<String, String>`，实现 ACL 类型隔离
    - _需求：1.1, 1.2, 1.3_

- [x] 2. 扩展 OrderItem 领域模型以支持快照版本号
  - [x] 2.1 在 `OrderItem` 接口中新增 `snapshotVersion: Long` 属性
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderItem.kt`
    - _需求：2.1, 2.5_
  - [x] 2.2 在 `OrderItemImpl` 构造函数中新增 `snapshotVersion: Long = 0` 参数（默认值 0 兼容历史数据）
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderItemImpl.kt`
    - _需求：2.1, 2.5_

- [x] 3. 修改 OrderFactory 以填充快照数据并生成 skuDescription
  - [x] 3.1 在 `OrderFactoryImpl` 中新增 `buildSkuDescription(skuName: String, attributes: List<Pair<String, String>>): String` 私有方法
    - 当 `attributes` 为空时返回 `skuName`；否则按 `"key:value"` 格式拼接，多个属性之间使用空格分隔
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt`
    - _需求：5.1, 5.2_
  - [x] 3.2 修改 `OrderFactoryImpl.create()` 方法中构建 `OrderItem` 的逻辑
    - 从 `GoodsInfo` 读取 `spuName` 填充 `goodsName`（替代硬编码空字符串）
    - 调用 `buildSkuDescription` 生成 `skuDescription`（替代硬编码空字符串）
    - 从 `GoodsInfo` 读取 `price` 填充 `unitPrice`
    - 从 `GoodsInfo` 读取 `snapshotVersion` 填充 `snapshotVersion`
    - 快照缺失时通过 `.msg()` 增强错误信息，包含具体的 SPU ID
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt`
    - _需求：2.2, 2.3, 2.4, 6.1, 6.2, 6.3_
  - [ ]* 3.3 编写属性测试：skuDescription 往返一致性
    - **Property 5: skuDescription 往返一致性**
    - 使用 Kotest Property `checkAll` 生成随机的 `List<Pair<String, String>>`（key/value 不含冒号和空格），验证 `buildSkuDescription` 生成的字符串可以按空格分割、按冒号拆分还原为等价的属性列表
    - 测试类：`SkuDescriptionRoundTripPropertyTest`，模块：`j-store-order`
    - **验证需求：5.1, 5.2, 5.3**
  - [ ]* 3.4 编写属性测试：OrderFactory 正确映射 GoodsInfo 到 OrderItem
    - **Property 4: OrderFactory 正确映射 GoodsInfo 到 OrderItem**
    - 使用 Kotest Property `checkAll` 生成随机的 `GoodsInfo`，Mock `GoodsService` 返回该数据，验证 `OrderFactory` 创建的 `OrderItem` 的 `goodsName == GoodsInfo.spuName`、`unitPrice == GoodsInfo.price`、`snapshotVersion == GoodsInfo.snapshotVersion`
    - 测试类：`OrderFactoryGoodsInfoMappingPropertyTest`，模块：`j-store-order`
    - **验证需求：2.2, 2.3, 2.4**
  - [ ]* 3.5 编写属性测试：快照缺失时 OrderFactory 快速失败
    - **Property 7: 快照缺失时 OrderFactory 快速失败**
    - 使用 Kotest Property `checkAll` 生成包含至少一个快照缺失商品的 `OrderCreateCMD`，Mock `GoodsService` 返回部分结果，验证 `OrderFactory.create` 返回 `Failure` 且错误信息包含缺失快照的 SPU ID
    - 测试类：`OrderFactoryMissingSnapshotFailurePropertyTest`，模块：`j-store-order`
    - **验证需求：6.1, 6.2, 6.3**

- [x] 4. 检查点 - 领域层验证
  - 确保所有 j-store-order 模块的测试通过，ask the user if questions arise.

- [x] 5. 实现 GoodsServiceImpl 基础设施层
  - [x] 5.1 在 `j-store-order-infrastructure/build.gradle.kts` 中添加对 `j-store-goods` 模块的依赖
    - 添加 `implementation(project(":j-store-goods"))` 以访问 `SpuSnapshotRepository`、`SpuSnapshot`、`SkuSnapshot`、`SpuId`、`Attribute` 等类型
    - _需求：3.1_
  - [x] 5.2 创建 `GoodsServiceImpl` 实现类
    - 文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/acl/GoodsServiceImpl.kt`
    - 注入 `SpuSnapshotRepository`，实现 `GoodsService.queryGoods()` 方法
    - 按 `spuId` 去重查询最新快照，将 `SpuSnapshot` + `SkuSnapshot` 转换为 `GoodsInfo`
    - 快照不存在或 SKU 不在快照中时，该 `GoodsId` 不出现在返回列表中
    - 将 `Attribute<String, String>` 转换为 `Pair<String, String>`，实现 ACL 类型隔离
    - _需求：3.1, 3.2, 3.3, 1.2, 1.3_
  - [ ]* 5.3 编写属性测试：快照到 GoodsInfo 的转换保持字段完整性
    - **Property 1: 快照到 GoodsInfo 的转换保持字段完整性**
    - 使用 Kotest Property `checkAll` 生成随机的 `SpuSnapshot`（含 `SkuSnapshot` 列表），Mock `SpuSnapshotRepository`，验证转换后 `GoodsInfo` 的 `spuName`、`skuName`、`attributes`、`price`、`snapshotVersion` 与原始快照一致
    - 测试类：`GoodsServiceSnapshotConversionPropertyTest`，模块：`j-store-order-infrastructure`
    - **验证需求：3.1, 3.2**
  - [ ]* 5.4 编写属性测试：GoodsServiceImpl 返回最新快照版本
    - **Property 2: GoodsServiceImpl 返回最新快照版本**
    - 使用 Kotest Property `checkAll` 生成同一 SPU 的多个不同版本快照，Mock `SpuSnapshotRepository.findLatestBySpuId` 返回最大版本，验证 `GoodsInfo.snapshotVersion` 等于最大版本号
    - 测试类：`GoodsServiceLatestVersionPropertyTest`，模块：`j-store-order-infrastructure`
    - **验证需求：1.2**
  - [ ]* 5.5 编写属性测试：缺失快照的商品被排除
    - **Property 3: 缺失快照的商品被排除**
    - 使用 Kotest Property `checkAll` 生成部分有快照、部分无快照的 `GoodsId` 列表，Mock `SpuSnapshotRepository`，验证返回的 `GoodsInfo` 列表不包含缺失快照的商品，且列表大小等于有快照的商品数量
    - 测试类：`GoodsServiceMissingSnapshotPropertyTest`，模块：`j-store-order-infrastructure`
    - **验证需求：1.3, 3.3**

- [x] 6. 扩展订单行项持久化层
  - [x] 6.1 在 `OrderItemPO` 中新增 `snapshotVersion` 字段
    - 添加 `@Column(name = "snapshot_version", nullable = false) var snapshotVersion: Long = 0`
    - 文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt`
    - _需求：4.1_
  - [x] 6.2 修改 `OrderRepositoryImpl.Converter` 的 `toItemPO` 和 `toDomainItem` 方法
    - `toItemPO`：将 `OrderItem.snapshotVersion` 映射到 `OrderItemPO.snapshotVersion`
    - `toDomainItem`：将 `OrderItemPO.snapshotVersion` 映射回 `OrderItemImpl.snapshotVersion`
    - 文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/OrderRepositoryImpl.kt`
    - _需求：4.2, 4.3_
  - [ ]* 6.3 编写属性测试：OrderItem PO 转换往返保持 snapshotVersion
    - **Property 6: OrderItem PO 转换往返保持 snapshotVersion**
    - 使用 Kotest Property `checkAll` 生成随机的 `OrderItem`（含任意 `snapshotVersion`），经过 `Converter.toItemPO` → `Converter.toDomainItem` 往返转换后，验证 `snapshotVersion` 与原始值相等
    - 测试类：`OrderItemPOConversionPropertyTest`，模块：`j-store-order-infrastructure`
    - **验证需求：4.2, 4.3**

- [x] 7. 创建数据库迁移脚本
  - [x] 7.1 创建 `08-order-item-snapshot-version.sql` 迁移脚本
    - 文件：`docker/postgres/init/08-order-item-snapshot-version.sql`
    - 为 `order_items` 表新增 `snapshot_version BIGINT NOT NULL DEFAULT 0` 列
    - 添加列注释说明用途
    - _需求：4.4_

- [x] 8. 更新现有测试以适配 GoodsInfo 新结构
  - [x] 8.1 修改 `OrderFactoryUnitTest` 和 `OrderFactoryShippingInfoPropertyTest` 中的 `GoodsService` stub
    - 将 `GoodsInfo(id = it, version = 1L, price = ...)` 更新为包含 `snapshotVersion`、`spuName`、`skuName`、`attributes` 的新结构
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderFactoryUnitTest.kt`
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderFactoryShippingInfoPropertyTest.kt`
    - _需求：1.1_

- [x] 9. 最终检查点 - 全模块验证
  - 确保 j-store-order 和 j-store-order-infrastructure 模块的所有测试通过，ask the user if questions arise.

## 说明

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 属性测试验证设计文档中定义的 7 个正确性属性
- 属性测试使用 Kotest Property 框架，每个属性至少运行 100 次迭代
- 单元测试和属性测试均使用 Mock 模拟外部依赖，不依赖数据库或 Spring 容器

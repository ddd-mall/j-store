# 实现计划：商品 Copy-on-Write 草稿编辑模型

## 概述

本实现计划为在售商品引入 Copy-on-Write 草稿编辑模型，并在订单创建时增加快照版本校验。实现按依赖顺序排列：先扩展商品领域层（Spu 聚合根、SpuFactory、CommodityErrors），再实现商品应用层（CommodityService 新用例），然后扩展持久化层（SpuPO、SpuRepository、数据库迁移），接着扩展订单领域层（OrderCreateCMD、OrderFactory、OrderErrors），最后更新受影响的现有测试。

## Tasks

- [x] 1. 扩展商品领域层错误常量
  - [x] 1.1 在 `CommodityErrors` 中新增草稿流程相关错误常量
    - 新增 `DRAFT_ALREADY_EXISTS`（409）、`ON_SALE_DIRECT_EDIT_REJECTED`（400）、`NOT_A_DRAFT_COPY`（400）、`ONLY_ON_SALE_NEEDS_DRAFT`（400）、`DRAFT_NO_SKU_FOR_PUBLISH`（400）
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/CommodityErrors.kt`
    - _需求：12.1, 12.2, 12.3, 12.4, 12.5_

- [x] 2. 扩展 Spu 聚合根
  - [x] 2.1 在 `Spu` 接口中新增 `sourceSpuId: SpuId?` 属性和 `mergeFromDraft(draft: Spu): Result<Unit, BusinessError>` 方法
    - `sourceSpuId` 为 null 表示原始商品，非 null 表示草稿副本
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/Spu.kt`
    - _需求：1.1, 9.1_
  - [x] 2.2 在 `SpuImpl` 中实现 `sourceSpuId` 和 `mergeFromDraft`
    - 构造函数新增 `sourceSpuId: SpuId? = null` 参数（默认 null 兼容现有代码）
    - `name` 和 `description` 改为通过 `_name` / `_description` 可变字段 + getter 暴露，以支持 `mergeFromDraft` 修改
    - `mergeFromDraft` 校验当前状态必须为 ON_SALE，草稿 SKU 不能为空；合并 name、description、SKU 列表并递增 version
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/SpuImpl.kt`
    - _需求：1.2, 9.2, 9.3, 9.4, 9.5, 9.6_
  - [x] 2.3 编写属性测试：mergeFromDraft 正确合并数据并递增版本
    - **Property 4: mergeFromDraft 正确合并数据并递增版本**
    - 使用 Kotest Property `checkAll` 生成随机的 ON_SALE 状态源 SPU 和包含至少一个 SKU 的草稿 SPU，验证合并后源 SPU 的 name/description/SKU 列表与草稿一致，version 递增 1，status 保持 ON_SALE
    - 测试类：`MergeFromDraftPropertyTest`，模块：`j-store-goods`
    - **验证需求：6.2, 6.3, 6.4, 9.2, 9.3, 9.4**
  - [x] 2.4 编写属性测试：mergeFromDraft 拒绝非 ON_SALE 目标
    - **Property 5: mergeFromDraft 拒绝非 ON_SALE 目标**
    - 使用 Kotest Property `checkAll` 生成 DRAFT 或 OFF_SALE 状态的 SPU，验证调用 `mergeFromDraft` 返回 Failure，且 SPU 所有字段保持不变
    - 测试类：`MergeFromDraftStatusGuardPropertyTest`，模块：`j-store-goods`
    - **验证需求：9.5**

- [x] 3. 扩展 SpuFactory 创建草稿副本能力
  - [x] 3.1 在 `SpuFactory` 接口中新增 `createDraftCopy(source: Spu): Result<Spu, BusinessError>` 方法
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/SpuFactory.kt`
    - _需求：2.1_
  - [x] 3.2 在 `SpuFactoryImpl` 中实现 `createDraftCopy` 和修改 `update` 方法
    - `createDraftCopy`：校验源商品状态为 ON_SALE，生成新 SpuId，复制 name/description/SKU 列表，状态设为 DRAFT，sourceSpuId 设为源商品 id，version 复制源商品 version
    - `update`：新增 `sourceSpuId = old.sourceSpuId` 保留原始 sourceSpuId
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/SpuFactory.kt`
    - _需求：2.2, 2.3, 2.4, 1.3, 1.4_
  - [x] 3.3 编写属性测试：createDraftCopy 保持源商品数据完整性
    - **Property 1: createDraftCopy 保持源商品数据完整性**
    - 使用 Kotest Property `checkAll` 生成随机的 ON_SALE 状态 SPU，验证草稿副本的 name/description/SKU/version 与源商品一致，status 为 DRAFT，sourceSpuId 等于源商品 id，id 不等于源商品 id
    - 测试类：`CreateDraftCopyDataIntegrityPropertyTest`，模块：`j-store-goods`
    - **验证需求：2.2, 2.3**
  - [x] 3.4 编写属性测试：createDraftCopy 拒绝非 ON_SALE 源商品
    - **Property 2: createDraftCopy 拒绝非 ON_SALE 源商品**
    - 使用 Kotest Property `checkAll` 生成 DRAFT 或 OFF_SALE 状态的 SPU，验证调用 `createDraftCopy` 返回 Failure
    - 测试类：`CreateDraftCopyStatusGuardPropertyTest`，模块：`j-store-goods`
    - **验证需求：2.4**

- [x] 4. 扩展 SpuRepository 接口
  - [x] 4.1 在 `SpuRepository` 接口中新增 `findDraftBySourceSpuId(sourceSpuId: SpuId): Spu?` 和 `delete(spu: Spu)` 方法
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepository.kt`
    - _需求：3.1, 14.1_

- [x] 5. 检查点 - 商品领域层验证
  - 确保 j-store-goods 模块编译通过，所有现有测试通过，ask the user if questions arise.

- [x] 6. 实现 CommodityService 新用例
  - [x] 6.1 在 `CommodityService.createOrUpdate` 中新增 ON_SALE 商品直接编辑拦截
    - 当目标 SPU 状态为 ON_SALE 时返回 `Failure(CommodityErrors.ON_SALE_DIRECT_EDIT_REJECTED)`
    - DRAFT 和 OFF_SALE 状态正常执行更新
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/service/CommodityService.kt`
    - _需求：5.1, 5.2, 8.1_
  - [x] 6.2 新增 `editOnSale(spuId: SpuId): Result<Spu, BusinessError>` 方法
    - 校验 SPU 状态为 ON_SALE；已有草稿直接返回（幂等）；无草稿则通过 SpuFactory 创建并持久化
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/service/CommodityService.kt`
    - _需求：4.1, 4.2, 4.3, 4.4, 4.5_
  - [x] 6.3 新增 `publishDraft(draftSpuId: SpuId): Result<SpuSnapshot, BusinessError>` 方法
    - 校验 sourceSpuId 非 null；加载源商品；调用 `source.mergeFromDraft(draft)`；创建新快照；持久化源商品和快照；删除草稿
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/service/CommodityService.kt`
    - _需求：6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
  - [x] 6.4 新增 `discardDraft(draftSpuId: SpuId): Result<Unit, BusinessError>` 方法
    - 校验 sourceSpuId 非 null；删除草稿副本；源商品不受影响
    - 文件：`j-store-goods/src/main/kotlin/com/jstore/goods/service/CommodityService.kt`
    - _需求：7.1, 7.2, 7.3, 7.4_
  - [x] 6.5 编写属性测试：createOrUpdate 状态守卫
    - **Property 3: createOrUpdate 状态守卫**
    - 使用 Kotest Property `checkAll` 生成随机的 CommodityCreateCmd 和不同状态的 SPU，验证 ON_SALE 时返回 Failure，DRAFT/OFF_SALE 时正常执行
    - 测试类：`CreateOrUpdateStatusGuardPropertyTest`，模块：`j-store-goods`
    - **验证需求：5.1, 5.2**
  - [x] 6.6 编写属性测试：discardDraft 不影响源商品
    - **Property 8: discardDraft 不影响源商品**
    - 使用 Kotest Property `checkAll` 生成 ON_SALE 状态的源 SPU 及其草稿副本，Mock SpuRepository，验证 discardDraft 后源 SPU 的 name/description/SKU/version/status 均保持不变
    - 测试类：`DiscardDraftSourceUnchangedPropertyTest`，模块：`j-store-goods`
    - **验证需求：7.2, 7.4**
  - [x] 6.7 编写单元测试：CommodityService 草稿流程
    - 测试 editOnSale 的幂等性、草稿创建、状态校验
    - 测试 publishDraft 的完整流程（合并、快照、删除）
    - 测试 discardDraft 的完整流程（删除草稿、源商品不变）
    - 测试 createOrUpdate 对 ON_SALE 商品的拦截
    - 测试类：`CommodityServiceDraftFlowTest`，模块：`j-store-goods`
    - _需求：4.1~4.5, 5.1, 5.2, 6.1~6.7, 7.1~7.4_

- [x] 7. 检查点 - 商品应用层验证
  - 确保 j-store-goods 模块所有测试通过，ask the user if questions arise.

- [ ] 8. 实现商品持久化层变更
  - [x] 8.1 在 `SpuPO` 中新增 `sourceSpuId` 字段
    - 添加 `@Column(name = "source_spu_id") var sourceSpuId: Long? = null`
    - 文件：`j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/persistence/SpuPO.kt`
    - _需求：13.1_
  - [x] 8.2 在 `SpuPOJpaRepository` 中新增查询方法
    - 添加 `fun findBySourceSpuIdAndStatus(sourceSpuId: Long, status: CommodityStatus): SpuPO?`
    - 文件：`j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/persistence/SpuPOJpaRepository.kt`
    - _需求：13.4_
  - [x] 8.3 修改 `SpuRepositoryImpl` 的 Converter 和新增方法
    - `Converter.toPO`：新增 `sourceSpuId = spu.sourceSpuId?.value` 映射
    - `Converter.toDomain`：新增 `sourceSpuId = po.sourceSpuId?.let { SpuId(it) }` 映射
    - 新增 `findDraftBySourceSpuId` 实现：调用 `jpaRepository.findBySourceSpuIdAndStatus`
    - 新增 `delete` 实现：调用 `jpaRepository.deleteById(spu.id.value)`
    - 文件：`j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt`
    - _需求：13.2, 13.3, 13.4, 3.1, 3.2, 3.3, 14.1, 14.2_
  - [x] 8.4 创建数据库迁移脚本 `09-goods-spu-source-spu-id.sql`
    - 为 `spu` 表新增 `source_spu_id BIGINT` 列（可为 null，默认 null 兼容历史数据）
    - 添加列注释和部分索引 `idx_spu_source_spu_id`
    - 文件：`docker/postgres/init/09-goods-spu-source-spu-id.sql`
    - _需求：13.5_
  - [x] 8.5 编写属性测试：SpuPO ↔ Spu 转换往返保持 sourceSpuId
    - **Property 7: SpuPO ↔ Spu 转换往返保持 sourceSpuId**
    - 使用 Kotest Property `checkAll` 生成随机的 Spu（sourceSpuId 为 null 或非 null），经过 `Converter.toPO` → `Converter.toDomain` 往返转换后，验证 sourceSpuId 与原始值相等
    - 测试类：`SpuPOSourceSpuIdRoundTripPropertyTest`，模块：`j-store-goods-infrastructure`
    - **验证需求：13.2, 13.3**

- [x] 9. 检查点 - 商品持久化层验证
  - 确保 j-store-goods-infrastructure 模块编译通过，所有测试通过，ask the user if questions arise.

- [ ] 10. 扩展订单领域层
  - [x] 10.1 在 `OrderErrors` 中新增 `SNAPSHOT_VERSION_MISMATCH` 错误常量
    - 错误信息："商品信息已变更，请刷新页面后重新下单"，错误码：`Order.Snapshot.VersionMismatch`，HTTP 状态码：409
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderErrors.kt`
    - _需求：11.1, 11.3_
  - [x] 10.2 在 `OrderCreateCMD.OrderItemCMD` 中新增 `snapshotVersion: Long` 字段
    - 记录买家下单时看到的商品快照版本号
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/command/OrderCreateCMD.kt`
    - _需求：10.1_
  - [x] 10.3 在 `OrderFactoryImpl.create()` 中新增快照版本校验逻辑
    - 在构建 OrderItem 循环中，比较 `itemCmd.snapshotVersion` 与 `goods.snapshotVersion`，不一致时返回 `Failure(OrderErrors.SNAPSHOT_VERSION_MISMATCH.msg(...))`，错误信息包含 SPU ID
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt`
    - _需求：10.2, 10.3, 10.4, 11.2_
  - [x] 10.4 编写属性测试：快照版本不匹配时 OrderFactory 拒绝创建订单
    - **Property 6: 快照版本不匹配时 OrderFactory 拒绝创建订单**
    - 使用 Kotest Property `checkAll` 生成 OrderCreateCMD（至少一个 OrderItemCMD 的 snapshotVersion 与 GoodsService 返回值不一致），Mock GoodsService，验证 `OrderFactory.create` 返回 Failure（SNAPSHOT_VERSION_MISMATCH）
    - 测试类：`SnapshotVersionMismatchPropertyTest`，模块：`j-store-order`
    - **验证需求：10.2, 10.3, 11.2**

- [x] 11. 更新现有订单测试以适配 OrderItemCMD 新字段
  - [x] 11.1 修改 `OrderFactoryUnitTest` 中所有 `OrderItemCMD` 构造处，新增 `snapshotVersion` 参数
    - 将 `OrderItemCMD(spuId = 1, skuId = 1, quantity = 1)` 更新为 `OrderItemCMD(spuId = 1, skuId = 1, quantity = 1, snapshotVersion = 1L)`
    - snapshotVersion 值应与 stubGoodsService 返回的 `snapshotVersion` 一致（均为 1L）
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderFactoryUnitTest.kt`
    - _需求：10.1_
  - [x] 11.2 修改 `OrderFactoryShippingInfoPropertyTest` 中所有 `OrderItemCMD` 构造处，新增 `snapshotVersion` 参数
    - 将 `OrderItemCMD(spuId = 1, skuId = 1, quantity = 1)` 更新为 `OrderItemCMD(spuId = 1, skuId = 1, quantity = 1, snapshotVersion = 1L)`
    - snapshotVersion 值应与 stubGoodsService 返回的 `snapshotVersion` 一致（均为 1L）
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderFactoryShippingInfoPropertyTest.kt`
    - _需求：10.1_

- [x] 12. 检查点 - 订单领域层验证
  - 确保 j-store-order 模块所有测试通过，ask the user if questions arise.

- [x] 13. 最终检查点 - 全模块验证
  - 确保 j-store-goods、j-store-goods-infrastructure、j-store-order 模块的所有测试通过，ask the user if questions arise.

## 说明

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 属性测试验证设计文档中定义的 8 个正确性属性
- 属性测试使用 Kotest Property 框架，每个属性至少运行 100 次迭代
- 单元测试和属性测试均使用 Mock（Mockito-Kotlin）模拟外部依赖，不依赖数据库或 Spring 容器
- 实现顺序：商品领域层 → 商品应用层 → 商品持久化层 → 订单领域层 → 测试更新，确保每一步都可编译验证

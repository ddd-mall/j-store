# Implementation Plan: 商品展示样式（GoodsStyle）与 SKU 编码增强

## 概述

本实现计划将设计文档拆分为可增量执行的编码任务。按照以下顺序推进：先扩展 SKU 编码字段（影响面小、无新实体），再构建 GoodsStyle 完整体系（实体→仓储→工厂→应用服务），最后完成数据库迁移和集成验证。每个任务引用具体的需求条款，确保全覆盖。

## Tasks

- [x] 1. SKU 编码字段扩展（领域层）
  - [x] 1.1 为 Sku 接口和 SkuImpl 新增 merchantCode 与 barcode 字段
    - 在 `Sku.kt` 接口中新增 `val merchantCode: String?` 和 `val barcode: String?` 属性
    - 在 `SkuImpl` 构造函数中新增对应参数，默认值为 `null`
    - _Requirements: 8.1, 8.2, 9.1, 9.2_
  - [x] 1.2 扩展 SkuCreateCmd 支持编码字段
    - 在 `SkuCreateCmd` data class 中新增 `val merchantCode: String? = null` 和 `val barcode: String? = null`
    - _Requirements: 8.3, 9.3_
  - [x] 1.3 更新 SpuFactory.createSku 传递编码字段
    - 修改 `SpuFactoryImpl.createSku` 方法，将 `cmd.merchantCode` 和 `cmd.barcode` 传入 `SkuImpl` 构造函数
    - _Requirements: 8.3, 9.3_
  - [x] 1.4 扩展 SkuSnapshot 包含编码字段
    - 在 `SkuSnapshot` data class 中新增 `val merchantCode: String? = null` 和 `val barcode: String? = null`
    - _Requirements: 10.1, 10.2_
  - [x] 1.5 更新 SpuSnapshotFactory 将 SKU 编码写入快照
    - 修改 `SpuSnapshotFactoryImpl.createSnapshot` 中的 SkuSnapshot 构建，传入 `sku.merchantCode` 和 `sku.barcode`
    - _Requirements: 10.3_
  - [x] 1.6 编写属性测试：快照保持 SKU 编码字段
    - **Property 7: 快照保持 SKU 编码字段**
    - 使用 Kotest property testing，随机生成含任意 merchantCode/barcode（含 null）的 SKU 列表，验证 SpuSnapshotFactory 创建的快照中每个 SkuSnapshot 的编码字段与原始 SKU 一致
    - 最少 100 次迭代
    - **Validates: Requirements 10.3, 10.4**

- [x] 2. SKU 编码字段扩展（基础设施层）
  - [x] 2.1 扩展 SkuPO 新增 merchant_code 和 barcode 列
    - 在 `SkuPO` 中新增 `@Column(name = "merchant_code", length = 128) var merchantCode: String? = null` 和 `@Column(name = "barcode", length = 64) var barcode: String? = null`
    - _Requirements: 8.4, 8.5, 9.4, 9.5_
  - [x] 2.2 更新 SpuRepositoryImpl Converter 支持编码字段
    - 修改 `Converter.toSkuPO` 传入 `merchantCode` 和 `barcode`
    - 修改 `Converter.toDomainSku` 从 SkuPO 读取 `merchantCode` 和 `barcode` 传入 SkuImpl
    - _Requirements: 8.5, 9.5_
  - [x] 2.3 更新 SpuSnapshotRepositoryImpl Converter 支持编码字段
    - 修改 `toSkuSnapshotMap` 将 `merchantCode` 和 `barcode` 写入 JSON map
    - 修改 `toSkuSnapshot` 从 JSON map 读取 `merchantCode` 和 `barcode`
    - _Requirements: 10.4_
  - [x] 2.4 编写属性测试：SKU Converter 往返一致性（含编码字段）
    - **Property 6: SKU Converter 往返一致性（含编码字段）**
    - 使用 Kotest property testing，随机生成含任意 merchantCode/barcode（含 null）的 SKU 对象，验证 `toSkuPO` → `toDomainSku` 往返后所有字段等价
    - 最少 100 次迭代
    - **Validates: Requirements 8.5, 9.5**

- [x] 3. Checkpoint — 确保 SKU 编码扩展完成
  - 确保所有测试通过，如有疑问请向用户确认。

- [x] 4. GoodsStyle 实体与核心领域对象
  - [x] 4.1 创建 GoodsStyleId 值对象
    - 在 `j-store-goods` 模块 `domain/commodity/` 包下创建 `GoodsStyleId.kt`
    - `class GoodsStyleId(override val value: Long) : Id<Long>(value)`
    - _Requirements: 1.1_
  - [x] 4.2 创建 GoodsStyle 接口和 GoodsStyleImpl 实现
    - 在 `domain/commodity/` 包下创建 `GoodsStyle.kt`，定义 `GoodsStyle` 接口（实现 `Entity<GoodsStyleId>`）和 `GoodsStyleImpl` 类
    - 接口包含 `spuId`、`mainImages`、`detailHtml`、`skuImages` 属性及 `updateMainImages`、`updateDetailHtml`、`updateSkuImages` 方法
    - 实现类封装重复图片检测逻辑，返回 `Result<Unit, BusinessError>`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 4.1, 4.2, 4.3, 4.4_
  - [x] 4.3 在 CommodityErrors 中新增 DUPLICATE_IMAGE_KEY 错误常量
    - 添加 `val DUPLICATE_IMAGE_KEY = BusinessError("图片标识重复", "Goods.DuplicateImageKey", 400)`
    - _Requirements: 2.4, 4.4_
  - [x] 4.4 编写属性测试：主图列表顺序保持
    - **Property 1: 主图列表顺序保持**
    - 随机生成不含重复元素的 ImageKey 列表（含空列表），调用 `updateMainImages` 后验证 `mainImages` 与输入完全相等
    - 最少 100 次迭代
    - **Validates: Requirements 1.3, 2.1, 2.2, 2.3**
  - [x] 4.5 编写属性测试：重复图片标识拒绝
    - **Property 2: 重复图片标识拒绝**
    - 随机生成含至少一个重复元素的 ImageKey 列表，调用 `updateMainImages` 或 `updateSkuImages` 验证返回 Failure 且原有状态不变
    - 最少 100 次迭代
    - **Validates: Requirements 2.4, 4.4**
  - [x] 4.6 编写属性测试：详情 HTML 存储保持
    - **Property 3: 详情 HTML 存储保持**
    - 随机生成任意字符串（含空串、特殊字符），调用 `updateDetailHtml` 后验证 `detailHtml` 与输入完全相等
    - 最少 100 次迭代
    - **Validates: Requirements 3.1, 3.2**
  - [x] 4.7 编写属性测试：SKU 图片列表顺序保持
    - **Property 4: SKU 图片列表顺序保持**
    - 随机生成 SkuId 和不含重复元素的 ImageKey 列表，调用 `updateSkuImages` 后验证 `skuImages[skuId]` 与输入完全相等
    - 最少 100 次迭代
    - **Validates: Requirements 4.1, 4.2, 4.3**

- [x] 5. GoodsStyle 仓储与命令对象
  - [x] 5.1 创建 GoodsStyleRepository 接口
    - 在 `domain/commodity/` 包下创建 `GoodsStyleRepository.kt`
    - 继承 `Repository<GoodsStyleId, GoodsStyle>`，新增 `fun findBySpuId(spuId: SpuId): GoodsStyle?`
    - _Requirements: 5.1, 5.2_
  - [x] 5.2 创建 GoodsStyleSaveCmd 命令对象
    - 在 `domain/commodity/comand/` 包下创建 `GoodsStyleSaveCmd.kt`
    - 包含 `spuId`、`mainImages`、`detailHtml`、`skuImages` 字段和 `verify()` 方法（校验重复图片）
    - _Requirements: 2.4, 4.4, 6.3_
  - [x] 5.3 创建 GoodsStyleFactory 接口和实现
    - 在 `domain/commodity/` 包下创建 `GoodsStyleFactory.kt`
    - 工厂接口定义 `create` 方法，实现类使用 `SnowFlakSequence` 生成 ID
    - _Requirements: 6.1, 6.2_

- [x] 6. GoodsStyle 基础设施层
  - [x] 6.1 创建 GoodsStylePO 持久化对象
    - 在 `j-store-goods-infrastructure` 模块 `persistence/` 包下创建 `GoodsStylePO.kt`
    - JPA 实体映射 `goods_style` 表，包含 id、spuId、mainImages（JSONB）、detailHtml（TEXT）、skuImages（JSONB）、createTime、updateTime
    - _Requirements: 5.4, 5.6_
  - [x] 6.2 创建 GoodsStylePOJpaRepository
    - 在 `persistence/` 包下创建 `GoodsStylePOJpaRepository.kt`
    - Spring Data JPA 接口，提供 `findBySpuId` 查询方法
    - _Requirements: 5.2, 5.3_
  - [x] 6.3 创建 GoodsStyleRepositoryImpl 仓储实现
    - 在 `j-store-goods-infrastructure` 模块 `domain/commodity/` 包下创建 `GoodsStyleRepositoryImpl.kt`
    - 包含 `Converter` 对象实现 GoodsStyle ↔ GoodsStylePO 双向转换
    - 实现 `save`、`findById`、`findBySpuId` 方法
    - _Requirements: 5.3, 5.6, 5.7_
  - [x] 6.4 编写属性测试：GoodsStyle Converter 往返一致性
    - **Property 5: GoodsStyle Converter 往返一致性**
    - 随机生成有效的 GoodsStyle 领域对象，验证 `toPO` → `toDomain` 往返后所有业务字段等价
    - 最少 100 次迭代
    - **Validates: Requirements 5.6, 5.7**

- [x] 7. OssService ACL 接口与应用服务集成
  - [x] 7.1 创建 OssService ACL 接口
    - 在 `j-store-goods` 模块 `acl/` 包下创建 `OssService.kt`
    - 定义 `generateUrl(imageKey: String): String` 和 `generateUrls(imageKeys: List<String>): List<String>` 方法签名
    - 纯接口，不依赖任何框架
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  - [x] 7.2 扩展 CommodityService 新增 saveGoodsStyle 方法
    - 在 `CommodityService` 中注入 `GoodsStyleRepository` 和 `GoodsStyleFactory`
    - 实现 `saveGoodsStyle(cmd: GoodsStyleSaveCmd): Result<GoodsStyle, BusinessError>` 方法
    - 包含：命令校验、SPU 存在性验证、已有则更新/不存在则创建逻辑
    - _Requirements: 6.3, 6.4, 6.5_
  - [x] 7.3 编写 CommodityService.saveGoodsStyle 单元测试
    - 测试场景：SPU 不存在返回错误、新建 GoodsStyle、更新已有 GoodsStyle
    - 使用 Mockito 模拟依赖
    - _Requirements: 6.3, 6.4, 6.5_

- [x] 8. Checkpoint — 确保 GoodsStyle 完整体系可编译
  - 确保所有测试通过，如有疑问请向用户确认。

- [x] 9. 数据库迁移脚本
  - [x] 9.1 创建数据库迁移脚本 07-goods-style-sku-code.sql
    - 在 `docker/postgres/init/` 目录下创建 `07-goods-style-sku-code.sql`
    - 包含：创建 `goods_style` 表（含 spu_id 唯一索引）、为 `sku` 表新增 `merchant_code` 和 `barcode` 列
    - 遵循现有迁移脚本的注释和命名规范
    - _Requirements: 5.4, 5.5, 8.4, 9.4, 11.5_

- [x] 10. 最终 Checkpoint — 确保所有测试通过
  - 确保所有测试通过，如有疑问请向用户确认。

## Notes

- 标记 `*` 的子任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用具体的需求条款，确保可追溯性
- 属性测试验证设计文档中定义的 7 个正确性属性
- 单元测试验证具体场景和边界条件
- Checkpoint 任务确保增量验证，避免问题累积
- 实现语言为 Kotlin，与项目技术栈一致

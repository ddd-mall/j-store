# Catalog 商品模型 v2 交付摘要

## 交付结果

- 草稿 SKU 采用独立持久化 ID，并通过 `sourceSkuId` 恢复已发布稳定身份；草稿副本不能被当作独立商品直接发布。
- `GoodsStyle` 纳入草稿复制和发布事务，SKU 图片会在草稿 ID 与稳定 ID 之间映射；已发布素材不能直接修改。
- `SpuSnapshot` 和公共 Goods API 冻结描述、主图、详情、SKU 图片、Product Type、结构化属性、品牌、类目和本地化内容。
- 草稿合并递增业务资料版本并产生新的 `CommodityPublishedEvent`。
- SPU 持久化增加 JPA 乐观锁版本，数据库增加“一份源商品最多一个草稿”的部分唯一索引。
- 新增 Product Type 聚合、保存用例、事务装饰器和 JPA 仓储；支持文本、数字、布尔、枚举、层级、必填和变体轴组合校验。
- 草稿 SKU 支持新增、修改和删除，并校验属性组合、商家货号和条码唯一性。
- 新增 Category、LocalizedText 和 MediaAsset 领域基础类型；Brand 完整接入商户级聚合、应用用例、事务装配和 JPA 仓储，并保证商户内规范化名称唯一。
- 商品保存与发布拒绝不存在、停用或跨商户的 Brand；SPU 保存稳定引用，快照和公共查询契约冻结发布时的品牌多语言名称。
- Catalog、Offer 和 Inventory 的权威边界保持不变。

## 验收映射

| 验收 | 实现与证据 |
|---|---|
| R1 草稿 SKU 身份 | `DraftSkuIdentityTest`、`CreateDraftCopyDataIntegrityPropertyTest`、`CatalogV2ConverterTest` |
| R2 版本化内容 | `CommodityServiceDraftStyleFlowTest`、`SpuSnapshotContentTest`、Goods snapshot API 回归 |
| R3 发布与并发 | `DraftSkuIdentityTest` 发布事件断言、`SpuPO.@Version`、Flyway 全量迁移测试 |
| R4 Product Type | `ProductTypeValidationTest`、`ProductTypeServiceTest`、Product Type JPA 仓储编译与全仓回归 |
| R5 SKU 管理 | `SkuManagementTest`、`CommodityServiceSkuManagementTest` |
| R6 内容基础 | `CatalogContentValueTest`、SPU/快照转换回归 |
| R7 Brand 聚合闭环 | `BrandTest`、`BrandServiceTest`、`CommodityServiceBrandValidationTest`、`BrandRepositoryConverterTest`、Flyway 全量迁移测试 |

## 验证证据

通过：

```text
./gradlew :j-store-goods-domain:test :j-store-goods-application:test \
  :j-store-goods-infrastructure:test :j-store-goods-boot:test \
  :j-store-order-infrastructure:test

uv run --python 3.12 -- ./scripts/quality-gate.sh
```

完整质量门禁结果：治理契约、135 个 Python 治理/规格/工具测试、文件所有权、Spotless、所有 Gradle 测试、依赖许可证审计和 53 个发布 JAR 许可证验证全部通过。

系统 `/usr/bin/python3` 为 3.9.6，直接运行门禁会因缺少标准库 `tomllib` 停止；最终使用 `uv` 管理的 Python 3.12.13 执行同一未修改门禁脚本并通过。

## 数据与兼容策略

项目处于内部开发期，本次直接更新当前 Flyway 基线和初始化快照。旧开发数据库应重建，不提供旧表结构的双读、回填或兼容迁移。

## 后续候选与残余风险

- `MediaAsset` 已建立领域语义；现有 `GoodsStyle` 仍以 OSS key 保存媒体引用。接入视频、3D、alt text 等富媒体元数据时再将其用于具体展示用例。
- Category 已有稳定类型并可被 SPU/快照引用，完整类目管理、层级移动规则和搜索投影属于后续迭代；Brand 的领域与应用闭环已完成，管理端 HTTP 接口不在本次范围。
- `detailHtml` 尚无面向消费者的 Controller；未来输出到页面前必须增加白名单清洗。
- Bundle、商品关系、搜索 facets、合规模板和第三方平台 schema 映射仍按计划留在后续候选。
- 按仓库治理规则，领域模型和数据库变更在合并前仍需要非实现者独立评估与人工批准；本摘要和绿灯门禁不能替代该审批。

## 收敛结论

实现、测试、数据库基线、公共契约和领域文档对本规格范围已经语义一致。技术验收已收敛；合并治理仍等待非实现者评估和人工批准。

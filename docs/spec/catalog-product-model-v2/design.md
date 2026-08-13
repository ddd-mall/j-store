# Catalog 商品模型 v2 设计

## 核心决策

### 1. 保留现有 Copy-on-Write SPU 草稿，修正身份语义

本次不一次性迁移为新的 revision 表，以降低跨层改造风险。草稿 SPU 继续由 `sourceSpuId` 指向发布源，但草稿 SKU 必须深复制：

```text
published sku 101
       |
       +-- draft sku 901, sourceSkuId=101

new draft sku 902, sourceSkuId=null
```

发布草稿时，901 的资料映射回稳定 SKU 101；902 成为新稳定 SKU。这样既避免数据库主键共享，也不破坏 Offer、Inventory 和历史订单对已有 SKU ID 的引用。

### 2. GoodsStyle 仍是独立聚合，但生命周期受 SPU 发布编排约束

`GoodsStyle` 继续独立持久化，避免把大块内容塞进 SPU 聚合。应用事务负责：

1. 创建草稿时复制样式并映射 SKU ID；
2. 只允许 DRAFT SPU 修改样式；
3. 发布时将草稿样式映射回稳定 SKU ID；
4. 用 SPU 与样式共同生成不可变快照；
5. 删除草稿样式和草稿 SPU。

这是一个显式的同上下文多聚合事务；`j-store-goods-boot` 的事务装饰器覆盖整个用例。

### 3. 业务版本与持久化版本分离

- `Spu.version`：消费者可见的资料快照版本。
- `SpuPO.persistenceVersion`：JPA 乐观锁版本。

数据库对 `source_spu_id IS NOT NULL AND status = 'DRAFT'` 建立唯一索引，处理并发创建草稿的最终约束。

### 4. Product Type 是独立聚合

`ProductType` 定义属性 schema，SPU 只保存 `productTypeId` 引用。为保持现有订单快照契约，本次属性值继续使用字符串传输，但必须由定义按类型解析和校验。

```text
AttributeDefinition
  code, label, level, valueType,
  required, variantAxis, allowedValues
```

`ProductType.validate(spuAttributes, skus)` 负责 schema 规则；SPU 负责 SKU 身份、编辑状态和商品内唯一性。

### 5. 内容基础类型先落领域语义

本次提供 `Category`、`Brand`、`LocalizedText`、`MediaAsset` 类型及不变量，但不引入完整管理后台和搜索投影。SPU 对 Category 只引用稳定 ID；Brand 作为 Catalog 内商户级聚合，通过仓储和应用用例维护，SPU 仍只持有 `brandId`。

Brand 默认启用，可维护多语言名称并显式启用或停用；商户内按确定性首选本地化名称规范化后保持唯一。商品保存和发布由应用服务加载 Brand，校验存在性、启用状态和商户归属；数据库外键保证引用目标存在。快照保存 Brand ID 与发布时名称，历史查询不回查可变 Brand。

## 发布事务

```text
load draft + source + draft style
        |
validate ProductType and SKU invariants
        |
merge draft into source and build SKU ID mapping
        |
map/save source style
        |
save source + snapshot + publish event
        |
delete draft style + draft
```

任一步失败均由外层事务回滚。

## 数据结构

- `sku.source_sku_id`：草稿 SKU 指向其稳定来源。
- `brand` 保存商户、名称、规范化名称、状态和乐观锁版本，并以 `(merchant_id, normalized_name)` 保证商户内唯一；`spu.brand_id` 使用外键引用。
- `spu.product_type_id`、`brand_id`、`localized_name`、`localized_description`、`category_ids`。
- `spu_snapshot.brand_name` 冻结发布时的本地化品牌名称。
- `spu.persistence_version`：JPA 乐观锁。
- `product_type`：类型定义 JSONB。
- 快照 JSON 扩展内容和新的商品资料字段。

项目仍处于内部开发期，因此更新当前 Flyway 基线与初始化快照，不增加旧结构双读或回填代码。

## 安全与边界

- 媒体只保存受控对象 key，不接受任意文件内容。
- `detailHtml` 仍是存储内容，不在本次新增消费者页面；未来 Controller 必须清洗后输出。
- Product Type 不保存价格或库存规则。
- Category 不决定销售资格。

## 验证策略

- 领域：草稿身份映射、SKU 修改删除、不变量、Product Type 属性校验、内容值对象。
- 应用：样式复制/发布编排、直接编辑拒绝、发布事件。
- 基础设施：PO 往返、数据库唯一约束和乐观锁字段。
- 契约：Goods snapshot 扩展字段映射，Order 既有字段保持。

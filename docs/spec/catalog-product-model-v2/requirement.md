# Catalog 商品模型 v2 需求

## 背景

当前 Catalog 已经区分 SPU、SKU、资料生命周期和发布快照，但草稿通过复制整个 SPU 实现，复制时复用了 SKU 身份；商品素材又拥有独立于草稿和快照的修改路径。与此同时，SKU 属性只是自由文本键值，缺少商品类型约束，SKU 也只有新增能力。

本需求将 Catalog 收敛为可持续演进的商品资料权威，同时保持现有上下文边界：价格、店铺销售状态和销售周期仍归 Store/Offer，库存仍归 Inventory/WMS。

## 范围

### Iteration 1：发布一致性与并发安全

- 草稿 SKU 与已发布 SKU 使用不同的持久化身份，并记录其源 SKU 身份。
- 草稿发布时，已有 SKU 恢复其稳定源身份，新 SKU 保留新身份。
- 商品素材参与草稿复制、发布和资料快照，已发布资料不能绕过草稿直接修改。
- 草稿合并产生新的商品发布事件。
- SPU 持久化使用独立的乐观锁版本；一个已发布 SPU 同时最多存在一个草稿。

### Iteration 2：结构化属性与 SKU 管理

- Product Type 定义可复用的商品属性结构。
- 属性定义至少支持文本、数字、布尔和枚举类型，以及商品级/SKU 级、必填和变体轴语义。
- 发布前校验所有 SKU 的属性 code、值类型、必填项和变体组合唯一性。
- 草稿支持新增、修改和删除 SKU；删除不存在的 SKU 必须返回业务失败。
- 商家货号和条码在同一商品内不得重复。

### Iteration 3：内容基础

- 提供稳定的 Category、Brand、LocalizedText 和 MediaAsset 领域类型，供后续管理用例与查询投影复用。
- 媒体必须具有稳定 key、类型、角色、排序和可选替代文本。
- 类目负责分类，Product Type 负责数据结构，二者不得合并。

## 验收标准

### R1 草稿 SKU 身份

1. WHEN 从已发布 SPU 创建草稿, THE Catalog SHALL 为每个草稿 SKU 分配新的草稿身份并记录源 SKU 身份。
2. WHEN 草稿发布, THE Catalog SHALL 让源 SKU 的修改继续使用原稳定 SKU ID，并让草稿中新建的 SKU 获得稳定 ID。
3. THE Catalog SHALL NOT 让两个 SPU 聚合同时持有相同的持久化 SKU 主键。

### R2 版本化商品内容

1. WHEN 创建已发布商品的草稿, THE Catalog SHALL 复制当前发布素材并正确映射草稿 SKU 图片。
2. WHEN 发布草稿, THE Catalog SHALL 将草稿素材映射回稳定 SKU 身份并作为新版本内容发布。
3. WHEN 创建资料快照, THE snapshot SHALL 包含描述、主媒体、详情内容和 SKU 媒体。
4. WHEN 尝试直接修改已发布 SPU 的素材, THE Catalog SHALL 返回业务失败。

### R3 发布和并发

1. WHEN 草稿合并成功, THE SPU SHALL 递增资料版本并产生包含新版本的发布事件。
2. THE persistence model SHALL 使用独立于业务资料版本的乐观锁版本。
3. THE database schema SHALL 保证每个源 SPU 同时最多存在一个 DRAFT 副本。

### R4 Product Type

1. THE ProductType SHALL 由稳定 ID、商户、名称和属性定义组成。
2. WHEN 校验商品资料, THE ProductType SHALL 拒绝未知属性、错误类型、缺失必填属性和非法枚举值。
3. WHEN 多个 SKU 使用相同变体轴组合, THE Catalog SHALL 拒绝发布。
4. ProductType、Category 和 Offer SHALL 保持独立领域语义。

### R5 SKU 管理

1. DRAFT 商品 SHALL 支持新增、修改和删除 SKU。
2. PUBLISHED 或 ARCHIVED 商品 SHALL NOT 直接修改 SKU。
3. THE Catalog SHALL 拒绝同一商品内重复的商家货号和条码。
4. THE Catalog SHALL 拒绝删除不存在的 SKU。

### R6 内容基础类型

1. LocalizedText SHALL 要求至少一个非空语言值并使用规范化 locale key。
2. MediaAsset SHALL 拒绝空 key、负数排序和缺失媒体类型。
3. Category SHALL 使用父 Category ID 表达树关系，不直接嵌套父对象。
4. Brand SHALL 使用稳定身份供 SPU 引用。

## 质量目标

- 领域规则必须由无框架单元测试覆盖。
- 草稿复制、发布映射和持久化转换必须有回归测试。
- 公共快照契约扩展必须保持 Order 现有字段语义不变。
- 数据库结构直接演进到当前目标形态，不为可丢弃开发数据增加双写或兼容层。
- `detailHtml` 在进入面向消费者的 HTTP 页面前必须经过白名单清洗；本次没有商品页面 Controller，因此清洗器属于后续接口层工作。

## 范围外

- Store/Offer 的价格、渠道销售资格和限购。
- Inventory/WMS 的库存数量、包装库存和库存预留。
- Bundle、订阅、数字下载授权和定制商品定价。
- Elasticsearch/OpenSearch 索引和完整类目管理后台。
- 面向第三方平台的类目映射与动态 JSON Schema 规则同步。

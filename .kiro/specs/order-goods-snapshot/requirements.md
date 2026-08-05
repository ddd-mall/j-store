# 需求文档：订单商品快照

## 引言

在电商系统中，商品信息（名称、描述、价格、规格属性等）会随时间变化（编辑、调价、下架等）。订单作为交易凭证，必须记录下单时刻的商品信息快照，确保用户在订单详情中看到的商品信息与下单时一致，不受后续商品编辑的影响。

### 现状分析

当前 j-store 项目中：

1. **商品模块（j-store-goods）已具备快照基础设施**：`SpuSnapshot` / `SkuSnapshot` 实体已定义，`SpuSnapshotFactory` 在商品上架时创建快照，`spu_snapshot` 数据库表已建立。
2. **订单模块（j-store-order）未消费快照数据**：
   - `GoodsService` ACL 接口仅返回 `GoodsId`（spuId + skuId）、`version` 和 `price`，缺少商品名称、SKU 描述、销售属性等展示信息。
   - `OrderFactory` 创建订单时将 `goodsName` 硬编码为空字符串 `""`，`skuDescription` 同样为空。
   - `OrderItem` 接口虽然定义了 `goodsName` 和 `skuDescription` 字段，但从未被正确填充。
3. **订单行项缺少快照版本追溯**：`OrderItem` / `OrderItemPO` 没有 `snapshotVersion` 字段，无法追溯下单时引用的是哪个版本的商品快照。

### 改进目标

通过本次需求，打通商品快照与订单的集成链路，使订单创建时能够读取商品快照信息，并将快照数据冻结在订单行项中，实现订单商品信息的不可变性。

## 术语表

- **Spu**：Standard Product Unit，标准产品单元，代表一个商品（如"Nike Air Max 90"）
- **Sku**：Stock Keeping Unit，库存量单位，代表商品的具体规格（如"红色/42码"）
- **SpuSnapshot**：商品快照，记录某一时刻 SPU 及其所有 SKU 的完整信息，不可变
- **SkuSnapshot**：SKU 快照，SpuSnapshot 的组成部分，记录单个 SKU 的名称、属性、价格等
- **GoodsService**：订单上下文中的防腐层（ACL）接口，用于跨上下文查询商品信息
- **GoodsInfo**：订单上下文中的本地数据类型，承载从商品上下文获取的商品信息
- **OrderItem**：订单行项实体，记录订单中每一项商品的信息，生命周期依附于 Order 聚合根
- **OrderFactory**：订单工厂，负责组装合法的初始状态 Order 聚合根
- **SnapshotVersion**：快照版本号，与 SPU 的 version 字段对应，每次上架时递增
- **Attribute**：销售属性键值对，如 `{"key": "颜色", "value": "红色"}`

## 需求

### 需求 1：扩展 GoodsService ACL 以返回快照信息

**用户故事：** 作为订单模块的开发者，我希望通过 GoodsService ACL 获取商品快照中的完整信息（商品名称、SKU 名称、销售属性、快照版本号），以便在创建订单时填充订单行项。

#### 验收标准

1. THE GoodsInfo SHALL 包含以下字段：spuId、skuId、snapshotVersion、spuName、skuName、attributes（销售属性列表）、price
2. WHEN OrderFactory 通过 GoodsService 查询商品信息时，THE GoodsService SHALL 返回基于最新快照版本的商品信息
3. IF GoodsService 查询的商品不存在对应的快照记录，THEN THE GoodsService SHALL 返回空结果（该商品视为不可下单）

### 需求 2：订单行项记录快照数据

**用户故事：** 作为买家，我希望订单中的商品信息（名称、规格描述、价格）是下单时刻的快照，以便即使商品后续被编辑，我看到的订单信息仍与下单时一致。

#### 验收标准

1. THE OrderItem SHALL 包含 snapshotVersion 字段，记录下单时引用的商品快照版本号
2. WHEN OrderFactory 创建订单行项时，THE OrderFactory SHALL 从 GoodsInfo 中读取 spuName 并填充到 OrderItem 的 goodsName 字段
3. WHEN OrderFactory 创建订单行项时，THE OrderFactory SHALL 从 GoodsInfo 中读取 skuName 和 attributes，拼接为人类可读的规格描述并填充到 OrderItem 的 skuDescription 字段
4. WHEN OrderFactory 创建订单行项时，THE OrderFactory SHALL 从 GoodsInfo 中读取 price 并填充到 OrderItem 的 unitPrice 字段
5. WHILE 订单行项已创建，THE OrderItem 中的 goodsName、skuDescription、unitPrice 和 snapshotVersion SHALL 保持不可变

### 需求 3：GoodsService ACL 基础设施层实现

**用户故事：** 作为系统集成开发者，我希望 GoodsService 的基础设施层实现能够正确地从商品模块的 SpuSnapshotRepository 读取快照数据并转换为订单上下文的本地类型，以便订单模块与商品模块之间通过 ACL 解耦。

#### 验收标准

1. THE GoodsServiceImpl SHALL 通过 SpuSnapshotRepository 查询最新快照，将 SpuSnapshot 和 SkuSnapshot 转换为订单上下文的 GoodsInfo 列表
2. WHEN GoodsServiceImpl 转换快照数据时，THE GoodsServiceImpl SHALL 仅提取订单上下文所需的字段（spuName、skuName、attributes、price、snapshotVersion），丢弃商品上下文的内部细节
3. IF SpuSnapshotRepository 中不存在指定 SPU 的快照，THEN THE GoodsServiceImpl SHALL 跳过该商品，返回的 GoodsInfo 列表中不包含该商品

### 需求 4：订单行项持久化层扩展

**用户故事：** 作为系统开发者，我希望订单行项的持久化层能够存储快照版本号和完整的商品快照信息，以便订单数据在数据库中完整可追溯。

#### 验收标准

1. THE OrderItemPO SHALL 包含 snapshot_version 列（BIGINT 类型），用于持久化快照版本号
2. WHEN OrderRepositoryImpl 将 OrderItem 转换为 OrderItemPO 时，THE Converter SHALL 将 snapshotVersion 字段映射到 snapshot_version 列
3. WHEN OrderRepositoryImpl 将 OrderItemPO 转换为 OrderItem 时，THE Converter SHALL 将 snapshot_version 列映射回 snapshotVersion 字段
4. THE 数据库迁移脚本 SHALL 为 order_items 表新增 snapshot_version 列，默认值为 0（兼容历史数据）

### 需求 5：SKU 规格描述生成

**用户故事：** 作为买家，我希望在订单详情中看到清晰的商品规格描述（如"颜色:红色 尺码:XL"），以便快速识别我购买的具体规格。

#### 验收标准

1. WHEN OrderFactory 生成 skuDescription 时，THE OrderFactory SHALL 将 SkuSnapshot 的 attributes 列表按顺序拼接为 "key:value" 格式，多个属性之间使用空格分隔
2. IF SkuSnapshot 的 attributes 列表为空，THEN THE OrderFactory SHALL 使用 skuName 作为 skuDescription 的值
3. FOR ALL 有效的 attributes 列表，生成 skuDescription 后再解析回 attributes 列表 SHALL 产生等价的结果（往返一致性）

### 需求 6：商品不可下单校验

**用户故事：** 作为系统运营者，我希望当商品没有可用快照时（未上架或快照缺失），系统拒绝创建包含该商品的订单，以便防止无效订单的产生。

#### 验收标准

1. WHEN OrderFactory 创建订单时发现某个商品的 GoodsInfo 不存在（快照缺失），THEN THE OrderFactory SHALL 返回包含明确错误信息的失败结果
2. THE 错误信息 SHALL 包含无法找到快照的商品 SPU ID，便于排查问题
3. WHEN 订单中包含多个商品且其中部分商品快照缺失时，THE OrderFactory SHALL 在遇到第一个缺失快照的商品时立即返回失败，不继续处理后续商品

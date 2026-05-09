# 需求文档：商品 Copy-on-Write 草稿编辑模型

## 引言

在电商系统中，在售商品（ON_SALE）的编辑是一个高风险操作——直接修改在售商品数据会导致买家在浏览、下单过程中看到不一致的信息，甚至引发订单金额错误。为了解决这一问题，本需求引入 Copy-on-Write 草稿模型：当运营人员需要编辑在售商品时，系统自动创建一份独立的 DRAFT 副本，所有编辑操作在 DRAFT 副本上进行，在售商品在整个编辑期间保持不变、可正常购买。编辑完成后，运营人员可以选择发布草稿（覆盖在售数据、递增版本号、生成新快照）或丢弃草稿（不影响在售商品）。

### 现状分析

当前 j-store 项目中：

1. **商品状态模型**：`Spu` 聚合根有三种状态 `DRAFT`、`OFF_SALE`、`ON_SALE`，其中 `DRAFT` 仅用于新建商品的初始状态，不支持在售商品的草稿编辑。
2. **在售商品不可编辑**：`CommodityService.createOrUpdate()` 对所有状态的商品均允许直接修改，缺少对 ON_SALE 商品的编辑保护。
3. **无草稿副本机制**：`Spu` 没有 `sourceSpuId` 字段来关联原始商品，`SpuFactory` 没有从已有商品创建草稿副本的能力。
4. **快照版本校验缺失**：`OrderCreateCMD` 不携带买家看到的快照版本号，`OrderFactory` 创建订单时不校验快照版本是否为最新，可能导致买家基于过期信息下单。

### 改进目标

1. 在售商品编辑时自动创建 DRAFT 副本，保护在售数据不被直接修改
2. 支持草稿发布（合并回在售商品）和草稿丢弃
3. 每个在售商品同一时间最多存在一个 DRAFT 副本
4. 下架商品（OFF_SALE）不走草稿流程，可直接编辑
5. 订单创建时校验快照版本号，防止买家基于过期商品信息下单

## 术语表

- **Spu**：Standard Product Unit，标准产品单元，代表一个商品聚合根（如"Nike Air Max 90"）
- **Sku**：Stock Keeping Unit，库存量单位，代表商品的具体规格（如"红色/42码"）
- **SpuSnapshot**：商品快照，记录某一时刻 SPU 及其所有 SKU 的完整信息，不可变
- **CommodityStatus**：商品状态枚举，包含 DRAFT、OFF_SALE、ON_SALE
- **SourceSpuId**：草稿副本中指向原始在售商品的 SPU ID，用于关联草稿与源商品
- **Draft_Copy**：草稿副本，从在售商品复制而来的独立 SPU 记录，状态为 DRAFT，携带 sourceSpuId
- **CommodityService**：商品应用服务，负责编排商品相关的用例操作
- **SpuFactory**：SPU 工厂，负责创建合法的初始状态 SPU 聚合根
- **SpuRepository**：SPU 仓储接口，负责 SPU 聚合根的持久化和查询
- **SpuSnapshotFactory**：快照工厂，负责从 SPU 创建不可变快照
- **SpuSnapshotRepository**：快照仓储接口，负责快照的持久化和查询
- **OrderCreateCMD**：创建订单命令，承载买家下单所需的全部信息
- **OrderFactory**：订单工厂，负责组装合法的初始状态 Order 聚合根
- **SnapshotVersion**：快照版本号，与 SPU 的 version 字段对应，每次上架或发布草稿时递增
- **GoodsService**：订单上下文中的防腐层（ACL）接口，用于跨上下文查询商品信息
- **GoodsInfo**：订单上下文中的本地数据类型，承载从商品上下文获取的商品信息

## 需求

### 需求 1：Spu 聚合根扩展 sourceSpuId 字段

**用户故事：** 作为系统开发者，我希望 Spu 聚合根具备 sourceSpuId 字段，以便区分原始商品和草稿副本，并建立草稿与源商品之间的关联关系。

#### 验收标准

1. THE Spu 接口 SHALL 包含 `sourceSpuId: SpuId?` 属性，其中 null 表示原始商品，非 null 表示该 SPU 是指定源商品的草稿副本
2. THE SpuImpl SHALL 在构造时接受 sourceSpuId 参数，并通过只读属性对外暴露
3. WHEN SpuFactory 创建新商品时，THE SpuFactory SHALL 将 sourceSpuId 设置为 null
4. WHEN SpuFactory 更新商品时，THE SpuFactory SHALL 保留原始 SPU 的 sourceSpuId 值

### 需求 2：SpuFactory 创建草稿副本

**用户故事：** 作为系统开发者，我希望 SpuFactory 能够从一个在售商品创建草稿副本，以便在不影响在售商品的前提下进行编辑。

#### 验收标准

1. THE SpuFactory 接口 SHALL 提供 `createDraftCopy(source: Spu): Spu` 方法，用于从源商品创建草稿副本
2. WHEN SpuFactory 创建草稿副本时，THE SpuFactory SHALL 生成新的 SpuId，复制源商品的 name、description 和全部 SKU 列表，将状态设置为 DRAFT，将 sourceSpuId 设置为源商品的 id
3. WHEN SpuFactory 创建草稿副本时，THE SpuFactory SHALL 将草稿副本的 version 设置为源商品的 version（发布时再递增）
4. IF 源商品的状态不是 ON_SALE，THEN THE SpuFactory SHALL 拒绝创建草稿副本并返回错误

### 需求 3：SpuRepository 扩展草稿查询能力

**用户故事：** 作为系统开发者，我希望 SpuRepository 能够根据 sourceSpuId 查询草稿副本，以便在编辑在售商品时判断是否已存在草稿。

#### 验收标准

1. THE SpuRepository SHALL 提供 `findDraftBySourceSpuId(sourceSpuId: SpuId): Spu?` 方法，用于查询指定源商品的草稿副本
2. WHEN 指定源商品存在草稿副本时，THE SpuRepository SHALL 返回该草稿副本
3. WHEN 指定源商品不存在草稿副本时，THE SpuRepository SHALL 返回 null

### 需求 4：在售商品编辑入口（editOnSale）

**用户故事：** 作为运营人员，我希望编辑在售商品时系统自动创建草稿副本（如果尚不存在），以便我可以安全地修改商品信息而不影响买家的购买体验。

#### 验收标准

1. THE CommodityService SHALL 提供 `editOnSale(spuId: SpuId): Result<Spu, BusinessError>` 方法，用于获取在售商品的可编辑草稿副本
2. WHEN 调用 editOnSale 且指定 SPU 已存在草稿副本时，THE CommodityService SHALL 直接返回已有的草稿副本，不创建新的
3. WHEN 调用 editOnSale 且指定 SPU 不存在草稿副本时，THE CommodityService SHALL 通过 SpuFactory 创建草稿副本，持久化后返回
4. IF 指定 SPU 的状态不是 ON_SALE，THEN THE CommodityService SHALL 返回错误，提示只有在售商品需要通过草稿编辑
5. WHILE 在售商品存在草稿副本期间，THE 在售商品 SHALL 保持 ON_SALE 状态，可正常被买家购买

### 需求 5：在售商品直接编辑拦截

**用户故事：** 作为系统开发者，我希望系统拒绝对在售商品的直接编辑请求，以便强制运营人员通过草稿流程修改在售商品，保护在售数据的一致性。

#### 验收标准

1. WHEN CommodityService.createOrUpdate 接收到针对 ON_SALE 状态商品的更新请求时，THE CommodityService SHALL 返回错误，提示在售商品需通过 editOnSale 流程编辑
2. WHEN CommodityService.createOrUpdate 接收到针对 DRAFT 或 OFF_SALE 状态商品的更新请求时，THE CommodityService SHALL 正常执行更新操作

### 需求 6：草稿发布（publishDraft）

**用户故事：** 作为运营人员，我希望将编辑完成的草稿发布到在售商品上，以便买家能看到最新的商品信息，同时系统自动递增版本号并生成新快照。

#### 验收标准

1. THE CommodityService SHALL 提供 `publishDraft(draftSpuId: SpuId): Result<SpuSnapshot, BusinessError>` 方法，用于将草稿内容合并回源商品
2. WHEN publishDraft 执行时，THE CommodityService SHALL 将草稿副本的 name、description 和 SKU 列表覆盖到源商品上
3. WHEN publishDraft 执行时，THE CommodityService SHALL 递增源商品的 version 并创建新的 SpuSnapshot
4. WHEN publishDraft 执行时，THE CommodityService SHALL 保持源商品的状态为 ON_SALE（不改变原有状态）
5. WHEN publishDraft 执行成功后，THE CommodityService SHALL 删除草稿副本
6. IF draftSpuId 对应的 SPU 不是草稿副本（sourceSpuId 为 null），THEN THE CommodityService SHALL 返回错误
7. IF 草稿副本的 SKU 列表为空，THEN THE CommodityService SHALL 返回错误，提示至少需要一个 SKU

### 需求 7：草稿丢弃（discardDraft）

**用户故事：** 作为运营人员，我希望能够丢弃未完成的草稿，以便在不需要修改时放弃编辑，且不影响在售商品。

#### 验收标准

1. THE CommodityService SHALL 提供 `discardDraft(draftSpuId: SpuId): Result<Unit, BusinessError>` 方法，用于删除草稿副本
2. WHEN discardDraft 执行时，THE CommodityService SHALL 删除草稿副本，不修改源商品的任何数据
3. IF draftSpuId 对应的 SPU 不是草稿副本（sourceSpuId 为 null），THEN THE CommodityService SHALL 返回错误
4. WHEN discardDraft 执行成功后，THE 源商品 SHALL 保持原有状态和数据不变

### 需求 8：下架商品直接编辑（不走草稿流程）

**用户故事：** 作为运营人员，我希望下架商品（OFF_SALE）可以直接编辑，无需创建草稿，以便简化非在售商品的编辑流程。

#### 验收标准

1. WHEN CommodityService.createOrUpdate 接收到针对 OFF_SALE 状态商品的更新请求时，THE CommodityService SHALL 直接执行更新操作
2. WHEN 下架商品被编辑后重新上架（putOnSale）时，THE Spu SHALL 自动递增 version 并触发快照创建（沿用现有 putOnSale 逻辑）

### 需求 9：Spu 聚合根支持草稿发布合并

**用户故事：** 作为系统开发者，我希望 Spu 聚合根提供合并草稿内容的领域方法，以便 CommodityService 在发布草稿时能够通过聚合根的行为方法完成数据合并，遵循 DDD 原则。

#### 验收标准

1. THE Spu 接口 SHALL 提供 `mergeFromDraft(draft: Spu): Result<Unit, BusinessError>` 方法，用于将草稿副本的内容合并到当前 SPU
2. WHEN mergeFromDraft 执行时，THE Spu SHALL 用草稿的 name、description 和 SKU 列表覆盖自身对应字段
3. WHEN mergeFromDraft 执行时，THE Spu SHALL 递增自身的 version
4. WHEN mergeFromDraft 执行时，THE Spu SHALL 保持自身的 status 不变
5. IF 当前 SPU 的状态不是 ON_SALE，THEN THE Spu SHALL 返回错误
6. IF 草稿的 SKU 列表为空，THEN THE Spu SHALL 返回错误

### 需求 10：订单创建命令携带快照版本号

**用户故事：** 作为买家，我希望下单时系统校验我看到的商品信息是否为最新版本，以便避免基于过期信息（如旧价格、旧规格）下单。

#### 验收标准

1. THE OrderCreateCMD.OrderItemCMD SHALL 包含 `snapshotVersion: Long` 字段，记录买家下单时看到的商品快照版本号
2. WHEN OrderFactory 创建订单时，THE OrderFactory SHALL 将每个 OrderItemCMD 中的 snapshotVersion 与 GoodsService 返回的最新 snapshotVersion 进行比较
3. IF 某个商品的 OrderItemCMD.snapshotVersion 与 GoodsService 返回的最新 snapshotVersion 不一致，THEN THE OrderFactory SHALL 返回错误，提示商品信息已变更，买家需刷新页面
4. THE 快照版本校验 SHALL 以 SPU 为粒度进行，同一 SPU 下的所有 SKU 共享同一个 snapshotVersion

### 需求 11：订单快照版本不匹配错误类型

**用户故事：** 作为前端开发者，我希望快照版本不匹配时系统返回明确的错误码和提示信息，以便前端能够识别该错误并引导买家刷新页面。

#### 验收标准

1. THE OrderErrors SHALL 定义 `SNAPSHOT_VERSION_MISMATCH` 错误常量，包含明确的错误信息、错误码和 HTTP 状态码 409（Conflict）
2. WHEN 快照版本不匹配时，THE 错误信息 SHALL 包含不匹配的商品 SPU ID，便于前端定位具体商品
3. THE 错误码 SHALL 遵循现有命名规范（如 `Order.Snapshot.VersionMismatch`）

### 需求 12：商品模块草稿相关错误类型

**用户故事：** 作为系统开发者，我希望商品模块定义草稿流程相关的错误常量，以便在草稿创建、发布、丢弃等操作中返回明确的错误信息。

#### 验收标准

1. THE CommodityErrors SHALL 定义 `DRAFT_ALREADY_EXISTS` 错误常量，用于同一在售商品重复创建草稿时返回
2. THE CommodityErrors SHALL 定义 `ON_SALE_DIRECT_EDIT_REJECTED` 错误常量，用于拒绝在售商品直接编辑时返回
3. THE CommodityErrors SHALL 定义 `NOT_A_DRAFT_COPY` 错误常量，用于对非草稿副本执行发布或丢弃操作时返回
4. THE CommodityErrors SHALL 定义 `ONLY_ON_SALE_NEEDS_DRAFT` 错误常量，用于对非在售商品调用 editOnSale 时返回
5. THE CommodityErrors SHALL 定义 `DRAFT_NO_SKU_FOR_PUBLISH` 错误常量，用于草稿 SKU 列表为空时拒绝发布

### 需求 13：草稿副本持久化层支持

**用户故事：** 作为系统开发者，我希望持久化层能够存储和查询草稿副本的 sourceSpuId 字段，以便草稿与源商品的关联关系能够正确持久化和恢复。

#### 验收标准

1. THE SpuPO SHALL 包含 `source_spu_id` 列（BIGINT 类型，可为 null），用于持久化 sourceSpuId 字段
2. WHEN SpuRepositoryImpl 将 Spu 转换为 SpuPO 时，THE Converter SHALL 将 sourceSpuId 映射到 source_spu_id 列
3. WHEN SpuRepositoryImpl 将 SpuPO 转换为 Spu 时，THE Converter SHALL 将 source_spu_id 列映射回 sourceSpuId 字段
4. THE SpuPOJpaRepository SHALL 提供按 source_spu_id 和 status 查询的方法，用于支持 `findDraftBySourceSpuId` 的实现
5. THE 数据库迁移脚本 SHALL 为 spu 表新增 source_spu_id 列，默认值为 null（兼容历史数据）

### 需求 14：草稿副本删除支持

**用户故事：** 作为系统开发者，我希望 SpuRepository 支持删除草稿副本，以便在草稿发布或丢弃后能够清理草稿数据。

#### 验收标准

1. THE SpuRepository SHALL 提供 `delete(spu: Spu)` 方法，用于删除指定的 SPU 记录
2. WHEN delete 执行时，THE SpuRepository SHALL 同时删除该 SPU 关联的所有 SKU 记录
3. THE delete 方法 SHALL 仅用于删除草稿副本（sourceSpuId 非 null 的 SPU），应用层负责确保不会误删原始商品

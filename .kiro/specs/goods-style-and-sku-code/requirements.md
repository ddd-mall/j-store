# 需求文档：商品展示样式（GoodsStyle）与 SKU 编码增强

## 简介

当前 j-store 商品模块的 SPU 聚合仅包含基础商品信息（名称、描述、SKU 列表），缺少前端展示所需的图片和富文本详情内容。同时 SKU 实体缺少商家内部货号和标准条形码等编码字段，无法满足商品管理和供应链对接的需求。

本特性包含两部分增强：
1. 引入独立的 `GoodsStyle` 实体，统一管理 SPU 主图、详情页富文本 HTML 以及各 SKU 的图片资源，避免 SPU 聚合过重
2. 为 SKU 补充商家内部货号和标准条形码两个编码字段

图片资源仅存储图片标识（key），未来通过 OSS 存储服务动态获取访问链接。本次在 ACL 层定义 OSS 服务接口，不做具体实现。

## 术语表

- **Goods_Style**: 商品展示样式实体，独立于 SPU 聚合，管理 SPU 的前端展示内容（主图、详情、SKU 图片）
- **Goods_Style_Id**: Goods_Style 的唯一标识，Long 类型
- **Spu_Id**: SPU 的唯一标识，Goods_Style 通过 Spu_Id 关联到对应的 SPU
- **Image_Key**: 图片资源标识字符串，对应 OSS 存储中的对象 key，客户端通过 OSS_Service 获取可访问的 URL
- **Main_Images**: SPU 主图列表，有序的 Image_Key 集合，用于商品列表页和详情页头部展示
- **Detail_Html**: SPU 详情页富文本 HTML 内容，用于商品详情页的图文描述区域
- **Sku_Images**: SKU 图片映射，以 Sku_Id 为 key、Image_Key 列表为 value 的映射结构，统一在 Goods_Style 中管理
- **Sku_Id**: SKU 的唯一标识，Long 类型
- **OSS_Service**: 对象存储服务的 ACL 接口，负责根据 Image_Key 生成可访问的 URL（本次仅定义接口，不实现）
- **Merchant_Code**: 商家内部货号，商家自定义的 SKU 编码字符串，用于商家内部管理
- **Barcode**: 标准条形码，遵循 EAN/UPC 规范的 SKU 条形码字符串，用于供应链和零售终端扫码
- **Goods_Style_Repository**: Goods_Style 的仓储接口，提供持久化和查询能力
- **Goods_Style_Factory**: Goods_Style 的工厂，负责创建 Goods_Style 实例

## 需求

### 需求 1：GoodsStyle 实体建模

**用户故事：** 作为商品运营人员，我希望能够为商品配置展示样式（主图、详情、SKU 图片），以便前端能够展示丰富的商品信息。

#### 验收标准

1. THE Goods_Style SHALL 作为独立实体实现 Entity 接口，拥有唯一的 Goods_Style_Id 标识
2. THE Goods_Style SHALL 通过 Spu_Id 关联到对应的 SPU，一个 SPU 对应一个 Goods_Style
3. THE Goods_Style SHALL 包含 Main_Images 属性，类型为有序的 Image_Key 列表，用于存储 SPU 主图
4. THE Goods_Style SHALL 包含 Detail_Html 属性，类型为字符串，用于存储 SPU 详情页富文本 HTML 内容
5. THE Goods_Style SHALL 包含 Sku_Images 属性，类型为 Map<Sku_Id, List<Image_Key>>，用于统一管理各 SKU 的图片
6. THE Goods_Style SHALL 放置在 `j-store-goods` 模块的 `domain/commodity/` 包下，不依赖任何 Spring 或 JPA 框架

### 需求 2：GoodsStyle 主图管理

**用户故事：** 作为商品运营人员，我希望能够设置和更新商品的主图列表，以便控制商品在列表页和详情页头部的图片展示。

#### 验收标准

1. WHEN 运营人员提供 Main_Images 列表时，THE Goods_Style SHALL 接受并存储该有序列表
2. THE Goods_Style SHALL 允许 Main_Images 为空列表
3. THE Goods_Style SHALL 保持 Main_Images 中 Image_Key 的插入顺序
4. IF Main_Images 列表中包含重复的 Image_Key，THEN THE Goods_Style SHALL 返回描述性错误

### 需求 3：GoodsStyle 详情页内容管理

**用户故事：** 作为商品运营人员，我希望能够编辑商品的详情页富文本内容，以便在详情页展示图文并茂的商品描述。

#### 验收标准

1. WHEN 运营人员提供 Detail_Html 内容时，THE Goods_Style SHALL 接受并存储该 HTML 字符串
2. THE Goods_Style SHALL 允许 Detail_Html 为空字符串

### 需求 4：GoodsStyle SKU 图片管理

**用户故事：** 作为商品运营人员，我希望能够为每个 SKU 配置独立的图片列表，以便前端在用户选择不同规格时展示对应的 SKU 图片。

#### 验收标准

1. WHEN 运营人员为指定 Sku_Id 提供图片列表时，THE Goods_Style SHALL 在 Sku_Images 映射中存储该 Sku_Id 对应的 Image_Key 列表
2. THE Goods_Style SHALL 允许 Sku_Images 为空映射
3. THE Goods_Style SHALL 保持每个 Sku_Id 对应的 Image_Key 列表的插入顺序
4. IF 同一 Sku_Id 的图片列表中包含重复的 Image_Key，THEN THE Goods_Style SHALL 返回描述性错误

### 需求 5：GoodsStyle 持久化

**用户故事：** 作为开发人员，我希望 GoodsStyle 能够被持久化到数据库，以便商品展示信息能够可靠存储和查询。

#### 验收标准

1. THE Goods_Style_Repository SHALL 定义在 `j-store-goods` 模块的 `domain/commodity/` 包下，继承 Repository 基础接口
2. THE Goods_Style_Repository SHALL 提供通过 Spu_Id 查询 Goods_Style 的方法
3. THE Goods_Style_Repository 的实现 SHALL 放置在 `j-store-goods-infrastructure` 模块中
4. THE 基础设施层 SHALL 创建 `goods_style` 数据库表，包含 id、spu_id、main_images（JSONB）、detail_html（TEXT）、sku_images（JSONB）、create_time、update_time 字段
5. THE 基础设施层 SHALL 在 spu_id 列上创建唯一索引，确保一个 SPU 只对应一个 Goods_Style
6. THE 基础设施层 SHALL 提供 GoodsStylePO 持久化对象和 Converter，实现 Goods_Style 领域对象与 GoodsStylePO 之间的双向转换
7. FOR ALL 有效的 Goods_Style 对象，保存后再通过 Spu_Id 查询 SHALL 产生与原始对象等价的对象（round-trip 属性）

### 需求 6：GoodsStyle 工厂与应用服务

**用户故事：** 作为开发人员，我希望通过工厂创建 GoodsStyle 实例，并通过应用服务编排 GoodsStyle 的创建和更新操作。

#### 验收标准

1. THE Goods_Style_Factory SHALL 提供创建 Goods_Style 实例的方法，接受 Spu_Id、Main_Images、Detail_Html、Sku_Images 参数
2. THE Goods_Style_Factory SHALL 使用 SnowFlakSequence 生成 Goods_Style_Id
3. THE CommodityService SHALL 提供保存或更新 Goods_Style 的方法
4. WHEN 调用保存 Goods_Style 方法时，THE CommodityService SHALL 验证对应的 SPU 存在，不存在则返回错误
5. WHEN 对应 Spu_Id 已存在 Goods_Style 时，THE CommodityService SHALL 执行更新操作而非创建新记录

### 需求 7：OSS 服务 ACL 接口定义

**用户故事：** 作为开发人员，我希望在商品模块的 ACL 层定义 OSS 服务接口，以便未来引入 OSS 存储服务时有明确的集成边界。

#### 验收标准

1. THE OSS_Service 接口 SHALL 定义在 `j-store-goods` 模块的 `acl/` 包下
2. THE OSS_Service SHALL 提供根据单个 Image_Key 生成可访问 URL 的方法签名
3. THE OSS_Service SHALL 提供根据 Image_Key 列表批量生成可访问 URL 的方法签名
4. THE OSS_Service 接口 SHALL 仅包含方法签名定义，不提供实现
5. THE OSS_Service 接口 SHALL 不依赖任何 Spring 或 JPA 框架

### 需求 8：SKU 商家内部货号

**用户故事：** 作为商家，我希望能够为每个 SKU 设置内部货号，以便在商家内部管理系统中标识和追踪商品。

#### 验收标准

1. THE Sku 实体 SHALL 包含 Merchant_Code 属性，类型为可空字符串
2. THE Merchant_Code SHALL 允许为 null，表示商家未设置内部货号
3. WHEN 创建 SKU 时，THE SkuCreateCmd SHALL 支持传入可选的 Merchant_Code 参数
4. THE sku 数据库表 SHALL 新增 merchant_code 列，类型为 VARCHAR(128)，允许为空
5. THE SkuPO SHALL 新增 merchant_code 字段，并在 Converter 中实现与领域对象的双向映射

### 需求 9：SKU 标准条形码

**用户故事：** 作为商家，我希望能够为每个 SKU 设置标准条形码（EAN/UPC），以便在供应链和零售终端中通过扫码识别商品。

#### 验收标准

1. THE Sku 实体 SHALL 包含 Barcode 属性，类型为可空字符串
2. THE Barcode SHALL 允许为 null，表示商家未设置条形码
3. WHEN 创建 SKU 时，THE SkuCreateCmd SHALL 支持传入可选的 Barcode 参数
4. THE sku 数据库表 SHALL 新增 barcode 列，类型为 VARCHAR(64)，允许为空
5. THE SkuPO SHALL 新增 barcode 字段，并在 Converter 中实现与领域对象的双向映射

### 需求 10：SKU 快照包含编码字段

**用户故事：** 作为系统使用者，我希望商品快照中包含 SKU 的编码信息，以便订单历史中能够追溯 SKU 的货号和条形码。

#### 验收标准

1. THE SkuSnapshot SHALL 包含 Merchant_Code 属性，类型为可空字符串
2. THE SkuSnapshot SHALL 包含 Barcode 属性，类型为可空字符串
3. WHEN 创建 SPU 快照时，THE SpuSnapshotFactory SHALL 将 SKU 的 Merchant_Code 和 Barcode 写入 SkuSnapshot
4. THE SpuSnapshotRepositoryImpl 的 Converter SHALL 在序列化和反序列化 SkuSnapshot 时包含 Merchant_Code 和 Barcode 字段

### 需求 11：模块归属与架构合规

**用户故事：** 作为开发人员，我希望所有新增代码按照项目 DDD 架构规范放置在正确的模块和包中，以便保持架构一致性。

#### 验收标准

1. THE Goods_Style 实体、Goods_Style_Id、Goods_Style_Repository 接口和 Goods_Style_Factory SHALL 放置在 `j-store-goods` 模块的 `domain/commodity/` 包下
2. THE GoodsStylePO、GoodsStylePOJpaRepository 和 GoodsStyleRepositoryImpl SHALL 放置在 `j-store-goods-infrastructure` 模块中
3. THE OSS_Service 接口 SHALL 放置在 `j-store-goods` 模块的 `acl/` 包下
4. THE `domain/commodity/` 包下的所有新增代码 SHALL 不引入 Spring、JPA 或其他基础设施框架的依赖
5. THE 数据库迁移脚本 SHALL 放置在 `docker/postgres/init/` 目录下，遵循现有的编号命名规范

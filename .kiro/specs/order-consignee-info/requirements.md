# 需求文档：订单收货人信息独立存储

## 简介

当前 j-store 订单系统的 Order 聚合根直接持有 `shippingAddress`（I18nGeoAddress）和 `shippingDetailAddress` 字段，但缺少收货人姓名和联系方式的独立字段。收货人信息（姓名、联系方式）与配送地址信息在业务上属于同一个"收货信息"概念，应当作为一个完整的值对象（ShippingInfo）统一管理。

用户已完成 CMD 层改造（创建了 ConsigneeInfoCMD）和 ShippingInfo 值对象定义。本需求覆盖将 ShippingInfo 值对象集成到 Order 聚合根、OrderFactory、持久化层（OrderPO/数据库）以及应用服务的完整链路改造。

持久化策略：OrderPO 中所有收货相关信息（consigneeName、consigneePhone、consigneeEmail、countryCode、districtCode、shippingAddress、detailAddress）统一存储在一个 `consignee_info` 的 jsonb 列中，替代当前分散的多个独立列。

## 术语表

- **Order_Aggregate**：订单聚合根，封装订单的完整生命周期状态和行为
- **ShippingInfo**：收货信息值对象，包含收货人姓名（consigneeName）、收货人联系方式（consigneeContractInfo: ContractInfo）、收货地址（shippingAddress: I18nGeoAddress）、详细收货地址（shippingDetailAddress）
- **ContractInfo**：联系方式值对象，包含邮箱（email）和手机号（phoneNumber: PhoneNumber），至少提供其一
- **ConsigneeInfoCMD**：创建订单命令中的收货人信息子命令，包含 consigneeName、countryCode、consigneeContractInfo、shippingDistrictCode、shippingDetailAddress
- **OrderFactory**：订单工厂，负责组装合法初始状态的 Order 聚合根
- **OrderPO**：订单持久化对象，对应数据库 orders 表
- **ConsigneeInfoPO**：收货人信息持久化数据结构，作为 `consignee_info` jsonb 列的序列化/反序列化目标，包含 consigneeName、consigneePhone、consigneeEmail、countryCode、districtCode、shippingAddress（I18nGeoAddress JSON）、detailAddress
- **GeoAddressService**：地理地址防腐层服务，根据 countryCode 和 districtCode 查询 I18nGeoAddress
- **I18nGeoAddress**：国际化地理地址值对象，包含 countryCode 和多层级行政区划组件

## 需求

### 需求 1：Order 聚合根引入 ShippingInfo 值对象

**用户故事：** 作为系统开发者，我希望 Order 聚合根使用 ShippingInfo 值对象替代当前分散的 shippingAddress 和 shippingDetailAddress 字段，以便收货人信息作为一个完整的业务概念被统一管理。

#### 验收标准

1. THE Order_Aggregate SHALL 持有一个 ShippingInfo 类型的 `shippingInfo` 属性，替代当前的 `shippingAddress: I18nGeoAddress` 和 `shippingDetailAddress: String?` 两个独立属性
2. THE Order_Aggregate SHALL 通过 ShippingInfo 值对象提供对收货人姓名（consigneeName）、收货人联系方式（consigneeContractInfo）、收货地址（shippingAddress）和详细收货地址（shippingDetailAddress）的访问
3. THE ShippingInfo SHALL 为不可变值对象，所有属性使用 `val` 声明

### 需求 2：ConsigneeInfoCMD 到 ShippingInfo 的转换

**用户故事：** 作为系统开发者，我希望 OrderFactory 能够将 ConsigneeInfoCMD 中的收货人信息与 GeoAddressService 查询到的地址信息组合为 ShippingInfo 值对象，以便创建订单时正确构建收货信息。

#### 验收标准

1. WHEN 创建订单时，THE OrderFactory SHALL 从 ConsigneeInfoCMD 中提取 countryCode 和 shippingDistrictCode，通过 GeoAddressService 查询 I18nGeoAddress
2. WHEN ConsigneeInfoCMD 中的 countryCode 为 null 时，THE OrderFactory SHALL 使用默认值 "CN" 进行地址查询
3. THE OrderFactory SHALL 将 ConsigneeInfoCMD 中的 consigneeName、consigneeContractInfo 与查询到的 I18nGeoAddress 和 shippingDetailAddress 组合为 ShippingInfo 值对象
4. THE OrderFactory SHALL 将 ContractInfoCMD 转换为领域层的 ContractInfo 值对象，映射 phoneNumber 和 emailAddress 字段
5. IF GeoAddressService 查询地址失败，THEN THE OrderFactory SHALL 返回包含地址查询错误信息的 Failure 结果

### 需求 3：ConsigneeInfoCMD 验证规则

**用户故事：** 作为系统开发者，我希望 ConsigneeInfoCMD 在订单创建前完成自身验证，以便尽早拒绝不合法的收货人信息。

#### 验收标准

1. WHEN ConsigneeInfoCMD 的 consigneeName 为空白字符串时，THE ConsigneeInfoCMD SHALL 返回验证失败的 Failure 结果
2. WHEN ConsigneeInfoCMD 的 shippingDistrictCode 为空白字符串时，THE ConsigneeInfoCMD SHALL 返回验证失败的 Failure 结果
3. WHEN ConsigneeInfoCMD 的 consigneeContractInfo 中 phoneNumber 和 emailAddress 均为 null 时，THE ConsigneeInfoCMD SHALL 返回验证失败的 Failure 结果
4. THE OrderCreateCMD 的 validate 方法 SHALL 调用 ConsigneeInfoCMD 的 validate 方法，并在验证失败时传播错误

### 需求 4：持久化层支持收货人信息（consignee_info jsonb 合并存储）

**用户故事：** 作为系统开发者，我希望 OrderPO 使用单个 `consignee_info` jsonb 列统一存储所有收货相关信息（收货人姓名、联系方式、国家编码、行政区划编码、国际化地址、详细地址），替代当前分散的多个独立列，以便收货信息在持久化层也作为一个整体管理。

#### 验收标准

1. THE OrderPO SHALL 新增一个 `consigneeInfo` 属性，对应数据库 `consignee_info` 列（columnDefinition = "jsonb"），使用 JPA AttributeConverter 进行序列化和反序列化
2. THE `consignee_info` jsonb 列 SHALL 包含以下字段：consigneeName（收货人姓名）、consigneePhone（收货人手机号）、consigneeEmail（收货人邮箱）、countryCode（国家编码）、districtCode（行政区划叶子编码）、shippingAddress（I18nGeoAddress 的 JSON 表示）、detailAddress（详细地址）
3. THE OrderPO SHALL 移除当前独立的 `countryCode`、`districtCode`、`shippingAddress`（jsonb）、`detailAddress` 四个列，这些信息统一由 `consignee_info` jsonb 列承载
4. WHEN 将 Order 聚合根转换为 OrderPO 时，THE OrderRepositoryImpl 的 Converter SHALL 从 ShippingInfo 中提取 consigneeName、consigneeContractInfo（phoneNumber、email）、shippingAddress（含 countryCode 和 leafCode 作为 districtCode）、shippingDetailAddress，组装为 ConsigneeInfoPO 并序列化到 `consignee_info` jsonb 列
5. WHEN 将 OrderPO 还原为 Order 聚合根时，THE OrderRepositoryImpl 的 Converter SHALL 从 `consignee_info` jsonb 列反序列化 ConsigneeInfoPO，重建 ShippingInfo 值对象（包含 consigneeName、ContractInfo、I18nGeoAddress、detailAddress）
6. THE ConsigneeInfoPO SHALL 为基础设施层的数据结构（非领域对象），仅用于 jsonb 序列化/反序列化

### 需求 5：数据库迁移脚本

**用户故事：** 作为系统运维人员，我希望有数据库迁移脚本将 orders 表的分散收货信息列合并为单个 `consignee_info` jsonb 列，以便系统平滑升级。

#### 验收标准

1. THE 迁移脚本 SHALL 为 orders 表添加 `consignee_info`（jsonb）列
2. THE 迁移脚本 SHALL 将现有 `country_code`、`district_code`、`shipping_address`（jsonb）、`detail_address` 列的数据迁移到 `consignee_info` jsonb 列中，其中 consigneeName 默认为空字符串、consigneePhone 和 consigneeEmail 默认为 null
3. THE 迁移脚本 SHALL 在数据迁移完成后，删除 `country_code`、`district_code`、`shipping_address`、`detail_address` 四个旧列
4. THE 迁移脚本 SHALL 使用 `IF NOT EXISTS` 语义添加列，使用 `IF EXISTS` 语义删除列，保证脚本可重复执行
5. THE 迁移脚本 SHALL 为 `consignee_info` 列创建 GIN 索引，支持 jsonb 查询

### 需求 6：历史数据向后兼容

**用户故事：** 作为系统开发者，我希望从数据库加载历史订单数据时，即使 `consignee_info` jsonb 中缺少收货人姓名和联系方式字段，系统也能正常还原 Order 聚合根，以便系统平滑升级不影响已有数据。

#### 验收标准

1. WHEN 从 `consignee_info` jsonb 反序列化时 consigneeName 字段缺失或为 null，THE Converter SHALL 使用空字符串 "" 作为 ShippingInfo 的 consigneeName 默认值
2. WHEN 从 `consignee_info` jsonb 反序列化时 consigneePhone 和 consigneeEmail 字段均缺失或为 null，THE Converter SHALL 构建一个 phoneNumber 和 email 均为 null 的 ContractInfo
3. WHEN 从 `consignee_info` jsonb 反序列化时 shippingAddress 字段存在，THE Converter SHALL 正确还原 I18nGeoAddress 值对象（包含 countryCode 和 components）
4. THE Order_Aggregate SHALL 在所有业务流程中正常处理 ShippingInfo 中收货人信息为默认值的情况

### 需求 7：OrderCreateCMD 字段清理

**用户故事：** 作为系统开发者，我希望 OrderCreateCMD 中与收货信息相关的冗余字段被移除，以便命令对象的结构清晰且不存在歧义。

#### 验收标准

1. THE OrderCreateCMD SHALL 移除顶层的 `shippingDistrictCode` 字段，该信息已包含在 ConsigneeInfoCMD 中
2. THE OrderCreateCMD SHALL 移除顶层的 `countryCode` 字段，该信息已包含在 ConsigneeInfoCMD 中
3. THE OrderCreateCMD SHALL 移除顶层的 `shippingDetailAddress` 字段，该信息已包含在 ConsigneeInfoCMD 中
4. THE OrderFactory SHALL 从 ConsigneeInfoCMD 中读取 countryCode、shippingDistrictCode 和 shippingDetailAddress，替代从 OrderCreateCMD 顶层字段读取

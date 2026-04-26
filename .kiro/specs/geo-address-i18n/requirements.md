# 需求文档：i18n 多国家地理地址系统支持

## 简介

当前 j-store 电商系统的地理地址模块（`com.jstore.common.geo`）仅支持中国行政区划体系，`GeoAddressInfo` 值对象硬编码了 `province`、`city`、`county` 字段，`DistrictLevel` 枚举也仅包含中国特有的省/市/区层级。本需求旨在将地址模块泛化为支持多国家的 i18n 地址系统，使系统能够表达不同国家的行政区划层级、支持多语言地址名称、按国家惯例格式化地址，并保持与现有订单聚合根及持久化层的向后兼容。

## 术语表

- **GeoAddress_System**：j-store 中负责地理地址管理的通用模块，位于 `com.jstore.common.geo` 包
- **Country_Code**：ISO 3166-1 alpha-2 标准的两位字母国家编码（如 `CN`、`US`、`JP`、`SG`）
- **Administrative_Division**：一个国家内的行政区划层级（如中国的省/市/区、美国的 State/County/City）
- **Division_Level**：行政区划在层级树中的深度，Level 0 为国家级，Level 1 为最高行政区划（如省/州），依次递增
- **Address_Component**：地址中的一个行政区划节点，包含编码、各语言名称和层级信息
- **Locale**：语言区域标识，遵循 IETF BCP 47 标准（如 `zh-CN`、`en-US`、`ja-JP`）
- **Address_Template**：特定国家的地址格式化模板，定义各层级的排列顺序和分隔符
- **Country_Address_Provider**：针对特定国家的地址数据加载与查询实现
- **GeoAddressInfo**：当前系统中的地址值对象，包含 `districtCode`、`province`、`city`、`county`、`detailAddress` 字段
- **Order_Aggregate**：订单聚合根，其 `shippingAddress` 字段引用 `GeoAddressInfo`
- **Address_Code**：各国行政区划的唯一标识编码（如中国6位数字编码、美国 FIPS 编码）

## 需求

### 需求 1：通用地址模型

**用户故事：** 作为系统开发者，我希望有一个通用的地址数据模型能够表达任意国家的行政区划结构，以便系统不再局限于中国地址格式。

#### 验收标准

1. THE GeoAddress_System SHALL 提供一个通用的地址值对象，包含 Country_Code、Address_Component 有序列表和可选的详细地址字段
2. WHEN 创建地址值对象时，THE GeoAddress_System SHALL 要求 Country_Code 为合法的 ISO 3166-1 alpha-2 编码
3. THE Address_Component SHALL 包含以下属性：编码（code）、Division_Level、以及一个 Locale 到名称的映射（Map<Locale, String>）
4. WHEN 地址值对象中的 Address_Component 列表为空时，THE GeoAddress_System SHALL 返回验证失败的 BusinessError
5. THE GeoAddress_System SHALL 保证地址值对象的不可变性，所有属性使用 `val` 声明且 Address_Component 列表为不可变列表

### 需求 2：国家特定行政区划层级定义

**用户故事：** 作为系统开发者，我希望每个国家能够定义自己的行政区划层级体系，以便正确表达不同国家的地址结构差异。

#### 验收标准

1. THE GeoAddress_System SHALL 为每个支持的国家定义独立的行政区划层级配置，包含层级数量和各层级名称
2. WHEN 查询中国地址时，THE GeoAddress_System SHALL 支持三级行政区划层级：省（Level 1）、市（Level 2）、区/县（Level 3）
3. WHEN 查询美国地址时，THE GeoAddress_System SHALL 支持两级行政区划层级：State（Level 1）、City（Level 2）
4. WHEN 查询日本地址时，THE GeoAddress_System SHALL 支持三级行政区划层级：都道府県（Level 1）、市区町村（Level 2）、町域（Level 3）
5. WHEN 查询新加坡地址时，THE GeoAddress_System SHALL 支持一级行政区划层级：Planning Area（Level 1）
6. IF 请求的 Country_Code 未在系统中注册，THEN THE GeoAddress_System SHALL 返回包含错误码 `Address.Country.Unsupported` 的 BusinessError

### 需求 3：多语言地址名称支持

**用户故事：** 作为系统用户，我希望地址名称能够根据我的语言偏好显示，以便我能用自己熟悉的语言阅读地址信息。

#### 验收标准

1. THE Address_Component SHALL 存储至少一种 Locale 的名称
2. WHEN 请求特定 Locale 的地址名称时，THE GeoAddress_System SHALL 返回该 Locale 对应的名称
3. IF 请求的 Locale 在 Address_Component 中不存在，THEN THE GeoAddress_System SHALL 回退到该 Address_Component 的默认 Locale 名称
4. THE GeoAddress_System SHALL 为每个 Address_Component 指定一个默认 Locale，该默认 Locale 对应该国家的官方语言（如中国为 `zh-CN`，美国为 `en-US`）
5. WHEN 中国地址的 Address_Component 包含 `zh-CN` 和 `en-US` 两种 Locale 时，THE GeoAddress_System SHALL 能够分别返回 "北京市" 和 "Beijing" 作为名称

### 需求 4：国家特定地址格式化

**用户故事：** 作为系统用户，我希望地址按照各国惯例格式化显示，以便地址信息符合当地阅读习惯。

#### 验收标准

1. THE GeoAddress_System SHALL 为每个支持的国家提供 Address_Template 用于地址格式化
2. WHEN 格式化中国地址时，THE GeoAddress_System SHALL 按照从大到小的顺序排列（省 → 市 → 区/县 → 详细地址）
3. WHEN 格式化美国地址时，THE GeoAddress_System SHALL 按照从小到大的顺序排列（详细地址 → City → State）
4. WHEN 格式化日本地址时，THE GeoAddress_System SHALL 按照从大到小的顺序排列（都道府県 → 市区町村 → 町域 → 详细地址）
5. THE GeoAddress_System SHALL 支持在格式化时指定目标 Locale，使用对应语言的地址名称进行格式化
6. WHEN 格式化地址时传入的 Address_Component 列表为空，THE GeoAddress_System SHALL 返回空字符串

### 需求 5：国家特定地址编码验证

**用户故事：** 作为系统开发者，我希望系统能够按照各国规则验证地址编码的合法性，以便拒绝格式错误的地址编码。

#### 验收标准

1. WHEN 验证中国地址编码时，THE GeoAddress_System SHALL 校验编码为6位数字格式
2. WHEN 验证美国地址编码时，THE GeoAddress_System SHALL 校验 State 编码为2位字母的 USPS 州缩写格式
3. IF 地址编码不符合对应国家的格式规则，THEN THE GeoAddress_System SHALL 返回包含错误码 `Address.Code.Invalid` 和具体原因描述的 BusinessError
4. THE GeoAddress_System SHALL 将编码验证逻辑委托给对应国家的 Country_Address_Provider 实现

### 需求 6：GeoAddressService 接口演进与国家路由

**用户故事：** 作为系统开发者，我希望 GeoAddressService 接口能够支持按国家路由到不同的地址实现，以便新增国家支持时无需修改现有调用方代码。

#### 验收标准

1. THE GeoAddress_System SHALL 提供一个支持 Country_Code 参数的地址查询方法：`getByCode(countryCode: String, addressCode: String): Result<通用地址值对象, BusinessError>`
2. THE GeoAddress_System SHALL 保留现有的 `getByDistrictCode(districtCode: String)` 方法签名，该方法默认使用中国（`CN`）作为 Country_Code
3. WHEN 调用带 Country_Code 的查询方法时，THE GeoAddress_System SHALL 根据 Country_Code 路由到对应的 Country_Address_Provider 实现
4. IF 指定的 Country_Code 没有对应的 Country_Address_Provider 注册，THEN THE GeoAddress_System SHALL 返回包含错误码 `Address.Country.Unsupported` 的 BusinessError
5. THE GeoAddress_System SHALL 使用策略模式（Strategy Pattern）管理多个 Country_Address_Provider，通过 Country_Code 进行分发

### 需求 7：与现有订单聚合根的向后兼容

**用户故事：** 作为系统开发者，我希望地址模型的 i18n 改造不会破坏现有订单聚合根和持久化层的功能，以便系统平滑升级。

#### 验收标准

1. THE GeoAddress_System SHALL 保证新的通用地址值对象能够提供与现有 `GeoAddressInfo` 相同的 `districtCode`、`province`、`city`、`county`、`detailAddress` 访问方式
2. WHEN 从数据库加载现有订单数据时，THE Order_Aggregate SHALL 能够将已持久化的 `districtCode`、`province`、`city`、`county` 字段正确还原为新的通用地址值对象
3. WHEN 保存包含新通用地址值对象的订单时，THE Order_Aggregate SHALL 将地址数据持久化到现有的数据库字段中，同时额外存储 Country_Code
4. THE GeoAddress_System SHALL 对于缺少 Country_Code 的历史订单数据，默认将其视为中国地址（`CN`）
5. THE OrderCreateCMD SHALL 扩展为支持可选的 Country_Code 参数，缺省时默认为 `CN`
6. WHILE 系统处于向后兼容过渡期，THE GeoAddress_System SHALL 同时支持旧的 `GeoAddressInfo` 构造方式和新的通用地址值对象构造方式

### 需求 8：Country_Address_Provider 扩展机制

**用户故事：** 作为系统开发者，我希望新增国家地址支持时只需实现一个 Country_Address_Provider 并注册即可，以便系统具备良好的可扩展性。

#### 验收标准

1. THE GeoAddress_System SHALL 定义 Country_Address_Provider 接口，包含以下方法：查询地址、验证编码、获取行政区划层级配置、获取地址格式化模板
2. THE Country_Address_Provider 接口 SHALL 声明 `supportedCountryCode(): String` 方法，返回该 Provider 支持的 Country_Code
3. WHEN 系统启动时，THE GeoAddress_System SHALL 自动发现并注册所有 Country_Address_Provider 实现（通过 Spring 依赖注入）
4. IF 存在两个 Country_Address_Provider 声明支持相同的 Country_Code，THEN THE GeoAddress_System SHALL 在启动时抛出配置错误并记录日志
5. THE GeoAddress_System SHALL 提供中国（`CN`）的 Country_Address_Provider 默认实现，复用现有 `ChinaGeoAddressServiceExcelImpl` 的数据加载逻辑

### 需求 9：地址序列化与反序列化

**用户故事：** 作为系统开发者，我希望通用地址值对象能够正确地序列化为 JSON 和从 JSON 反序列化，以便 API 层和持久化层能够正确传输地址数据。

#### 验收标准

1. THE GeoAddress_System SHALL 支持将通用地址值对象序列化为 JSON 格式
2. THE GeoAddress_System SHALL 支持从 JSON 格式反序列化为通用地址值对象
3. FOR ALL 合法的通用地址值对象，序列化后再反序列化 SHALL 产生与原始对象等价的结果（round-trip 属性）
4. WHEN 序列化地址时，THE GeoAddress_System SHALL 在 JSON 中包含 Country_Code、Address_Component 列表（含多语言名称）和详细地址
5. IF 反序列化的 JSON 缺少必要字段（Country_Code 或 Address_Component），THEN THE GeoAddress_System SHALL 返回描述性的错误信息

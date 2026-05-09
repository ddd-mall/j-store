# 实施计划：i18n 多国家地理地址系统支持

## 概述

按自底向上的顺序实施：先在 common-core 中创建值对象和接口（无 Spring 依赖），再在 common-spring 中实现中国地址 Provider 和路由分发器，最后适配订单聚合根和持久化层。每个阶段配有属性测试和检查点，确保增量验证。

## 任务

- [x] 1. common-core 值对象与接口
  - [x] 1.1 创建 CountryCode 值对象
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/CountryCode.kt` 中创建 `data class CountryCode`
    - ISO 3166-1 alpha-2 验证（2位大写字母），`init` 块 `require`
    - 预定义常量 `CN`、`US`、`JP`、`SG`
    - _需求: 1.2_

  - [x] 1.2 编写 CountryCode 属性测试
    - **Property 2: CountryCode 验证**
    - 在 `j-store-common-core/src/test/kotlin/com/jstore/common/geo/CountryCodePropertyTest.kt` 中创建 Kotest FunSpec
    - 使用 `Arb.string` 生成随机字符串，验证仅2位大写字母构造成功，其余抛异常
    - **验证: 需求 1.2**

  - [x] 1.3 创建 DivisionLevel 和 DivisionLevelConfig 值对象
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/DivisionLevel.kt` 中创建
    - `DivisionLevel(depth: Int, name: String)`，`require(depth >= 0)`
    - `DivisionLevelConfig(countryCode: CountryCode, levels: List<DivisionLevel>)`，`require(levels.isNotEmpty())`
    - _需求: 2.1_

  - [x] 1.4 创建 AddressComponent 值对象
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/AddressComponent.kt` 中创建
    - 包含 `code`、`level: DivisionLevel`、`names: Map<Locale, String>`、`defaultLocale: Locale`
    - `init` 块验证 code 非空、names 非空、defaultLocale 存在于 names 中
    - 实现 `getName(locale)` 回退逻辑和 `getDefaultName()`
    - _需求: 1.3, 3.1, 3.2, 3.3, 3.4_

  - [x] 1.5 编写 AddressComponent Locale 回退属性测试
    - **Property 3: Locale 名称解析与回退**
    - 在 `j-store-common-core/src/test/kotlin/com/jstore/common/geo/AddressComponentPropertyTest.kt` 中创建
    - 自定义 `Arb.addressComponent()` 生成器
    - 验证存在的 Locale 返回对应名称，不存在的回退到 defaultLocale
    - **验证: 需求 3.2, 3.3**

  - [x] 1.6 创建 I18nGeoAddress 通用地址值对象
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/I18nGeoAddress.kt` 中创建
    - 包含 `countryCode`、`components: List<AddressComponent>`、`detailAddress: String?`
    - `init` 块验证 components 非空
    - 实现 `getComponentAtLevel(depth)`、`getLeafCode()`
    - 实现 `toLegacyGeoAddressInfo()` 和 `fromLegacyGeoAddressInfo()` 兼容方法（标记 `@Deprecated`）
    - _需求: 1.1, 1.4, 1.5, 7.1, 7.6_

  - [x] 1.7 编写 I18nGeoAddress 构造不变量属性测试
    - **Property 1: 值对象构造不变量**
    - 在 `j-store-common-core/src/test/kotlin/com/jstore/common/geo/I18nGeoAddressPropertyTest.kt` 中创建
    - 自定义 `Arb.i18nGeoAddress()` 生成器
    - 验证所有合法实例满足 countryCode 有效、components 非空、每个 component 的 code 非空且 names 非空且 defaultLocale 在 names 中
    - **验证: 需求 1.1, 1.3, 1.4, 3.1, 3.4**

  - [x] 1.8 编写旧地址模型往返转换属性测试
    - **Property 9: 旧地址模型往返转换**
    - 在同一测试文件 `I18nGeoAddressPropertyTest.kt` 中添加
    - 自定义 `Arb.geoAddressInfo()` 生成合法的中国地址
    - 验证 `fromLegacyGeoAddressInfo(info).toLegacyGeoAddressInfo()` 与原始 info 在关键字段上等价
    - **验证: 需求 7.1, 7.6**

  - [x] 1.9 创建 AddressTemplate 接口和 AddressFormatter 工具
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/AddressTemplate.kt` 中创建接口
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/AddressFormatter.kt` 中创建 `object AddressFormatter`
    - `AddressFormatter.format(address, template, locale)` 空组件返回空字符串
    - _需求: 4.1, 4.5, 4.6_

  - [x] 1.10 创建 CountryAddressProvider 接口
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/CountryAddressProvider.kt` 中创建
    - 方法：`supportedCountryCode()`、`getByCode()`、`validateCode()`、`getDivisionLevelConfig()`、`getAddressTemplate()`
    - _需求: 8.1, 8.2_

  - [x] 1.11 演进 GeoAddressService 接口
    - 在现有 `GeoAddressService.kt` 中新增 `getByCode(countryCode: String, addressCode: String): Result<I18nGeoAddress, BusinessError>` 方法
    - 保留旧 `getByDistrictCode` 方法签名不变
    - _需求: 6.1, 6.2_

  - [x] 1.12 扩展 AddressErrors 错误常量
    - 在现有 `AddressErrors.kt` 中新增 `InvalidCode`、`UnsupportedCountry`、`ComponentsEmpty` 错误常量
    - 保留现有 `IllegalAddressCode` 不变
    - _需求: 5.3, 2.6, 6.4_

- [x] 2. 检查点 — common-core 模块编译
  - 确保 `j-store-common-core` 模块编译通过，如有问题请向用户确认。

- [x] 3. common-spring 实现
  - [x] 3.1 创建 ChinaAddressTemplate
    - 在 `j-store-common-spring/src/main/kotlin/com/jstore/common/geo/ChinaAddressTemplate.kt` 中创建
    - 中国地址按 depth 升序排列（省→市→区/县→详细地址），无分隔符直接拼接
    - _需求: 4.2_

  - [x] 3.2 创建 ChinaAddressProvider
    - 在 `j-store-common-spring/src/main/kotlin/com/jstore/common/geo/ChinaAddressProvider.kt` 中创建
    - `@Component` 注解，实现 `CountryAddressProvider`
    - 复用 `ChinaGeoAddressServiceExcelImpl` 的 Excel 数据加载逻辑
    - `validateCode`：6位数字校验
    - `getDivisionLevelConfig`：省(1)/市(2)/区县(3) 三级
    - `getByCode`：先验证编码，再通过 excelService 查询并转换为 `I18nGeoAddress`
    - _需求: 5.1, 8.5, 2.2_

  - [x] 3.3 演进 GeoAddressServiceProxy
    - 修改 `GeoAddressServiceProxy` 构造函数接收 `List<CountryAddressProvider>`
    - `init` 块构建 `providerMap`，检测重复注册并 `require` 报错
    - 实现 `getByCode(countryCode, addressCode)` 路由逻辑
    - `getByDistrictCode` 委托到 `getByCode("CN", districtCode)` 并转换为 `GeoAddressInfo`
    - 标记旧 `ChinaGeoAddressServiceExcelImpl` 为 `@Deprecated`
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 8.3, 8.4_

  - [x] 3.4 编写地址格式化顺序属性测试
    - **Property 4: 国家特定地址格式化顺序**
    - 在 `j-store-common-spring/src/test/kotlin/com/jstore/common/geo/AddressFormatterPropertyTest.kt` 中创建
    - 验证中国地址格式化后行政区划按 depth 升序排列
    - **验证: 需求 4.2, 4.3, 4.4**

  - [x] 3.5 编写格式化 Locale 名称属性测试
    - **Property 5: 格式化使用指定 Locale 的名称**
    - 在同一测试文件 `AddressFormatterPropertyTest.kt` 中添加
    - 验证格式化结果包含每个 component 在指定 Locale 下的名称（或z回退名称）
    - **验证: 需求 4.5**

  - [x] 3.6 编写地址编码验证属性测试
    - **Property 6: 国家特定地址编码验证**
    - 在 `j-store-common-spring/src/test/kotlin/com/jstore/common/geo/AddressCodeValidationPropertyTest.kt` 中创建
    - 验证中国编码仅6位数字成功，其余失败并返回 `Address.Code.Invalid`
    - **验证: 需求 5.1, 5.2, 5.3**

  - [x] 3.7 编写不支持国家错误属性测试
    - **Property 7: 不支持的国家编码错误**
    - 在 `j-store-common-spring/src/test/kotlin/com/jstore/common/geo/GeoAddressServiceProxyPropertyTest.kt` 中创建
    - 验证未注册的 CountryCode 调用 `getByCode` 返回 `Address.Country.Unsupported` 错误
    - **验证: 需求 2.6, 6.4**

  - [x] 3.8 编写服务路由与向后兼容属性测试
    - **Property 8: 服务路由与向后兼容**
    - 在同一测试文件 `GeoAddressServiceProxyPropertyTest.kt` 中添加
    - 验证 `getByDistrictCode(code)` 等价于 `getByCode("CN", code).toLegacyGeoAddressInfo()`
    - **验证: 需求 6.2, 6.3**

- [x] 4. 检查点 — common 模块编译与测试
  - 确保 `j-store-common-core` 和 `j-store-common-spring` 模块编译通过且所有测试通过，如有问题请向用户确认。

- [x] 5. 订单聚合根适配
  - [x] 5.1 修改 Order 接口的 shippingAddress 类型
    - 将 `Order.kt` 中 `val shippingAddress: GeoAddressInfo` 改为 `val shippingAddress: I18nGeoAddress`
    - 更新 import 语句
    - _需求: 7.1_

  - [x] 5.2 修改 OrderImpl 的 shippingAddress 类型
    - 将 `OrderImpl.kt` 中构造参数和属性类型从 `GeoAddressInfo` 改为 `I18nGeoAddress`
    - 更新 import 语句
    - _需求: 7.1_

  - [x] 5.3 扩展 OrderCreateCMD
    - 在 `OrderCreateCMD` 中新增 `val countryCode: String? = null` 参数，缺省为 null（工厂中默认 CN）
    - _需求: 7.5_

  - [x] 5.4 修改 OrderFactory 使用新地址服务
    - 修改 `OrderFactoryImpl.create()` 中地址查询逻辑：
      - 根据 `cmd.countryCode` 决定调用 `getByCode(countryCode, districtCode)` 或 `getByDistrictCode(districtCode)`
      - 缺省 countryCode 时默认 "CN"
    - 将查询结果 `I18nGeoAddress` 直接赋给 `shippingAddress`
    - _需求: 7.5, 6.1_

- [x] 6. 持久化层适配
  - [x] 6.1 修改 OrderPO 新增 country_code 列
    - 在 `OrderPO` 中新增 `@Column(name = "country_code", nullable = false, length = 2) var countryCode: String = "CN"`
    - _需求: 7.3, 7.4_

  - [x] 6.2 修改 OrderRepositoryImpl Converter
    - `toPO`：从 `I18nGeoAddress` 提取 `countryCode.value`、`getLeafCode()`、各层级 `getDefaultName()` 写入 PO
    - `toDomain`：从 PO 的 `countryCode`、`districtCode`、`province`、`city`、`county`、`detailAddress` 还原 `I18nGeoAddress`
    - 历史数据 `countryCode` 默认 "CN"，使用 `Locale.SIMPLIFIED_CHINESE` 作为默认 Locale
    - _需求: 7.2, 7.3, 7.4_

  - [x] 6.3 创建数据库迁移脚本
    - 创建 SQL 迁移文件 `ALTER TABLE orders ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'CN'`
    - 放置在项目约定的迁移目录中
    - _需求: 7.3, 7.4_

- [x] 7. JSON 序列化支持
  - [x] 7.1 确保 I18nGeoAddress 的 Jackson 序列化/反序列化正确
    - 为 `I18nGeoAddress`、`AddressComponent`、`DivisionLevel`、`CountryCode` 添加必要的 Jackson 注解或自定义序列化器
    - 确保 `Locale` 键在 JSON 中正确序列化为字符串（如 `"zh-CN"`）
    - _需求: 9.1, 9.2, 9.4_

  - [x] 7.2 编写 JSON 序列化往返属性测试
    - **Property 10: JSON 序列化往返**
    - 在 `j-store-common-core/src/test/kotlin/com/jstore/common/geo/I18nGeoAddressSerializationPropertyTest.kt` 中创建
    - 自定义 `Arb.i18nGeoAddress()` 生成器
    - 验证序列化为 JSON 后再反序列化与原始对象等价
    - **验证: 需求 9.1, 9.2, 9.3, 9.4**

  - [x] 7.3 编写 JSON 反序列化缺失字段错误处理属性测试
    - **Property 11: JSON 反序列化缺失字段错误处理**
    - 在同一测试文件 `I18nGeoAddressSerializationPropertyTest.kt` 中添加
    - 验证缺少 `countryCode` 或 `components` 的 JSON 反序列化产生描述性错误
    - **验证: 需求 9.5**

- [x] 8. 最终检查点 — 全项目构建
  - 确保全项目 Gradle 构建通过（`./gradlew build`），所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的子任务为可选，可跳过以加速 MVP 交付
- 每个任务标注了对应的需求编号，确保可追溯性
- 检查点确保增量验证，避免问题累积
- 属性测试验证设计文档中定义的正确性属性，使用 Kotest Property Testing
- 单元测试验证具体示例和边界情况

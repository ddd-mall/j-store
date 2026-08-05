# 实施计划：订单收货人信息独立存储

## 概述

将 Order 聚合根中分散的 `shippingAddress` + `shippingDetailAddress` 替换为统一的 `ShippingInfo` 值对象，同时改造 CMD 层、工厂层、持久化层和数据库迁移脚本。改造按自底向上顺序进行：先领域层，再命令层/工厂层，再基础设施层，最后数据库迁移。

## Tasks

- [x] 1. 领域层改造：Order 接口与 OrderImpl 引入 ShippingInfo
  - [x] 1.1 修改 Order 接口，移除 `shippingAddress: I18nGeoAddress` 和 `shippingDetailAddress: String?`，新增 `shippingInfo: ShippingInfo`
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/Order.kt`
    - 移除 `val shippingAddress: I18nGeoAddress` 和 `val shippingDetailAddress: String?`
    - 新增 `val shippingInfo: ShippingInfo`
    - 移除不再需要的 `I18nGeoAddress` import
    - _需求: 1.1, 1.2_

  - [x] 1.2 修改 OrderImpl，将构造参数 `shippingAddress` + `shippingDetailAddress` 替换为 `shippingInfo: ShippingInfo`
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`
    - 构造参数替换：移除 `override val shippingAddress: I18nGeoAddress` 和 `override val shippingDetailAddress: String?`，新增 `override val shippingInfo: ShippingInfo`
    - 移除不再需要的 `I18nGeoAddress` import
    - _需求: 1.1, 1.2, 1.3_

- [x] 2. 命令层改造：OrderCreateCMD 字段清理与验证增强
  - [x] 2.1 在 OrderErrors 中新增 `CONSIGNEE_NAME_BLANK` 和 `DISTRICT_CODE_BLANK` 错误常量
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderErrors.kt`
    - 新增 `val CONSIGNEE_NAME_BLANK = BusinessError("收货人姓名不能为空", "Order.Consignee.NameBlank", 400)`
    - 新增 `val DISTRICT_CODE_BLANK = BusinessError("行政区划编码不能为空", "Order.Consignee.DistrictCodeBlank", 400)`
    - _需求: 3.1, 3.2_

  - [x] 2.2 完善 ConsigneeInfoCMD 的 validate 方法，增加 consigneeName 和 shippingDistrictCode 空白校验
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/command/OrderCreateCMD.kt`
    - 在 `ConsigneeInfoCMD.validate()` 中增加：`if (consigneeName.isBlank()) return Failure(OrderErrors.CONSIGNEE_NAME_BLANK)`
    - 增加：`if (shippingDistrictCode.isBlank()) return Failure(OrderErrors.DISTRICT_CODE_BLANK)`
    - 确保 `consigneeContractInfo.validate()` 校验保留
    - 最后返回 `Success(this)`
    - _需求: 3.1, 3.2, 3.3_

  - [x] 2.3 修改 OrderCreateCMD，移除顶层冗余字段 `shippingDistrictCode`、`countryCode`、`shippingDetailAddress`，完善 validate 方法
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/command/OrderCreateCMD.kt`
    - 移除 `val shippingDistrictCode: String`、`val countryCode: String?`、`val shippingDetailAddress: String?`
    - 在 `validate()` 中调用 `consigneeInfo.validate().onFailure { return Failure(it) }` 并传播错误
    - _需求: 7.1, 7.2, 7.3, 3.4_

  - [x] 2.4 编写 ConsigneeInfoCMD 空白字段验证属性测试
    - **Property 2: ConsigneeInfoCMD 空白字段验证**
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/command/ConsigneeInfoCMDValidationPropertyTest.kt`
    - 使用 Kotest Property Testing，生成仅由空白字符组成的字符串，验证 consigneeName 和 shippingDistrictCode 为空白时 validate() 返回 Failure
    - **验证: 需求 3.1, 3.2**

  - [x] 2.5 编写 OrderCreateCMD 验证错误传播属性测试
    - **Property 3: OrderCreateCMD 验证错误传播**
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/command/OrderCreateCMDValidationPropertyTest.kt`
    - 验证任何导致 ConsigneeInfoCMD.validate() 失败的输入，OrderCreateCMD.validate() 也返回相同的 Failure
    - **验证: 需求 3.4**

- [x] 3. 工厂层改造：OrderFactory 从 ConsigneeInfoCMD 构建 ShippingInfo
  - [x] 3.1 修改 OrderFactoryImpl.create()，从 ConsigneeInfoCMD 读取地址信息并构建 ShippingInfo
    - 文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt`
    - 从 `cmd.consigneeInfo` 提取 countryCode（默认 "CN"）和 shippingDistrictCode 调用 geoAddressService
    - 将 `ConsigneeInfoCMD.consigneeContractInfo`（ContractInfoCMD）转换为领域层 `ContractInfo`（映射 phoneNumber 和 emailAddress → email）
    - 组装 `ShippingInfo(consigneeName, contractInfo, address, shippingDetailAddress)`
    - 将 OrderImpl 构造参数从 `shippingAddress + shippingDetailAddress` 改为 `shippingInfo`
    - _需求: 2.1, 2.2, 2.3, 2.4, 2.5, 7.4_

  - [x] 3.2 编写 OrderFactory 正确组装 ShippingInfo 属性测试
    - **Property 1: 工厂正确组装 ShippingInfo**
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderFactoryShippingInfoPropertyTest.kt`
    - Mock GeoAddressService 返回成功的 I18nGeoAddress，验证 OrderFactory 创建的 Order.shippingInfo 各字段与 CMD 输入一致
    - **验证: 需求 2.1, 2.3, 2.4, 7.4**

- [x] 4. 检查点 - 确保领域层和工厂层编译通过
  - 确保所有修改后的代码编译通过，运行已有测试，如有问题请向用户确认。

- [x] 5. 基础设施层改造：OrderPO、ConsigneeInfoPO、Converter
  - [x] 5.1 在 j-store-order-infrastructure 的 build.gradle.kts 中添加 Kotest 测试依赖
    - 文件：`j-store-order-infrastructure/build.gradle.kts`
    - 添加 `testImplementation(libs.kotest.runner.junit5)`、`testImplementation(libs.kotest.assertions.core)`、`testImplementation(libs.kotest.property)`
    - _需求: 无（测试基础设施准备）_

  - [x] 5.2 创建 ConsigneeInfoPO 数据结构
    - 文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/ConsigneeInfoPO.kt`
    - 所有字段可空 + 默认 null，确保 Jackson 反序列化历史数据时缺失字段不报错
    - 包含：consigneeName、consigneePhone、consigneeEmail、countryCode、districtCode、shippingAddress（I18nGeoAddress）、detailAddress
    - _需求: 4.2, 4.6_

  - [x] 5.3 创建 ConsigneeInfoPOConverter（JPA AttributeConverter）
    - 文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/ConsigneeInfoPOConverter.kt`
    - 参照已有的 `I18nGeoAddressConverter` 模式，使用 `JsonUtils` 进行序列化/反序列化
    - _需求: 4.1_

  - [x] 5.4 修改 OrderPO，移除 countryCode、districtCode、shippingAddress、detailAddress 四个独立列，新增 consigneeInfo jsonb 列
    - 文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt`
    - 移除 `countryCode`、`districtCode`、`shippingAddress`（I18nGeoAddress）、`detailAddress` 四个字段及其注解
    - 新增 `@Convert(converter = ConsigneeInfoPOConverter::class) @Column(name = "consignee_info", columnDefinition = "jsonb") var consigneeInfo: ConsigneeInfoPO? = null`
    - 移除不再需要的 `I18nGeoAddressConverter` 相关 import
    - _需求: 4.1, 4.2, 4.3_

  - [x] 5.5 修改 OrderRepositoryImpl 的 Converter，适配 ShippingInfo ↔ ConsigneeInfoPO 转换
    - 文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/OrderRepositoryImpl.kt`
    - `toPO()`：从 `order.shippingInfo` 提取字段组装 `ConsigneeInfoPO`，设置到 `OrderPO.consigneeInfo`
    - `toDomain()`：从 `po.consigneeInfo` 反序列化，重建 `ShippingInfo`（consigneeName 缺失默认 ""，phone/email 缺失默认 null）
    - 移除旧的 `countryCode`、`districtCode`、`shippingAddress`、`detailAddress` 字段映射
    - _需求: 4.4, 4.5, 6.1, 6.2, 6.3_

  - [x] 5.6 编写 ShippingInfo ↔ ConsigneeInfoPO 序列化往返属性测试
    - **Property 4: ShippingInfo ↔ ConsigneeInfoPO 序列化往返**
    - 文件：`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/order/ConsigneeInfoPORoundTripPropertyTest.kt`
    - 验证任意合法 ShippingInfo 经 Converter 转为 ConsigneeInfoPO 再转回，所有字段等价
    - **验证: 需求 4.4, 4.5, 6.3**

  - [x] 5.7 编写历史数据反序列化默认值属性测试
    - **Property 5: 历史数据反序列化默认值**
    - 文件：`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/order/ConsigneeInfoPOBackwardCompatPropertyTest.kt`
    - 验证 consigneeName 缺失时默认 ""，consigneePhone 和 consigneeEmail 均缺失时 ContractInfo 的 phoneNumber 和 email 均为 null
    - **验证: 需求 6.1, 6.2**

- [x] 6. 检查点 - 确保基础设施层编译通过并运行测试
  - 确保所有修改后的代码编译通过，运行已有测试，如有问题请向用户确认。

- [x] 7. 数据库迁移脚本
  - [x] 7.1 创建数据库迁移脚本，合并旧列到 consignee_info jsonb
    - 文件：`docker/postgres/init/05-order-consignee-info.sql`
    - Step 1: `ALTER TABLE orders ADD COLUMN IF NOT EXISTS consignee_info jsonb`
    - Step 2: 将 country_code、district_code、shipping_address、detail_address 迁移到 consignee_info（consigneeName 默认空字符串，consigneePhone/consigneeEmail 默认 null）
    - Step 3: 设置 NOT NULL 约束
    - Step 4: `DROP COLUMN IF EXISTS` 删除四个旧列
    - Step 5: 创建 GIN 索引 `idx_orders_consignee_info`
    - _需求: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 8. 单元测试补充
  - [x] 8.1 编写 OrderFactory countryCode 默认值和地址查询失败的单元测试
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderFactoryUnitTest.kt`
    - 测试 ConsigneeInfoCMD.countryCode 为 null 时默认使用 "CN"
    - 测试 GeoAddressService 查询失败时 Factory 返回 Failure
    - _需求: 2.2, 2.5_

  - [x] 8.2 编写 Order 聚合根使用默认 ShippingInfo 执行状态转移的单元测试
    - 文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderShippingInfoUnitTest.kt`
    - 验证 ShippingInfo 中收货人信息为默认值（consigneeName=""，ContractInfo(null, null)）时，Order 所有业务流程正常执行
    - _需求: 6.4_

- [x] 9. 最终检查点 - 确保全部测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的子任务为可选，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 属性测试验证设计文档中定义的正确性属性
- 单元测试验证具体的边界场景和错误条件
- ShippingInfo 和 ContractInfo 值对象已存在，无需创建

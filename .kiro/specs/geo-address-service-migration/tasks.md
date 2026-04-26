# 实现计划：GeoAddressService 迁移至支撑域

## 概述

将 GeoAddressService 及相关组件从订单限界上下文（j-store-order / j-store-boot）迁移至支撑域（j-store-common-core / j-store-common-spring），使地址服务成为可被多个限界上下文复用的通用能力。采用自底向上的迁移策略：先迁移值对象和接口到 common-core，再迁移实现到 common-spring，最后更新消费者引用并清理旧文件。

## Tasks

- [x] 1. 迁移值对象和接口到 j-store-common-core
  - [x] 1.1 在 j-store-common-core 中创建 `com.jstore.common.geo` 包，将 `GeoAddressInfo` 数据类和 `DistrictLevel` 枚举迁移到该包下
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/` 下创建 `GeoAddressInfo.kt`
    - 包含 `GeoAddressInfo` data class 和 `DistrictLevel` enum，保留所有现有逻辑（`getProvinceCode`、`getCityCode`、`getCountyCode`、`level` 属性推导）
    - 包路径从 `com.jstore.order.domain.order` 改为 `com.jstore.common.geo`
    - 所有属性保持 `val` 声明，确保不可变性
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 1.2 将 `AddressErrors` 对象迁移到 `com.jstore.common.geo` 包下
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/` 下创建 `AddressErrors.kt`
    - 保留 `IllegalAddressCode` 错误定义（消息 "Illegal address code"、错误码 "Address.Code.Illegal"、HTTP 状态码 400）
    - 包路径从 `com.jstore.com.jstore.order.acl.geo` 改为 `com.jstore.common.geo`
    - _Requirements: 4.1, 4.2_

  - [x] 1.3 将 `GeoAddressService` 接口迁移到 `com.jstore.common.geo` 包下
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/geo/` 下创建 `GeoAddressService.kt`
    - 保留 `getByDistrictCode(districtCode: String): GeoAddressInfo` 方法签名
    - 接口引用的 `GeoAddressInfo` 使用同包下的新类
    - 包路径从 `com.jstore.order.acl` 改为 `com.jstore.common.geo`
    - _Requirements: 2.1, 2.2_

  - [x] 1.4 为 `GeoAddressInfo` 编写属性基测试（Property 1: 行政区划编码解析格式不变量）
    - **Property 1: 行政区划编码解析格式不变量**
    - **Validates: Requirements 1.3**
    - 在 `j-store-common-core/src/test/kotlin/com/jstore/common/geo/` 下创建测试文件
    - 使用 Kotest Property 库，生成随机 6-12 位数字字符串
    - 验证 `getProvinceCode` 返回前 2 位 + 4 个 "0"，`getCityCode` 返回前 4 位 + 2 个 "0"，`getCountyCode` 返回原编码前 6 位
    - 验证三个函数返回值长度均等于输入编码长度
    - 最少 100 次迭代

  - [x] 1.5 为 `GeoAddressInfo` 编写属性基测试（Property 2: level 属性推导正确性）
    - **Property 2: level 属性推导正确性**
    - **Validates: Requirements 1.4**
    - 生成随机 province/city/county 组合（含空字符串场景）
    - 验证：county 非空 → COUNTY；否则 city 非空 → CITY；否则 → PROVINCE
    - 最少 100 次迭代

- [x] 2. 迁移实现类到 j-store-common-spring
  - [x] 2.1 更新 `j-store-common-spring/build.gradle.kts`，新增 `fastexcel` 依赖
    - 添加 `implementation(libs.fastexcel)` 依赖声明
    - _Requirements: 6.1_

  - [x] 2.2 将 `ChinaGeoAddressServiceExcelImpl`、`GeoAddressServiceProxy`、`GeoAddressServiceFactory` 迁移到 `j-store-common-spring` 的 `com.jstore.common.geo` 包下
    - 在 `j-store-common-spring/src/main/kotlin/com/jstore/common/geo/` 下创建对应文件
    - `GeoAddressServiceProxy` 保留 `@Service` 注解
    - `ChinaGeoAddressServiceExcelImpl` 保留 Excel 数据加载逻辑和行政区划编码查询逻辑
    - `GeoAddressServiceFactory` 保留 `AbstractFactory` 模式
    - 更新所有 import 为新包路径（`com.jstore.common.geo.GeoAddressInfo`、`com.jstore.common.geo.GeoAddressService`、`com.jstore.common.geo.AddressErrors`）
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 2.3 将 `district.xlsx` 数据文件从 `j-store-boot/src/main/resources/data/` 迁移到 `j-store-common-spring/src/main/resources/data/`
    - 复制文件到新位置
    - classpath 相对路径 `data/district.xlsx` 保持不变
    - _Requirements: 5.1, 5.3_

  - [x] 2.4 为地址查询编写属性基测试（Property 3: 地址查询 districtCode 一致性）
    - **Property 3: 地址查询 districtCode 一致性**
    - **Validates: Requirements 3.4**
    - 在 `j-store-common-spring/src/test/kotlin/com/jstore/common/geo/` 下创建测试文件
    - 从 `district.xlsx` 已知数据集中随机选取编码
    - 验证 `getByDistrictCode(code)` 返回的 `GeoAddressInfo.districtCode` 与输入 `code` 完全一致
    - 最少 100 次迭代

  - [x] 2.5 为 `ChinaGeoAddressServiceExcelImpl` 编写单元测试
    - 测试编码长度 < 6 时抛出 `AddressErrors.IllegalAddressCode` 异常
    - 测试不存在的编码抛出异常
    - 测试已知有效编码返回正确的省/市/区信息
    - _Requirements: 3.4_

- [x] 3. 检查点 - 确保 common 模块编译通过
  - 确保 j-store-common-core 和 j-store-common-spring 模块编译通过，所有测试通过，如有问题请询问用户。

- [x] 4. 更新消费者引用并清理旧文件
  - [x] 4.1 更新 `OrderFactory`（`j-store-order`）中的导入路径
    - 将 `import com.jstore.order.acl.GeoAddressService` 更新为 `import com.jstore.common.geo.GeoAddressService`
    - `GeoAddressInfo` 通过 `j-store-common-core` 传递依赖自动解析
    - _Requirements: 7.1, 7.2_

  - [x] 4.2 更新 `Order.kt` 和 `OrderImpl.kt`（`j-store-order`）中的 `GeoAddressInfo` 导入路径
    - 将 `import com.jstore.order.domain.order.GeoAddressInfo` 更新为 `import com.jstore.common.geo.GeoAddressInfo`
    - 同步更新 `DistrictLevel` 的引用（如有）
    - _Requirements: 1.6, 7.4_

  - [x] 4.3 更新 `OrderRepositoryImpl`（`j-store-order-infrastructure`）中的 `GeoAddressInfo` 导入路径
    - 将 `GeoAddressInfo` 引用更新为 `com.jstore.common.geo.GeoAddressInfo`
    - _Requirements: 7.3_

  - [x] 4.4 删除 j-store-order 模块中的旧文件
    - 删除 `j-store-order/src/main/kotlin/com/jstore/order/domain/order/GeoAddressInfo.kt`（含 `DistrictLevel` 枚举）
    - 删除 `j-store-order/src/main/kotlin/com/jstore/order/acl/GeoAddressService.kt`
    - _Requirements: 1.5, 2.3, 2.5_

  - [x] 4.5 删除 j-store-boot 模块中的旧文件和数据
    - 删除 `j-store-boot/src/main/kotlin/com/jstore/order/acl/geo/` 目录及其所有文件（`AddressErrors.kt`、`address/GeoAddressServiceImpl.kt`、`address/GeoAddressServiceFactory.kt`）
    - 删除 `j-store-boot/src/main/resources/data/district.xlsx`
    - _Requirements: 3.5, 4.3, 5.2_

- [x] 5. 最终检查点 - 全项目编译验证
  - 运行 `./gradlew build` 确保全项目编译通过且无编译错误
  - 确认旧路径文件已全部移除
  - 确认无残留的旧包路径导入
  - 确保所有测试通过，如有问题请询问用户
  - _Requirements: 6.2, 6.3, 6.4_

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号以确保可追溯性
- 检查点确保增量验证，避免问题累积
- 属性基测试验证通用正确性属性，单元测试验证具体示例和边界情况
- 迁移过程中保持所有组件的行为和接口不变，仅改变物理位置和包路径

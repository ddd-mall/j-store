# 需求文档：GeoAddressService 迁移至支撑域

## 简介

将 `GeoAddressService`（地理地址服务）从订单限界上下文（Order Bounded Context）的 ACL 层迁移至支撑域（Supporting Domain），使其成为可被多个限界上下文复用的通用地址能力。当前该服务仅被订单上下文使用，但地址解析是通用基础能力，不应耦合在特定业务域中。

迁移范围包括：
- `GeoAddressInfo` 值对象（当前位于 `j-store-order` 域模块，已有 TODO 标记需迁移）
- `DistrictLevel` 枚举
- `GeoAddressService` 接口（当前位于 `j-store-order/acl/`）
- `ChinaGeoAddressServiceExcelImpl` 实现类、`GeoAddressServiceProxy`、`GeoAddressServiceFactory`（当前位于 `j-store-boot`）
- `AddressErrors` 错误定义（当前位于 `j-store-boot`）
- `district.xlsx` 数据文件（当前位于 `j-store-boot/src/main/resources/data/`）

## 术语表

- **GeoAddressService**：地理地址服务接口，根据行政区划编码查询省/市/区地址信息
- **GeoAddressInfo**：地理地址信息值对象，包含行政区划编码、省、市、区等字段
- **DistrictLevel**：行政区划级别枚举（PROVINCE / CITY / COUNTY）
- **Supporting_Domain**：支撑域，提供通用基础能力供多个限界上下文复用
- **ACL**：防腐层（Anti-Corruption Layer），限界上下文之间的适配层
- **j-store-common-core**：共享域框架模块，不依赖 Spring，存放通用值对象和接口
- **j-store-common-spring**：共享 Spring 集成模块，存放依赖 Spring 的通用实现
- **OrderFactory**：订单工厂，当前 GeoAddressService 的主要消费者
- **ChinaGeoAddressServiceExcelImpl**：基于 Excel 文件的中国行政区划地址服务实现

## 需求

### 需求 1：GeoAddressInfo 值对象迁移至 j-store-common-core

**用户故事：** 作为开发者，我希望将 GeoAddressInfo 值对象迁移到 j-store-common-core 模块，以便多个限界上下文可以复用该地址值对象。

#### 验收标准

1. WHEN GeoAddressInfo 值对象被迁移后，THE j-store-common-core 模块 SHALL 在 `com.jstore.common.geo` 包下包含 `GeoAddressInfo` 数据类及 `DistrictLevel` 枚举
2. THE GeoAddressInfo 值对象 SHALL 保持不可变性，所有属性使用 `val` 声明
3. THE GeoAddressInfo 值对象 SHALL 保留现有的行政区划编码解析逻辑（`getProvinceCode`、`getCityCode`、`getCountyCode`）
4. THE GeoAddressInfo 值对象 SHALL 保留现有的 `level` 属性推导逻辑
5. WHEN 迁移完成后，THE j-store-order 模块中原有的 `GeoAddressInfo.kt` 文件 SHALL 被移除
6. WHEN 迁移完成后，THE j-store-order 模块及 j-store-boot 模块中所有引用 `com.jstore.order.domain.order.GeoAddressInfo` 的代码 SHALL 更新为引用 `com.jstore.common.geo.GeoAddressInfo`

### 需求 2：GeoAddressService 接口迁移至 j-store-common-core

**用户故事：** 作为开发者，我希望将 GeoAddressService 接口迁移到 j-store-common-core 模块，以便多个限界上下文可以依赖该通用地址服务接口。

#### 验收标准

1. WHEN GeoAddressService 接口被迁移后，THE j-store-common-core 模块 SHALL 在 `com.jstore.common.geo` 包下包含 `GeoAddressService` 接口
2. THE GeoAddressService 接口 SHALL 保留现有的 `getByDistrictCode(districtCode: String): GeoAddressInfo` 方法签名
3. WHEN 迁移完成后，THE j-store-order 模块中原有的 `GeoAddressService.kt` 文件 SHALL 被移除
4. WHEN 迁移完成后，THE OrderFactory 及其他消费者 SHALL 通过 `com.jstore.common.geo.GeoAddressService` 引用该接口
5. THE j-store-order 模块 SHALL 不再在 `acl/` 包下定义 GeoAddressService 接口

### 需求 3：GeoAddressService 实现迁移至 j-store-common-spring

**用户故事：** 作为开发者，我希望将 GeoAddressService 的实现类迁移到 j-store-common-spring 模块，以便地址服务实现作为通用 Spring 组件被自动装配。

#### 验收标准

1. WHEN 实现类被迁移后，THE j-store-common-spring 模块 SHALL 在 `com.jstore.common.geo` 包下包含 `ChinaGeoAddressServiceExcelImpl` 类
2. WHEN 实现类被迁移后，THE j-store-common-spring 模块 SHALL 在 `com.jstore.common.geo` 包下包含 `GeoAddressServiceProxy` 对象（标注 `@Service`）
3. WHEN 实现类被迁移后，THE j-store-common-spring 模块 SHALL 在 `com.jstore.common.geo` 包下包含 `GeoAddressServiceFactory` 类
4. THE ChinaGeoAddressServiceExcelImpl 类 SHALL 保留现有的 Excel 数据加载逻辑和行政区划编码查询逻辑
5. WHEN 迁移完成后，THE j-store-boot 模块中原有的 `com.jstore.order.acl.geo` 包及其所有文件 SHALL 被移除

### 需求 4：AddressErrors 错误定义迁移

**用户故事：** 作为开发者，我希望将 AddressErrors 错误定义迁移到 j-store-common-core 模块，以便地址相关的错误码在各模块中保持一致。

#### 验收标准

1. WHEN AddressErrors 被迁移后，THE j-store-common-core 模块 SHALL 在 `com.jstore.common.geo` 包下包含 `AddressErrors` 对象
2. THE AddressErrors 对象 SHALL 保留现有的 `IllegalAddressCode` 错误定义（消息、错误码、HTTP 状态码）
3. WHEN 迁移完成后，THE ChinaGeoAddressServiceExcelImpl 中对 AddressErrors 的引用 SHALL 更新为新包路径

### 需求 5：district.xlsx 数据文件迁移

**用户故事：** 作为开发者，我希望将 district.xlsx 数据文件迁移到 j-store-common-spring 模块的资源目录，以便地址服务实现可以在自身模块内加载数据。

#### 验收标准

1. WHEN 数据文件被迁移后，THE j-store-common-spring 模块 SHALL 在 `src/main/resources/data/` 目录下包含 `district.xlsx` 文件
2. WHEN 迁移完成后，THE j-store-boot 模块中原有的 `src/main/resources/data/district.xlsx` 文件 SHALL 被移除
3. THE ChinaGeoAddressServiceExcelImpl 中的资源加载路径 SHALL 保持为 `data/district.xlsx`（classpath 相对路径不变）

### 需求 6：Gradle 依赖关系调整

**用户故事：** 作为开发者，我希望调整 Gradle 模块依赖关系，以确保迁移后各模块的编译和运行正常。

#### 验收标准

1. THE j-store-common-spring 模块的 `build.gradle.kts` SHALL 包含 `fastexcel` 依赖（用于 Excel 数据加载）
2. THE j-store-order 模块 SHALL 继续通过 `j-store-common-core` 的传递依赖访问 `GeoAddressService` 接口和 `GeoAddressInfo` 值对象
3. THE j-store-boot 模块 SHALL 继续通过 `j-store-common-spring` 的传递依赖获取 `GeoAddressServiceProxy` 的 Spring Bean
4. WHEN 所有迁移完成后，THE 项目 SHALL 通过 Gradle 编译（`./gradlew build`）且无编译错误

### 需求 7：消费者引用更新

**用户故事：** 作为开发者，我希望所有 GeoAddressService 的消费者代码更新为引用新的包路径，以确保迁移后功能正常。

#### 验收标准

1. WHEN 迁移完成后，THE OrderFactory 中的 `GeoAddressService` 导入 SHALL 更新为 `com.jstore.common.geo.GeoAddressService`
2. WHEN 迁移完成后，THE OrderFactory 中的 `GeoAddressInfo` 相关引用 SHALL 通过传递依赖正常解析
3. WHEN 迁移完成后，THE j-store-order-infrastructure 模块中所有引用 `GeoAddressInfo` 的代码 SHALL 更新为新包路径 `com.jstore.common.geo.GeoAddressInfo`
4. IF 存在其他模块引用了旧包路径的 GeoAddressService 或 GeoAddressInfo，THEN THE 迁移过程 SHALL 同步更新这些引用

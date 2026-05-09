# 技术设计文档：GeoAddressService 迁移至支撑域

## 概述

本设计描述将 GeoAddressService（地理地址服务）从订单限界上下文的 ACL 层迁移至支撑域（j-store-common-core 和 j-store-common-spring）的技术方案。

迁移的核心动机：地址解析是通用基础能力，不应耦合在订单域中。当前 `GeoAddressInfo` 值对象已有 `TODO: move to common module` 标记。迁移后，任何限界上下文都可以通过依赖 common 模块复用地址能力。

迁移涉及以下组件的重新定位：
- **值对象层**：`GeoAddressInfo`、`DistrictLevel` → `j-store-common-core`
- **接口层**：`GeoAddressService` 接口、`AddressErrors` → `j-store-common-core`
- **实现层**：`ChinaGeoAddressServiceExcelImpl`、`GeoAddressServiceProxy`、`GeoAddressServiceFactory`、`district.xlsx` → `j-store-common-spring`

## 架构

### 迁移前架构

```mermaid
graph TD
    subgraph j-store-boot
        Proxy[GeoAddressServiceProxy<br/>@Service]
        Factory[GeoAddressServiceFactory]
        Impl[ChinaGeoAddressServiceExcelImpl]
        AErr[AddressErrors]
        Excel[district.xlsx]
        Proxy --> Factory --> Impl
        Impl --> AErr
        Impl --> Excel
    end

    subgraph j-store-order
        IF[GeoAddressService 接口<br/>acl/]
        VO[GeoAddressInfo 值对象<br/>domain/order/]
        OF[OrderFactory]
        OF --> IF
        OF --> VO
    end

    subgraph j-store-order-infrastructure
        Repo[OrderRepositoryImpl]
        Repo --> VO
    end

    Proxy -.->|implements| IF
    Impl --> VO
```

### 迁移后架构

```mermaid
graph TD
    subgraph j-store-common-core
        VO2[GeoAddressInfo<br/>com.jstore.common.geo]
        DL2[DistrictLevel<br/>com.jstore.common.geo]
        IF2[GeoAddressService 接口<br/>com.jstore.common.geo]
        AErr2[AddressErrors<br/>com.jstore.common.geo]
    end

    subgraph j-store-common-spring
        Proxy2[GeoAddressServiceProxy<br/>@Service]
        Factory2[GeoAddressServiceFactory]
        Impl2[ChinaGeoAddressServiceExcelImpl]
        Excel2[district.xlsx]
        Proxy2 --> Factory2 --> Impl2
        Impl2 --> Excel2
    end

    subgraph j-store-order
        OF2[OrderFactory]
    end

    subgraph j-store-order-infrastructure
        Repo2[OrderRepositoryImpl]
    end

    Proxy2 -.->|implements| IF2
    Impl2 --> VO2
    Impl2 --> AErr2
    OF2 --> IF2
    OF2 --> VO2
    Repo2 --> VO2
```

### 设计决策

1. **接口放 common-core，实现放 common-spring**：遵循项目现有的分层约定——common-core 不依赖 Spring，common-spring 依赖 Spring。`GeoAddressService` 接口和 `GeoAddressInfo` 值对象是纯域概念，放 common-core；`ChinaGeoAddressServiceExcelImpl` 依赖 FastExcel 和 Spring `@Service` 注解，放 common-spring。

2. **统一包路径 `com.jstore.common.geo`**：所有迁移组件使用同一个包，保持内聚性。这与 common-core 中已有的 `com.jstore.common.errors`、`com.jstore.common.properties` 等包命名风格一致。

3. **保留 `GeoAddressServiceProxy` + `GeoAddressServiceFactory` 模式**：当前使用 `AbstractFactory` 模式选举实现类，虽然目前只有一个实现，但保留该模式为未来扩展（如基于数据库的实现）留出空间。

4. **OrderFactory 中的 GeoAddressService 保持 ACL 语义**：虽然接口迁移到了 common-core，但 OrderFactory 通过构造函数注入 `GeoAddressService`，这仍然是 ACL 的使用方式——订单域通过接口隔离外部地址能力。接口的物理位置变了，但逻辑角色不变。

## 组件与接口

### 迁移组件清单

| 组件 | 源位置 | 目标位置 | 目标模块 |
|------|--------|----------|----------|
| `GeoAddressInfo` | `com.jstore.order.domain.order` | `com.jstore.common.geo` | j-store-common-core |
| `DistrictLevel` | `com.jstore.order.domain.order` (同文件) | `com.jstore.common.geo` | j-store-common-core |
| `GeoAddressService` | `com.jstore.order.acl` | `com.jstore.common.geo` | j-store-common-core |
| `AddressErrors` | `com.jstore.com.jstore.order.acl.geo` | `com.jstore.common.geo` | j-store-common-core |
| `ChinaGeoAddressServiceExcelImpl` | `com.jstore.com.jstore.order.acl.geo.address` | `com.jstore.common.geo` | j-store-common-spring |
| `GeoAddressServiceProxy` | `com.jstore.com.jstore.order.acl.geo.address` | `com.jstore.common.geo` | j-store-common-spring |
| `GeoAddressServiceFactory` | `com.jstore.com.jstore.order.acl.geo.address` | `com.jstore.common.geo` | j-store-common-spring |
| `district.xlsx` | `j-store-boot/src/main/resources/data/` | `j-store-common-spring/src/main/resources/data/` | j-store-common-spring |

### 接口定义（迁移后）

```kotlin
// j-store-common-core: com.jstore.common.geo.GeoAddressService
package com.jstore.common.geo

interface GeoAddressService {
    fun getByDistrictCode(districtCode: String): GeoAddressInfo
}
```

### 受影响的消费者

| 消费者 | 模块 | 需要更新的导入 |
|--------|------|---------------|
| `OrderFactoryImpl` | j-store-order | `GeoAddressService`, `GeoAddressInfo` |
| `OrderRepositoryImpl` | j-store-order-infrastructure | `GeoAddressInfo` |
| `Order` / `OrderImpl` | j-store-order | `GeoAddressInfo` |

### Gradle 依赖变更

| 模块 | 变更 |
|------|------|
| j-store-common-spring | 新增 `implementation(libs.fastexcel)` |
| j-store-order | 无变更（已依赖 j-store-common-core） |
| j-store-boot | 无变更（已依赖 j-store-common-spring） |

## 数据模型

### GeoAddressInfo 值对象（不变）

```kotlin
package com.jstore.common.geo

data class GeoAddressInfo(
    val districtCode: String,
    val province: String,
    val city: String,
    val county: String,
    val detailAddress: String? = null
) {
    companion object {
        fun getProvinceCode(districtCode: String): String
        fun getCityCode(districtCode: String): String
        fun getCountyCode(districtCode: String): String
    }
    val level: DistrictLevel  // 根据 county/city 是否为空推导
}
```

### DistrictLevel 枚举（不变）

```kotlin
package com.jstore.common.geo

enum class DistrictLevel {
    PROVINCE { override fun getCodeLen() = 2 },
    CITY { override fun getCodeLen() = 4 },
    COUNTY { override fun getCodeLen() = 6 };
    abstract fun getCodeLen(): Int
}
```

### AddressErrors（不变）

```kotlin
package com.jstore.common.geo

object AddressErrors {
    val IllegalAddressCode: Errors = Errors("Illegal address code", "Address.Code.Illegal", 400)
}
```

数据模型本身不发生任何结构性变更，仅包路径从 `com.jstore.order.domain.order` / `com.jstore.order.acl` / `com.jstore.com.jstore.order.acl.geo` 统一迁移到 `com.jstore.common.geo`。

## 正确性属性

*正确性属性是在系统所有有效执行中都应成立的特征或行为——本质上是对系统应做什么的形式化陈述。属性是人类可读规格说明与机器可验证正确性保证之间的桥梁。*

### Property 1：行政区划编码解析格式不变量

*For any* 有效的行政区划编码（长度 ≥ 6 的数字字符串），`getProvinceCode` 应返回前 2 位 + 4 个 "0"，`getCityCode` 应返回前 4 位 + 2 个 "0"，`getCountyCode` 应返回原编码本身（前 6 位 + 0 个 "0"）。且三个函数的返回值长度均等于输入编码长度。

**Validates: Requirements 1.3**

### Property 2：level 属性推导正确性

*For any* `GeoAddressInfo` 实例，`level` 属性应满足：若 `county` 非空（含非空白字符）则 `level == COUNTY`；否则若 `city` 非空则 `level == CITY`；否则 `level == PROVINCE`。

**Validates: Requirements 1.4**

### Property 3：地址查询 districtCode 一致性

*For any* 在 `district.xlsx` 数据集中存在的有效行政区划编码，调用 `getByDistrictCode(code)` 返回的 `GeoAddressInfo` 的 `districtCode` 字段应与输入的 `code` 完全一致。

**Validates: Requirements 3.4**

## 错误处理

迁移不改变现有错误处理逻辑：

| 错误场景 | 错误类型 | 行为 |
|----------|----------|------|
| 行政区划编码长度 < 6 | `AddressErrors.IllegalAddressCode` | 抛出异常，消息包含编码和原因 |
| 编码在数据集中不存在 | `AddressErrors.IllegalAddressCode` | 抛出异常，消息包含编码 |
| 省/市/区全部为空 | `AddressErrors.IllegalAddressCode` | 抛出异常，消息包含编码 |
| 编码解析时长度不足 | `CommonErrors.INVALID_PARAM` | 抛出异常（GeoAddressInfo.commonDecoding） |

`AddressErrors` 从 `j-store-boot` 迁移到 `j-store-common-core` 后，错误码 `Address.Code.Illegal` 和 HTTP 状态码 `400` 保持不变。

## 测试策略

### PBT 适用性评估

本次迁移涉及纯函数逻辑（行政区划编码解析、level 推导），适合属性基测试。但大部分验收标准是文件迁移和编译验证（SMOKE 类型），不适合 PBT。

### 属性基测试（Property-Based Testing）

- 使用 **Kotest Property** 库（项目已配置）
- 每个属性测试最少运行 **100 次迭代**
- 标签格式：`Feature: geo-address-service-migration, Property {number}: {property_text}`

针对 3 个正确性属性编写 PBT：
1. 行政区划编码解析格式不变量 — 生成随机 6-12 位数字字符串，验证三个解析函数的输出格式
2. level 属性推导 — 生成随机 province/city/county 组合（含空字符串），验证 level 推导逻辑
3. 地址查询一致性 — 从已知数据集中随机选取编码，验证查询结果的 districtCode 一致性

### 单元测试（Example-Based）

- `AddressErrors.IllegalAddressCode` 的 message/errorCode/httpCode 值验证
- 编码长度 < 6 时抛出异常
- 不存在的编码抛出异常
- 省/市/区全空时抛出异常

### 集成测试

- Spring 上下文启动后 `GeoAddressServiceProxy` Bean 可正常注入
- `district.xlsx` 从 classpath 正常加载
- 端到端：通过 `GeoAddressServiceProxy` 查询已知编码返回正确地址

### 编译验证（SMOKE）

- `./gradlew build` 全项目编译通过
- 旧路径文件不存在
- 新路径文件存在
- 无残留的旧包路径导入

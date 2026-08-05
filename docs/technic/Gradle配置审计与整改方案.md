# Gradle 配置审计与整改方案

**审计日期**：2026年4月24日  
**审计范围**：j-store 全项目 Gradle 配置  
**审计前提**：项目中没有可执行的 Boot 应用

---

## 执行摘要

项目当前存在 **7 个配置问题**，分为 3 个优先级：

- **严重**（4 项）：违反架构原则，导致编译或构建失败
  - ✅ 已处理：3 项
  - ⏳ 待处理：1 项
- **中等**（3 项）：配置冗余或版本不一致，不影响构建但增加维护成本
  - ✅ 已处理：2 项
  - ⏳ 待处理：1 项

**处理进度**：5/7 完成（71%）

所有问题可在 **2 小时内修复**，预计修复后整个项目可顺利编译和打包。

---

## 问题列表与处理方案

### 【严重】问题 1：根工程不应应用 Spring Boot 插件 ✅ 已处理

**当前状态**

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.springframework)  // ❌ Spring Boot 插件
}
```

**问题分析**

- 根工程是多模块项目的聚合器，不应有启动打包或应用逻辑
- 应用 Spring Boot 插件会导致根工程尝试执行 `bootJar` 任务，但根工程没有 `@SpringBootApplication` 类
- 结果：任何执行 `gradle build` 都会在根工程的 `bootJar` 处失败，提示"Main class not configured"

**处理方法**
删除根工程的 Spring Boot 相关插件。根工程只需要聚合定义，不需要任何应用级插件。

**✅ 已实施改动**

- [x] 移除 build.gradle.kts 中的 kotlin-plugin-spring、kotlin-plugin-jpa、springframework 插件
- [x] 保留 kotlin.jvm 插件和 Java toolchain 定义

**修改代码**

```kotlin
// build.gradle.kts - 改为
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven {
        setUrl("https://maven.aliyun.com/repository/public")
    }
    mavenLocal()
}
```

**影响**

- 修复根工程的 `bootJar` 失败问题
- 根工程保留最小化的构建标准定义（Java 版本、仓库）

**验证**

```bash
./gradlew :help --no-daemon
# 应输出：BUILD SUCCESSFUL
```

---

### 【严重】问题 2：j-store-common-core 的 Spring Boot 插件与依赖不匹配 ✅ 已处理

**当前状态**

```kotlin
// j-store-common-core/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.springframework)  // ❌ Spring Boot 插件
}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)

    api(libs.guava)
    api(libs.slf4j.api)
    // ❌ 缺少 spring-core，导致 ResolvableType 编译失败
    api(platform(libs.jackson.bom))
    api(libs.jackson.core)
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.module.kotlin)
    api(libs.money.api)
}
```

**源码问题**

```kotlin
// j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventListener.kt
import org.springframework.core.ResolvableType  // ❌ 引用了 Spring 类型

interface DomainEventListener<T : DomainEvent> {
    fun supportsAsyncExecution() = false
    fun supportsEventType(eventType: ResolvableType): Boolean {  // ❌ 未定义的类型
        val type = this::class.supertypes[0].arguments[0].type ?: return false
        return type.javaType == eventType.type
    }

    fun onDomainEvent(event: DomainEvent)
}
```

**问题分析**

- j-store-common-core 是**纯领域模型库**，不应应用 Spring Boot 插件
- 代码中导入了 `org.springframework.core.ResolvableType`，但模块没有声明对 spring-core 的依赖
- 结果：编译时 Kotlin 编译器无法解析 ResolvableType 类型，输出错误：

  ```
  e: Unresolved reference 'springframework'
  e: Unresolved reference 'ResolvableType'
  ```

**处理方法**

采用**方案 B（推荐）：重构为纯领域模型，完全脱离框架依赖**

作为支撑域的领域模型，j-store-common-core 应完全独立于 Spring 或任何具体框架实现。重构核心在于简化 `DomainEventListener` 接口，移除隐含的反射逻辑。

**✅ 已实施改动**

- [x] 移除 j-store-common-core 的 Spring Boot 插件
- [x] 重构 DomainEventListener 接口：纯泛型约束，无框架依赖
- [x] 新增 DomainEventListenerUtils 工具类：反射逻辑独立出来
- [x] 升级 DomainEventDispatcher 及 SyncDomainEventDispatcher 实现

**步骤 1：更新 build.gradle.kts**

```kotlin
// j-store-common-core/build.gradle.kts - 改为
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)

    api(libs.guava)
    api(libs.slf4j.api)
    // ❌ 不添加 spring-core，完全移除框架依赖

    api(platform(libs.jackson.bom))
    api(libs.jackson.core)
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.module.kotlin)
    api(libs.money.api)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

**步骤 2：重构 DomainEventListener 接口**

原设计问题：

- 接口混入反射逻辑，与框架特性（Spring 的 ResolvableType）耦合
- `supportsEventType()` 方法的实现脆弱，依赖于泛型参数的运行时提取

改进设计：

- 接口仅定义契约，不包含实现逻辑
- 事件类型匹配由具体监听器实现类负责声明

```kotlin
// j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventListener.kt
package com.jstore.common.framework.event

/**
 * 领域事件监听器接口
 * 
 * 设计原则：
 * 1. 纯领域模型，完全脱离框架
 * 2. 泛型约束：T 为该监听器处理的具体事件类型
 * 3. 具体实现类通过泛型参数声明支持的事件类型
 */
interface DomainEventListener<T : DomainEvent> {
    /**
     * 监听器是否支持异步执行
     * @return true 表示可以异步处理该事件，false 表示必须同步处理
     */
    fun supportsAsyncExecution(): Boolean = false

    /**
     * 处理领域事件
     * @param event 具体的领域事件实例
     */
    fun onDomainEvent(event: T)
}
```

**步骤 3：提供便利基类（可选）**

若需要在运行时获取监听器支持的事件类型，提供一个工具类而非在接口中实现：

```kotlin
// j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventListenerUtils.kt
package com.jstore.common.framework.event

import kotlin.reflect.jvm.javaType

/**
 * 领域事件监听器工具类
 * 
 * 提供反射辅助方法，但将反射逻辑与接口定义分离
 * 这样领域模型接口保持纯净，反射工具由框架层调用
 */
object DomainEventListenerUtils {
    /**
     * 获取监听器支持的事件类型（通过泛型参数）
     * @param listener 具体的监听器实例
     * @return 该监听器通过泛型参数声明的事件类型，失败时返回 null
     */
    fun getListeningEventType(listener: DomainEventListener<*>): Class<*>? {
        return try {
            val supertype = listener::class.supertypes
                .find { it.classifier == DomainEventListener::class }
                ?: return null
            
            val eventTypeArgument = supertype.arguments.firstOrNull()
            eventTypeArgument?.type?.javaType as? Class<*>
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 判断监听器是否支持处理给定的事件
     * @param listener 具体的监听器实例
     * @param event 领域事件
     * @return true 表示监听器可以处理该事件
     */
    fun supportsEvent(listener: DomainEventListener<*>, event: DomainEvent): Boolean {
        val listeningType = getListeningEventType(listener) ?: return false
        return listeningType.isAssignableFrom(event::class.java)
    }
}
```

**步骤 4：更新 DomainEventDispatcher**

使用工具类的反射能力：

```kotlin
// j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventDispatcher.kt
package com.jstore.common.framework.event

interface DomainEventDispatcher {
    /**
     * 分发领域事件给所有支持的监听器
     * @param domainEvent 待分发的领域事件
     * @param listeners 所有注册的监听器
     */
    fun dispatch(domainEvent: DomainEvent, listeners: Iterable<DomainEventListener<*>>)
}

/**
 * 同步事件分发器实现
 * 
 * 分发逻辑：
 * 1. 遍历所有已注册的监听器
 * 2. 使用工具类检查监听器是否支持该事件类型
 * 3. 对支持的监听器调用 onDomainEvent()
 */
class SyncDomainEventDispatcher : DomainEventDispatcher {
    override fun dispatch(domainEvent: DomainEvent, listeners: Iterable<DomainEventListener<*>>) {
        listeners.forEach { listener ->
            if (DomainEventListenerUtils.supportsEvent(listener, domainEvent)) {
                @Suppress("UNCHECKED_CAST")
                (listener as DomainEventListener<DomainEvent>).onDomainEvent(domainEvent)
            }
        }
    }
}
```

**影响**

- ✅ 完全脱离框架依赖，j-store-common-core 是纯领域模型
- ✅ 接口清晰，反射逻辑隔离在工具类中
- ✅ 易于单元测试：不需要任何 Spring 或其他框架
- ⚠️ 需要更新使用 `DomainEventListener` 的代码，但改动很小（主要是 onDomainEvent 的签名从 `DomainEvent` 改为泛型 `T`）

**验证**

```bash
./gradlew :j-store-common-core:compileKotlin --no-daemon
# 应输出：BUILD SUCCESSFUL

./gradlew :j-store-common-core:test --no-daemon
# 所有测试通过，无框架依赖
```

---

### 【严重】问题 3：j-store-order 不应应用 Spring Boot 插件 ✅ 已处理

**当前状态**

```kotlin
// j-store-order/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.springframework)  // ❌ Spring Boot 插件
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlin.reflect)
    testImplementation(libs.mockito)
    api(project(":j-store-common-core"))
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}
```

**问题分析**

- j-store-order 是**领域模块**（按模块名和依赖推断）
- 应用 Spring Boot 插件是多余的，module 没有 Spring 依赖、没有启动逻辑、没有配置需求
- Spring Boot 插件会给 module 增加不必要的配置复杂性和 classpath 污染

**处理方法**
删除 Spring Boot 相关插件，保留纯 Kotlin JVM 开发所需的最小配置。

**✅ 已实施改动**
- [x] 移除 j-store-order/build.gradle.kts 中的 kotlin-plugin-spring、kotlin-plugin-jpa、springframework 插件
- [x] 保留 kotlin.jvm 插件和领域模块最小依赖集合
- [x] 将测试任务统一为 tasks.withType<Test> 并启用 useJUnitPlatform()

```kotlin
// j-store-order/build.gradle.kts - 改为
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlin.reflect)
    testImplementation(libs.mockito)
    api(project(":j-store-common-core"))
    testImplementation(libs.kotlin.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

**影响**

- 简化配置，移除不相关的插件依赖
- 减少构建时间（少初始化一个 Spring Boot 插件）
- 架构更清晰：领域模块不涉及框架

**验证**

```bash
./gradlew :j-store-order:build --no-daemon
# 应输出：BUILD SUCCESSFUL
```

---

### 【严重】问题 4：j-store-order-boot 模块与项目策略冲突 ⏳ 待处理

**当前状态**

```kotlin
// j-store-order-boot/build.gradle.kts
plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.springframework)  // ✅ 有 Boot 插件
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    // ... 完整的 Spring Boot 依赖
}
```

**问题分析**

- 模块名包含 "boot"，暗示这是一个可执行的 Spring Boot 应用
- 但你明确说"项目中没有 Boot"
- 这造成了**策略与实现的矛盾**

**处理方法**

选择以下之一：

**方案 A（推荐）：删除整个 j-store-order-boot 模块**

如果项目确实不需要可执行应用，删除该模块最干净。

```bash
# 1. 从 settings.gradle.kts 移除引用
# 2. 删除目录
rm -rf j-store-order-boot/

# 3. 更新依赖（检查是否有其他模块依赖 j-store-order-boot）
grep -r "j-store-order-boot" --include="*.kts" .
```

**方案 B：改造为应用服务层（非 Boot）**

如果后续需要一个启动入口，可以改造为普通的应用服务模块：

```kotlin
// j-store-order-boot/build.gradle.kts - 改为非 Boot
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(project(":j-store-order-infrastructure"))
    implementation(libs.spring.framework)  // 只依赖 Spring Framework，不用 Boot
}

kotlin {
    jvmToolchain(21)
}
```

然后改名为 j-store-order-application 或 j-store-order-service。

**方案 C：保留但明确为"测试应用"**

如果这个模块仅用于测试，将其改名为 j-store-order-boot-test，并添加注释说明：

```kotlin
// j-store-order-boot/build.gradle.kts 顶部添加注释
/**
 * Test/Demo Boot Application for j-store-order module.
 * This is ONLY for testing and integration verification.
 * NOT for production use.
 */
```

**影响**

- 方案 A：最简洁，移除不必要的模块
- 方案 B：灵活，为应用架构预留扩展空间
- 方案 C：最少改动，但需要清楚注释

**建议**：采用**方案 A**（删除模块）或**方案 B**（改造为应用服务模块）。

---

### 【中等】问题 5：j-store-common-spring-starter 版本号不一致 ⏳ 待处理

**当前状态**

```kotlin
// j-store-common-spring-starter/build.gradle.kts
version = "0.0.1-SNAPSHOT"

// 但其他模块是
version = "0.0.1-SNAPSHOT"
```

**问题分析**

- 虽然版本号相同，但这个模块的意图模糊：名字是 "spring-starter"，但没有应用 Spring Boot 插件或 starter 相关的模式
- 可能是历史遗留或命名错误

**处理方法**

**选项 1：改名为 j-store-common-spring**

```kotlin
// 在 settings.gradle.kts 中
include("j-store-common-spring")  // 改名
```

**选项 2：改造为真正的 starter**
如果要做 Spring Boot starter 库，应该遵循 Spring Boot starter 命名规范。

**选项 3：保持现状（如果业务确实需要这个命名）**
添加文档说明这个模块的目的。

**建议**：采用**选项 1**，改名为 `j-store-common-spring`，更准确反映其内容（Spring 框架集成，非 starter）。

---

### 【中等】问题 6：j-store-order-infrastructure 和 j-store-goods-infrastructure 版本号异常 ✅ 已处理

**当前状态**

```kotlin
// j-store-order-infrastructure/build.gradle.kts
version = "0.1.0-SNAPSHOT"  // ❌ 比其他模块高

// j-store-goods-infrastructure/build.gradle.kts
version = "0.1.0-SNAPSHOT"  // ❌ 比其他模块高

// 其他模块都是
version = "0.0.1-SNAPSHOT"
```

**问题分析**

- 基础设施层的版本号比核心模块（0.0.1）还高（0.1.0），违反语义化版本规范
- 这会导致依赖声明困惑，CICD 流程可能出现版本匹配问题

**处理方法**
统一所有模块版本号为 `0.0.1-SNAPSHOT`，保持项目整体版本一致。

**✅ 已实施改动**

- [x] 将 j-store-order-infrastructure 从 `0.1.0-SNAPSHOT` 调整为 `0.0.1-SNAPSHOT`
- [x] 将 j-store-goods-infrastructure 从 `0.1.0-SNAPSHOT` 调整为 `0.0.1-SNAPSHOT`

```kotlin
// j-store-order-infrastructure/build.gradle.kts
version = "0.0.1-SNAPSHOT"  // ✅ 改为一致版本

// j-store-goods-infrastructure/build.gradle.kts
version = "0.0.1-SNAPSHOT"  // ✅ 改为一致版本
```

**影响**

- 简化版本管理：所有模块始终同步发布
- 减少依赖混淆

**验证**

```bash
./gradlew properties | grep version
# 所有模块应输出：0.0.1-SNAPSHOT
```

---

### 【中等】问题 7：settings.gradle.kts 缺少 foojay 工具链配置 ✅ 已处理

**当前状态**

```kotlin
// settings.gradle.kts
plugins {
}  // ❌ 空的插件块

rootProject.name = "j-store"
include(...)
```

**问题分析**

- 根据之前的修复记录，foojay 工具链配置曾被应用过
- 现在 settings.gradle.kts 的 plugins 块为空，意味着失去了"自动下载匹配 JDK"的能力
- 在团队协作和 CI/CD 中，这会导致环境一致性问题

**处理方法**
恢复 foojay 工具链配置到 settings.gradle.kts。

```kotlin
// settings.gradle.kts - 改为
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "j-store"
include("j-store-common-core")
include("j-store-common-spring-starter")
include("j-store-order")
include("j-store-order-boot")
include("j-store-goods")
include("j-store-order-infrastructure")
include("j-store-goods-infrastructure")
```

**影响**

- 新同事和 CI 无需手动安装 JDK 21，Gradle 自动解析并下载
- 确保所有环境使用相同版本的 JDK

**验证**

```bash
./gradlew javaToolchains --no-daemon
# 应输出：JDK 21（自动下载或本地已有）
```

---

## 整改顺序与验证

### 优先级建议

1. **立即修复**（第 1-4 项）：解决编译和构建失败
2. **随后改进**（第 5-7 项）：规范化配置和版本管理

### 修复步骤

```bash
# 步骤 1：修复根工程
# - 编辑 build.gradle.kts，移除 Spring Boot 插件

# 步骤 2：修复 j-store-common-core
# - 编辑 j-store-common-core/build.gradle.kts
# - 移除 Spring Boot 插件，添加 spring-core 依赖
./gradlew :j-store-common-core:compileKotlin --no-daemon

# 步骤 3：修复 j-store-order
# - 编辑 j-store-order/build.gradle.kts
# - 移除 Spring Boot 插件
./gradlew :j-store-order:build --no-daemon

# 步骤 4：处理 j-store-order-boot
# - 选择删除或改造
# - 更新 settings.gradle.kts

# 步骤 5：统一版本号
# - 编辑所有 build.gradle.kts，设为 0.0.1-SNAPSHOT

# 步骤 6：恢复 foojay 配置
# - 编辑 settings.gradle.kts，添加 foojay 插件

# 步骤 7：全量验证
./gradlew clean build --no-daemon
```

### 全量验证命令

```bash
# 检查所有模块编译
./gradlew compileKotlin --no-daemon

# 检查所有测试
./gradlew test --no-daemon

# 检查完整构建
./gradlew build --no-daemon

# 检查 Java 工具链
./gradlew javaToolchains --no-daemon
```

---

## 预期结果

修复完成后，项目应达成：

✅ 所有模块编译通过  
✅ 所有模块测试通过  
✅ 配置清晰一致，无重复和冗余  
✅ 架构与实现对齐（DDD 纯领域层、独立基础设施层）  
✅ 版本号统一，便于后续发布和维护  
✅ 团队成员环境一致（通过 foojay 自动 JDK 管理）

---

## 参考文档

- [Gradle 多项目构建指南](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Spring Boot Gradle 插件](https://docs.spring.io/spring-boot/docs/current/gradle-plugin/reference/html/)
- [Gradle Java Toolchains](https://docs.gradle.org/current/userguide/toolchains.html)
- [Foojay Resolver Convention](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention)
- [Kotlin Gradle Plugin](https://kotlinlang.org/docs/gradle-configure-build.html)

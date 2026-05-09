# 实施计划：Authentication SDK

## 概述

将 `j-store-authentication-sdk` 模块从空壳状态实现为完整的 Spring MVC 认证拦截器 SDK。按照设计文档的包结构，先实现无 Spring 依赖的核心组件（注解、上下文、错误常量、配置接口），再实现 Spring MVC 集成层（拦截器、参数解析器、自动配置），最后通过属性测试和单元测试验证正确性。

## Tasks

- [x] 1. 更新 build.gradle.kts 依赖配置
  - 添加 `api(project(":j-store-user"))` 依赖
  - 添加 `implementation(platform(libs.spring.boot.dependencies))`
  - 添加 `implementation(libs.spring.boot.starter.web)`
  - 添加 `implementation(libs.jackson.databind)`
  - 添加 `testImplementation(libs.spring.boot.starter.test)`
  - 删除 `j-store-authentication-sdk/src/main/kotlin/Main.kt` 占位文件
  - _需求: 7.2, 8.1, 8.2_

- [x] 2. 实现核心注解和上下文组件（无 Spring 依赖）
  - [x] 2.1 创建注解类 `@RequireLogin`、`@SkipLogin`、`@CurrentUserId`
    - 创建 `com.jstore.authentication.annotation` 包
    - `RequireLogin`：支持 `CLASS` 和 `FUNCTION` 目标，`RUNTIME` 保留
    - `SkipLogin`：仅支持 `FUNCTION` 目标，`RUNTIME` 保留
    - `CurrentUserId`：仅支持 `VALUE_PARAMETER` 目标，`RUNTIME` 保留
    - _需求: 1.1, 1.4, 5.6, 8.4_

  - [x] 2.2 实现 `AuthenticationException` 异常类
    - 创建 `com.jstore.authentication.context` 包
    - 纯 Kotlin 异常，继承 `RuntimeException`
    - _需求: 5.4_

  - [x] 2.3 实现 `AuthenticatedUserContext` 用户上下文
    - 使用 `object` 单例 + `ThreadLocal<UserId>` 存储
    - 提供 `set(userId)`、`getCurrentUserId()`、`getCurrentUserIdOrNull()`、`clear()` 方法
    - `getCurrentUserId()` 在无用户时抛出 `AuthenticationException`
    - _需求: 5.1, 5.2, 5.3, 5.4, 5.5, 8.4_

  - [x] 2.4 编写属性测试：AuthenticatedUserContext 存取 round-trip
    - **属性 4：AuthenticatedUserContext 存取 round-trip**
    - **验证需求: 5.1, 5.3, 5.5**
    - 使用 Kotest Property 生成随机 `UserId`，验证 `set` → `getCurrentUserId` → `clear` → `getCurrentUserIdOrNull` 行为

  - [x] 2.5 编写属性测试：AuthenticatedUserContext 线程隔离
    - **属性 5：AuthenticatedUserContext 线程隔离**
    - **验证需求: 5.2**
    - 生成随机 UserId 对，在不同线程中 set 后验证各线程获取到自己的 UserId

  - [x] 2.6 编写单元测试：AuthenticatedUserContext 边界行为
    - 验证未认证上下文中 `getCurrentUserId()` 抛出 `AuthenticationException`
    - 验证未认证上下文中 `getCurrentUserIdOrNull()` 返回 `null`
    - _需求: 5.4_

- [x] 3. 实现错误常量和配置接口（无 Spring 依赖）
  - [x] 3.1 实现 `AuthenticationErrors` 错误常量对象
    - 创建 `com.jstore.authentication.error` 包
    - 定义 `TOKEN_MISSING`、`TOKEN_INVALID`、`TOKEN_BLACKLISTED`、`INTERNAL_ERROR` 四个 `BusinessError` 常量
    - _需求: 6.1, 6.2_

  - [x] 3.2 实现 `AuthenticationConfigurer` 配置接口
    - 创建 `com.jstore.authentication.config` 包
    - 定义 `authenticatedPathPatterns()` 和 `excludedPathPatterns()` 方法，默认返回空列表
    - _需求: 2.1_

  - [x] 3.3 编写单元测试：AuthenticationErrors 常量验证
    - 验证每个错误常量的 `message`、`errorCode`、`httpCode` 值正确
    - _需求: 6.1, 6.2_

- [x] 4. 检查点 — 确保核心组件编译通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 5. 实现 Spring MVC 集成层
  - [x] 5.1 实现 `AuthenticationInterceptor` 拦截器
    - 创建 `com.jstore.authentication.spring` 包
    - 实现 `HandlerInterceptor` 接口
    - 构造函数注入 `TokenProvider`、`TokenStore`、`List<AuthenticationConfigurer>`
    - 实现 `preHandle`：非 `HandlerMethod` 直接放行；按优先级判定是否需要认证；需要认证时执行 Token 验证流程
    - 实现 `requiresAuthentication` 私有方法：按 `@SkipLogin` → `@RequireLogin`（方法/类级）→ 路径排除 → 路径认证 → 默认放行 的优先级判定
    - 实现 Bearer Token 提取：从 `Authorization` 头解析 `"Bearer "` 前缀
    - 实现 Token 验证流程：`parseAccessToken` → `getAccessTokenJti` → `isAccessTokenBlacklisted` → `set(userId)`
    - 实现 `writeErrorResponse` 私有方法：使用 `ObjectMapper` 写入 JSON 错误响应
    - 实现 `afterCompletion`：始终调用 `AuthenticatedUserContext.clear()`
    - Token 验证逻辑包裹在 `try-catch(Exception)` 中，捕获非预期异常返回 `INTERNAL_ERROR`
    - 使用 Spring `AntPathMatcher` 进行路径匹配
    - _需求: 1.2, 1.3, 1.5, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 5.1, 5.5, 6.3, 6.4_

  - [x] 5.2 编写属性测试：统一认证判定
    - **属性 1：统一认证判定**
    - **验证需求: 1.2, 1.3, 1.4, 1.5, 2.2, 2.3, 2.4, 2.5, 3.2, 3.3**
    - 使用 Mockito 构造 `HandlerMethod`，生成随机注解组合 + 路径模式 + 请求路径，验证 `requiresAuthentication` 判定结果符合优先级规则

  - [x] 5.3 编写属性测试：Bearer Token 提取
    - **属性 2：Bearer Token 提取**
    - **验证需求: 4.1, 4.2**
    - 生成随机字符串作为 Authorization 头值，验证 `"Bearer " + s` 提取出 `s`；缺失/空/非 Bearer 前缀判定为缺失

  - [x] 5.4 编写属性测试：Token 验证错误映射
    - **属性 3：Token 验证错误映射**
    - **验证需求: 4.4, 4.6, 4.7**
    - Mock `TokenProvider` 和 `TokenStore` 的不同返回值组合，验证错误响应的 errorCode 和 JSON 结构正确

  - [x] 5.5 编写单元测试：AuthenticationInterceptor 异常处理
    - 验证 Token 验证过程中非预期异常返回 HTTP 500 + `Auth.InternalError`
    - 验证注解 + 路径配置同时满足时只执行一次验证
    - _需求: 3.1, 6.4_

- [x] 6. 实现参数解析器
  - [x] 6.1 实现 `CurrentUserIdArgumentResolver`
    - 实现 `HandlerMethodArgumentResolver` 接口
    - `supportsParameter`：检查参数标注了 `@CurrentUserId` 且类型为 `UserId`
    - `resolveArgument`：从 `AuthenticatedUserContext.getCurrentUserId()` 获取
    - _需求: 5.6_

  - [x] 6.2 编写单元测试：CurrentUserIdArgumentResolver
    - 验证支持正确参数类型和注解组合
    - 验证不支持错误参数类型或缺少注解的情况
    - 验证从 AuthenticatedUserContext 正确获取 UserId
    - _需求: 5.6_

- [x] 7. 实现自动配置
  - [x] 7.1 实现 `AuthenticationAutoConfiguration` 自动配置类
    - 使用 `@AutoConfiguration` 注解
    - 添加 `@ConditionalOnWebApplication(type = SERVLET)` 条件
    - 添加 `@ConditionalOnBean(TokenProvider::class, TokenStore::class)` 条件
    - 注册 `AuthenticationInterceptor` Bean（`@ConditionalOnMissingBean`）
    - 注册 `CurrentUserIdArgumentResolver` Bean（`@ConditionalOnMissingBean`）
    - 注册 `WebMvcConfigurer` Bean，添加拦截器（`addPathPatterns("/**")`）和参数解析器
    - _需求: 7.1, 7.3, 7.4_

  - [x] 7.2 创建 Spring Boot 自动配置注册文件
    - 创建 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
    - 写入 `com.jstore.authentication.spring.AuthenticationAutoConfiguration`
    - _需求: 7.5_

  - [x] 7.3 编写集成测试：自动配置条件激活
    - 使用 `@SpringBootTest` 或 `ApplicationContextRunner` 验证：
    - 容器中存在 `TokenProvider` 和 `TokenStore` Bean 时自动配置激活
    - 容器中缺少 `TokenProvider` 或 `TokenStore` Bean 时自动配置不激活
    - _需求: 7.1, 7.3, 7.4_

- [x] 8. 检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

## 备注

- 标记 `*` 的子任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点任务确保增量验证
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界情况
- 所有代码使用 Kotlin 语言，遵循项目 DDD 架构规范

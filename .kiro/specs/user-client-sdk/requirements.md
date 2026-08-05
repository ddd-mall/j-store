# 需求文档：j-store-user-client-sdk 用户鉴权 SDK

## 简介

j-store-user-client-sdk 是面向 C 端用户模块的轻量级鉴权 SDK 模块。该模块从现有 j-store-boot 中的 `JwtAuthenticationFilter` 逻辑中抽取通用的访问控制能力，封装为可复用的 Spring MVC 拦截器和注解。任何需要用户鉴权的 boot 模块只需引入该 SDK，即可通过 `@RequireLogin` 注解或拦截器路径配置实现接口级别的访问控制，并通过 `UserContext` 获取当前已认证用户的 ID。

该 SDK 依赖 j-store-user 领域层的 `TokenProvider` 和 `TokenStore` 接口，不依赖完整的 user 模块或 user-infrastructure 模块，保持轻量和解耦。模块定位类似 j-store-common-spring，是一个 Spring 集成模块，提供 Spring MVC 拦截器、注解处理和自动配置能力。

## 术语表

- **UserClientSdk**：用户鉴权 SDK 模块（j-store-user-client-sdk），提供可复用的访问控制组件
- **RequireLogin**：自定义注解，标注在 Controller 方法或类上，表示该接口需要用户登录后才能访问
- **AuthenticationInterceptor**：鉴权拦截器，基于 Spring MVC `HandlerInterceptor`，负责解析 AccessToken、校验黑名单、注入用户上下文
- **UserContext**：用户上下文持有者，基于 `ThreadLocal` 实现，存储当前请求中已认证用户的 UserId，供业务代码在请求生命周期内获取当前用户身份
- **UserClientSdkProperties**：SDK 配置属性类，通过 Spring `@ConfigurationProperties` 绑定，支持配置需要鉴权的路径模式和排除路径
- **UserClientSdkAutoConfiguration**：SDK 自动配置类，通过 Spring Boot `spring.factories` 或 `@AutoConfiguration` 机制自动注册拦截器和相关 Bean
- **TokenProvider**：令牌提供者接口，定义在 j-store-user 领域层，负责 AccessToken 的解析和验证
- **TokenStore**：令牌存储接口，定义在 j-store-user 领域层，负责 AccessToken 黑名单检查
- **UserId**：用户唯一标识，类型化 ID 值对象，定义在 j-store-user 领域层

## 需求

### 需求 1：@RequireLogin 注解声明式鉴权

**用户故事：** 作为开发者，我希望通过在 Controller 方法或类上标注 `@RequireLogin` 注解来声明该接口需要登录鉴权，以便以最小的代码侵入实现访问控制。

#### 验收标准

1. THE UserClientSdk SHALL 提供 `@RequireLogin` 注解，支持标注在方法级别和类级别
2. WHEN `@RequireLogin` 标注在类级别, THE AuthenticationInterceptor SHALL 对该类下所有接口方法执行鉴权校验
3. WHEN `@RequireLogin` 标注在方法级别, THE AuthenticationInterceptor SHALL 仅对该方法执行鉴权校验
4. WHEN 请求命中标注了 `@RequireLogin` 的接口且携带有效 AccessToken, THE AuthenticationInterceptor SHALL 允许请求通过并将 UserId 注入 UserContext
5. IF 请求命中标注了 `@RequireLogin` 的接口但未携带 AccessToken, THEN THE AuthenticationInterceptor SHALL 返回 HTTP 401 未认证错误响应，响应体包含 errorCode 和 message
6. IF 请求命中标注了 `@RequireLogin` 的接口但 AccessToken 签名无效或已过期, THEN THE AuthenticationInterceptor SHALL 返回 HTTP 401 未认证错误响应
7. IF 请求命中标注了 `@RequireLogin` 的接口但 AccessToken 的 jti 存在于 Redis 黑名单中, THEN THE AuthenticationInterceptor SHALL 返回 HTTP 401 未认证错误响应（令牌已被吊销）

### 需求 2：拦截器路径配置式鉴权

**用户故事：** 作为开发者，我希望通过配置文件指定需要鉴权的路径模式，以便在不修改代码的情况下灵活控制哪些接口需要登录。

#### 验收标准

1. THE UserClientSdkProperties SHALL 支持通过 `user-client-sdk.auth.include-patterns` 配置项指定需要鉴权的路径模式列表（Ant 风格，如 `/api/**`）
2. THE UserClientSdkProperties SHALL 支持通过 `user-client-sdk.auth.exclude-patterns` 配置项指定排除鉴权的路径模式列表（如 `/api/users/login`, `/api/users/register`）
3. WHEN 请求路径匹配 include-patterns 且不匹配 exclude-patterns, THE AuthenticationInterceptor SHALL 对该请求执行鉴权校验
4. WHEN 请求路径匹配 exclude-patterns, THE AuthenticationInterceptor SHALL 跳过鉴权校验，允许请求直接通过
5. WHEN 请求路径不匹配任何 include-patterns 且未命中 `@RequireLogin` 注解, THE AuthenticationInterceptor SHALL 跳过鉴权校验
6. THE UserClientSdkProperties SHALL 支持通过 `user-client-sdk.auth.enabled` 配置项控制 SDK 鉴权功能的全局开关，默认值为 true
7. WHILE `user-client-sdk.auth.enabled` 设置为 false, THE AuthenticationInterceptor SHALL 跳过所有鉴权校验

### 需求 3：用户上下文获取

**用户故事：** 作为开发者，我希望在业务代码中能方便地获取当前已认证用户的 UserId，以便在 Controller 或 Service 层中使用当前用户身份。

#### 验收标准

1. THE UserContext SHALL 提供 `currentUserId()` 静态方法，返回当前请求中已认证用户的 UserId
2. THE UserContext SHALL 提供 `currentUserIdOrNull()` 静态方法，当用户未认证时返回 null 而非抛出异常
3. WHEN AuthenticationInterceptor 鉴权通过, THE AuthenticationInterceptor SHALL 将 UserId 存入 UserContext（基于 ThreadLocal）
4. WHEN 请求处理完成（包括正常完成和异常完成）, THE AuthenticationInterceptor SHALL 在 `afterCompletion` 阶段清除 UserContext 中的 ThreadLocal 数据，防止内存泄漏
5. IF 在未经鉴权的请求中调用 `UserContext.currentUserId()`, THEN THE UserContext SHALL 抛出业务异常，提示用户未登录

### 需求 4：鉴权拦截器核心逻辑

**用户故事：** 作为系统，我希望鉴权拦截器能正确解析和校验 AccessToken，以便确保只有持有有效令牌的用户才能访问受保护资源。

#### 验收标准

1. WHEN 收到需要鉴权的请求, THE AuthenticationInterceptor SHALL 从 HTTP 请求头 `Authorization` 中提取 Bearer Token
2. WHEN 提取到 AccessToken, THE AuthenticationInterceptor SHALL 通过 TokenProvider 解析 Token 获取 UserId
3. WHEN Token 解析成功, THE AuthenticationInterceptor SHALL 通过 TokenStore 检查该 Token 的 jti 是否存在于黑名单中
4. WHEN Token 有效且未被吊销, THE AuthenticationInterceptor SHALL 将 UserId 注入 UserContext 并允许请求继续
5. THE AuthenticationInterceptor SHALL 返回统一格式的 JSON 错误响应，包含 `message` 和 `errorCode` 字段
6. IF Authorization 请求头缺失或格式不符合 `Bearer {token}` 模式, THEN THE AuthenticationInterceptor SHALL 返回 HTTP 401 错误，errorCode 为 `User.Token.Invalid`
7. IF TokenProvider 解析 AccessToken 返回 null（签名无效或已过期）, THEN THE AuthenticationInterceptor SHALL 返回 HTTP 401 错误，errorCode 为 `User.Token.Invalid`
8. IF Token 的 jti 存在于 TokenStore 黑名单中, THEN THE AuthenticationInterceptor SHALL 返回 HTTP 401 错误，errorCode 为 `User.Token.Invalid`

### 需求 5：Spring Boot 自动配置

**用户故事：** 作为开发者，我希望引入 SDK 依赖后无需手动配置即可自动启用鉴权功能，以便实现开箱即用的体验。

#### 验收标准

1. THE UserClientSdkAutoConfiguration SHALL 通过 Spring Boot 自动配置机制（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）自动注册
2. WHEN boot 模块引入 j-store-user-client-sdk 依赖, THE UserClientSdkAutoConfiguration SHALL 自动注册 AuthenticationInterceptor 到 Spring MVC 拦截器链
3. THE UserClientSdkAutoConfiguration SHALL 依赖 Spring 容器中已存在的 TokenProvider 和 TokenStore Bean（由 boot 模块的 Configuration 提供）
4. THE UserClientSdkAutoConfiguration SHALL 根据 UserClientSdkProperties 中的 include-patterns 和 exclude-patterns 配置拦截器的路径匹配规则
5. WHILE `user-client-sdk.auth.enabled` 设置为 false, THE UserClientSdkAutoConfiguration SHALL 不注册 AuthenticationInterceptor

### 需求 6：模块依赖与隔离

**用户故事：** 作为开发者，我希望 SDK 模块保持轻量，仅依赖必要的接口而非完整的 user 模块实现，以便其他限界上下文能低成本引入鉴权能力。

#### 验收标准

1. THE j-store-user-client-sdk 模块 SHALL 依赖 j-store-user 模块（获取 TokenProvider、TokenStore、UserId 等领域接口和类型）
2. THE j-store-user-client-sdk 模块 SHALL 依赖 Spring Boot Web Starter（获取 HandlerInterceptor、WebMvcConfigurer 等 Spring MVC 类型）
3. THE j-store-user-client-sdk 模块 SHALL 依赖 Spring Boot Autoconfigure（获取 @ConfigurationProperties、@AutoConfiguration 等自动配置类型）
4. THE j-store-user-client-sdk 模块 SHALL 不依赖 j-store-user-infrastructure 模块（JWT 实现、Redis 实现等基础设施细节）
5. THE j-store-user-client-sdk 模块 SHALL 不依赖 j-store-boot 模块
6. THE j-store-user-client-sdk 模块 SHALL 遵循项目现有的 Gradle Kotlin DSL 构建规范，使用 `libs.versions.toml` 版本目录管理依赖

### 需求 7：注解与路径配置的协同

**用户故事：** 作为开发者，我希望 `@RequireLogin` 注解和路径配置能协同工作，以便灵活组合使用两种鉴权方式。

#### 验收标准

1. WHEN 请求路径匹配 include-patterns 或命中 `@RequireLogin` 注解（满足任一条件）, THE AuthenticationInterceptor SHALL 执行鉴权校验
2. WHEN 请求路径匹配 exclude-patterns, THE AuthenticationInterceptor SHALL 跳过鉴权校验，即使该接口标注了 `@RequireLogin`（exclude-patterns 优先级最高）
3. WHEN 未配置任何 include-patterns（列表为空）, THE AuthenticationInterceptor SHALL 仅对标注了 `@RequireLogin` 注解的接口执行鉴权校验（纯注解模式）

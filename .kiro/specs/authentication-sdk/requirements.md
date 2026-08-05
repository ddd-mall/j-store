# 需求文档：Authentication SDK

## 简介

`j-store-authentication-sdk` 是一个跨切面的认证 SDK 模块，为 j-store 项目中的所有 Spring MVC 应用模块提供统一的"需要登录"声明能力和 Token 验证机制。该 SDK 支持两种方式声明接口需要登录：`@RequireLogin` 注解方式和拦截器路径配置方式。SDK 依赖 `j-store-user` 模块中定义的 `TokenProvider` 和 `TokenStore` 接口完成 Token 的解析与合法性校验（包括过期检测和黑名单检测），并将认证后的用户身份信息（UserId）传递给 Controller 层使用。

该 SDK 作为可复用类库，仅定义抽象接口和 Spring MVC 拦截器逻辑，不包含具体的 JWT 实现或 Redis 存储实现——这些由消费方应用（如 `j-store-boot`）通过依赖 `j-store-user-infrastructure` 提供。

## 术语表

- **Authentication_SDK**: `j-store-authentication-sdk` 模块，提供认证拦截器、注解和用户上下文持有器的可复用类库
- **RequireLogin_Annotation**: `@RequireLogin` 注解，标注在 Controller 方法或类上，声明该接口需要登录才能访问
- **AuthenticationInterceptor**: Spring MVC `HandlerInterceptor` 实现，负责在请求到达 Controller 之前执行 Token 验证逻辑
- **AuthenticationConfigurer**: 配置接口，允许消费方应用通过路径模式（URL Pattern）批量声明需要登录的接口
- **TokenProvider**: `j-store-user` 领域层定义的令牌提供者接口，负责 AccessToken 的解析和 JTI 提取
- **TokenStore**: `j-store-user` 领域层定义的令牌存储接口，负责 AccessToken 黑名单检测
- **AuthenticatedUserContext**: 线程级用户上下文持有器，存储当前请求已认证的 UserId，供 Controller 和 Service 层获取
- **UserId**: `j-store-user` 领域层定义的用户身份标识值对象，`data class UserId(val value: Long)`
- **AccessToken**: JWT 格式的访问令牌，有效期 15 分钟，包含 userId、jti、type="access" 等 claims
- **JTI**: JWT Token ID，AccessToken 的唯一标识符，用于黑名单机制
- **Bearer_Token**: HTTP Authorization 请求头中以 "Bearer " 为前缀的令牌格式

## 需求

### 需求 1：@RequireLogin 注解声明

**用户故事：** 作为开发者，我希望通过在 Controller 方法或类上添加 `@RequireLogin` 注解来声明该接口需要登录，以便以最小的代码侵入性实现接口级别的认证控制。

#### 验收标准

1. THE Authentication_SDK SHALL 提供 `@RequireLogin` 注解，该注解支持标注在方法级别和类级别
2. WHEN `@RequireLogin` 注解标注在 Controller 类上时，THE AuthenticationInterceptor SHALL 对该类中所有处理器方法执行登录验证
3. WHEN `@RequireLogin` 注解标注在 Controller 方法上时，THE AuthenticationInterceptor SHALL 仅对该方法执行登录验证
4. WHEN Controller 类上标注了 `@RequireLogin` 且某个方法不需要登录时，THE Authentication_SDK SHALL 提供 `@SkipLogin` 注解允许该方法跳过登录验证
5. WHEN 请求匹配到未标注 `@RequireLogin` 的处理器方法且该方法所在类也未标注 `@RequireLogin` 时，THE AuthenticationInterceptor SHALL 放行该请求不执行登录验证

### 需求 2：拦截器路径配置方式声明

**用户故事：** 作为开发者，我希望通过配置 URL 路径模式来批量声明需要登录的接口，以便在不修改 Controller 代码的情况下集中管理认证策略。

#### 验收标准

1. THE Authentication_SDK SHALL 提供 `AuthenticationConfigurer` 接口，允许消费方应用配置需要认证的 URL 路径模式和排除的 URL 路径模式
2. WHEN 消费方应用实现 `AuthenticationConfigurer` 并注册为 Spring Bean 时，THE AuthenticationInterceptor SHALL 根据配置的路径模式对匹配的请求执行登录验证
3. WHEN 请求 URL 匹配已配置的认证路径模式时，THE AuthenticationInterceptor SHALL 对该请求执行登录验证
4. WHEN 请求 URL 匹配已配置的排除路径模式时，THE AuthenticationInterceptor SHALL 放行该请求不执行登录验证
5. WHEN 同一请求同时匹配认证路径模式和排除路径模式时，THE AuthenticationInterceptor SHALL 优先应用排除路径模式，放行该请求

### 需求 3：注解与路径配置的协同工作

**用户故事：** 作为开发者，我希望注解方式和路径配置方式能够协同工作，以便灵活组合两种策略满足不同场景的认证需求。

#### 验收标准

1. WHEN 请求同时满足路径配置的认证条件和 `@RequireLogin` 注解条件时，THE AuthenticationInterceptor SHALL 执行一次登录验证（不重复验证）
2. WHEN 请求满足路径配置的排除条件但处理器方法标注了 `@RequireLogin` 时，THE AuthenticationInterceptor SHALL 执行登录验证（注解优先级高于路径排除）
3. WHEN 请求满足路径配置的认证条件但处理器方法标注了 `@SkipLogin` 时，THE AuthenticationInterceptor SHALL 放行该请求不执行登录验证（`@SkipLogin` 优先级最高）

### 需求 4：Token 验证

**用户故事：** 作为开发者，我希望 SDK 能够根据 j-store-user 模块的登录态定义验证请求中的 Token 合法性，以便确保只有持有有效 Token 的用户才能访问受保护的接口。

#### 验收标准

1. WHEN 需要登录验证的请求到达时，THE AuthenticationInterceptor SHALL 从 HTTP 请求的 `Authorization` 头中提取 Bearer_Token
2. WHEN Authorization 头缺失或格式不符合 "Bearer {token}" 模式时，THE AuthenticationInterceptor SHALL 返回 HTTP 401 响应，响应体包含错误码 "Auth.Token.Missing" 和错误消息
3. WHEN Bearer_Token 提取成功时，THE AuthenticationInterceptor SHALL 调用 TokenProvider 的 `parseAccessToken` 方法解析 Token 并获取 UserId
4. WHEN TokenProvider 的 `parseAccessToken` 返回 null（Token 无效或已过期）时，THE AuthenticationInterceptor SHALL 返回 HTTP 401 响应，响应体包含错误码 "Auth.Token.Invalid" 和错误消息
5. WHEN AccessToken 解析成功时，THE AuthenticationInterceptor SHALL 调用 TokenProvider 的 `getAccessTokenJti` 方法获取 JTI，并调用 TokenStore 的 `isAccessTokenBlacklisted` 方法检查该 JTI 是否在黑名单中
6. WHEN AccessToken 的 JTI 在黑名单中时，THE AuthenticationInterceptor SHALL 返回 HTTP 401 响应，响应体包含错误码 "Auth.Token.Blacklisted" 和错误消息
7. THE AuthenticationInterceptor SHALL 以 JSON 格式返回所有错误响应，JSON 结构包含 `message`（字符串）和 `errorCode`（字符串）两个字段

### 需求 5：认证用户上下文传递

**用户故事：** 作为开发者，我希望在 Token 验证通过后能够方便地获取当前登录用户的 UserId，以便在 Controller 和 Service 层中使用用户身份信息。

#### 验收标准

1. WHEN Token 验证通过时，THE AuthenticationInterceptor SHALL 将解析得到的 UserId 存储到 AuthenticatedUserContext 中
2. THE AuthenticatedUserContext SHALL 使用 ThreadLocal 机制存储 UserId，确保线程安全
3. THE AuthenticatedUserContext SHALL 提供 `getCurrentUserId(): UserId` 静态方法，返回当前线程的已认证 UserId
4. WHEN 在未认证的上下文中调用 `getCurrentUserId()` 时，THE AuthenticatedUserContext SHALL 提供 `getCurrentUserIdOrNull(): UserId?` 方法返回 null，以及 `getCurrentUserId()` 方法抛出 `AuthenticationException`
5. WHEN 请求处理完成后（无论成功或异常），THE AuthenticationInterceptor SHALL 清除 AuthenticatedUserContext 中的 UserId，防止 ThreadLocal 内存泄漏
6. THE Authentication_SDK SHALL 提供 `@CurrentUserId` 参数注解，配合 `HandlerMethodArgumentResolver` 实现，允许 Controller 方法参数直接注入当前 UserId

### 需求 6：错误处理与响应格式

**用户故事：** 作为开发者，我希望认证失败时返回统一格式的错误响应，以便前端能够统一处理认证相关的错误。

#### 验收标准

1. THE Authentication_SDK SHALL 定义 `AuthenticationErrors` 错误常量对象，包含所有认证相关的 `BusinessError` 定义
2. THE AuthenticationErrors SHALL 包含以下错误定义：TOKEN_MISSING（HTTP 401，错误码 "Auth.Token.Missing"）、TOKEN_INVALID（HTTP 401，错误码 "Auth.Token.Invalid"）、TOKEN_BLACKLISTED（HTTP 401，错误码 "Auth.Token.Blacklisted"）
3. THE AuthenticationInterceptor SHALL 使用 `AuthenticationErrors` 中定义的错误常量构造错误响应
4. IF AuthenticationInterceptor 在 Token 验证过程中捕获到非预期异常，THEN THE AuthenticationInterceptor SHALL 返回 HTTP 500 响应，响应体包含错误码 "Auth.InternalError" 和通用错误消息，不泄露异常详情

### 需求 7：SDK 自动配置与集成

**用户故事：** 作为开发者，我希望在消费方应用中引入 SDK 依赖后能够通过最少的配置完成集成，以便降低接入成本。

#### 验收标准

1. THE Authentication_SDK SHALL 提供 Spring Boot 自动配置类，在消费方应用引入 SDK 依赖后自动注册 AuthenticationInterceptor 和 `@CurrentUserId` 参数解析器
2. THE Authentication_SDK SHALL 依赖 `j-store-user` 模块（获取 TokenProvider 和 TokenStore 接口定义）和 `spring-boot-starter-web`（获取 Spring MVC 拦截器支持）
3. WHEN 消费方应用的 Spring 容器中存在 `TokenProvider` 和 `TokenStore` Bean 时，THE Authentication_SDK 的自动配置 SHALL 激活并注册拦截器
4. WHEN 消费方应用的 Spring 容器中不存在 `TokenProvider` 或 `TokenStore` Bean 时，THE Authentication_SDK 的自动配置 SHALL 不激活，不影响应用启动
5. THE Authentication_SDK SHALL 通过 `spring.factories` 或 `@AutoConfiguration` 机制注册自动配置类，消费方应用无需手动 `@Import`

### 需求 8：模块依赖边界

**用户故事：** 作为架构师，我希望 SDK 模块遵循 DDD 架构规范，保持清晰的依赖边界，以便 SDK 可被任意 boot 模块复用而不引入不必要的耦合。

#### 验收标准

1. THE Authentication_SDK SHALL 仅依赖 `j-store-common-core`、`j-store-user`（领域层接口）和 `spring-boot-starter-web`
2. THE Authentication_SDK SHALL 不依赖 `j-store-user-infrastructure`、JJWT 库、Spring Data Redis 或任何具体基础设施实现
3. THE Authentication_SDK SHALL 不包含 TokenProvider 或 TokenStore 的任何实现类
4. THE Authentication_SDK 中的注解类（`@RequireLogin`、`@SkipLogin`、`@CurrentUserId`）和 `AuthenticatedUserContext` SHALL 不依赖 Spring 框架，确保可在领域层引用
5. THE Authentication_SDK 中的拦截器和自动配置类 SHALL 依赖 Spring MVC，放置在独立的 Spring 集成包中

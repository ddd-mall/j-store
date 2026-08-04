# 实现计划：平台统一用户账号模型

## 概述

基于设计文档，在 j-store 项目中实现平台统一自然人账号限界上下文。账号模型不区分 C/B 类型，商户经营资格和权限由 shop 上下文的成员关系表达。从 Gradle 模块搭建和领域层核心模型开始，逐步构建值对象、聚合根、工厂、应用服务，再到基础设施层（JPA 持久化、JWT Token、BCrypt 密码哈希、Redis Token 存储），最后在 j-store-boot 中完成控制器和认证过滤器的接入。所有代码使用 Kotlin，遵循项目 DDD 架构规范。

## Tasks

- [x] 1. 搭建 Gradle 模块结构和依赖配置
  - [x] 1.1 创建 j-store-user-infrastructure 模块
    - 创建 `j-store-user-infrastructure/build.gradle.kts`，参照 `j-store-order-infrastructure/build.gradle.kts` 的模式
    - 添加 `api(project(":j-store-user"))` 依赖
    - 添加 Spring Data JPA、Spring Data Redis、PostgreSQL 等基础设施依赖
    - 在 `settings.gradle.kts` 中添加 `include("j-store-user-infrastructure")`
    - _需求: 10.1, 10.2_

  - [x] 1.2 更新 j-store-user 模块的 build.gradle.kts
    - 参照 `j-store-order/build.gradle.kts` 的模式
    - 添加 `api(project(":j-store-common-core"))` 依赖
    - 添加 Kotest（runner-junit5、assertions-core、property）和 Mockito 测试依赖
    - _需求: 10.1_

  - [x] 1.3 更新 j-store-boot 模块的 build.gradle.kts
    - 添加 `implementation(project(":j-store-user"))` 和 `implementation(project(":j-store-user-infrastructure"))` 依赖
    - _需求: 10.1_

  - [x] 1.4 在 gradle/libs.versions.toml 中添加 JWT 库依赖
    - 添加 jjwt（jjwt-api、jjwt-impl、jjwt-jackson）版本和库定义
    - 添加 spring-security-crypto 库定义（用于 BCrypt）
    - _需求: 2.3, 1.4_

- [x] 2. 实现领域层值对象和基础类型
  - [x] 2.1 创建 UserId、Nickname、Password 值对象和 UserAccountStatus 枚举
    - 在 `j-store-user/src/main/kotlin/com/jstore/user/domain/useraccount/` 包下创建
    - `UserId`：`data class UserId(override val value: Long) : Id<Long>(value)`，与 OrderId 模式一致
    - `Nickname`：data class，init 块校验非空且长度 ≤ 20
    - `Password`：data class，封装哈希密文，init 块校验非空
    - `UserAccountStatus`：枚举，包含 ACTIVE 和 DISABLED
    - _需求: 1.1, 1.7, 1.4, 3.1, 10.1_

  - [x] 2.2 创建 AuthTokenPair 值对象
    - 在同一包下创建，包含 accessToken、accessTokenExpiresAt、refreshToken、refreshTokenExpiresAt
    - _需求: 2.4, 7.3_

  - [x] 2.3 创建 UserRegisterCMD 注册命令
    - 在 `domain/useraccount/command/` 包下创建
    - 包含 phoneNumber（PhoneNumber）、nickname（String）、rawPassword（String）字段
    - _需求: 1.1, 12.1_

  - [x] 2.4 创建 UserAccountErrors 错误常量对象
    - 在同一包下创建，定义所有用户相关的 BusinessError 常量
    - 包含 USER_NOT_FOUND、PHONE_ALREADY_REGISTERED、PASSWORD_STRENGTH_INSUFFICIENT、NICKNAME_INVALID、PASSWORD_MISMATCH、OLD_PASSWORD_MISMATCH、ACCOUNT_DISABLED、ILLEGAL_STATE、TOKEN_INVALID、TOKEN_EXPIRED、REFRESH_TOKEN_REVOKED
    - _需求: 1.5, 1.6, 1.7, 2.5, 2.6, 2.7, 3.3, 3.4, 4.2, 5.2, 5.3, 6.3, 6.4, 6.5, 7.4, 7.5, 7.6, 8.4_

  - [x] 2.5 编写 Property 4 属性测试：Nickname 值对象校验
    - **Property 4: Nickname 值对象校验**
    - 使用 Kotest property testing 生成随机字符串，验证空白或超长字符串构造 Nickname 抛出异常，非空且 ≤ 20 字符的字符串构造成功
    - **验证: 需求 1.7, 5.2**

- [x] 3. 实现领域层接口和领域事件
  - [x] 3.1 创建 PasswordHasher 和 TokenProvider 领域接口
    - 在 `domain/useraccount/` 包下创建
    - `PasswordHasher`：定义 hash(rawPassword) 和 matches(rawPassword, hashedPassword) 方法
    - `TokenProvider`：定义 issueAccessToken、issueRefreshToken、parseAccessToken、parseRefreshToken、getAccessTokenJti、getAccessTokenRemainingSeconds 方法
    - _需求: 1.4, 2.2, 2.3, 9.5_

  - [x] 3.2 创建 TokenStore 领域接口
    - 在同一包下创建，定义 storeRefreshToken、getRefreshToken、removeRefreshToken、blacklistAccessToken、isAccessTokenBlacklisted 方法
    - _需求: 2.3, 7.2, 7.3, 8.1, 8.2_

  - [x] 3.3 创建 UserAccountRepository 仓储接口
    - 继承 `Repository<UserId, UserAccount>`
    - 定义 add、save、findById、findByPhoneNumber、existsById、existsByPhoneNumber 方法
    - _需求: 1.2, 1.5, 10.3_

  - [x] 3.4 创建领域事件类
    - 在 `domain/useraccount/event/` 包下创建
    - `UserAccountRegisteredEvent`：携带 userId 和 phoneNumber，实现 DomainEvent
    - `UserAccountLoggedInEvent`：携带 userId 和 loginTime，实现 DomainEvent
    - `UserAccountForcedOfflineEvent`：携带 userId 和 operationTime，实现 DomainEvent
    - _需求: 1.3, 11.1, 11.2, 11.3, 11.4_

- [x] 4. 实现 UserAccount 聚合根
  - [x] 4.1 创建 UserAccount 聚合根接口
    - 在 `domain/useraccount/` 包下创建
    - 继承 `AgreeGate<UserId>`，定义 phoneNumber、nickname、passwordHash、status、createTime、updateTime 属性
    - 定义 changeNickname、changePassword、disable、enable 行为方法，返回 `Result<Unit, BusinessError>`
    - _需求: 1.1, 3.1, 3.2, 5.1, 6.2, 10.2_

  - [x] 4.2 创建 UserAccountImpl 聚合根实现
    - 实现 UserAccount 接口，包含 domainEventQueue
    - `changeNickname`：校验新昵称有效性，更新 nickname 和 updateTime
    - `changePassword`：更新 passwordHash 和 updateTime
    - `disable`：校验当前状态为 ACTIVE，转移为 DISABLED，更新 updateTime
    - `enable`：校验当前状态为 DISABLED，转移为 ACTIVE，更新 updateTime
    - _需求: 3.1, 3.2, 3.3, 3.4, 5.1, 5.2, 6.2_

  - [x] 4.3 编写 Property 5 属性测试：账号状态转移规则
    - **Property 5: 账号状态转移规则**
    - 生成随机 UserAccount（ACTIVE/DISABLED 状态），验证合法状态转移成功、非法状态转移返回失败
    - **验证: 需求 3.1, 3.2, 3.3, 3.4**

  - [x] 4.4 编写 Property 6 属性测试：昵称修改生效
    - **Property 6: 昵称修改生效**
    - 生成随机 ACTIVE 状态的 UserAccount 和合法 Nickname，验证 changeNickname 后 nickname 等于新值
    - **验证: 需求 5.1**

- [x] 5. 实现 UserAccountFactory 工厂
  - [x] 5.1 创建 UserAccountFactory 接口和实现
    - 在 `domain/useraccount/` 包下创建接口
    - create 方法接受 UserRegisterCMD 和 PasswordHasher，校验密码强度（8-32 位，至少包含字母和数字），校验 Nickname 有效性
    - 创建 ACTIVE 状态的 UserAccountImpl，发布 UserAccountRegisteredEvent
    - 使用 SnowFlakSequence 生成 UserId
    - _需求: 1.1, 1.3, 1.4, 1.6, 1.7_

  - [x] 5.2 编写 Property 1 属性测试：注册创建不变量
    - **Property 1: 注册创建不变量**
    - 生成随机合法 PhoneNumber + Nickname + 密码，验证工厂创建的 UserAccount 状态为 ACTIVE，domainEventQueue 包含 UserAccountRegisteredEvent 且事件携带正确的 userId 和 phoneNumber
    - **验证: 需求 1.1, 1.3, 11.1**

  - [x] 5.3 编写 Property 3 属性测试：无效密码拒绝
    - **Property 3: 无效密码拒绝**
    - 生成不满足强度要求的随机字符串（纯字母/纯数字/过短/过长），验证密码强度校验返回失败
    - **验证: 需求 1.6, 6.4**

- [x] 6. 实现 UserAccountService 应用服务
  - [x] 6.1 创建 UserAccountService 类
    - 在 `service/` 包下创建
    - 注入 UserAccountFactory、UserAccountRepository、PasswordHasher、TokenProvider、TokenStore、DomainEventPublisher
    - 实现 register 方法：查重手机号 → 工厂创建 → 持久化 → 发布事件
    - 实现 login 方法：查找用户 → 验证密码 → 检查状态 → 签发 AuthTokenPair → 存储 RefreshToken 到 Redis → 发布登录事件
    - 实现 refreshToken 方法：解析 RefreshToken → 验证 Redis 存储一致性 → 检查用户状态 → 签发新 AuthTokenPair → Rotation 替换旧 RefreshToken
    - 实现 findById、changeNickname、changePassword 方法
    - 实现 disable 方法：禁用账号 + 自动执行强制下线
    - 实现 enable、forceOffline 方法
    - 所有方法遵循"加载聚合 → 执行领域行为 → 保存 → 发布事件"编排模式
    - 所有可能失败的操作返回 `Result<T, BusinessError>`
    - _需求: 1.2, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 8.1, 8.2, 8.3, 8.4, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.8, 12.9, 12.10, 12.11_

  - [x] 6.2 编写 UserAccountService 单元测试
    - 使用 Mockito mock 仓储和基础设施接口
    - 测试注册流程：成功注册、手机号重复拒绝
    - 测试登录流程：成功登录、用户不存在、密码错误、账号禁用
    - 测试 Token 刷新流程：成功刷新、Token 无效、Token 不一致（删除并返回错误）、账号禁用
    - 测试密码修改流程：成功修改、旧密码错误、新密码强度不足
    - 测试强制下线流程：黑名单 AccessToken + 删除 RefreshToken
    - 测试禁用自动下线：disable 后自动执行 forceOffline
    - _需求: 1.2, 1.5, 2.1-2.7, 6.1-6.5, 7.1-7.6, 8.1-8.4, 12.1-12.11_

- [x] 7. 检查点 - 确保领域层和应用服务层测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 8. 实现基础设施层 - JPA 持久化
  - [x] 8.1 创建 UserAccountPO JPA 实体类
    - 在 `j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/persistence/` 包下创建
    - 使用 `@Entity`、`@Table(name = "user_accounts")` 注解
    - 字段映射：id、phoneNumber（unique）、nickname、passwordHash、status（EnumType.STRING）、createTime、updateTime
    - _需求: 1.2_

  - [x] 8.2 创建 UserAccountPOJpaRepository 接口
    - 继承 `JpaRepository<UserAccountPO, Long>`
    - 定义 findByPhoneNumber、existsByPhoneNumber 查询方法
    - _需求: 1.2, 1.5, 10.3_

  - [x] 8.3 实现 UserAccountRepositoryImpl 仓储实现
    - 在 `j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/` 包下创建
    - 包含 Converter 对象实现 PO ↔ 领域模型转换
    - 实现 add、save、findById、findByPhoneNumber、existsById、existsByPhoneNumber 方法
    - 使用 `@Repository` 注解
    - _需求: 1.2, 1.5, 4.1, 10.3_

- [x] 9. 实现基础设施层 - JWT、BCrypt、Redis
  - [x] 9.1 实现 JwtTokenProvider
    - 在 `j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/` 包下创建
    - 使用 jjwt 库实现 TokenProvider 接口
    - AccessToken 有效期 15 分钟，claims 包含 userId、jti、exp、iat
    - RefreshToken 有效期 7 天
    - 实现 issueAccessToken、issueRefreshToken、parseAccessToken、parseRefreshToken、getAccessTokenJti、getAccessTokenRemainingSeconds
    - _需求: 2.3, 2.4, 7.1, 9.5_

  - [x] 9.2 实现 BCryptPasswordHasher
    - 在同一包下创建，使用 Spring Security Crypto 的 BCryptPasswordEncoder
    - 实现 hash 和 matches 方法
    - _需求: 1.4, 2.2, 6.1, 6.2_

  - [x] 9.3 实现 RedisTokenStore
    - 在同一包下创建，使用 Spring Data Redis 的 StringRedisTemplate
    - storeRefreshToken：key 为 `refresh_token:{userId}`，TTL 7 天
    - getRefreshToken / removeRefreshToken：操作 RefreshToken
    - blacklistAccessToken：key 为 `token_blacklist:{jti}`，TTL 为 AccessToken 剩余有效期
    - isAccessTokenBlacklisted：检查黑名单
    - _需求: 2.3, 7.2, 7.3, 7.5, 8.1, 8.2_

  - [x] 9.4 编写 Property 2 属性测试：密码哈希 round-trip
    - **Property 2: 密码哈希 round-trip**
    - 使用 BCryptPasswordHasher 实例，生成随机合法密码字符串，验证 hash 后 matches 返回 true
    - **验证: 需求 1.4, 2.2, 6.1, 6.2**

  - [x] 9.5 编写 Property 7 属性测试：Token 签发解析 round-trip
    - **Property 7: Token 签发解析 round-trip**
    - 使用 JwtTokenProvider 实例，生成随机 UserId，验证签发 AccessToken 后解析返回相同 UserId 且包含 jti；签发 RefreshToken 后解析返回相同 UserId
    - **验证: 需求 7.1, 9.5**

- [x] 10. 检查点 - 确保基础设施层测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 11. 实现接口层 - 控制器和认证过滤器
  - [x] 11.1 创建 UserBootConfiguration 配置类
    - 在 `j-store-boot/src/main/kotlin/com/jstore/user/config/` 包下创建
    - 注册 UserAccountFactory、UserAccountService 等 Bean
    - 配置 JWT 密钥等属性
    - _需求: 12.1-12.9_

  - [x] 11.2 创建 UserAccountController 控制器
    - 在 `j-store-boot/src/main/kotlin/com/jstore/user/controller/` 包下创建
    - 提供 REST API：POST /api/users/register、POST /api/users/login、POST /api/users/refresh-token、GET /api/users/{id}、PUT /api/users/{id}/nickname、PUT /api/users/{id}/password、POST /api/users/{id}/disable、POST /api/users/{id}/enable、POST /api/users/{id}/force-offline
    - 调用 UserAccountService 编排用例，将 Result 转换为 HTTP 响应
    - _需求: 1.1, 2.1, 4.1, 5.1, 6.1, 7.1, 8.1, 12.1-12.9_

  - [x] 11.3 创建 JwtAuthenticationFilter 认证过滤器
    - 在 `j-store-boot/src/main/kotlin/com/jstore/user/filter/` 包下创建
    - 继承 OncePerRequestFilter
    - 拦截请求 → 从 Authorization header 提取 AccessToken → 解析 JWT 签名和有效期 → 检查 Redis 黑名单 → 通过则将 userId 注入请求上下文
    - 配置白名单路径（注册、登录、刷新 Token 等不需要认证的接口）
    - 无效/过期/被吊销的 Token 返回 401
    - _需求: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 12. 创建数据库 DDL 脚本
  - [x] 12.1 创建 user_accounts 表的 DDL 迁移脚本
    - 在 `j-store-boot/src/main/resources/db/migration/` 下创建 SQL 脚本
    - 包含 user_accounts 表创建语句（id、phone_number、nickname、password_hash、status、create_time、update_time）
    - phone_number 列添加 UNIQUE 约束
    - _需求: 1.2_

- [x] 13. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点任务确保增量验证
- 属性测试使用 Kotest property testing 模块验证正确性属性
- 单元测试验证具体示例和边界情况
- 领域模块（j-store-user）不依赖任何 Spring/JPA 框架，保持领域层纯净
- 基础设施层属性测试（Property 2、7）需要实际的 BCrypt 和 JWT 实现，放在基础设施层测试中

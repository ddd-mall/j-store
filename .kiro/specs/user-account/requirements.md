# 需求文档：平台统一用户账号模型

## 简介

在 j-store-user 模块中搭建平台统一自然人账号领域模型，支持账号注册与登录功能。用户账号是电商平台的全局登录身份，可用于买家行为，也可通过商户成员关系参与一个或多个商户经营。其他限界上下文通过 UserId 引用用户身份，不通过账号类型区分 C 端或 B 端。

本需求聚焦于用户身份管理（注册、登录、基本信息维护），权限/授权体系明确不在本期范围内。认证方式采用双 Token 机制（短期 Access Token + 长期 Refresh Token），配合 Redis Token 黑名单实现即时踢人下线和拉黑能力。

## 术语表

- **UserAccount**：全局自然人账号聚合根，封装注册、登录、信息维护等生命周期行为
- **UserId**：用户唯一标识，类型化 ID 值对象，其他限界上下文通过 UserId 引用用户
- **PhoneNumber**：手机号值对象，已在 j-store-common-core 中定义，包含格式校验
- **Nickname**：用户昵称值对象，封装昵称长度和内容校验规则
- **Password**：密码值对象，封装密码强度校验和哈希存储逻辑；存储形式为哈希后的密文，明文密码不持久化
- **UserAccountStatus**：用户账号状态枚举，包含 ACTIVE（正常）和 DISABLED（禁用）
- **UserAccountRepository**：用户账号仓储接口，定义在领域层，实现在基础设施层
- **UserAccountService**：用户账号应用服务，编排注册、登录等用例
- **UserAccountFactory**：用户账号工厂，负责创建初始状态的 UserAccount 聚合
- **UserAccountErrors**：用户账号领域错误常量对象，定义所有用户相关的业务错误
- **JWT**：JSON Web Token，用于登录成功后颁发的认证令牌
- **AccessToken**：短期访问令牌，有效期 15 分钟，携带 userId 等 claims，用于 API 请求认证；校验时无需查库（无状态），但需检查 Redis 黑名单
- **RefreshToken**：长期刷新令牌，有效期 7 天，存储在 Redis 中（key 为 userId），用于在 AccessToken 过期后换取新的 AccessToken；不可用于 API 请求认证
- **TokenBlacklist**：Token 黑名单，基于 Redis 实现，存储已失效的 AccessToken（key 为 token 的 jti，TTL 为 token 剩余有效期）；用于即时踢人下线和拉黑场景
- **AuthTokenPair**：认证令牌对值对象，封装 accessToken、refreshToken 及各自的过期时间
- **TokenProvider**：令牌提供者接口，定义在领域层，负责 AccessToken 和 RefreshToken 的签发、解析和验证；实现在基础设施层（JWT 实现）
- **PasswordHasher**：密码哈希服务接口，定义在领域层，负责密码的哈希和验证；实现在基础设施层

## 需求

### 需求 1：用户注册

**用户故事：** 作为平台用户，我希望通过手机号注册账号，以便获得可用于消费和商户经营的平台身份。

#### 验收标准

1. WHEN 用户提交注册请求（包含 PhoneNumber、Nickname 和明文密码）, THE UserAccountFactory SHALL 创建一个状态为 ACTIVE 的 UserAccount 聚合，并为其分配唯一的 UserId
2. WHEN UserAccount 被成功创建, THE UserAccountRepository SHALL 持久化该 UserAccount
3. WHEN UserAccount 被成功创建, THE UserAccount SHALL 发布 UserAccountRegisteredEvent 领域事件，事件中携带 userId 和 phoneNumber
4. THE Password SHALL 在创建时通过 PasswordHasher 将明文密码转换为哈希密文，明文密码不存储在 UserAccount 中
5. IF 注册时提供的 PhoneNumber 已被其他 UserAccount 使用, THEN THE UserAccountService SHALL 拒绝注册并返回手机号已注册的业务错误
6. IF 注册时提供的明文密码不满足强度要求（长度 8-32 位，至少包含字母和数字）, THEN THE UserAccountFactory SHALL 拒绝创建并返回密码强度不足的业务错误
7. IF 注册时提供的 Nickname 为空或长度超过 20 个字符, THEN THE UserAccountFactory SHALL 拒绝创建并返回昵称无效的业务错误

### 需求 2：用户登录

**用户故事：** 作为平台用户，我希望通过手机号和密码登录，以便获取认证令牌并访问平台功能。

#### 验收标准

1. WHEN 用户提交登录请求（包含 PhoneNumber 和明文密码）, THE UserAccountService SHALL 根据 PhoneNumber 查找对应的 UserAccount
2. WHEN UserAccount 被找到, THE UserAccountService SHALL 通过 PasswordHasher 验证明文密码与存储的哈希密文是否匹配
3. WHEN 密码验证通过且 UserAccount 状态为 ACTIVE, THE UserAccountService SHALL 通过 TokenProvider 签发 AuthTokenPair（包含 AccessToken 和 RefreshToken），并将 RefreshToken 存储到 Redis（key 为 `refresh_token:{userId}`，TTL 为 7 天）
4. WHEN 登录成功, THE UserAccountService SHALL 返回 AuthTokenPair，其中 AccessToken 有效期为 15 分钟，RefreshToken 有效期为 7 天
5. IF 提供的 PhoneNumber 未找到对应的 UserAccount, THEN THE UserAccountService SHALL 返回账号不存在的业务错误
6. IF 密码验证不通过, THEN THE UserAccountService SHALL 返回密码错误的业务错误
7. IF UserAccount 状态为 DISABLED, THEN THE UserAccountService SHALL 返回账号已禁用的业务错误

### 需求 3：用户账号状态管理

**用户故事：** 作为系统运营方，我希望能禁用和启用用户账号，以便在违规场景下管控用户行为。

#### 验收标准

1. WHILE UserAccount 处于 ACTIVE 状态, WHEN 运营方发起禁用请求, THE UserAccount SHALL 将状态转移为 DISABLED
2. WHILE UserAccount 处于 DISABLED 状态, WHEN 运营方发起启用请求, THE UserAccount SHALL 将状态转移为 ACTIVE
3. IF 对已处于 DISABLED 状态的 UserAccount 发起禁用请求, THEN THE UserAccount SHALL 拒绝操作并返回状态不合法的业务错误
4. IF 对已处于 ACTIVE 状态的 UserAccount 发起启用请求, THEN THE UserAccount SHALL 拒绝操作并返回状态不合法的业务错误

### 需求 4：用户基本信息查询

**用户故事：** 作为平台用户，我希望能查看自己的账号基本信息，以便确认个人资料。

#### 验收标准

1. WHEN 用户提供 UserId 查询账号信息, THE UserAccountService SHALL 返回对应 UserAccount 的 userId、phoneNumber、nickname 和 status
2. IF 提供的 UserId 对应的 UserAccount 不存在, THEN THE UserAccountService SHALL 返回用户不存在的业务错误
3. THE UserAccountService SHALL 在返回结果中排除密码哈希等敏感信息

### 需求 5：用户昵称修改

**用户故事：** 作为平台用户，我希望能修改自己的昵称，以便更新个人展示信息。

#### 验收标准

1. WHEN 用户提交昵称修改请求（包含 UserId 和新 Nickname）, THE UserAccount SHALL 将昵称更新为新值
2. IF 新 Nickname 为空或长度超过 20 个字符, THEN THE UserAccount SHALL 拒绝修改并返回昵称无效的业务错误
3. IF 提供的 UserId 对应的 UserAccount 不存在, THEN THE UserAccountService SHALL 返回用户不存在的业务错误

### 需求 6：密码修改

**用户故事：** 作为平台用户，我希望能修改自己的登录密码，以便保障账号安全。

#### 验收标准

1. WHEN 用户提交密码修改请求（包含 UserId、旧密码和新密码）, THE UserAccountService SHALL 通过 PasswordHasher 验证旧密码与存储的哈希密文是否匹配
2. WHEN 旧密码验证通过, THE UserAccount SHALL 通过 PasswordHasher 将新密码哈希后更新存储的密码哈希值
3. IF 旧密码验证不通过, THEN THE UserAccountService SHALL 返回旧密码错误的业务错误
4. IF 新密码不满足强度要求（长度 8-32 位，至少包含字母和数字）, THEN THE UserAccountService SHALL 返回密码强度不足的业务错误
5. IF 提供的 UserId 对应的 UserAccount 不存在, THEN THE UserAccountService SHALL 返回用户不存在的业务错误

### 需求 7：Token 刷新

**用户故事：** 作为平台用户，我希望在 AccessToken 过期后能通过 RefreshToken 自动获取新的令牌对，以便无需重新登录即可继续使用平台。

#### 验收标准

1. WHEN 用户提交刷新请求（包含 RefreshToken）, THE UserAccountService SHALL 验证 RefreshToken 的有效性（签名、过期时间）
2. WHEN RefreshToken 有效, THE UserAccountService SHALL 从 Redis 中查找对应的存储记录（key 为 `refresh_token:{userId}`），验证提交的 RefreshToken 与存储值一致
3. WHEN RefreshToken 验证通过, THE UserAccountService SHALL 签发新的 AuthTokenPair（新 AccessToken + 新 RefreshToken），并用新 RefreshToken 替换 Redis 中的旧值（Refresh Token Rotation）
4. IF RefreshToken 签名无效或已过期, THEN THE UserAccountService SHALL 返回令牌无效的业务错误
5. IF RefreshToken 与 Redis 中存储的值不一致（可能已被轮换或被盗用）, THEN THE UserAccountService SHALL 删除该用户的 RefreshToken 并返回令牌已失效的业务错误，要求用户重新登录
6. IF RefreshToken 对应的 UserAccount 状态为 DISABLED, THEN THE UserAccountService SHALL 删除 RefreshToken 并返回账号已禁用的业务错误

### 需求 8：强制下线（踢人）

**用户故事：** 作为系统运营方，我希望能即时强制指定用户下线，以便在安全事件或违规场景下立即中断用户会话。

#### 验收标准

1. WHEN 运营方对指定 UserId 发起强制下线请求, THE UserAccountService SHALL 将该用户当前的 AccessToken 加入 Redis 黑名单（key 为 `token_blacklist:{jti}`，TTL 为该 AccessToken 的剩余有效期）
2. WHEN 强制下线执行, THE UserAccountService SHALL 同时删除该用户在 Redis 中的 RefreshToken（key 为 `refresh_token:{userId}`），阻止后续刷新
3. WHEN 用户账号被禁用（需求 3）, THE UserAccountService SHALL 自动执行强制下线流程（黑名单 AccessToken + 删除 RefreshToken）
4. IF 指定 UserId 对应的 UserAccount 不存在, THEN THE UserAccountService SHALL 返回用户不存在的业务错误

### 需求 9：请求认证校验

**用户故事：** 作为系统，我希望每个 API 请求都经过认证校验，以便确保只有持有有效令牌的用户才能访问受保护资源。

#### 验收标准

1. WHEN 收到携带 AccessToken 的 API 请求, THE 认证过滤器 SHALL 依次执行：解析 JWT 签名和有效期 → 检查 Redis 黑名单（key 为 `token_blacklist:{jti}`）→ 通过则将 userId 注入请求上下文
2. IF AccessToken 签名无效或已过期, THEN THE 认证过滤器 SHALL 返回 401 未认证错误
3. IF AccessToken 的 jti 存在于 Redis 黑名单中, THEN THE 认证过滤器 SHALL 返回 401 未认证错误（令牌已被吊销）
4. IF 请求未携带 AccessToken 且访问的是受保护资源, THEN THE 认证过滤器 SHALL 返回 401 未认证错误
5. THE AccessToken 的 JWT claims SHALL 至少包含：userId、jti（唯一标识，用于黑名单匹配）、exp（过期时间）、iat（签发时间）

### 需求 10：用户身份跨上下文引用

**用户故事：** 作为开发者，我希望其他限界上下文能通过 UserId 引用用户身份，以便在订单、商品等模块中关联用户信息。

#### 验收标准

1. THE UserId SHALL 采用 `data class UserId(override val value: Long) : Id<Long>(value)` 的类型化 ID 模式，与 OrderId 保持一致
2. THE UserAccount 聚合 SHALL 通过 UserId 作为唯一标识，其他限界上下文通过 UserId 值引用用户，不直接引用 UserAccount 对象
3. THE UserAccountRepository SHALL 提供 existsById 方法，供其他上下文在需要时验证用户是否存在

### 需求 11：领域事件定义

**用户故事：** 作为开发者，我希望用户账号的关键操作产生领域事件，以便其他限界上下文能响应用户状态变化。

#### 验收标准

1. THE UserAccountRegisteredEvent SHALL 携带 userId 和 phoneNumber
2. THE UserAccountLoggedInEvent SHALL 携带 userId 和 loginTime
3. THE UserAccountForcedOfflineEvent SHALL 携带 userId 和操作时间
4. FOR ALL 用户领域事件, THE 事件 SHALL 实现 DomainEvent 接口

### 需求 12：应用服务编排

**用户故事：** 作为开发者，我希望应用服务层提供完整的用户操作用例编排，以便控制器层能调用统一的入口执行用户业务。

#### 验收标准

1. THE UserAccountService SHALL 提供 register 方法，接受注册命令参数，编排用户注册用例
2. THE UserAccountService SHALL 提供 login 方法，接受 PhoneNumber 和明文密码参数，编排用户登录用例，返回 AuthTokenPair
3. THE UserAccountService SHALL 提供 refreshToken 方法，接受 RefreshToken 参数，编排令牌刷新用例，返回新的 AuthTokenPair
4. THE UserAccountService SHALL 提供 findById 方法，接受 UserId 参数，编排用户信息查询用例
5. THE UserAccountService SHALL 提供 changeNickname 方法，接受 UserId 和新 Nickname 参数，编排昵称修改用例
6. THE UserAccountService SHALL 提供 changePassword 方法，接受 UserId、旧密码和新密码参数，编排密码修改用例
7. THE UserAccountService SHALL 提供 disable 方法，接受 UserId 参数，编排账号禁用用例（含自动强制下线）
8. THE UserAccountService SHALL 提供 enable 方法，接受 UserId 参数，编排账号启用用例
9. THE UserAccountService SHALL 提供 forceOffline 方法，接受 UserId 参数，编排强制下线用例
10. FOR ALL 应用服务方法, THE UserAccountService SHALL 遵循"加载聚合 → 执行领域行为 → 保存 → 发布事件"的编排模式
11. FOR ALL 可能失败的操作, THE UserAccountService SHALL 返回 Result<T, BusinessError> 类型

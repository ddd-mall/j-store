# 用户资料跨上下文查询交付摘要

## 已交付行为

- User 发布只包含用户 ID、昵称、规范 E.164 已验证手机号和账号状态的标量查询契约。
- 模块化单体默认通过 `UserProfileReader` 和进程内 `UserProfileQueryService` 读取；remote 模式只装配带超时和 Bearer 服务凭证的 HTTP 客户端，不会静默回退本地消费实现。
- User 内部端点默认关闭，启用时要求至少 32 字符服务凭证；用户不存在返回 404，其它依赖或资料错误显式失败。
- Order 通过本地 ACL 读取 ACTIVE 用户，在创建时冻结权威买家快照；客户端不能提交买家昵称或手机号。
- 买家订单详情和取消校验订单所有权，越权按订单不存在处理；公开订单响应不返回账号昵称或已验证手机号。
- Shop 通过发布契约验证账号存在，不再直接读取 User Repository。
- Payment、Fulfillment 和 Accounting 继续使用交易快照，不回查可变用户资料解释历史交易。
- 开发验证码发送器不记录验证码，只记录脱敏手机号。

## 配置与兼容性

- 默认 `jstore.user-query.mode=local`，内部服务端点默认关闭。
- 微服务消费方设置 `mode=remote`、服务 URL、至少 32 字符 token 和 connect/read timeout；User 提供方启用 server endpoint 并使用相同服务凭证。
- 本次按用户授权进行了破坏性契约调整：`OrderCreateCMD` 删除买家昵称和手机号，订单详情/取消用例新增认证买家 ID，公开订单响应删除买家昵称和手机号。
- 无数据库迁移；切回单体模式只需恢复 `mode=local` 并关闭内部端点。

## 验证证据

- 四个安全相关模块以 `--rerun-tasks --no-daemon` 强制执行，55 个任务全部成功。
- `./scripts/quality-gate.sh` 通过：28 个规格契约测试、14 个治理测试、6 个工具测试及 167 个 Gradle 任务成功。
- `./gradlew :j-store-boot:bootJar` 成功，组合 Jar 包含 `j-store-user-api` 和 `j-store-user-client-spring`。
- 定向 Spotless、`git diff --check` 通过。
- 独立安全复评最终 PASS，无未解决 blocker。

## 剩余风险

- User API DTO 可补 E.164 边界长度和错误消息不回显原值的直接单测。
- 内部端点尚未在真实 `AuthenticationInterceptor` 组合下做 HTTP 集成测试。
- connect/read timeout 已验证配置与正值门禁，尚未通过真实延迟端点验证触发行为。

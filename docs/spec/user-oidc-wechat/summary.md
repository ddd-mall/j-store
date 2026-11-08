# 本地实现与验收记录

日期：2026-11-08。分支：`codex/user-oidc-wechat`。

状态：已按用户确认方向实现本地候选；整体安全验收尚未完成，不代表获准合并或生产发布。真实微信/短信联调及独立安全审查未执行，依赖漏洞扫描有继承发现。

## 实现结果

- Spring Boot 3.5.16 BOM 管理 Spring Authorization Server 1.5.8 / Spring Security 6.5.11，没有为本功能升级技术栈。
- 统一 Authorization Code + PKCE S256、OIDC discovery/JWKS/ID Token/UserInfo、同意、刷新轮换、撤销、introspection 和 RP 登出。Public client 无 Refresh Token，confidential BFF 按 offline_access 获得刷新资格。
- 微信网站扫码通过服务端固定端点兑换，校验 token/userinfo 的 openid 一致性；state 与 onboarding 绑定浏览器、app 和原授权请求。首次验证手机号后复用已有账号，或创建具有正常密码/昵称的新账号。
- 数据库保存授权码和 Refresh Token 摘要，以事务行锁限制并发兑换；保留已花费 Refresh 摘要，重放撤销 family。协议响应在提交后返回，提交失败不返回凭证。
- Redis 账号会话按 issuer 隔离，随机初始化撤销代次；屏障丢失不恢复旧会话。改密/禁用/强制下线保持立即撤销，密码登录与改密/禁用串行化。
- 已删除旧 token 端点、TokenProvider、AuthTokenPair、HS256 JWT 和 Redis 刷新槽位；注册、资料、自助改密等保留。Order 认证域配置同步改为 `jstore.oidc.issuer`。
- 当前基线和初始化快照直接演进；未执行现有数据库清空、生产迁移、微信后台配置、真实密钥操作、合并或发布。

运行配置及客户端切换方法见 [runbook](runbook.md)。新签名材料由外部资源提供，没有生产随机密钥兜底。

## 自动化证据

| 验收范围 | 证据 |
|---|---|
| AUTH-1 / AUTH-2 标准协议 | `OidcProtocolIntegrationTest`：真实 PostgreSQL 与 Redis 下的 consent、PKCE、ID Token nonce/aud、UserInfo、public client、Refresh 轮换/重放、scope 收窄、跨 client introspection、账号会话撤销、RP 登出、登录 CSRF 和 session fixation |
| 授权码并发与摘要 | `DigestAuthorizationServiceTest`：数据库无明文 code/refresh，多个并发事务只有一个消费成功，旧 Refresh 重放使后继 Access Token 无效，数据库身份唯一约束 |
| 提交失败 | `ProtocolTransactionFilterTest`：提交异常清空 body/Location，不泄漏令牌或授权码跳转 |
| 重认证 | `OidcReauthenticationFilterTest`：prompt=login 强制一次新认证，SavedRequest continue 参数不会导致循环 |
| WX-1 上游身份 | `WechatIdentityGatewayTest`：固定假 HTTP 服务的成功、缺失 unionid、openid 不一致与上游 token 不外泄 |
| WX-1 / WX-2 组合流程 | `OidcProtocolIntegrationTest`：跨浏览器 state 拒绝、首次绑定前无登录态、手机号验证后持久化已有账号绑定、会话 ID 更换、回调重放拒绝；上游与短信网关为测试替身 |
| WX-2 应用规则 | `ExternalAccountBindingServiceTest`：验证证明必需、已有账号复用、新账号创建、不覆盖其他绑定 |
| 临时意图/验证码 | `WechatLoginInfrastructureTest`：真实 Redis 单次消费、浏览器绑定、注册与微信验证码用途隔离 |
| 账号会话 | `RedisAccountSessionStoreIntegrationTest`：多设备、issuer 隔离、撤销后陈旧登录拒绝、丢失 epoch 后不复活旧凭证 |
| OPS-1 | `OidcPropertiesTest`、`ProductionProfileContractTest`：显式配置、HTTPS、精确回调、public/offline_access 限制；JWKS 不包含私钥；完整 Flyway 基线与非 Web 根上下文回归 |
| 根应用实际运行 | `OidcApplicationRuntimeTest`：真实 Tomcat、PostgreSQL、Redis 启动；discovery 使用固定 issuer，登录页创建 Redis Session，认证 SDK 和会话存储装配成功，业务接口返回 JSON 401 |

已执行的相关模块命令：

```bash
./gradlew :j-store-user-domain:test :j-store-user-application:test :j-store-user-infrastructure:test :j-store-user-boot:test :j-store-authentication-spring-sdk:test
./gradlew :j-store-user-boot:test :j-store-boot:test
./gradlew :j-store-user-infrastructure:dependencyInsight --dependency spring-security-oauth2-authorization-server --configuration runtimeClasspath
./gradlew :j-store-boot:cyclonedxDirectBom
```

最初的失败测试覆盖缺失协议 Store、凭证服务、账号绑定服务、微信 HTTP/state 适配器、旧端点删除及配置校验；后续增加了组合及安全反例。丢失 epoch 的回归也先复现再修复。未把所有测试都描述为实现前建立。

完整门禁 `./scripts/quality-gate.sh`：PASS。治理、规格/工具契约、源码归属、Spotless、依赖解析、许可证、Gradle 全仓回归及 JAR 许可证检查全部通过。Gradle XML 共 224 个测试套件、755 个测试，失败/错误/跳过均为 0；其中 user domain/application/infrastructure/boot 分别为 13/12/16/31 项，认证 SDK 为 25 项。66 个 JAR 许可证校验通过。

先前门禁发现旧生产 JWT 配置断言，已按批准 delta 改为 OIDC 外部密钥配置约束；删除文件需先进入 Git index 才能由现有文件清单检查器正确识别。根应用运行夹具按项目实际配置使用 develop schema 和 local profile，并为列表式客户端提供完整配置。

## 依赖与安全扫描

使用与 CI 相同的 OSV Scanner **2.4.0**，Darwin ARM64 二进制 SHA-256 为 `9ca3185ad63e9ab54f7cb90f46a7362be02d80e37f0123d095a54355ea202f5d`，已与官方 release 资产摘要核对。没有使用本机 2.5.0 的结果替代 CI 基线。

```bash
osv-scanner-2.4.0 scan source --lockfile j-store-boot/build/reports/cyclonedx-direct/bom.json --format json --output-file osv-results.json
```

生产 SBOM 共扫描 227 个包，扫描退出码 1，报告如下 11 条公告，涉及本次未调整版本的 5 个既有依赖；不能将扫描标为通过，也不以“继承”作为风险接受。

| 组件 | 解析版本 | 扫描公告 |
|---|---|---|
| Guava | 33.6.0-jre | GHSA-xxph-c9ww-hj94 |
| Jackson Core | 2.21.5 | GHSA-7hhh-6rmp-j9qf、GHSA-p6pp-m3f8-5c89 |
| Jackson Databind | 2.21.5 | GHSA-cxp5-3px4-pw24、GHSA-gx83-3vf8-gh7j、GHSA-q4xh-88c3-wmh7、GHSA-wjgm-6hv5-3cvf、GHSA-wv8q-qhhj-9h54 |
| OpenTelemetry API | 1.62.0 | GHSA-ccrj-gw8r-854m |
| Spring Web MVC | 6.2.19 | GHSA-j9f9-w8pj-32f8、GHSA-pc63-qcmh-9cmg |

catalog 中 Guava/Jackson/OpenTelemetry 版本和 Boot BOM 版本均与 develop 一致，Spring Web MVC 仍由同一 Boot BOM 解析。新增的授权服务器和 Spring Session 没有本次扫描报告的新增公告。上述结果只表示扫描器发现，不等于每条均可由本功能利用；可达性、安全修复及升级兼容性须另行评估。本次未自行接受风险或升级这些依赖。

## 尚未满足的验收门槛

1. 独立安全审查和人工最终批准：未执行。实现者自检与自动测试不能替代。
2. 微信真实应用、真实回调域名和短信供应商端到端联调：未执行；测试替身不能证明平台配置已通过。
3. 依赖安全扫描：失败，以上 11 条发现未收敛；CI Semgrep 和 OCI 镜像漏洞扫描未执行。
4. 多实例真实部署、跨节点登录/登出及故障恢复演练：未执行。共享 PostgreSQL/Redis 的实现路径和撤销策略已有本地测试，但不冒充集群验收证据。

恢复方式见 [delta](delta.md) 与 [runbook](runbook.md)。当前没有旧内部端点或旧开发数据库的兼容层；真实环境恢复仍需针对确切目标取得授权。

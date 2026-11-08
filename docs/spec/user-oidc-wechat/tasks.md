# 实现与验证任务

状态：用户已确认 [requirement](requirement.md) 与 [delta](delta.md) 方向；以 Red → Green → Refactor 推进，证据随实现更新。

本地候选的 T1–T8 已具备实现和自动化证据，详见 [summary](summary.md)。T9 仍未完成：独立审查、真实微信/短信联调和依赖安全发现尚未收敛，不能将任务表解释为整体批准。

| 任务 | 可验收结果 | 验收映射 | 必要证据 |
|---|---|---|---|
| T1 协议风险验证 | Boot BOM 管理的授权服务器可装配；明确并实现授权码原子消费、Refresh family 摘要与重放扩展点 | AUTH-1、AUTH-2.5–6 | dependencyInsight、并发兑换/刷新失败先行及通过记录；无法满足时先报告阻塞 |
| T2 身份绑定模型 | 外部身份唯一绑定；已注册手机号复用账号；新账号保持密码/手机号不变量 | WX-2 | 领域/应用测试、PostgreSQL 唯一约束及并发创建/绑定测试 |
| T3 临时意图与手机验证 | state、onboarding、用途化验证码与浏览器及授权请求绑定，过期和重放均拒绝 | WX-1.2、WX-2.1–2、8 | Redis Lua/CAS 集成测试、跨浏览器攻击、并发消费、数据库失败恢复测试 |
| T4 微信 ACL | 服务端兑换并验证响应，不保留上游 token，不接受客户端身份输入 | WX-1、WX-2.7 | 可读官方资料核对、HTTP 假服务成功/拒绝/超时/错误/身份不一致测试及日志检查 |
| T5 登录及授权页面 | 本地密码/微信登录、同意和首次手机号绑定可完成同一标准授权请求 | AUTH-1.6、WX-2 | MockMvc/真实浏览器流程；CSRF、session fixation、授权上下文混淆和开放重定向反例 |
| T6 OIDC 与授权存储 | discovery/JWKS/ID Token/UserInfo、持久化授权与 scope 同意、码兑换、刷新、撤销、introspection、登出 | AUTH-1–2、OPS-1.1 | public/confidential 完整协议测试；nonce、aud、scope、码重放和 Refresh 并发测试；重启/多实例状态验证 |
| T7 SDK 及旧端点收敛 | 新 Access Token 可供现有业务使用；旧 JWT 与 ID Token 不被业务接受；立即撤销生效 | AUTH-2.7–9、delta | authentication SDK、user 四层、Trade/Order/Shop 回归；业务注解语义与 JSON 401 测试 |
| T8 配置与运行文档 | 缺生产配置失败；dev/test adapter 不进入生产；客户端和微信接入样例可执行 | OPS-1 | Profile/配置测试、敏感日志检查、基线/快照验证、README 与当前事实文档更新 |
| T9 供应链与完整验收 | 最终候选通过回归及独立安全审查，人工决定接受 | 全部 | SBOM、CI 同版本 OSV、许可证审计、完整质量门禁、独立审查和真实联调结果 |

## 测试顺序

1. 每项先运行最小失败测试，记录命令与失败原因，再实现并复跑；测试命名表达行为。
2. 领域与应用层验证不引入 Spring/JPA；infrastructure 测试使用真实嵌入式 PostgreSQL/Redis，覆盖唯一约束、事务和原子操作，不能只使用 mock 证明并发安全。
3. Boot 测试验证标准协议响应及 Security FilterChain 与 SDK 边界；微信只通过固定假服务模拟，不访问真实用户身份。
4. 运行受影响 user、authentication-sdk、根 boot 及身份消费方测试；依赖新增后执行 dependencyInsight、许可证与漏洞验证。
5. 跨模块实现完成后运行 `./scripts/quality-gate.sh`；任何失败或跳过必须记录原因，不能用规格复选框替代证据。

## 最终交付材料

在 `summary.md` 中按验收 ID 记录实现位置、测试命令、结果、候选提交、独立审查结论、已知差距、未运行检查、开发环境恢复方式。同步 `docs/project-overview.md` 和 `docs/domain-modeling.md` 的当前事实，不能提前把设计写成已经实现。

真实微信联调需要已审核网站应用及平台配置、可访问的回调域名、实际运行环境和外部 secret 注入；真实短信需要已配置的发送适配器。上述外部事项不阻止本地代码与假服务测试，但缺少它们时真实平台验收保持未验证。禁止要求用户在聊天中提交 secret，禁止未经授权写入微信平台或生产环境。

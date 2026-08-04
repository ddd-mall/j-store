# 长期自动化维护治理审查记录

## 2026-08-04 收敛审查

### 结论

PASS（仓库内范围）。需求、设计、实现和本地验证证据一致；需要仓库管理员或外部平台执行的动作已作为交付后置事项保留，没有被误报为已完成。

### 审查维度

| 维度 | 结果 | 证据 |
|---|---|---|
| 需求意图与权限边界 | PASS | `AGENTS.md` 与 `agent-governance.md` 区分当前事实和预期意图，并列出人工审批事项 |
| Agent 职责分离 | PASS | 六个 `.codex/agents/*.toml`；合同测试验证全部加载权威治理文档 |
| 确定性质量门禁 | PASS | `./scripts/quality-gate.sh` 退出码 0；规格测试 28 个、治理测试 2 个通过，Gradle build successful |
| 凭据与本地配置 | PASS | 已知密码、JWT 值和私网地址不再存在于当前工作树；`.env` 被忽略，示例只含占位值 |
| CI 与供应链 | PASS（静态） | workflow/dependabot YAML 可解析；CodeQL、依赖审查、Gitleaks 和依赖 PR 已配置 |
| 兼容性 | PASS | 文档和 Docker 运行时与 Kotlin 2.3、Java 25、Spring Boot 3.5.16 构建一致 |

### 审查中修复的问题

1. 初始本地质量脚本依赖系统 Python，但系统环境缺少 `jsonschema`。已增加固定版本依赖和 uv 隔离运行路径。
2. uv 默认缓存目录被沙箱禁止写入。已改用任务专用临时缓存目录。
3. Docker 运行时仍为 Java 21，而构建 toolchain 是 Java 25。已升级到 Corretto 25 并加入治理检查。
4. dependency review 需要 PR 写权限才能发布摘要。已将权限只添加到该 job。

### 未执行与残余风险

- GitHub Actions 只能在提交到 GitHub 后进行真实执行；本地完成了 YAML 解析和等价质量入口验证。
- 未构建 Docker 镜像，`amazoncorretto:25-al2023-headless` 标签需要在 CI/镜像构建环境验证可拉取。
- 历史中出现过的开发凭据尚未由本次仓库修改完成外部轮换，必须由环境所有者执行。
- GitHub ruleset、required checks、私有漏洞报告和云端 agent schedule 尚需管理员在外部启用。

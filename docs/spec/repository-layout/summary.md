# 前后端目录分离交付记录

日期：2026-11-09。前置快照已提交并推送至 PR [#72](https://github.com/ddd-mall/j-store/pull/72)，目标 develop。该快照已同步最新 develop 并通过后端完整门禁、前端 check 和 Chromium 测试。

本次目录重整位于独立分支 `codex/separate-backend-frontend`，没有自动合并或部署。

## 实现与验收

| 目标 | 实现和证据 |
|---|---|
| 分离构建根 | backend/ 包含 64 个原有 JVM 模块及 Gradle 配置；frontend/ 包含 pnpm workspace 与 apps/admin-web；原 Gradle project ID、包名及 npm 名保持 |
| 统一后端入口 | scripts/backend-gradle.sh 从 /tmp 等外部 cwd 仍可定位项目；Windows helper 显式传入 backend 项目路径 |
| 共享资源与工具 | 根 LICENSE/THIRD_PARTY、Spotless 规则、Git hooks 保留；Git 相对目标清单按仓库根解析；安装新 hooks 成功 |
| CI / Docker / 发布路径 | 质量门禁、SBOM/JAR 路径、OCI context、GateRunner 缓存构建、PowerShell 和 Compose bind 路径更新；Qodana 使用 onlyDirectory: backend |
| 业务行为保持 | 对照前置提交比较 719 个 Kotlin 文件：除一处 KDoc 换行外，去空白内容一致；必要格式变更全部来自当前 Spotless，不修改业务逻辑 |
| 前端产物保持 | 冻结锁文件安装、check、Chromium E2E 通过；构建产物仍为 index-DSMfQTjf.css / index-CAXG-S8-.js，大小与搬迁前一致 |
| 文档与历史 | 当前 README、概览、steering、操作手册和路径契约更新；历史 summary/review-log 与 Git secret-scan 指纹保持原始执行记录，不伪造当时命令 |

## 验证

- 目录契约测试先失败：3 项，原因分别为 backend/frontend/新入口不存在；迁移后通过。
- `./scripts/backend-gradle.sh projects`：通过，原有项目标识保留。
- `./scripts/backend-gradle.sh test`：通过，所有 JVM 模块重新执行。
- `./scripts/quality-gate.sh`：通过，包含 28 项技能契约、76 项治理、354 项工具测试，以及 Spotless、依赖解析、许可证、JVM 回归和 66 个 JAR 许可证检查。
- `pnpm --dir frontend install --frozen-lockfile`：通过。
- `pnpm --dir frontend check`：通过，包含格式、lint、类型、2 项组件测试和生产构建。
- `pnpm --dir frontend test:e2e`：Chromium 1 项通过。
- `pnpm --dir frontend format:check`、`git diff --check`：通过；变更 Markdown 的本地链接检查通过。

根目录已不再有 Gradle/pnpm 工程入口。后端可在 backend/ 中运行 ./gradlew，也可使用仓库脚本；前端使用 frontend/ 中的 pnpm，不提供旧根目录代理工程。

## 未运行及漂移

- 原生 Windows、Docker Compose 启动、OCI 镜像构建及 Qodana 远程执行未运行；本机没有 Docker CLI。对应路径与配置已有契约检查，不冒充实际部署证据。
- LAYOUT-DRIFT-01：搬迁前的 observability Compose 和 smoke 配置仍要求旧 JWT 环境变量，而 OAuth 候选已改用外部 JWK/OIDC 配置。此项归属用户认证/可观测性部署配置，已记录到 OAuth 交付记录；本次只迁移路径，不将既有部署配置漂移伪装为已经修复。
- OAuth 真实微信/短信联调、独立安全审查仍属于前置 PR 的未决门槛。历史文档中的 11 条 Maven 公告来自同步 develop 安全修复前的扫描，不是本次目录重整重新扫描的结论。
- 未新增或升级依赖、未执行生产写入或数据库迁移。恢复以撤销目录重整提交为边界，不复制新旧两套源码。

## CI 初始化失败修复

PR #73 首轮 quality、dependency-license-audit 和 dependency-vulnerability-scan 均在下载 Action 阶段失败，日志为 `Unable to resolve action backend/gradle, repository not found`。目录替换误将外部 `gradle/actions/setup-gradle` 坐标添加了本地 `backend/` 前缀；本地 Gradle 门禁无法发现远程 Action 解析错误。

已修复 quality、security 和 release-evidence 工作流中的全部四处引用，保留原有 SHA 固定版本。新增 YAML 解析回归检查，确保远程 Action 不使用本地目录前缀、Gradle Action 保留正确发布者；测试先复现失败，修复后 4 项目录契约通过。远端门禁以修复提交对应的 Actions 结果为准，不能用此前本地通过代替。

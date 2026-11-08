# 后台前端初始化交付证据

## 交付范围

已创建 `apps/admin-web` 空白前端应用及根 pnpm workspace。React / TypeScript strict / Vite / Ant Design / React Router / TanStack Query / CSS Modules 已装配，OpenAPI 类型生成和请求工具已安装，测试与格式工具可运行。

按用户最新指示只保留最小首页与 404 返回入口，没有增加业务页面、后台布局、登录、租户或权限行为，也没有生成虚构 API。后端现有工作区变更保留。

## 验收映射

| 验收 | 实现 | 证据 |
|---|---|---|
| 独立 workspace 应用与可复现安装 | 根 package.json、pnpm-workspace.yaml、pnpm-lock.yaml | `pnpm install --frozen-lockfile` 退出码 0；`pnpm dev` 启动 Vite，入口返回 HTTP 200 |
| 最小入口、Provider 与路由 | App、AppProviders、main、CSS Modules | 两项组件测试通过；Chromium 入口与未知路径返回测试通过 |
| 不请求不存在的接口 | 未定义业务请求、未生成虚构 schema；Query Provider 只负责装配 | 代码检查；入口无业务请求调用，E2E 无 pageerror |
| 工程与测试基线 | ESLint、Prettier、TypeScript、Vitest、Testing Library、Playwright 配置 | `pnpm check` 与 `pnpm test:e2e` 退出码 0 |
| OpenAPI 接入路径 | api:generate 脚本及 shared/api/README | CLI help 可执行，文档记录从权威契约生成的命令；未验证真实契约生成与业务 API 调用 |
| 依赖和使用文档 | 应用 README、根 README、项目概览、THIRD_PARTY、ignore 规则 | 精确版本与锁文件存在，构建及测试产物被忽略，`git diff --check` 通过 |

## 验证结果

- TDD：先建立入口测试并运行 `pnpm test`，因 `./App` 尚不存在而失败；补充最小实现后两项测试通过。
- `pnpm check`：格式、ESLint、类型、2 项组件测试、生产构建全部通过。最终 JS 产物约 380.34 kB，gzip 126.24 kB，无大 chunk 警告。
- `pnpm test:e2e`：Chromium 1 项通过；验证生产构建加载、直接访问未知路由及返回首页，无浏览器 pageerror。执行后预览服务器自动停止。
- `pnpm audit`：修复开发工具链 minimatch 间接依赖后返回 `No known vulnerabilities found`。覆盖依据为 [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249)，不代表未来无漏洞。
- `pnpm licenses list --json`：许可证清单可生成；`--prod` 清单包含 77 项 MIT 包。此结果是清单盘点，不是正式发布的独立许可证批准。
- `./scripts/quality-gate.sh`：**失败**。治理脚本和 28 项 spec-dev 测试通过；随后 68 项治理测试中出现 1 error、1 failure，原因均为当前后端工作区的 `j-store-user-domain/src/main/kotlin/com/jstore/user/domain/useraccount/TokenStore.kt` 已被删除但仍在索引中，`repository_files.py` 拒绝缺失文件。该文件不是本次修改。脚本随后中止，后续 tooling / Spotless / JVM 依赖与许可证 / Gradle 回归 / 发布制品检查没有执行。

## 限制与后续边界

- 前端检查尚未接入现有 CI，当前需要单独执行 `pnpm check` 与 `pnpm test:e2e`。
- 未实现或验证 BFF、OIDC、管理员授权、生产路由回退和部署。这些属于后续功能，不由本次空白工程替代。
- 未进行独立批准或外部评审；本次只交付本地初始化，没有提交、推送、合并或发布。
- 恢复方式：移除本次前端新增文件及文档/ignore 增量；不操作已有后端变更或业务数据。

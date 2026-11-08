# 后台前端空白工程初始化

## 意图与范围

用户已选定 React、TypeScript、Vite、Ant Design、React Router、TanStack Query、OpenAPI 类型化客户端、CSS Modules、pnpm workspace 以及 Vitest / Testing Library / Playwright 技术基线，并要求先创建空白项目。

本次只建立可独立运行和构建的前端工程，不建立工作台布局、业务页面、租户切换、角色权限、模拟数据或登录流程。不修改已有 OIDC 工作区变更，不实现 Spring Boot BFF，不发布站点。

## 验收标准

1. 仓库根目录通过 pnpm workspace 管理 `frontend/apps/admin-web`，应用名为 `@j-store/admin-web`，保持 private；无需运行 Gradle 或数据库即可启动。
2. 首页仅呈现最小项目入口，采用 Ant Design Provider、中文语言包和 CSS Modules；路由不存在时提供返回首页入口。
3. Query Provider 完成基础装配；不自动请求尚未存在的 API，不生成虚构接口，不在浏览器持久化 token 或 secret。
4. TypeScript strict、ESLint、Prettier、Vitest 和 Testing Library 可执行；Playwright 提供针对构建产物的入口和路由回退冒烟验证。
5. OpenAPI 工具安装完成，文档明确如何从后端权威契约生成类型；在契约就绪前不生成占位 API schema。
6. 提交精确依赖和 pnpm 锁文件，忽略依赖、构建与测试产物；文档包含安装、启动、构建和验证命令。

## 技术决策与边界

- 使用单个应用的模块化工程起步；未来有第二个实际消费者时再提取共享 workspace 包，不预建空的业务模块和微前端。
- Node.js 使用 24 或 26 系列，pnpm 版本由根 packageManager 固定；版本兼容性以安装解析、类型检查、构建和测试为证据。
- 当前不存在受保护的业务页面。未来登录接入 OIDC + confidential BFF，授权由服务端实施，本次不增加认证绕过、假登录或前端权限策略。
- 前端工具不写入 JVM version catalog，前端依赖由 package.json 与 pnpm-lock.yaml 管理；不修改 Gradle 依赖解析策略。
- npm 初始审计发现 ESLint / typescript-eslint 间接引入的 minimatch 10.2.6 受 [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249) 影响；workspace 将受影响范围集中覆盖到最小修复版本 11.0.1。该变更仅作用于开发工具链，Node engines 与本项目运行环境相容；跨 major 兼容性以覆盖后的 lint、类型、测试与构建验证。上游支持修复版后移除覆盖；回滚时须重新评估该漏洞，不能静默恢复受影响版本。
- 此次为本地仓库工程初始化，不部署、不合并、不发布。回滚仅需移除本次前端新增文件及对应文档与 ignore 增量，不影响后端数据。

## 验证

先建立入口及路由测试，确认因入口实现缺失而失败，再完成装配并运行前端检查及浏览器冒烟。最终命令与结果见 [summary.md](summary.md)。

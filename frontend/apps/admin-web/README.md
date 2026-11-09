# J-Store Admin Web

后台前端空白工程。当前仅包含最小首页、404 回退及基础工具装配，没有业务页面、登录、权限或模拟数据。前端可独立于 Kotlin 后端运行。

## 环境与启动

使用 Node.js 24 或 26，以及`frontend/package.json` 固定的 pnpm 11.24.0。若已有 Corepack，可执行 `corepack enable`；否则按 pnpm 官方安装文档安装对应版本。

在 `frontend/` 目录执行：

```bash
pnpm install --frozen-lockfile
pnpm dev
```

访问终端显示的本地地址，默认是 `http://127.0.0.1:5173`。开发和预览服务只监听本机。

```bash
pnpm check          # 格式、lint、类型、组件测试、生产构建
pnpm build         # 输出 apps/admin-web/dist
pnpm preview       # 本地预览生产构建
pnpm format        # 仅格式化前端应用，不处理 Kotlin / Gradle
pnpm --filter @j-store/admin-web test:watch
```

## 浏览器测试

```bash
pnpm --filter @j-store/admin-web exec playwright install chromium
pnpm test:e2e
```

Playwright 会构建应用并启动独立预览服务，在 Chromium 中验证入口、直接访问未知路径及返回首页。测试使用 4173 端口；端口被占用时应先停止相应进程，不复用未知服务。

## 结构

```text
src/
  app/                 路由、Provider、最小入口
  shared/api/          API 契约生成接入说明
  test/                组件测试初始化
e2e/                   构建产物冒烟测试
```

React Router 管理导航；TanStack Query 管理未来的服务端数据；Ant Design Form 用于后续业务表单；样式采用 Ant Design Token 与 CSS Modules。未引入全局 Store、ProComponents、微前端或额外全栈服务。

## API 与认证

依赖已包含 `openapi-typescript` 和 `openapi-fetch`，接入方式见 [API 说明](src/shared/api/README.md)。目前没有权威后台 OpenAPI 契约，因此不生成虚构端点或类型，不自动发送业务请求。

未来通过同源 Spring Boot BFF 接入 OIDC，由 BFF 保存令牌并维护会话。BFF、管理员授权模型及其接口不属于本次初始化。客户端不能持有 confidential client secret；`VITE_*` 配置均视为公开信息。

生产静态服务器需要将应用内非资源路由回退到 `index.html`，实际 API 路径应单独路由至 BFF，不能以 SPA HTML 兜底 API 错误。当前仅验证本地 Vite preview，不包含生产托管配置。

## 依赖管理

直接依赖固定精确版本，`frontend/` 锁文件保存完整解析和完整性信息；workspace 启用严格 peer 检查和至少一天的发布冷却期。安装时若 pnpm 报告被阻止的依赖构建脚本，应逐项评估后再批准，不启用全部脚本。

开发工具链 minimatch 的安全覆盖集中在`frontend/pnpm-workspace.yaml`，依据、兼容性和移除条件见[初始化规格](../../../docs/spec/admin-web-bootstrap/requirement.md)。

```bash
pnpm audit
pnpm licenses list --json
```

现有 Gradle Licensee 与 SBOM 不覆盖 npm 依赖；前端审计单独执行。此应用尚未接入 CI，前端 `pnpm check` 和 `pnpm test:e2e` 应作为独立交付检查运行；仓库原质量门禁保持不变。

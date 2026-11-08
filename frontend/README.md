# 前端工作区

本目录独立管理 Node/pnpm 配置、锁文件及 `apps/admin-web`，不依赖 Gradle 执行前端构建。

```bash
# 在 frontend/ 中执行
pnpm install --frozen-lockfile
pnpm dev
pnpm check
pnpm test:e2e
```

也可以在仓库根使用 `pnpm --dir frontend <command>`。

Node/pnpm 版本、应用结构及 OpenAPI 接入边界见 [后台应用说明](apps/admin-web/README.md)。当前应用仍是空白初始化工程；目录重整不增加后台业务、BFF 或认证页面。

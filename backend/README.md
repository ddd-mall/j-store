# 后端工程

本目录是独立 Gradle 多模块构建。模块标识、Kotlin 包和应用端点保持不变。

```bash
# 在 backend/ 中执行
./gradlew test
./gradlew :j-store-boot:bootJar
```

从仓库根可使用 `./scripts/backend-gradle.sh`，脚本也支持从其他工作目录调用。共享许可证、Spotless 规则与 Git hooks 保留在仓库根；独立 Kotlin 示例使用 `backend/examples/`。

根目录的 [项目概览](../docs/project-overview.md)、[README](../README.md) 和 `.env.example` 提供完整运行说明。Docker Compose 文件位于本目录；从仓库根运行时显式使用根 `.env`：

```bash
docker compose --env-file .env -f backend/docker-compose.postgres.yml up -d
```

前端由 `frontend/` 的 pnpm workspace 独立管理，不是 Gradle 模块。

# j-store

j-store 是一个 Kotlin/Spring Boot 多模块电商后端，采用 DDD、Spring Data JPA、PostgreSQL 和 Redis。项目结构、模块边界与测试入口见 [`docs/project-overview.md`](docs/project-overview.md)。

## 代码格式化

Java、Kotlin 和 Gradle Kotlin DSL 代码统一由 Spotless 格式化：Java 使用 Google Java Format（AOSP 风格），Kotlin 使用 ktfmt（Kotlin 风格）。

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
```

如需启用仓库提供的 pre-push hook，可执行：

```bash
./gradlew spotlessInstallGitPrePushHook
```

该 hook 只检查本次待推送提交中新增或修改的 Kotlin、Java 与 Gradle Kotlin 文件；纯文档或其他非源码推送会跳过 Spotless。完整仓库检查仍可通过 `./gradlew spotlessCheck` 执行。

hook 会在推送前检查格式；发现问题时会自动格式化并终止本次推送，确认并提交格式化结果后再重新推送。

## 本地依赖服务

前置条件：Docker Desktop，或 Docker Engine 与 Compose 插件。

复制本地环境变量模板并生成自己的随机密码：

```bash
cp .env.example .env
```

编辑 `.env` 中的所有 `change-me` 值。`.env` 已被 Git 忽略，不得提交。启动服务：

```bash
docker compose --env-file .env -f docker-compose.postgres.yml up -d
docker compose --env-file .env -f docker-compose.postgres.yml ps
```

运行 Spring Boot local profile 前，将 `.env` 中对应的 `JSTORE_*` 变量导入启动进程。IDE 用户可在本地 Run Configuration 中配置；不要把值写入受版本控制的 properties 文件。

停止服务：

```bash
docker compose --env-file .env -f docker-compose.postgres.yml down
```

删除本地数据库卷会永久删除本地数据，仅在明确需要时执行：

```bash
docker compose --env-file .env -f docker-compose.postgres.yml down -v
```

## 验证

运行完整的仓库质量门禁：

```bash
./scripts/quality-gate.sh
```

也可以只运行相关模块测试，例如：

```bash
./gradlew :j-store-order:test
./gradlew :j-store-goods-domain:test :j-store-goods-application:test :j-store-goods-boot:test
```

## 安全提示

历史版本曾包含本地 PostgreSQL 和 JWT 开发凭据。删除当前文件中的明文不会使历史凭据失效；所有曾使用这些值的环境都必须轮换凭据。不要复用示例值或把生产连接信息放进仓库。

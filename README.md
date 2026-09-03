<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/j-store-logo-dark.svg">
    <img src="assets/j-store-logo.svg" alt="J-Store logo" width="160">
  </picture>
</p>

# j-store

j-store 是一个 Kotlin/Spring Boot 多模块电商后端，采用 DDD、Spring Data JPA、PostgreSQL 和 Redis。项目结构、模块边界与测试入口见 [`docs/project-overview.md`](docs/project-overview.md)。

## 代码格式化

Java、Kotlin 和 Gradle Kotlin DSL 代码只由 Spotless 格式化，其中 Kotlin 和 Gradle Kotlin DSL 使用 ktfmt（Kotlin 风格）。仓库不采用额外的人工或 AI 格式化规则。执行方式见 [`docs/steering/code-formatting-guidelines.md`](docs/steering/code-formatting-guidelines.md)。

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
```

Windows PowerShell 中如遇到 JDK NIO `Unable to establish loopback connection`，使用仓库包装脚本为 Gradle 子进程配置短临时目录：

```powershell
.\scripts\gradlew-windows.ps1 spotlessApply
.\scripts\gradlew-windows.ps1 spotlessCheck
```

脚本默认使用 `C:\jstore-jvm-tmp`，只在执行期间修改子进程的 `TEMP/TMP` 并在结束后恢复。如需使用其它磁盘，可在当前 PowerShell 会话中设置绝对路径，例如 `$env:JSTORE_JAVA_TMPDIR = "D:\jtmp"`。

如需启用仓库提供的 Spotless Git hooks，可执行：

```bash
./gradlew installSpotlessGitHooks
```

`pre-commit` hook 只格式化已暂存的 Kotlin、Java 与 Gradle Kotlin 文件，并将格式化结果重新暂存到当前 commit。如果目标文件同时包含未暂存修改，hook 会终止提交，避免意外提交这些修改。

`pre-push` hook 保留为后备检查，只检查本次待推送提交中新增或修改的目标文件；纯文档或其他非源码操作会跳过 Spotless。安装任务会使用 Git 实际的 hooks 目录，可用于主工作区、linked worktree 和自定义 `core.hooksPath`。完整仓库检查仍可通过 `./gradlew spotlessCheck` 执行。

Spotless 同时检查所有 Java、Kotlin 源码的 Apache-2.0 许可证声明。新增源码缺少声明时，运行
`spotlessApply` 会根据 [`config/spotless/license-header.txt`](config/spotless/license-header.txt) 自动补充。

依赖许可证由 Licensee 审计，未知或未批准许可证会使构建失败：

```bash
./gradlew licensee
```

机器可读文件归属见 [`config/licenses/file-ownership.toml`](config/licenses/file-ownership.toml)，仓库内第三方文件及依赖说明见 [`THIRD_PARTY.md`](THIRD_PARTY.md)。

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

模块化单体默认使用进程内用户资料查询，不开放内部 HTTP 端点：

```properties
jstore.user-query.mode=local
jstore.user-query.server.enabled=false
```

拆分为微服务时，User 服务启用内部查询端点：

```properties
jstore.user-query.server.enabled=true
jstore.user-query.server.token=${JSTORE_USER_QUERY_TOKEN}
```

Order、Shop 等消费服务切换为远程客户端：

```properties
jstore.user-query.mode=remote
jstore.user-query.remote.base-url=${JSTORE_USER_SERVICE_URL}
jstore.user-query.remote.token=${JSTORE_USER_QUERY_TOKEN}
jstore.user-query.remote.connect-timeout=2s
jstore.user-query.remote.read-timeout=3s
```

`JSTORE_USER_QUERY_TOKEN` 必须是至少 32 字符的独立随机凭证，不得提交到仓库。生产集群还应使用 mTLS、NetworkPolicy 或服务网格策略限制内部端点的网络访问。

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

## 多集群交付

物理隔离集群使用同一个 OCI digest 和 Kubernetes base，通过中央 CI/CD 的单目标隧道完成
晋级与部署。构建、目标配置、部署和回滚契约见
[`docs/operations/immutable-multi-cluster-delivery.md`](docs/operations/immutable-multi-cluster-delivery.md)。

## 安全提示

历史版本曾包含本地 PostgreSQL 和 JWT 开发凭据。删除当前文件中的明文不会使历史凭据失效；所有曾使用这些值的环境都必须轮换凭据。不要复用示例值或把生产连接信息放进仓库。

## 许可证

Copyright 2024-2026 潘少峰 (Peter Pan)。本项目依据 [Apache License 2.0](LICENSE) 授权。

所有 Gradle JAR 产物均携带 `META-INF/LICENSE` 和 `META-INF/THIRD_PARTY.md`。正式版本的证据生成与签名标签流程见 [`docs/operations/release-evidence.md`](docs/operations/release-evidence.md)。

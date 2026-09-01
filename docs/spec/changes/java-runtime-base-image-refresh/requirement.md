# Java 运行时基础镜像安全升级需求

## 背景

`j-store-boot` 当前使用的 Alpine 基础镜像包含 `openssl`、`libcrypto3` 和
`libssl3` 3.5.7-r0。仓库安全流水线固定的 OSV Scanner 2.4.0 已对这些包报告多个
Alpine CVE，导致最终应用镜像安全门禁失败。

## 目标

- 将 Java 25 运行时迁移到无上述已知漏洞的受维护基础镜像。
- 继续使用可读版本标签和 OCI image-index digest 双重固定。
- 保持 linux/amd64 与 linux/arm64/v8 架构支持。
- 保持 UID/GID 10001、`/tmp` 可写、现有 JVM 参数和 Spring Boot JAR 启动契约。
- 使用与 CI 一致且通过发布摘要校验的 OSV Scanner 2.4.0 扫描基础镜像和最终应用镜像。

## 不在范围内

- 不修改 JVM/Gradle 依赖版本。
- 不部署到集群，不修改 Kubernetes 发布策略。
- 不自动合并；required checks 和人工审批仍是合并前置条件。

## 验收标准

1. Dockerfile 使用版本化、digest 固定的 Java 25 运行时镜像，不使用浮动标签。
2. 目标 image index 至少包含 linux/amd64 与 linux/arm64/v8 manifest。
3. OSV Scanner 2.4.0 对目标基础镜像及由仓库 Dockerfile 构建的最终应用镜像均返回零漏洞。
4. 最终镜像以 UID/GID 10001 运行，能够写入 `/tmp` 并执行 Java 25。
5. Spring Boot 应用完成容器启动冒烟，不出现运行时或本地库兼容错误。
6. 镜像治理契约和仓库质量门禁通过，并记录许可证结果与回滚 digest。

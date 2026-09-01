# Java 运行时基础镜像安全升级摘要

## 变更选择

- 原镜像：`amazoncorretto:25-alpine3.24@sha256:027310590da693629c2cf704d2f87e9359c33ee2f02bcaa777680b2f4b94f4c7`。
- 新镜像：`registry.access.redhat.com/ubi10/openjdk-25-runtime:1.24-13@sha256:68525bc239f93a62070625354e3b863be0963f61f1338794011665d5b8a946f5`。
- 上游运行时：Red Hat OpenJDK 25.0.4.1，基础系统为 RHEL UBI 10.2。
- 新 image index 包含 linux/amd64、linux/arm64/v8、linux/s390x 和 linux/ppc64le；项目要求的前两个架构均保留。

继续使用当前 `amazoncorretto:25-alpine3.24` 标签不能解决问题：其最新 index digest
`sha256:2ad5f5cf03a3970f2478b130dc28f51b179ce13c58154fe3ec1a6fdeb3b86e3a`
仍包含 `openssl`、`libcrypto3` 和 `libssl3` 3.5.7-r0。Temurin Alpine 候选存在相同问题；
Corretto AL2023 headless 与 Distroless Debian 13 候选也仍被 CI 同版本扫描器报告其它漏洞。

## 安全与许可证证据

- 原 Alpine 镜像中的 3.5.7-r0 被 OSV Scanner 2.4.0 报告
  `ALPINE-CVE-2026-14456`、`ALPINE-CVE-2026-14457`、`ALPINE-CVE-2026-18798`、
  `ALPINE-CVE-2026-54874`、`ALPINE-CVE-2026-63072` 至 `ALPINE-CVE-2026-63076`
  以及 `ALPINE-CVE-2026-75803`。
- OSV Scanner 2.4.0 对新基础镜像 Docker archive 扫描返回 0 个漏洞组；JSON 报告 SHA-256 为
  `d73d3f0a4e58e014eaba5ff1162153ec94cfb9e6a3117ba31ffa1c9a20673cec`。
- 新镜像包含 `openssl-libs 3.5.5-6.el10_2`，RPM 声明许可证为 Apache-2.0；
  `java-25-openjdk-headless 25.0.4.1.1-1.1.el10` 的 RPM 元数据声明 GPLv2 with exceptions
  等上游组合许可证。本次未加入新的应用依赖。

## 兼容与回滚

- 基础镜像已验证 Java 25.0.4.1 可执行。
- 以 UID/GID 10001 运行时身份写入 `/tmp` 已验证通过；Dockerfile 继续显式覆盖上游默认用户和入口点。
- 最终应用镜像构建、扫描、Spring Boot 启动冒烟及完整质量门禁结果在 PR 验证后补充。
- 若兼容性或集群验证失败，回滚 Dockerfile 到原 Corretto digest；该 digest 带已知漏洞，
  因此只作为恢复可用性的临时回滚点，安全门禁保持阻断，不能继续晋级生产。

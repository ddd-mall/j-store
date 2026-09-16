# PR #69 Netty 安全阻塞修复

## 范围与依据

用户明确要求修复本 PR 的安全检查阻塞。生产 SBOM 中 `io.netty:netty-handler:4.1.136.Final` 命中下列公告，CI run `35104769066` 的 dependency-vulnerability-scan 退出 1：

- [GHSA-c4c3-7fpv-j4q5 / CVE-2026-75595](https://github.com/netty/netty/security/advisories/GHSA-c4c3-7fpv-j4q5)：分片 ClientHello 导致 SNI 路由回退，在特定 mTLS 配置下可绕过认证。
- [GHSA-fccg-mwvh-qqg4 / CVE-2026-75596](https://github.com/netty/netty/security/advisories/GHSA-fccg-mwvh-qqg4)：分片 ClientHello 预握手重组的二次复杂度问题。

两份维护者公告均标明 4.1 系列首个修复版本为 `4.1.137.Final`。采用该补丁版本，不升级到 4.2，不新增漏洞忽略项，不修改检查要求。此修复只更新一个不可拆分 Netty BOM，是用户要求的当前 PR 安全修复，不是定时依赖升级或业务范围扩张。

## 设计与不变项

catalog 将 Netty `4.1.136.Final` 更新为 `4.1.137.Final`，继续由 `j-store-dependencies-platform` 已有的 `api(platform(libs.netty.bom))` 统一对齐。消费模块不单独钉版本，无 force 或第二套解析策略。Spring Boot 3.5、Java 25 和 Reactor/Lettuce 接入方式保持不变；不修改业务、Outbox 契约和数据库。

验收：治理契约拒绝旧安全基线；dependencyInsight 和生产 SBOM 中解析为修复版本；CI 固定 OSV Scanner 2.4.0 扫描无漏洞；全仓质量门禁及受影响的 Redis/HTTP/Boot/Outbox 测试通过。补丁版本兼容性以解析和可执行回归验证，不仅依据版本号推断。

## 验证记录

- RED：先更新 `test_dependency_management.py` 的安全版本断言，7 项测试中 1 项失败，明确报告 4.1.136 与 4.1.137 不符。
- GREEN：catalog 升级后依赖治理 7 项测试通过。
- Linux `:j-store-boot:dependencyInsight --dependency io.netty:netty-handler --configuration runtimeClasspath` 确认解析为 4.1.137.Final，约束链来自 `netty-bom` → `j-store-dependencies-platform`。
- `:j-store-boot:cyclonedxDirectBom` 成功；使用与 CI 相同的 OSV Scanner 2.4.0，先验证二进制 SHA-256 `15314940c10d26af9c6649f150b8a47c1262e8fc7e17b1d1029b0e479e8ed8a0`，再扫描 213 个包，结果 `results: []`、退出码 0。
- 完整 `scripts/quality-gate.sh` 六阶段通过：449 项 Python 测试、全模块 JVM 回归、Spotless、依赖解析、licensee 及 66 个 JAR 许可证检查。受 Netty runtime classpath 变化影响的 `j-store-boot:test`、`j-store-user-boot:test`、`j-store-user-infrastructure:test` 重新执行通过；其他未变化任务使用有效 UP-TO-DATE 结果。
- 验证在 Linux 隔离副本进行，保留主机已有控制器、缓存和工作目录；证据位于验证目录的 `netty-evidence-2`。首次验证启动因 uv 默认缓存落在只读目录而失败，明确指定专属 UV_CACHE_DIR 后通过，不改测试或检查策略。
- GitHub PR 在更新后仍须重新完成远程 required checks，尤其包括 CI 独有的 OCI 容器漏洞扫描；以上生产 SBOM 扫描不冒充容器扫描结果。

## 恢复与残余风险

不部署、不迁移数据库、不自动合并。若发现兼容回归，通过新的修复/revert PR 恢复，不强推历史；回退旧 Netty 会重新引入已知漏洞并阻塞安全门禁，不能将其作为可发布状态，需选择其它已修复且经过验证的版本。完成证据不代替独立人工批准。

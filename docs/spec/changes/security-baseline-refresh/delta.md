# CI 与依赖安全基线刷新 Delta

## 背景

`develop` 的安全门禁因两项外部基线漂移而不再可重复通过：Semgrep `p/default`
规则内容发生变化，且 OSV 新增公告后确认 Spring Boot 3.5.16 默认管理的嵌入式 Tomcat
10.1.55 存在已公开漏洞。

## 变更

- Semgrep 仍使用可变规则入口加固定 SHA-256 的 fail-closed 模式。本次只在使用仓库固定的
  Semgrep CE 版本完成实际扫描后，将审核摘要更新为
  `d526987e830828f8962e2146969de0877b58efc786c312141c4d84673287e9b5`。
- Spring Boot 继续保持 3.5.16。作为安全例外，统一依赖 Platform 将
  `tomcat-embed-core`、`tomcat-embed-el` 和 `tomcat-embed-websocket` 整体约束为
  10.1.59；业务模块不得单独覆盖。
- 10.1.59 是 10.1.x 当前首个实际发布且包含相关修复的版本。Tomcat 10.1.58 候选未通过
  发布投票，不作为可解析目标。

## 安全依据

本次 Tomcat 下限覆盖 `CVE-2026-65905`、`CVE-2026-65182`、
`CVE-2026-68525`，并同时纳入 10.1.59 公布的其它 10.1.57 及以前版本修复。

## 验收

- 统一 Platform 之外不存在 Tomcat 版本覆盖。
- 所有生产 `runtimeClasspath` 中的 `org.apache.tomcat.embed` 组件解析为 10.1.59。
- 生产 SBOM 经 CI 固定的 OSV Scanner 2.4.0 扫描无已知漏洞。
- 新摘要对应的 Semgrep `p/default` 规则对当前仓库扫描通过。
- `./scripts/quality-gate.sh`、许可证审计和相关应用测试通过。

## 回滚

若 10.1.59 出现兼容性回归，应回滚本变更并停止合并依赖该安全基线的 PR；不得回退到
已知有漏洞的 10.1.55 后继续放行安全门禁。

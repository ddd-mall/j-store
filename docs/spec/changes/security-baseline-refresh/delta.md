# CI 与依赖安全基线刷新 Delta

## 背景

`develop` 的安全门禁因两项外部基线漂移而不再可重复通过：Semgrep `p/default`
规则内容发生变化，且 OSV 新增公告后确认 Spring Boot 3.5.16 默认管理的嵌入式 Tomcat
10.1.55 存在已公开漏洞。首次刷新后，`p/default` 在同一天再次改变内容，证明动态响应
配合摘要的方式只能检测漂移，不能提供可复现的规则输入。

## 变更

- Semgrep CE 版本继续由 `requirements-security.txt` 固定。规则输入改为 Semgrep 官方
  `semgrep-rules` 仓库提交 `40b8c63f75dc7c22c8a77482d73bfb864b146f7e`，不再下载可变的
  `p/default` 响应。
- CI 从固定提交中装载仓库所用语言的安全规则，排除 audit、best-practice、correctness、
  maintainability、performance 和 compatibility 等非阻断类别，以保持安全门禁的高信号语义。
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
- 固定提交的 Semgrep 安全规则对当前仓库扫描通过，且工作流不再访问 `p/default`。
- `./scripts/quality-gate.sh`、许可证审计和相关应用测试通过。

## 回滚

若 10.1.59 出现兼容性回归，应回滚本变更并停止合并依赖该安全基线的 PR；不得回退到
已知有漏洞的 10.1.55 后继续放行安全门禁。

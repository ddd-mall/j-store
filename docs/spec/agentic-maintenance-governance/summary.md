# 长期自动化维护治理交付总结

## 已交付

- 建立跨工具 `AGENTS.md`、治理政策、安全政策和周期自动化 runbook。
- 新增 Maintenance Orchestrator、Product Steward、Quality Gate、Security & Supply-chain、SRE / Incident、Release & Migration 六种治理 agent。
- 将 PostgreSQL、Redis 和 JWT 本地配置改为环境变量，添加安全的 `.env.example`，清理当前工作树中的旧地址和已知凭据。
- 新增统一 `scripts/quality-gate.sh`、治理合同脚本与测试。
- 新增 GitHub 质量/安全 workflows、Dependabot、CODEOWNERS、PR 模板和 Copilot 指令。Dependabot 属于当时交付事实，后续因依赖兼容性风险已由分支治理变更明确移除，当前行为以 `docs/spec/changes/branch-management-governance/` 为准。
- 修复 agent 引用路径、技术栈文档和 Docker Java 运行时漂移。

## 验收追踪

需求中的 9 项验收标准均有仓库内实现。外部 GitHub/云平台启用和真实凭据轮换属于明确非目标，不计为仓库实现完成，但仍是上线长期自动维护前的必做事项。

## 验证证据

2026-08-04 执行：

```text
./scripts/quality-gate.sh
PASS: governance contracts
PASS: 28 spec-dev contract tests
PASS: 2 governance contract tests
PASS: Gradle regression tests, BUILD SUCCESSFUL, 84 tasks
```

此外当时通过 `git diff --check`、shell 语法检查和 GitHub/Dependabot YAML 解析检查；该 Dependabot 证据仅描述历史候选，不代表当前仍启用。

## 外部启用事项

1. 轮换所有曾使用旧开发密码和 JWT 值的环境。
2. 推送分支并观察新增 GitHub Actions 的首次真实运行，验证 Docker 25 镜像构建。
3. 配置 GitHub ruleset：required checks、CODEOWNERS approval、禁止 direct push/force push。
4. 按 `docs/operations/agent-automation-runbook.md` 以只读模式启用外部定时 agent，观察两周后再授权其创建修复 PR。

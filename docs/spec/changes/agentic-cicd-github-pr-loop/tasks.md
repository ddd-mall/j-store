# Agentic CI/CD Level 2 GitHub 候选闭环任务

## 合同与核心

- [x] `GH-01` 定义Level 2完整能力profile；Level 0/1继续拒绝远端写，全部level永久拒绝approve、merge、release和production write。
- [x] `GH-02` 以fake GitHub API实现并验证唯一Draft PR、exact-head Ready门禁和三信号幂等handoff核心。
- [x] `GH-03` 将PR编号、GitHub事件、脱敏运维finding和handoff head纳入TaskSnapshot原子持久状态。
- [x] `GH-04` 将可信turn receipt的turn、墙钟时间和token用量接入唯一Symphony运行路径；turn或墙钟超限原子进入blocked。token只作审计，不在仓库内维护模型费率表或推算账单。

## GitHub适配与反馈

- [x] `GH-05` 实现短期installation token的host-side Git push与GitHub REST/GraphQL adapter；接口不得提供approve、merge、release、deployment或workflow写方法。
- [x] `GH-06` 实现唯一Workpad compare-and-reconcile、互斥`agent:*`标签迁移和API冲突恢复。
- [x] `GH-07` 聚合当前head的check runs/status contexts，处理分页、重复名称、rerun attempt和额外check状态。
- [x] `GH-08` 收集actionable review threads与评论并生成标准化ReviewPacket；旧head反馈只保留审计，不驱动新候选。
- [x] `GH-09` 实现候选、基线、基础设施、flaky、需求/权限五类失败路由和有界重试。
- [x] `GH-10` 实现base前移与冲突恢复；禁止静默force push，任何新head重新运行本地与远端门禁。

## Ready与人工交接

- [x] `GH-11` 把Reconciler接入唯一Symphony生命周期和原子SnapshotStore；故障注入证明API成功/本地保存前后均不重复副作用。
- [x] `GH-12` 实现PR模板结构化生成与完整性检查，绑定验收、命令、兼容性、恢复和残余风险。
- [x] `GH-13` 实现Ready、Workpad、`agent:human-review`和配置review request的真实adapter调用与远端资源/状态审计回执。
- [x] `GH-14` 证明handoff至少一种信号成功、失败增强项独立重试、全部失败保持pending。

## 真实验收与灰度

- [ ] `GH-15` 经精确授权在disposable仓库验证合法/违规Draft PR、ruleset、重复事件、失败CI、review返工、base前移和Supervisor重启。
  - 本地前置已完成：仓库身份与精确HTTPS URL绑定；`github-e2e-preflight`纯配置检查；完整Level 2 disposable示例profile；构建期repository/合同摘要绑定；运行期环境与TaskSnapshot身份复核；source record/archive/SBOM/provenance/digest三方校验后的`render-only`部署候选生成；token+expiry Secret与App bot/reviewer身份的完整运行注入；promotion/Git/GitHub/Snapshot副作用前对token lease和handoff身份的运行时fail-closed门禁；以及runbook中受合同检查保护的`GH15-01`至`GH15-07`真实场景、证据和停止条件。默认镜像和权威仓库合同仍为Level 0，该证据不替代真实镜像构建、部署或GitHub E2E。
- [ ] `GH-16` 独立验证GitHub App仓库范围和最小权限；token不得进入Codex、workspace、日志或TaskSnapshot。
- [ ] `GH-17` 在j-store完成一次低风险真实PR，转Ready后停在人工审核；确认无approve、merge、release或deployment路径。
- [ ] `GH-18` 回到只读profile观察两周，按任务类型记录`pass@1`、`success@budget`、unsafe-action、false-success、人工修改量、成本和恢复时间，再决定是否扩大范围。
- [ ] `GH-19` 生成summary，逐项映射AC-GH-01至AC-GH-07和总规格AC-06至AC-10。

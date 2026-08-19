# Agentic CI/CD Level 2 GitHub 候选闭环评审记录

## 2026-08-18 机器层级与Reconciler核心

实现者完成以下组件级切片：

- Level 2受依赖约束的分阶段remote capability profile；Level 0/1远端写保持关闭，全部level永久拒绝approve、merge、release和production write。
- 唯一Draft PR创建/恢复判定、exact-head CI/review/PR模板Ready门禁。
- Ready后Workpad、Issue label和review request逐项幂等handoff；至少一种成功完成交接，失败增强项独立重试。
- TaskSnapshot持久化PR编号、GitHub事件、handoff head和不含远端异常正文的运维finding。

TDD证据包含：缺失模块初始ImportError、Level 2初始拒绝、stale ReviewDecision head初始误通过，随后由最小实现分别关闭。最终Agentic CI/CD tooling 166项、治理合同17项、合同检查、治理检查、Python compile和`git diff --check`通过；exact-head加固后的相关46项再次通过。Gradle `spotlessCheck verifyDependencyResolution licensee test verifyLicenseArtifacts`成功，279个任务中4个执行、275个up-to-date，并验证58个JAR许可证。

完整`./scripts/quality-gate.sh`没有形成全绿证据：它在治理测试阶段因工作树中既有、尚未暂存的历史规格删除而停止，`repository_files()`仍从Git index读取这些路径并报告文件缺失。实现者没有暂存、恢复或改写该既有变更；Gradle后续阶段已单独执行通过。

当时裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`。实现者不能批准自己的权限敏感变更。当时真实GitHub adapter、Symphony接线、运行用量接线、disposable仓库E2E、GitHub App写权限和任何真实远端写仍未完成或授权；`state-contract.json`继续保持Level 0。后续进展与当前裁决见下方按日期追加的记录。

## 2026-08-19 Codex经验与范围复核

对照OpenAI Codex仓库的最小公共接口、有界执行、权限拒绝不可绕过和集成测试经验，本变更只采用与唯一Draft PR闭环直接相关的做法：窄GitHub adapter、确定性身份、持久幂等事件、有界重试与输出。明确不引入通用插件平台、完整agent runtime、goal/task子系统、transport fallback、多模型路由或Level 3 merge/release/deploy接口。

`GH-04`同步收窄：接入可信turn、墙钟和token用量，但token仅用于审计与后续度量，不引入仓库自维护的模型费率表或账单估算。确定性运行硬门仍是turn和墙钟上限；真实费用继续由专用provider project、外部spend controls和逐次模型调用授权治理。

实现已通过224项tooling测试、Agentic CI/CD与治理合同检查、Python compile和`git diff --check`；其中turn与wall-clock超限均有原子进入`blocked`且不推进phase的回归证据。两份Symphony patch已从固定源码快照机械重建，并在`8001b52e3062495a16e520e4ceaf8f9de868c4d0`上按顺序通过标准`git apply --check`；Elixir 1.19.5的`mix compile --warnings-as-errors`通过。在Elixir 1.19.5、Erlang/OTP 28、Git 2.39.5、完整Python 3和锁定依赖环境中，补丁应用后的Symphony原生测试为`296 tests, 0 failures, 6 skipped`。

独立评审随后发现真实协议覆盖缺口：当前Symphony routing patch从`turn/completed`读取usage，但Codex App Server通过独立`thread/tokenUsage/updated`事件上报token用量。现有原生测试fixture复现了错误payload形态，因此全绿结果不能证明真实事件序列可用；真实turn会因缺少可信usage阻断after-run hook。`GH-04`恢复为未完成，修正事件聚合并补真实协议序列测试前不开放模型或远端写能力，`state-contract.json`继续保持Level 0。

同次独立评审还发现两项fail-closed缺口：失败、取消或超时turn没有可信receipt，因而不累计task turn与墙钟预算；`GitHubReconciler.ensure_draft_pull_request()`在远端副作用前没有拒绝`cancelled`、`fused`、`blocked`或`human_review`等非法来源状态，可能创建Draft后把终态任务改回`waiting_ci`。因此`GH-02`也恢复为未完成；两项缺口均需先补负向测试再修复。

## 2026-08-19 独立复评收敛

三个高风险finding已关闭：Draft PR reconcile在任何GitHub调用前拒绝`blocked`、`fused`、`cancelled`和`human_review`；Symphony按可信`threadId + turnId`聚合真实`thread/tokenUsage/updated`与turn terminal事件，支持completion/usage反序和有界等待，并对跨turn、缺失usage、非单调usage及冲突terminal fail closed；失败turn先累计turn、墙钟和已观察token后保存receipt但不推进phase，缺失usage则在保留turn/墙钟事实后原子进入`blocked`。

最终验证包含227项tooling测试、Agentic CI/CD与治理合同检查、Python compile、`git diff --check`、固定commit上的两份patch顺序apply和锁定SHA-256校验。routing patch最终SHA-256为`490ee288ea8d2f44694dec7f35e09fc8a0becb754317bfafcb791f89ef1aaa6d`；Elixir 1.19.5/Erlang OTP 28环境中的`mix compile --warnings-as-errors`及原生`302 tests, 0 failures, 6 skipped`通过。独立复评裁决为`converged`，据此重新完成`GH-02`和`GH-04`。

残余非阻断风险是原生补丁测试直接覆盖failed turn，cancelled与timeout依赖同一receipt/预算分支而未分别执行完整after-run场景。完整`quality-gate.sh`仍被用户已有、尚未暂存的历史规格删除阻断在治理ownership检查；相关文件未被恢复、暂存或改写。机器合同继续保持Level 0，未开放模型、GitHub写入、approve、merge、release、deployment或production write。

## 2026-08-19 GH-05 host-side GitHub adapter实现

实现者完成GH-05组件候选：注入式短期installation token lease、固定GitHub REST/GraphQL操作、拒绝redirect且限制响应体的HTTPS transport，以及绑定可信repository、workspace branch和精确HEAD SHA的非强制Git push。push使用workspace外临时askpass，仅向Git子进程提供token；argv、remote URL、workspace、异常正文和GitHub token环境别名均有负向测试。缺失、空白、过期、非有限或不足以覆盖对应HTTP/Git超时与安全余量的token在外部调用前拒绝。

adapter当前只暴露列举目标head开放PR、创建Draft、转Ready和请求指定reviewer；Workpad与标签语义留给GH-06。调用方不能传入任意method、URL、GraphQL query、Git参数或force选项，且不存在approve、merge、release、deployment、workflow dispatch公共方法。全部远端响应正文和Git stderr均不进入错误消息；真实GitHub请求、push和权限尚未授权或执行。

TDD证据包含：模块缺失ImportError、Git runner异常泄露以及45秒剩余token对60秒push仍被接受的红灯，随后由最小实现关闭。最终相关adapter/Reconciler 28项、完整tooling 240项、spec-dev 28项、Agentic CI/CD合同脚本、治理合同脚本和Python compile通过。治理unittest的49项中47项通过，另2项继续因工作树中既有历史规格删除导致`repository_files()`报告文件缺失；实现者未恢复、暂存或改写这些删除。

当时裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`。GH-05涉及凭据和远端写基础设施，实现者不能批准自己的变更，因此任务保持未勾选；后续在不开放能力的前提下继续形成GH-06组件候选。机器合同仍为Level 0，未提交、推送、部署、读取真实token或执行任何GitHub写操作。

## 2026-08-19 GH-06 Workpad与状态标签冲突恢复实现

实现者完成GH-06组件候选。唯一Workpad由固定marker和配置的GitHub App bot login共同识别：创建后重新分页验证唯一性，已有评论先读取单评论ETag再比较或条件更新；重复marker、非配置bot持有marker、身份变化、缺失ETag或更新响应不一致均fail closed。`409`/`412`创建或更新竞态最多重新读取三次，每次使用新远端事实和ETag，达到上限返回脱敏冲突类别。

状态标签迁移只识别固定的七个`agent:*`流程状态，保留风险、领域及其它非状态标签。adapter从带ETag的Issue事实计算唯一目标集合，已一致时不写，否则用`If-Match`整体替换并校验完整响应；未知状态标签、重复/畸形标签、缺失ETag和响应集合漂移均拒绝。评论分页URL由adapter内部生成且最多100页，不消费远端任意Link目标。

TDD红灯覆盖缺失Workpad/标签接口、创建竞态未重读和重试上限；最终adapter/Reconciler相关39项、完整tooling 251项、spec-dev 28项、Agentic CI/CD合同脚本、治理合同脚本和Python compile通过。测试使用fake transport和非秘密fixture，未访问GitHub。完整质量门禁仍受既有、已跟踪历史规格删除阻断，相关文件没有被恢复、暂存或改写。

当前裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`。GH-05与GH-06都涉及凭据或外部控制面写，仍需独立安全评审后才能勾选；机器合同保持Level 0，没有真实token、GitHub写入、commit、push、deploy、approve、merge或release。

## 2026-08-19 GH-07 当前head CI事实聚合实现

实现者完成GH-07组件候选。adapter只接受完整小写commit SHA，使用固定endpoint分别分页读取`filter=latest` check runs和combined status contexts；每页`total_count`必须一致且最终数量完全匹配，超过100页或响应变化时不返回部分事实。该能力只读，不提供rerun、workflow dispatch或任意查询接口。

check run以`name + app.id + check_suite.id`区分producer，同一suite按`started_at + id`选择最新运行，因此旧失败不会覆盖新pending/success，不同workflow中的同名job也不会被误归为rerun。status context以`context + creator.id`及`updated_at + id`选择最新状态。GitHub已知非终态统一为`PENDING`，成功/neutral/skipped保留确定性结论，其它终态为`FAILURE`；多个producer对同一显示名结论不一致时输出`CONFLICT`。现有Ready门禁只允许required check为`SUCCESS`，并拒绝additional check的`PENDING`、`FAILURE`或`CONFLICT`。

TDD红灯覆盖缺失聚合接口、未识别`waiting`非终态，以及同一App不同check suite被误当作rerun。最终adapter/Reconciler相关45项、完整tooling 257项、spec-dev 28项、Agentic CI/CD合同脚本、治理合同脚本和Python compile通过。未访问真实GitHub，真实API schema与并发变化仍留待GH-15 disposable仓库E2E验证。

当前裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`。GH-07本身是只读聚合，但与待评审的凭据adapter共享实现文件，故与GH-05/GH-06一起等待独立复评；机器合同继续保持Level 0。

## 2026-08-19 GH-08 ReviewPacket与旧head反馈隔离实现

实现者完成GH-08组件候选。新增可序列化`ReviewCommentFeedback`、`ReviewThreadFeedback`和`ReviewPacket`合同；packet构造时强制actionable评论绑定当前head、未outdated且thread未resolved，重复comment、重复分类片段或跨片段元数据漂移均拒绝。每个thread可拆分为actionable和audit片段，使当前head反馈与旧head、缺失commit、outdated及resolved内容保持结构化隔离。

GitHub adapter使用两条固定GraphQL query收集reviewThreads和thread comments，双层分页分别限制100页并拒绝缺失或重复cursor。每个thread页和嵌套comment页都复核PR number与`headRefOid`，head在读取期间变化时不返回部分packet；重复thread/comment ID、GraphQL errors、空thread及畸形身份/时间同样fail closed。评论URL不进入packet，正文始终作为不可信数据保存，不参与工具、endpoint、权限或能力选择。

TDD红灯覆盖缺失ReviewPacket合同与adapter接口、旧head误标actionable、分页head变化未拒绝和重复评论。最终GH-05至GH-08相关协议/adapter测试52项、完整tooling 262项、spec-dev 28项、Agentic CI/CD合同脚本、治理合同脚本和Python compile通过。测试只使用fake GraphQL transport；字段兼容性、权限和真实并发行为仍需GH-15 disposable仓库E2E验证。

当前裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`。GH-05至GH-08继续保持未勾选，等待独立安全与结果评审；Level 0机器合同未改变，没有真实GitHub读写、模型调用、commit、push、deploy、approve、merge或release。

## 2026-08-19 GH-09 五类失败路由实现

实现者完成GH-09组件候选。新增`FailureRouter`，只消费host构造的结构化证据，不读取或解释日志、评论、check名称和LLM文本。路由固定区分需求/权限、基础设施、基线、flaky和候选失败；显式人工决策与基础设施优先，CI基线`FAILURE|CONFLICT`先于flaky，review反馈不得归类为flaky，缺失基线、非失败终态和互相矛盾的显式类别均在状态变更前拒绝。

候选失败复用每root cause最多两次不同strategy的语义修复预算，重复strategy因无进展进入`blocked`，第三种strategy进入`fused`；基础设施和flaky分别使用独立预算。flaky默认每root cause只允许一次授权重跑等待，本切片不提供workflow write或自动rerun接口。基线及需求/权限失败直接等待人工处理。路由结果按`base + head + source kind + event ID`持久化，重启重放不重复消耗预算，base变化后的同名事件形成新决策，旧base证据不能命中当前Snapshot。

TDD红灯为新增优先级回归用例下`25 tests, 1 failure`，暴露基线失败被误判为flaky；修正后focused suite为26项全绿。最终完整tooling 272项、spec-dev 28项、Agentic CI/CD与治理合同脚本、Python compile和`git diff --check`通过。治理unittest为49项中47项通过，另2项仍仅因工作树既有的已跟踪历史规格删除导致`repository_files()`报告文件缺失；实现者没有恢复、暂存或改写这些删除。

当前裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`，因此GH-09保持未勾选。机器合同仍为Level 0，所有GitHub与终态写能力均为false；本切片未读取真实token，未调用GitHub、模型或workflow rerun，未commit、push、deploy、approve、merge、release或写生产。

## 2026-08-19 GH-10 base前移与冲突恢复实现

实现者完成GH-10组件候选。`WorkspaceManager.sync_base()`在可信workspace、精确当前head和干净工作树上fetch固定`origin/develop`，只接受当前可信base的后代。base未变化时不修改候选；前移时使用固定自动化身份执行非交互普通merge，并禁用repository hooks和commit签名提示。成功后同时验证旧head与新base均为新head祖先，从合同上保证后续精确SHA push可fast-forward，不需要rebase或force。

内容冲突只在Git实际产生unmerged entry时分类为`CONFLICT`；host随后立即`merge --abort`并验证原head和干净工作树恢复。其它merge失败不伪装为冲突，目标分支历史重写也直接拒绝。结构化`BaseSyncResult`只记录状态和四个Git身份，不保存路径、diff或Git输出正文。

`SymphonyPhaseBridge.apply_base_sync()`要求结果previous base/head与TaskSnapshot完全一致。成功同步写入新base/head；冲突保留旧身份并持久记录待同步target base。两者都返回`queued/implement`，清除旧candidate、Gate request/receipt、当前review workspace、pending finding、turn receipt和handoff head；旧ReviewDecision与按head键控的GitHub事件只保留审计，不能满足新候选exact-head门禁。恢复轮必须形成包含target base的新候选，并重新执行本地Gate、独立review、普通push以及当前head CI/review聚合。

TDD红灯为focused suite中的两个缺失`sync_base`错误和一个缺失`BaseSyncResult`导入错误；最小实现后29项通过。最终差异审计又以一个失败测试证明通用新head失效没有把`human_review`任务重新排队，修正后补齐no-op、身份漂移、冲突abort、Snapshot重启及重新进入Gate循环覆盖，workspace、PhaseBridge及GitHub Reconciler相关47项通过。最终完整tooling 279项、spec-dev 28项、Agentic CI/CD与治理合同脚本、Python compile和`git diff --check`通过。治理unittest仍为49项中47项通过，另2项仅由工作树既有的已跟踪历史规格删除导致；实现者没有恢复、暂存或改写这些删除。

当前裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`，因此GH-10保持未勾选。测试只操作临时本地bare仓库；机器合同仍为Level 0，本切片没有访问真实GitHub、token或模型，没有对j-store工作分支执行merge、commit、push或deploy，也没有新增approve、merge、release、workflow write、force push或生产写能力。

## 2026-08-19 GH-11 Candidate promotion与唯一恢复入口实现

实现者完成GH-11组件候选，并关闭了此前“Reviewer审查CandidateRevision tree，但pusher只会推送旧base commit”的身份断点。`CandidatePromoter`要求本地Gate PASS、独立Reviewer PASS、phase complete及workspace与不可变CandidateRevision完全一致，以固定host身份创建单父commit；父提交、tree和CandidateRevision trailer均经重新读取验证，再以compare-and-swap更新任务branch并把ReviewDecision绑定到提升后的当前head。返工冻结改为以当前head作为下一CandidateRevision基线，旧`candidate_commit_sha`会在实现和base/head失效时清除。

`GitHubLifecycleController`按promotion、普通精确SHA push、唯一Draft reconcile、当前head checks/review、Ready和handoff的顺序执行，每个边界后由SnapshotStore原子保存。唯一生产接线位于同一Symphony `phase-context`；只有phase complete且`push_commit=true`才构造host-side短期installation token adapter，Level 0/1不读取token或调用GitHub。blocked、fused和cancelled在任何Git或远端副作用前拒绝。

故障注入使用临时真实Git仓库和有状态fake GitHub，在candidate `update-ref`、push、Draft创建、Ready转换以及Workpad、label、review request各自远端成功但Snapshot保存前模拟进程退出。重启后分别从commit parent/tree/trailer、remote branch head、唯一开放PR、Draft状态和信号当前事实恢复，各类语义写入计数均保持一次。review request adapter进一步改为先GET requested reviewers、缺失时POST、随后再次GET验证；已存在请求不重复POST。进入`human_review`后仍只重试失败增强项，成功信号不重写。

TDD红灯包括缺失`github_lifecycle`模块、requested reviewer观察尚未实现导致的两个HTTP合同错误，以及blocked任务在被拒绝前已经执行promotion/push的负向失败；均由最小实现关闭。最终新增6项生命周期故障/恢复测试和1项review request观察测试，完整tooling为286项、spec-dev为28项；Agentic CI/CD与治理合同脚本、Python compile和`git diff --check`通过。治理unittest仍为49项中47项通过，另2项仅由工作树既有的已跟踪历史规格删除导致；实现者没有恢复、暂存或改写这些删除。

当前裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`，因此GH-11保持未勾选。当前PR正文生成仅是满足固定章节的host默认值，验收映射与命令证据的完整结构化生成仍属于GH-12。测试未访问真实GitHub或token，未调用模型，也未对j-store工作分支执行commit、push、PR、Ready、handoff、deploy、approve、merge、release或生产写；机器合同继续保持Level 0。

## 2026-08-19 GH-12 实现自验证

GH-12没有使用模型补写验收意图。实现先确认原生TaskSnapshot只有Issue identifier，不足以生成可审核PR正文；随后把Symphony已规范化的title/description限制在`after_create -> host controller`边界，严格解析已知Issue Form并持久化`TaskBrief`。Issue Form新增兼容/迁移、恢复/回滚、所需人工审批和残余风险字段；每个验收项必须以唯一`AC-*`身份绑定必需验证命令。

`PullRequestPacket`只由已保存简报、CandidateRevision、PASS GateReceipt、精确GateRequest命令和独立PASS ReviewDecision生成。正文包含canonical marker及固定人类可读渲染；Ready从GitHub事实重新解析并要求全文、Snapshot packet、Issue、candidate、promoted head和分支逐项一致。重复/空验收ID、未绑定证据、命令偏差、过期候选/head、未勾选项、占位符、缺失兼容/恢复结论、未解决审批和人工改写都fail closed。

TDD首先以缺失`agentic_cicd.pr_packet`模块确认红灯，然后新增5项Packet/TaskBrief合同测试并扩展Reconciler、lifecycle、runtime controller和Kubernetes patch测试。聚焦的77项测试已通过；两段锁定Symphony patch在固定`8001b52e3062495a16e520e4ceaf8f9de868c4d0`的临时副本中依次`git apply`成功。本切片没有GitHub、token、模型、commit、push、PR、Ready、handoff、deploy或生产写；`state-contract.json`继续保持Level 0。当前裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`，GH-12保持未勾选。

后续原生Symphony audit暴露routing patch中已调用但未定义`SymphonyElixir.AgenticCicd.phase_context/3`的既有缺口，`--warnings-as-errors`因undefined module停止。本切片在已批准的唯一host phase-context边界内补上最小bridge：固定controller路径和参数、120秒上限、精确JSON schema/阶段角色组合校验、稳定脱敏错误，并明确拒绝未实现的remote worker路由。该修正不新增任何capability，也不提供备用controller、transport fallback或权限绕过。

最终本地确定性验证为tooling `291/291`、spec-dev `28/28`、Agentic CI/CD与治理合同脚本PASS、Python compile和`git diff --check` PASS。完整`quality-gate`仍在治理unittest `47/49`停止；两个失败仅由工作树中既有、已跟踪的`docs/spec/changes/agentic-cicd-kubernetes-level0/design.md`等历史规格删除导致，本切片未恢复、暂存或改写这些删除。Symphony audit已证明两段patch按锁定顺序应用成功且`--warnings-as-errors`编译通过；后续全量native test在第三方`lazy_html`预编译NIF下载阶段持续无输出，因审计脚本该步骤无网络超时而人工停止；未将编译成功误报为完整native suite通过。

## 2026-08-19 GH-13/GH-14 审计回执与人工交接恢复

实现者完成GH-13/GH-14组件候选。新增严格`GitHubEventReceipt`，以结构化字段绑定operation、repository、远端resource kind/ID、观察状态、`mutation|observation`来源以及适用的Issue/PR、head、comment更新时间、label和reviewer。Ready在GraphQL mutation前核对REST当前head；Workpad回执使用真实comment ID和`updated_at`；label与review request分别绑定精确Issue/PR目标。GitHub未提供统一mutation event ID的操作不再保存本地拼接的伪事件ID。

Reconciler在任何成功回执进入TaskSnapshot前核对操作、仓库、目标编号、head和目标状态；测试证明stale-head Ready回执和跨信号Workpad回执不会被持久化。Snapshot恢复重新解析所有结构化回执。三种handoff信号仍保持独立幂等键：任一成功即可完成交接，其余失败项下轮独立重试；全部失败保持pending，持久化finding仅含异常类型与固定动作类别，不含远端正文。

TDD红灯首先为缺失`GitHubEventReceipt`的ImportError；随后adapter旧字符串合同与Ready fixture暴露预期迁移失败。补齐实现后adapter/Reconciler/lifecycle聚焦测试通过；最终完整tooling `295/295`、spec-dev `28/28`、Agentic CI/CD合同脚本、治理合同脚本、Python compile和`git diff --check`通过。完整`quality-gate`仍在治理unittest `47/49`停止，两个失败仅来自工作树中既有、已跟踪的`docs/spec/changes/agentic-cicd-kubernetes-level0/design.md`等历史规格删除；本切片未恢复、暂存或改写这些删除。当前裁决为`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`，GH-13/GH-14保持未勾选。机器合同仍为Level 0；未读取真实token、访问GitHub、执行真实写入、启动模型、commit、push、deploy、approve、merge、release或写生产。

## 2026-08-19 GH-05至GH-14独立复审收敛

独立复审首先判定`FAIL / gaps`，确认失败路由和base sync未接入生产生命周期、既有PR正文不会更新、旧Ready回执可覆盖当前Draft事实、Ready/handoff存在head与body竞态、base sync Git子进程继承过宽环境，以及PR Packet无可信skipped-check证据。实现随后只围绕这些阻断项收敛，没有新增Level 3或通用agent runtime能力。

最终实现把base sync放在任何push/PR写入前，并为metadata已更新而Snapshot尚未保存的崩溃窗口记录和核对精确`BaseSyncResult`；后续任意提交不能冒充恢复结果。`FailureRouter`从当前head check、固定base check、同head有界历史、actionable ReviewPacket和固定adapter错误类别生成结构化证据；语义root跨head稳定，event仍绑定base/head/观察历史，push execution failure和remote rejection分别进入有界基础设施与permission路径。actionable ReviewPacket进入下一轮implement上下文，旧Gate、candidate和PR Packet资格在返工时失效。

既有PR会先按当前head恢复Draft并用ETag收敛canonical body。Ready前后及每个handoff信号前后均重读base/head/body/check/review事实；任一漂移立即停止后续信号且不保存`human_review`。GateReceipt显式记录skipped checks，只有可信空集合才能渲染`No skipped checks.`。base sync和candidate promotion的Git子进程统一使用裁剪环境，不继承GitHub token别名。

独立评审最终给出`PASS / converged`，GH-05至GH-14全部PASS。最终本地验证为tooling `319/319`、spec-dev治理测试`28/28`、Agentic CI/CD与agent governance合同脚本、Python compile和`git diff --check`通过。完整quality gate仍为治理`47/49`后停止，两个失败仅来自工作树既有的已跟踪历史规格删除；本次未恢复或改写这些删除。需要外部Symphony源码的runtime preflight未运行。机器合同继续为Level 0，GH-15至GH-19的disposable仓库真实验收、最小权限复核、j-store低风险PR、两周只读观察和最终summary均未执行。

## 2026-08-19 GH-15不可变运行绑定本地准备

GH-15预检后的安全复核发现：虽然示例Level 2合同不作为权威合同，但controller运行子命令仍可由调用者传入任意`--contract`，且镜像复制了整个配置目录，因此示例文件可被直接用于candidate freeze及后续能力判断。修正后，只有预检接受候选合同；全部运行命令固定读取镜像权威路径，示例文件从默认构建上下文排除。独立复核确认旧绕过在argparse阶段被拒绝且没有状态副作用，结论为`PASS`。

随后补充disposable Level 2不可变制品路径。固定构建选项只接受repository，不接受任意合同文件；host在Docker前验证完整候选并生成repository/URL/level/合同摘要绑定。派生镜像以只读文件、OCI labels、source record和manifest digest绑定该profile，默认Level 0 target不变。运行时在任何非预检命令前校验合同摘要、level和环境repository，并在任务入口复核TaskSnapshot repository，防止跨仓库复用镜像或旧状态。

本地聚焦验证覆盖配置预检、生产仓库大小写别名、合同/绑定篡改、缺失绑定的Level 2、环境repository漂移、TaskSnapshot漂移、运行命令合同参数绕过、Docker target和构建脚本边界。该切片没有构建镜像、访问GitHub、读取token、调用模型、部署、push、创建PR、Ready、handoff、approve、merge、release或生产写。真实GH-15仍等待精确仓库、最小权限凭据、reviewer和外部写授权。

独立复核发现同一固定合同绑定不同仓库时原制品后缀相同，可能造成镜像tag和输出文件覆盖。修正后tag与artifact prefix同时包含合同摘要和repository binding摘要；回归测试证明两个仓库的合同摘要相同，但binding摘要、tag和artifact prefix均不同。最终独立结论为`PASS`，仅批准GH-15不可变运行绑定的本地准备，不代表真实镜像构建、GitHub权限或disposable仓库E2E通过。最终本地验证为tooling `330/330`、治理合同`19/19`、Agentic CI/CD与agent governance合同脚本、spec-dev eval结构、Python compile、shell语法和`git diff --check`全部通过。完整quality gate在治理测试`49/51`停止，两个结果仍由工作树既有的已跟踪历史规格删除触发；本切片未恢复或改写这些删除。64-bit binding摘要截断保留为非阻塞理论碰撞风险，若未来命名承担敌对输入下的唯一防覆盖责任，应另行评审更长摘要或不可覆盖输出策略。

后续补齐真实E2E前的离线部署候选入口。构建source record新增明确archive文件名；prepare入口要求人工确认的source record SHA-256，并复核精确disposable repository、Level 2、合同/binding摘要、image tag、archive摘要以及SBOM/SLSA subject与最终manifest digest。渲染结果把主/init容器固定到同一digest-qualified镜像，repository/URL与运行绑定一致，只引用固定GitHub/Codex Secret名称，并写入source record、合同、binding和repository审计annotation。入口只生成只读`manifest.yaml`和`deployment-profile.json`，不读取Secret、不连接GitHub或Kubernetes API、不执行apply；本地Kustomize子进程只继承`PATH/LANG/LC_ALL/TZ`，不继承GitHub或模型凭据。最终聚焦测试`36/36`、完整tooling `337/337`、治理合同`19/19`、合同脚本、eval结构、Python compile、shell语法和`git diff --check`通过。完整quality gate仍在治理`49/51`停止，两个结果仍由既有历史规格删除触发。真实镜像尚未构建，GH-15仍未完成。

生产接线复核确认GitHub lifecycle已由唯一`phase-context`在phase complete且`push_commit=true`时触发，不需要新增第二个reconcile命令；但发现credentialed overlay只注入token，而host provider同时要求可信expiration，因此原配置到达首次push必然返回`token_unavailable`。修正后Secret入口同时保存token与GitHub签发的expiry，限制注入时剩余5分钟至2小时；overlay分别引用两个key。环境provider移入可单测adapter边界，证明token或expiry缺失/非法时不生成lease。Level 2 render候选还要求App bot login与人工reviewer login，拒绝非法、bot reviewer或相同身份，并把两者绑定进只读profile、Deployment环境和审计annotation。包含runtime controller的聚焦测试`81/81`、完整tooling `340/340`、治理合同`19/19`、合同脚本、eval结构、Python compile、shell语法和`git diff --check`通过；完整quality gate仍只因既有历史规格删除在治理`49/51`停止。未创建Secret、未部署、未访问GitHub。

随后补齐Deployment手工漂移时的运行时副作用前门禁。部署候选与controller现在复用同一handoff login校验；`phase-context`在构造adapter、pusher和lifecycle前先验证token lease至少覆盖65秒最长单次Git/HTTP超时，以及已开启Workpad/review request能力所需的App bot和人工reviewer身份。入口级负例证明token缺失或过期、App login缺失或非法、reviewer缺失或非法、身份相同时，adapter、pusher、lifecycle和CandidatePromoter均为零调用，TaskSnapshot字节不变；正例证明合法输入只到达既有唯一lifecycle入口。受影响面测试`150/150`、完整tooling `342/342`、治理相关测试`23/23`、spec-dev `28/28`、Agentic CI/CD与agent governance合同脚本、Python compile、shell语法和`git diff --check`全部通过。完整quality gate仍在治理`49/51`停止，两个结果仍由既有历史规格删除触发，首个缺失目标为`docs/spec/changes/agentic-cicd-kubernetes-level0/design.md`；本切片未恢复或改写这些删除。未读取真实token、访问GitHub、创建Secret、构建镜像或部署，GH-15保持未完成。

完成审计随后确认AC-GH-07仍没有可替代的真实远端证据，且现有runbook只覆盖预检、构建和render，未固定故障注入顺序、每项完成事实与停止条件。新增`GH15-01`至`GH15-07`及`GH16-01`演练契约，要求一次性授权记录、仓库外只读证据目录、原始GitHub/Snapshot/日志/Git事实及SHA-256清单，并明确fake transport、render和复选框不能替代E2E。合同检查与治理测试防止场景或“非授权”边界被删除。只读远端核对显示组织公开仓库只有`ddd-mall/j-store`，当前本机`gh`登录token失效，当前分支对应PR #51已合并；因此没有擅自选择生产仓库或已合并PR作为disposable目标，也未执行任何远端写。最终本地验证为tooling `342/342`、Agentic CI/CD与agent governance相关测试`24/24`、spec-dev `28/28`、两项合同脚本和`git diff --check`通过；完整quality gate仍只因既有历史规格删除在治理`50/52`停止，首个缺失目标不变。

后续GitHub OAuth登录恢复为`pan102887`，只读仓库清单再次确认`ddd-mall`仅有生产仓库`j-store`，个人名下也没有名称或用途明确的disposable/sandbox目标；现有仓库不得被擅自挪用。OAuth token可以读取仓库与ruleset，但GitHub对`user/installations`返回403并要求经GitHub App授权的token，因此不能据此声称已取得专用App installation或最小权限证据。

同一次只读审计确认远端`Protect develop` ruleset `20787654`仍为active，只匹配`refs/heads/develop`，要求review thread resolution和六个合同required contexts，禁止删除与non-fast-forward，`bypass_actors=[]`且当前用户不可bypass。`develop`当时精确SHA为`f383c65045b93c2a686223c18bcb14eade4088eb`；其Check Runs包含五个非PR required checks和两个成功的额外Qodana checks，combined status contexts为空。已合并PR #51原head `b2c099a943edd231877ddbd5ca457958be9323d5`包含全部六个required checks，其中`branch-policy`由同一GitHub Actions App以两个不同`check_suite.id`成功上报，验证了adapter按producer区分同名check并在结论一致时折叠的真实API假设。这些只读生产事实不验证Draft创建、CI/review返工、Ready、handoff、重启恢复或App权限，GH-15/GH-16继续未完成；未执行任何GitHub写入。

进一步只读核对确认`pan102887`是`ddd-mall`的active admin，具备后续由人工决定disposable仓库归属的管理前提。组织已有selected-repositories GitHub App installation `154429971`，app slug为`jstore-agentic-cicd`；当前权限为Metadata/Actions/Checks/Contents/Pull requests read与Issues write，未出现Administration、Secrets、Environments、Deployments或Workflows write。该App可以作为后续候选，不必预设创建第二个App，但Level 2仍需对精确disposable仓库批准安装范围，并把Contents与Pull requests从read提升为write。

现有OAuth token缺少`read:user`，`user/installations/154429971/repositories`返回403；仓库级installation endpoint要求App JWT并返回401。因此本次没有取得selected仓库清单，不能从installation存在推断它已安装到某个disposable仓库，也不能把组织admin身份或OAuth `repo` scope当作App installation token。未刷新OAuth scope、生成App JWT、创建仓库、修改App权限或执行其它远端写。

工作树提交边界审计确认全部tracked/untracked变化都位于Agentic CI/CD范围，但新增`docs/spec/agentic-cicd/archive.md`与7个已吸收的Kubernetes Level 0/Symphony phase-bridge历史规格删除必须作为同一语义迁移：新总规格和runbook已指向归档，若只提交archive而保留旧current change规格，会恢复重复且冲突的状态来源。为避免修改用户真实index，审计在`/tmp`使用独立`GIT_INDEX_FILE`和object database模拟完整候选；把所有当前变化加入该临时index后，完整governance suite `52/52`和staged `git diff --check`通过，而真实`git diff --cached`仍为空、工作树状态逐项不变。这证明此前quality gate的`50/52`只由未提交删除仍存在于真实index导致；形成洁净受审revision时必须共同提交归档与删除，或由用户明确撤销整个归档迁移，不能只挑其中一半。

2026-08-19继续推进时，已在不修改真实index的`/tmp`隔离克隆中把当前71个文件变化形成单一洁净候选提交，完整`./scripts/quality-gate.sh`通过：治理`52/52`、spec-dev `28/28`、tooling `342/342`、格式检查、55个runtime classpath依赖解析、55个模块许可证审计、212个Gradle回归task与58个JAR许可证制品检查均成功。真实工作树和index未被该验证提交改变。

同日只读复核确认GitHub App `jstore-agentic-cicd`归`pan102887`所有，当前仍只有Actions/Checks/Contents/Pull requests read、Issues write和Metadata read。随后按已锁定的Level 2目标创建私有空仓库`ddd-mall/j-store-agentic-cicd-disposable`（repository id `1339730949`）；未推送代码、未修改生产仓库、未修改App权限或安装范围。本地`github-e2e-preflight`对该精确repository/HTTPS URL与Level 2候选合同通过，权威`state-contract.json`仍为Level 0。OAuth调用仓库installation endpoint仍因缺少App JWT返回401，因此不将仓库存在或预检通过当作App已安装、最小权限已满足或GH-15/GH-16已完成的证据。

同日在凭据所有者完成GitHub设置后，App全局权限已收敛为Actions/Checks read、Contents/Issues/Pull requests write与Metadata read，未开放Administration、Secrets、Environments、Deployments或Workflows write。App JWT首次复核发现installation仍只含生产`ddd-mall/j-store`，第二次发现同时包含生产与disposable；两次都在签发正式token前fail closed，用于观察的临时token立即撤销，未产生仓库写入。最终复核证明installation `154429971`的完整仓库列表精确只有私有`ddd-mall/j-store-agentic-cicd-disposable`，才签发一枚进一步限定该repository和上述六项权限的短期installation token。token及脱敏metadata均保存在仓库外`0600`文件，GitHub签发到期时间为`2026-08-19T16:39:45Z`；日志与审计文档不保存token值或Authorization header。受限token只读验证再次列出唯一disposable仓库，`github-e2e-preflight`通过。由于生产`j-store`是公开仓库，匿名或未安装App也可读，因此不把对其HTTP 200或通用repository `permissions`字段当作App写权限证据；GH-16以App全局权限、installation完整列表及受限token签发响应为权威边界。

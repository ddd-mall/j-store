# Agentic CI/CD Level 2 GitHub 候选闭环设计

## 能力层级

| 能力组 | Level 0 | Level 1 | Level 2 |
|---|---:|---:|---:|
| 只读观察与可信bootstrap | true | true | true |
| workspace write、candidate freeze、isolated gate | false | true | true |
| remote branch、push、唯一Draft PR | false | false | true |
| Workpad、Issue label、Ready、review request | false | false | true |
| approve、merge、release、production write | false | false | false |

机器合同不从token权限推断流程授权。Level 2允许按独立审批使用受依赖约束的阶段profile：push依赖remote branch，Draft依赖branch+push，Ready依赖Draft，review request依赖Ready；Workpad和Issue label可独立灰度。最终完成profile覆盖表中全部Level 2能力。实际`state-contract.json`在真实E2E和授权完成前继续保持Level 0。

## 组件边界

`GitHubReconciler`是纯host-side确定性组件。它接收可信TaskSnapshot、GitHub当前事实和显式capability，不启动模型、不执行Git命令、不读取token，也不持有HTTP实现细节。

GitHub adapter负责短期installation token、REST/GraphQL传输、分页、ETag/冲突处理和响应脱敏。Reconciler通过窄接口请求：列举当前branch的开放PR、创建Draft、转Ready、compare-and-reconcile Workpad、替换互斥状态标签和请求配置reviewer。该接口不会暴露approve、merge、release、deployment或workflow写方法。

### GH-05 凭据与传输边界

短期installation token由host注入的provider按请求取得，并携带可信过期时间。缺失、空白、已过期或剩余有效期不足的token在启动Git或HTTP调用前拒绝；token对象的字符串表示必须脱敏。adapter只把token放入当前GitHub HTTPS请求的`Authorization` header，Git push只把token交给受控askpass子进程；token不得出现在argv、remote URL、workspace文件、TaskSnapshot、日志、异常正文或Codex/模型子进程环境中。

Git push要求`create_remote_branch`和`push_commit`同时开启，且本地当前branch和HEAD必须与可信TaskSnapshot完全一致。push refspec固定为`<head SHA>:refs/heads/<task branch>`，禁止接受调用方提供的额外refspec、force选项或任意Git参数；失败只返回稳定的脱敏类别，不转发远端stderr。

REST/GraphQL adapter只暴露唯一Draft闭环需要的窄操作：按完整repository和head branch列举开放PR、创建Draft、把指定PR转Ready、请求指定reviewer、compare-and-reconcile唯一Workpad及替换互斥状态标签。endpoint、HTTP method和GraphQL mutation均由adapter内部固定，调用方不能提交任意URL、method、query或mutation；adapter不得预建approve、merge、release、deployment或workflow写能力。HTTP状态、无效JSON和transport失败映射为不包含远端响应正文、请求header或token的稳定错误类别。

### GH-06 Workpad与状态标签冲突恢复

唯一Workpad使用固定marker和部署端配置的GitHub App bot login共同识别。零个匹配评论时创建后重新列举并验证唯一性；一个匹配评论时读取单评论及ETag，正文一致则不写，不一致则携带`If-Match`条件更新；多个marker、marker由非配置bot持有、响应身份变化或创建后不唯一均fail closed，不修改或删除任何竞争评论。调用方提供的body不得自行包含marker，adapter只生成一个marker。评论分页使用adapter内部生成的同源固定endpoint并设页数上限，不跟随远端任意链接。

互斥状态标签固定为`agent:candidate`、`agent:queued`、`agent:waiting-ci`、`agent:human-review`、`agent:blocked`、`agent:fused`和`agent:cancelled`。迁移时从带ETag的当前Issue事实删除该集合中的其它值、保留所有非状态标签并加入唯一目标标签；结果已一致时不写，否则用`If-Match`整体替换并验证响应中的标签集合完全一致。adapter不创建标签，也不修改集合之外标签的语义。

Workpad或标签条件写收到`409`/`412`时最多重新读取并尝试三次；每次都重新计算预期状态，不复用旧ETag。达到上限、缺失ETag、分页超过上限或响应身份/集合不一致时返回稳定脱敏冲突类别。权限拒绝、无效请求和其它HTTP错误不重试，也不切换endpoint或凭据。

### GH-07 当前head CI事实聚合

CI聚合只接受完整40位小写commit SHA，并分别分页读取该SHA的check runs和combined status contexts；不接受branch、PR number或调用方提供的任意查询参数。check runs显式使用GitHub的`filter=latest`语义，再按`name + app.id + check_suite.id`识别同一producer，并以`started_at + id`选择同一suite的最新运行；因此旧rerun失败不会覆盖新pending或success，不同workflow的同名job也不会被误当作rerun。status contexts按`context + creator.id`识别producer，并以`updated_at + id`选择最新状态。缺失身份、非法状态、时间或ID均fail closed，不依赖Check Run响应未保证提供的`run_attempt`字段。

check run的`queued`、`in_progress`、`waiting`、`requested`和`pending`映射为`PENDING`；`completed`只接受GitHub已知conclusion，其中`success`、`neutral`、`skipped`分别映射为同名大写值，其它终态映射为`FAILURE`。status context的`success`映射为`SUCCESS`，`pending`映射为`PENDING`，`failure`/`error`映射为`FAILURE`。同一显示名称由多个producer或check/status两种来源提供时，只有全部最新值一致才折叠；不一致输出`CONFLICT`，因此required check不会误判成功，additional check也会阻止Ready。

分页达到100页、check runs声明的`total_count`与实际收集数量不一致、响应结构异常或head SHA不合法时不返回部分事实。该聚合器不触发rerun、不修改workflow、不推断旧head结果，也不使用LLM解释check结论。

### GH-08 ReviewPacket与旧head反馈隔离

review adapter以`repository + PR number + expected head SHA`读取固定GraphQL query，并在每一页复核`headRefOid`仍等于expected head。review thread和每条thread内评论均独立分页且最多100页；`hasNextPage=true`必须提供非空cursor，重复cursor、重复thread ID、重复comment ID、响应PR/head变化或GraphQL errors全部fail closed，不返回部分packet。

标准`ReviewPacket`包含repository、PR number、head SHA以及结构化`ReviewThreadFeedback`。每个feedback保存thread ID、path、line、resolved状态、`actionable|audit`分类和评论；评论只保留node ID、作者login、正文、commit SHA、outdated与UTC时间，不保留或执行任意URL。未解决thread中绑定当前head且`outdated=false`的评论组成`actionable`片段；同一thread的旧head、缺失commit或outdated评论组成`audit`片段。resolved thread全部进入audit。混合thread可同时产生两个分类片段，但评论ID不得跨分类重复。

Ready使用packet中唯一actionable thread ID数量，只有零才满足thread门禁。后续实现循环只消费`actionable`片段；audit片段持久保留用于解释历史，但不得生成当前head finding、自动修复或Ready阻塞。评论正文始终视为不可信数据，不作为shell、GitHub endpoint、权限请求或能力合同输入。

### GH-09 五类失败路由与独立预算

`FailureRouter`只消费host构造的`FailureEvidence`：稳定event/root-cause ID、source kind、base/head SHA、当前与基线结论、同一head同一source的终态历史，以及可选的可信基础设施或人工决策类别。它不得从日志正文、评论文本、check名称或LLM判断猜测归因。权限/需求类别和基础设施类别必须来自固定枚举且互斥；证据缺字段、source/head不一致或当前并非失败终态时拒绝路由。

路由优先级固定为：显式需求/权限人工决策、显式基础设施、基线失败、同head同source的flaky证据、候选失败。flaky只适用于CI，且必须同时观察到`SUCCESS`和`FAILURE`；review反馈、`PENDING`或同名producer的`CONFLICT`不能证明flaky。CI候选失败要求基线`SUCCESS`，基线`FAILURE|CONFLICT`路由为baseline；review反馈的baseline固定为`NOT_APPLICABLE`。基线未知时不猜测候选责任。

候选路由以当前candidate revision作为strategy fingerprint，复用现有每根因最多两次实质修复限制；重复strategy表示无进展并block，第三个不同strategy进入fused。基础设施使用现有独立全局重试预算。flaky使用新增的每root-cause独立重跑预算，默认只允许一次，不消耗语义修复或基础设施计数；本切片只返回`await_authorized_rerun`，不增加workflow write或自动rerun API。基线及需求/权限直接block等待人工处理。

每个`base + head + source kind + event ID`只路由一次，结果以脱敏`FailureRoute`写入TaskSnapshot；重放返回原结果且不重复消耗任何预算。路由只记录类别、动作、root cause和稳定原因，不保存日志正文、评论正文、Authorization或远端错误响应。

### GH-10 base前移与冲突恢复

`WorkspaceManager.sync_base()`只对可信workspace、精确当前head和干净工作树执行同步。它先fetch固定的`origin/develop`，要求新base是已记录base的后代；目标分支被重写或可信base不再是候选head祖先时fail closed。base未变化时返回`UNCHANGED`且不修改状态；base前移时使用固定自动化身份执行非交互普通merge，禁用repository hooks和签名提示，不执行rebase、reset、push或force参数。

成功同步必须同时证明旧head和新base都是新head的祖先，因此后续已有的精确SHA push仍是fast-forward。若Git报告内容冲突，host收集是否存在unmerged entry后立即`merge --abort`，验证原head和干净工作树均已恢复，再返回不含路径或日志正文的`CONFLICT`；其它merge失败不伪装成代码冲突。`BaseSyncResult`固定绑定previous base、target base、previous head、result head和`UNCHANGED|UPDATED|CONFLICT`状态。

`SymphonyPhaseBridge.apply_base_sync()`只接受与TaskSnapshot当前base/head完全一致的结果。`UPDATED`写入新base/head，`CONFLICT`保留旧base/head并持久记录待同步目标；两者都把任务返回`queued/implement`，清除旧candidate、Gate request/receipt、当前review workspace、pending finding、turn receipt和handoff head。通用的新head失效入口执行同一清理和重新排队，因此已经进入`human_review`的任务也不会停留在旧handoff终态。旧ReviewDecision与按head键控的GitHub事件只作为审计保留，无法满足新候选的exact-head检查。冲突恢复由后续实现轮基于记录的target base形成包含该base的新候选，再重新执行freeze、本地Gate、独立review、push、当前head CI和review聚合；本切片不新增远端merge、workflow rerun或force push能力。

### GH-11 Candidate promotion与唯一恢复入口

本地闭环的CandidateRevision是对`base commit + exact tree + artifact + snapshot policy`的不可变证明，Reviewer实际审查只读物化tree；它不是可直接push的commit。`CandidatePromoter`因此只在本地Gate PASS、独立Reviewer PASS、phase=`complete`且workspace仍与CandidateRevision逐字节一致时，用`commit-tree`创建一个父提交。父提交固定为评审时head，tree固定为CandidateRevision tree，commit message包含稳定CandidateRevision trailer；随后以`update-ref <new> <old>`比较并交换当前任务branch。host重新读取parent、tree和trailer后才把ReviewDecision的head绑定到该提升commit。该重绑定不是新的模型判断，只证明已审查tree与待push commit完全相同。

promotion使用裁剪环境、固定自动化身份、禁用签名和replace object，不执行hook、任意Git参数、rebase或force。进程若在`update-ref`后、Snapshot保存前退出，重启只接受“当前workspace head是旧head的单父子提交、tree和trailer均匹配”的唯一恢复形态；其它head漂移fail closed。后续返工从当前promoted head冻结新CandidateRevision，并在实现或base/head失效时清除旧`candidate_commit_sha`。

`GitHubLifecycleController`是唯一host-side GitHub闭环入口。它依次执行并在每一步后用SnapshotStore同目录`fsync + os.replace`保存：candidate promotion、精确SHA普通push、唯一Draft reconcile、当前head checks/review packet读取、Ready和三信号handoff。文件原子替换不能包住远端API，因此恢复依赖重新读取事实和语义幂等操作：push相同SHA、按branch查找唯一PR、观察非Draft状态、Workpad compare-and-reconcile、标签条件替换，以及先GET requested reviewers再决定是否POST。远端成功但本地保存前退出时，下一轮只补记已观察事件，不产生第二个PR、评论、标签迁移或review request。

该入口接在现有`phase-context`，仍由同一个Symphony Orchestrator轮询和驱动，不启动第二个App Server或Supervisor。只有phase=`complete`且`push_commit=true`时才构造短期installation token adapter；Level 0/1不读取token也不访问GitHub。CI或review未满足Ready时保持Draft并在后续poll读取同一head；至少一种handoff信号成功进入`human_review`，但仍只重试失败增强项，直到状态标签等剩余信号收敛。blocked、fused和cancelled在任何promotion、push或GitHub调用前拒绝。

### GH-12 受信Issue简报与PR Packet

Symphony已规范化的Issue title和description在`after_create` hook边界以环境变量传给host controller；自由文本不作为shell、权限或endpoint输入。Controller只解析已知Issue Form标题，要求每个验收项使用唯一`AC-*` ID并绑定一条必需验证命令，同时要求兼容/迁移、恢复/回滚、所需人工审批和残余风险的显式结论。解析后的`TaskBrief`与workspace身份一起原子持久化；重启时Issue内容与已保存简报不同则fail closed，不用新文本静默改写验收意图。

Candidate promotion后，host从`TaskBrief + CandidateRevision + GateRequest/GateReceipt + ReviewDecision`生成`PullRequestPacket`。生成必须证明：Gate为PASS且命令集合与简报逐项相同；每个验收证据命令在Gate集合中；独立Reviewer PASS绑定当前promoted head和candidate revision；没有未解决人工审批。Packet包含Issue/候选/head/分支身份、验收映射、命令结果、Gate log digest、兼容性、恢复、reviewer身份、审批、跳过检查和残余风险。

PR body首行保存可往返的canonical Packet marker，其余为固定人类可读渲染。Ready之前从GitHub当前事实重新解析marker，要求全文等于canonical render，并且与Snapshot中Packet、Issue、CandidateRevision、promoted head、source/target branch完全相同。任意手工改写、占位符、未勾选项、过期身份、Gate命令偏差或未解决审批都保持Draft。

### GH-13/GH-14 GitHub审计回执与人工交接恢复

GitHub的Ready、Issue comment、label和requested reviewer接口不提供一种统一且稳定的mutation event ID，因此adapter不得返回本地拼接字符串并将其宣称为远端事件。每次成功调用返回结构化`GitHubEventReceipt`，固定记录operation、repository、resource kind、真实resource ID、观察状态和`mutation|observation`来源；按操作补充Issue/PR编号、head SHA、comment更新时间、目标label或reviewer。Ready在GraphQL mutation前核对REST当前head；review request绑定Reconciler刚读取的exact head；Workpad绑定真实comment ID与`updated_at`；label绑定Issue编号与精确目标label。

Reconciler在写TaskSnapshot前逐字段核对回执与当前幂等键、Issue、PR和head。跨操作、跨仓库、跨Issue/PR、stale head或错误目标回执不得成为成功事件；恢复时已持久化回执也必须重新解析和核对。远端状态已经满足时保存`source=observation`，实际执行写操作后保存`source=mutation`，不虚构GitHub未返回的事件身份。

三个handoff信号继续独立执行和持久化：任一信号具有有效回执即进入`human_review`并记录聚合回执，失败增强项保留脱敏动作类别并在后续轮次单独重试；全部失败时不记录聚合成功、不设置handoff head且保持pending。成功回执和失败finding都可经Snapshot原子保存与重启恢复。

### GH-15 disposable Level 2不可变运行绑定

仓库中的`state-contract.level2-disposable.example.json`只表示完整候选能力，不是可由运行命令选择的合同，也不进入默认镜像构建上下文。非预检controller命令不接受合同路径；默认镜像继续嵌入权威Level 0合同。

只有受审、洁净源码上的controller镜像构建入口接受`--disposable-level2-repository <owner/name>`。该选项固定选取仓库内Level 2候选，重新执行disposable预检，并在临时BuildKit context中生成精确合同副本和`RuntimeBinding`。绑定清单只包含schema版本、repository、精确HTTPS URL、capability level和合同SHA-256；生产仓库、大小写别名、任意候选路径、Level 0/1、不完整远端能力、终端能力或required checks漂移均在Docker调用前拒绝。

Level 2派生镜像以只读文件嵌入合同和绑定，并以OCI labels、source record、SBOM/SLSA subject及最终manifest digest记录repository、capability level、合同摘要和绑定摘要。Level 0沿用原镜像target；Level 2 tag和artifact prefix同时包含合同摘要与repository binding摘要，不会覆盖同一源码的Level 0制品，也不会让使用同一合同的两个disposable仓库互相覆盖。

controller启动任何非预检命令前重新计算合同摘要，并要求合同level、`JSTORE_SYMPHONY_REPOSITORY`和绑定逐项一致。任务创建后，submit proposal、phase、turn、Gate和candidate入口还要求TaskSnapshot repository与镜像绑定一致；因此把仓库A镜像配置为仓库B，或用仓库B镜像继续仓库A的旧状态，都会在Git/GitHub副作用前停止。

离线部署候选入口只接受精确repository、GitHub App bot login、人工reviewer login、构建source record及其人工确认的SHA-256。它要求source record为完整Level 2记录，镜像tag含匹配的合同/binding摘要后缀，archive摘要正确，SBOM和SLSA provenance的subject都绑定同一最终manifest digest，并拒绝生产仓库大小写别名、Level 0/1、目标漂移、未知字段、制品篡改、非法或相同的handoff身份和复用已有输出目录。通过后使用既有credentialed observer作为只读输入，生成repository URL、两个固定Secret引用、App/reviewer环境、主/init容器digest-qualified镜像和审计annotation一致的`manifest.yaml`与`deployment-profile.json`；输出为只读且标记`render-only`。

credentialed overlay从`symphony-github-token`同时读取token和`expires-at-epoch-seconds`，两者缺一时host provider不产生token lease。Secret入口要求操作者提供GitHub签发的可信到期epoch，并在注入时限制剩余有效期为5分钟至2小时；expiry可以出现在命令行和审计中，token值仍只能来自受限文件或非交互stdin。App bot login和reviewer不是Secret，但只从受审Level 2 manifest注入，不由Issue、模型或workspace提供。该入口不读取Secret、不连接GitHub、不执行server dry-run或`kubectl apply`。实际导入镜像、创建Secret、部署和外部写操作仍需对精确repository、image digest、context、namespace和操作集分别批准。

`phase-context`到达GitHub lifecycle前还必须重新执行运行时前置门禁，不能只信任render候选。host先取得token lease并要求剩余有效期至少覆盖Git push与HTTP两种最长单次超时加安全余量；随后按已开启能力要求Workpad作者为安全的`<app>[bot]`、review request目标为安全的人工login，并拒绝相同身份。任一检查失败时不得构造GitHub adapter/pusher/lifecycle，不得promotion、执行Git/HTTP或改写TaskSnapshot。进入lifecycle后Git push和每次HTTP请求仍分别复核token剩余有效期，前置检查不替代逐操作检查。

## Codex经验与范围护栏

本变更只采用OpenAI Codex中直接支撑候选闭环的经验：最小公共接口、固定身份、有界运行与输出、权限拒绝不可绕过、显式可重试错误和公共行为集成测试。基础设施重试必须有次数上限、退避和停止条件；预算超限、权限拒绝、无效请求与身份冲突不得通过重试或替代路径绕过。

不引入Codex的通用插件平台、完整agent runtime、goal/task子系统、transport fallback、多模型路由、通用sandbox/网络代理或自动审批机制，也不为未来Level 3预建merge、release、deploy接口。任何新增抽象必须能直接映射到AC-GH-01至AC-GH-07，否则作为独立变更重新审批。

## 唯一PR与恢复

创建前先按完整repository和可信head branch读取开放PR：

1. 零个：要求remote branch、push和Draft PR三个能力均开启，再创建Draft；
2. 一个：核对base=`develop`、head branch和head SHA后复用；
3. 多个：转blocked，等待人工消歧，不自动关闭任何PR。

成功后在TaskSnapshot记录PR number和稳定事件键。若API成功后进程在本地持久化前退出，恢复时以GitHub开放PR事实找回同一PR，因此不会再创建第二个。

## Ready判定

`PullRequestState`必须来自同一次当前head快照，并包含base/head、Draft状态、PR body、check结论和未解决thread数量。Reconciler拒绝：

- 任一身份与TaskSnapshot不一致；
- required check缺失或非SUCCESS；
- 其它check失败或仍pending；
- actionable thread未解决；
- exact CandidateRevision没有独立Reviewer PASS；
- PR模板章节为空或仍有`- [ ]`；
- 任务处于blocked、fused、cancelled或有pending finding。

Ready事件使用`ready:<repo>:<pr>:<head>`。head变化产生新键并重新执行全部门禁，不复用旧Ready资格。

## 人工交接

handoff前再次执行Ready判定，并要求GitHub事实表明PR已非Draft。三个信号分别使用：

```text
handoff:<repo>:<pr>:<head>:workpad
handoff:<repo>:<pr>:<head>:label
handoff:<repo>:<pr>:<head>:review-request
```

已有成功事件直接跳过；失败只保存动作类型和脱敏错误类别，下一次reconcile单独重试。任一信号成功后记录聚合键和`handoff_head_sha`，任务进入`human_review`；其它失败不回滚Ready或成功事件。全部失败时不迁移任务状态。

## 后续接线

当前已提供机器层级、核心Reconciler、持久状态、host-side candidate promotion/push、唯一Symphony reconcile入口、窄GitHub adapter、Workpad/状态标签/review request冲突恢复、当前head CI事实聚合、标准ReviewPacket、五类失败路由、base前移状态机、受信Issue简报、结构化PR Packet和fake transport验证。后续必须补齐disposable仓库故障注入和真实最小权限负向验证。

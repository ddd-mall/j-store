# LC-16 invocation binding 本地无模型证据

日期：2026-08-16
基线：`88dc2460f41ceae43f403acec1f2d08e3f88d3a8`

## 缺陷与修复合同

旧`complete-turn`根据callback到达时的当前snapshot phase推断role。Reviewer提交FAIL后phase回到implement，同一after hook重试会被重新分类为Implementer receipt并错误进入validate。

修复要求每次model invocation绑定启动前的phase、role、head SHA和可选CandidateRevision。Symphony把host controller返回的phase context附着到受信turn receipt并只通过after hook环境导出；controller在状态写入前核对全部字段，并幂等消费session/thread/turn规范元组。模型输出不能提供或覆盖这些字段。

## 可重放验证

```bash
python3 -m unittest \
  tests.tooling.test_agentic_cicd_runtime_controller \
  tests.tooling.test_agentic_cicd_phase_bridge \
  tests.tooling.test_agentic_cicd_gate_runtime \
  tests.tooling.test_agentic_cicd_coordinator \
  tests.tooling.test_agentic_cicd_protocol \
  tests.tooling.test_agentic_cicd_kubernetes \
  tests.governance.test_agentic_cicd_contract
```

结果：99项测试PASS。覆盖范围包括：

- implement完成与host snapshot恢复；
- validate重入时只冻结、发布和调度一次；
- Gate PASS后进入只读exact-candidate review；
- Review FAIL回流及同一callback立即重放拒绝；
- 任务未来再次进入review时旧session/thread/turn仍被幂等拒绝；
- 历史ReviewDecision保留，但新CandidateRevision不能复用旧PASS；
- 同根因允许两个不同修复候选，第三个进入fused；
- validate和complete的phase context均为`run_model=false`。

```bash
python3 scripts/check-agentic-cicd.py
```

结果：PASS。

```bash
./scripts/quality-gate.sh
```

结果：六阶段全部PASS，包括治理合同、28/44/183项Python测试组、1490个文件的ownership/format检查、55个runtime classpath依赖解析、55个模块许可证审计、212个Gradle测试任务和58个发布JAR许可证验证。

独立规格评审复验了callback重分类/重放、phase-role-head-candidate可信来源、历史ReviewDecision exact-candidate失效、两段patch clean apply以及lock/evidence摘要一致性，结论为PASS、无阻塞finding。评审明确保留以下未验证项：Elixir compile/test、新镜像构建与安全证据、真实App Server单turn及集群review恢复。

两段Symphony patch在锁定commit `8001b52e3062495a16e520e4ceaf8f9de868c4d0`的Git archive上按部署顺序执行`git apply --recount --check`及实际apply，均PASS。新的routing patch SHA-256为`00af6b18e85565de63b9535281ae2bf4c9f8f44744be27bf9db73ba15f69fbe2`；四个invocation环境键始终由host hook显式覆盖，非Reviewer的candidate键使用空值清除任何父进程同名环境继承。

## Symphony原生审计

初次审计暴露了脚本可重复性缺陷：依赖资格和compile/test两个隔离容器分别在线安装同一系统工具链，第二次安装长时间停滞。修复后的脚本从固定Elixir基础digest只构建一次临时audit-toolchain镜像，以`--iidfile`取得实际image ID；两个阶段仍使用独立容器，第二阶段不挂载第一阶段evidence目录，但均按同一image ID启动。Dockerfile只安装`build-essential`、`cmake`、`git`、`ca-certificates`和`python3`，本机回环代理通过build host network使用，apt具有两次重试和30秒HTTP/HTTPS超时。退出trap只删除精确image ID。

```bash
./scripts/agentic-cicd-symphony-audit.sh \
  --output-dir /tmp/jstore-symphony-audit-evidence.lc16-toolchain \
  --symphony-source /home/jupeter/source/symphony
```

结果：固定Symphony commit和两段patch顺序apply通过；依赖锁前后摘要一致；Hex无retired或security advisory；39项许可证清单与既有受审清单逐字节一致；`mix compile --warnings-as-errors`、296项Mix测试（0失败、6跳过）、escript和`codex-cli 0.146.0`全部PASS。报告绑定：

- audit-toolchain Dockerfile SHA-256：`a324ae9a2fd88f350dd88488e7a618d541d1fb841ffcecb03efbbe637f2e0918`；
- audit-toolchain image ID：`sha256:adaa67f5a3178a1f136b51d780643cb7282d35122740c13774c32cbfee6136c0`；
- 审计JSON SHA-256：`87db57f44e45c1ce90653cd2cc1b2f1c830eb520368927d867d4ae628c32a053`；
- 许可证清单SHA-256：`2a81324b800b193747fb988323c8b443ee98b8b34c467bd0f11497391a6df64d`。

原始JSON保存在`evidence/2026-08-16-lc16-symphony-audit.json`。审计结束后按报告中的image ID执行Docker inspect得到`No such image`，证明临时镜像已被精确清理；锁定Symphony checkout仍为洁净状态。

后续独立只读规格/安全复评检查了invocation绑定、callback重放、历史decision账本、两阶段审计隔离、JSON与全部锁定摘要，并独立重跑21项Kubernetes/脚本合同测试，结论为PASS、无阻塞finding。复评保留两个非阻塞风险：清理失败路径仍为best-effort且未注入并发/清理故障；审计脚本主要由静态合同和本次成功原生运行覆盖，尚无fake-Docker对malformed IID、阶段失败trap及本地/远程代理分支的系统故障注入。

## 未完成边界

新的routing patch已通过容器化原生Elixir编译和测试，但尚未构建/部署新的不可变controller镜像，也未启动App Server、模型turn或真实GitHub token。implement/review集群重启、真实单turn和第二App Server负向证据仍属于LC-16/LC-22后续验收。当前Level 0能力及全部远程写保持关闭。

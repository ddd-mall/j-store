# LC-17 controller 镜像安全证据

## 结论

状态：`PASS_SECURITY_REVIEW_DEPLOYMENT_REQUIRES_HUMAN_AUTHORIZATION`

最终controller候选已完成不可变身份、SBOM、SLSA provenance、运行时工具面和OSV扫描核对。移除runtime中的`curl`和OpenSSH客户端后，OpenSSH critical finding消失；最终扫描仍有12组未被Debian标记为`unimportant`的critical finding。独立安全复评逐类核对必要触发条件后确认受审生产路径不可达，AC-LC-09安全资格PASS，LC-17完成。该裁决不授权实际部署；部署仍需精确人工授权及其它适用门禁。

本证据没有修改Level 0机器能力，没有导入集群镜像、更新Pod、注入凭据、启动App Server/model turn或写入GitHub远端。

## 最终候选身份

- controller revision：`86480b1f3819312b3cb4ee978a094f95d81dc2c4`
- Symphony revision：`8001b52e3062495a16e520e4ceaf8f9de868c4d0`
- Codex：`0.146.0`
- runtime manifest digest：`sha256:7edcb88bd99edd88bf07659147beade6119a73f758807a6cd47bc99661566bf6`
- digest引用：`docker.io/library/jstore-agentic-cicd@sha256:7edcb88bd99edd88bf07659147beade6119a73f758807a6cd47bc99661566bf6`
- phase bridge patch SHA-256：`bbaad0e4ad04377b5b64238f7fabbfd383915cf60692f321493dd5f3372bcb8a`
- phase routing patch SHA-256：`00af6b18e85565de63b9535281ae2bf4c9f8f44744be27bf9db73ba15f69fbe2`
- dependency lock SHA-256：`9e22b8a3a5cb3ff49fb14899e224a0ac8dc08523e75b7835724071f00593890a`
- WORKFLOW SHA-256：`ca821efe0c5ed3c495f227ef68b9d4b6cebf785e16b22a34f29710420c8e344d`

本机`docker image inspect`确认RepoDigest与上述manifest一致，OCI labels逐项绑定两个源码revision、Codex、两段patch、依赖锁、WORKFLOW和两个基础镜像；运行用户为`10001:10001`。

## 制品与摘要

制品保存在未入库目录`/tmp/jstore-controller-build-86480b1f`：

| 制品 | SHA-256 |
|---|---|
| Docker archive | `6b42347467e780725a0c09c61680ffb9d0f19cf2125d87a6c4c3b6cfe7cdfdf1` |
| SPDX statement | `59ba9d0381264c1d0b2d4204aed68af70ecfcb47be16986040f3f6a176f8fd5c` |
| SLSA provenance | `fdec9eff6b9040fbf525380d676c6d3578d2b160e810a907afbc8c8d5bdd30f6` |
| source record | `dd43c74db2bfd556b4a7c1a39c49a3fe54e9f070a30e42a99a92c99f7328eae5` |
| OSV JSON | `841652b8eb14f85cd6e7cd4a1d594f8daeb9fb4e09243ccafef2fe92b0fdcb61` |

SPDX 2.3 statement包含140个package和6,422条relationship；SPDX和SLSA statement的唯一subject均绑定runtime manifest digest `7edcb88b...66bf6`。SLSA provenance的build arguments和VCS metadata绑定controller revision `86480b1f...c2c4`，source record再次绑定该revision和本次构建输入。

## 安全缓解

旧候选`175da3b1...b964`在runtime执行Bookworm安全更新后扫描为199组/34个受影响package：14 critical，其中13组critical未标记`unimportant`。独立评审对旧digest `sha256:5bbe1352...058e`给出BLOCK。

提交`a5e03c6d...d937`删除runtime中不必要的`curl`和`openssh-client`，生产bootstrap只接受`https://github.com/ddd-mall/j-store.git`。测试fixture只有显式构造`allow_local_repository=True`时才允许绝对本地仓库路径。所有可信Git子进程清除大小写proxy、SSH/askpass和Git路径状态环境变量，禁用system/global Git config，并设置以下fail-closed策略：

- 默认禁用所有Git transport，仅允许HTTPS；
- 禁止HTTP重定向、proxy、cookie持久化、credential helper和交互式提示；
- 固定HTTP/1.1，避免HTTP/2 stream dependency触发面；
- 低速低于1 byte/s持续30秒即失败，单个Git子进程总时限120秒。

独立评审随后发现候选冻结的`check-ignore`、临时index/tree、通用Git和`hash-object`仍继承完整controller环境，因此中间候选`sha256:cc26e425...3fe54`被判定BLOCK并作废。提交`86480b1f...c2c4`把可信子进程环境统一收敛为仅继承`PATH`、locale和时区，候选Git再显式追加禁用system/global config、交互提示和optional locks的受控变量；临时index只追加受控`GIT_INDEX_FILE`。真实Git wrapper回归仅记录环境变量名称，证明GitHub/model凭据、askpass、proxy、TLS/config及low-speed ambient变量均未进入候选Git子进程。相同检查在最终镜像内执行真实`CandidateSnapshotter.freeze`再次PASS。

最终镜像内直接核对结果：`curl`、`ssh`、`scp`、`sftp`、`sqlite3`和`minizip`均不存在；`git 2.39.5`、`Python 3.11.2`和`codex-cli 0.146.0`可执行；进程身份为`10001:10001`。此前真实GitHub clone smoke成功完成TLS、证书和HTTPS连接，但执行环境在等待GitHub `info/refs`时超时；该结果只证明请求未被本地协议策略拒绝，不能记录为bootstrap成功。新增低速和总时限保证同类外部停滞有界失败。

## 最终OSV扫描

- OSV Scanner：`2.4.0`，commit `b56b5191101d5f27d4787d5583d8d01e9518a7af`
- scanner binary SHA-256：`15314940c10d26af9c6649f150b8a47c1262e8fc7e17b1d1029b0e479e8ed8a0`
- 扫描输入：上述Docker archive，不是attestation-bearing OCI index
- 扫描退出码：`1`，表示发现漏洞，不得当作工具噪声忽略
- 统计：178组、33个受影响package；13 critical、53 high、92 medium、20 low或未评分；56组为Debian `unimportant`

未获`unimportant`分类的12组critical如下；版本为镜像内当前Bookworm源包版本：

| 源包 | 版本 | finding | CVSS |
|---|---|---|---:|
| curl | `7.88.1-10+deb12u15` | `DEBIAN-CVE-2026-10536` | 9.8 |
| curl | `7.88.1-10+deb12u15` | `DEBIAN-CVE-2026-11856` | 9.8 |
| curl | `7.88.1-10+deb12u15` | `DEBIAN-CVE-2026-8924` | 9.1 |
| curl | `7.88.1-10+deb12u15` | `DEBIAN-CVE-2026-8927` | 9.1 |
| glibc | `2.36-9+deb12u14` | `DEBIAN-CVE-2026-5450` | 9.8 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-12087` | 9.1 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-13221` | 9.1 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-42496` | 9.1 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-57433` | 9.8 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-8376` | 9.8 |
| sqlite3 | `3.40.1-2+deb12u2` | `DEBIAN-CVE-2025-7458` | 9.1 |
| zlib | `1:1.2.13.dfsg-1` | `DEBIAN-CVE-2023-45853` | 9.8 |

OSV仍按Debian源包归属报告`curl`，因为Git运行时依赖`libcurl`；删除`curl` CLI不会删除该源包finding。OpenSSH finding已消失。当前实现提供的触发面缓解包括：可信Git固定单一公开HTTPS origin、无凭据、无proxy、无redirect、无cookie，并强制HTTP/1.1；controller不导入`sqlite3`，也未发现MiniZip可执行文件或API。本证据没有由实现者自行接受这些风险，最终可达性裁决来自下述独立复评。

## 独立安全复评

独立评估者只读复算制品摘要、attestation subject和OSV统计，并核对最终镜像及代码触发面，未修改实现或证据。结论如下：

- 身份链、SBOM、SLSA provenance和source record：PASS；
- curl 4项：Git HTTPS helper仍链接`libcurl`，但固定单一HTTPS origin、HTTP/1.1、无Digest凭据、无proxy、无redirect和无cookie排除了四项必要触发条件，缓解充分；
- glibc 1项：受审代码不存在攻击者控制的、显式宽度大于1024的`%mc` scanf路径，当前路径不可达；
- Perl 5项：受审流程不执行相关Socket、Archive::Tar、Storable或超大动态正则API，且32位专属finding不适用于`ivsize=8`的最终镜像；
- SQLite 1项：无CLI、无controller import、无候选任意SQL入口，当前路径不可达；
- zlib/MiniZip 1项：最终镜像未发现MiniZip/ioapi文件或API，受影响组件不存在；
- 候选冻结全部Git入口采用统一最小环境，宿主与镜像内真实wrapper probe均未发现ambient凭据或Git状态传播；
- LC-17与digest `sha256:7edcb88b...66bf6`的AC-LC-09安全资格：PASS；
- 实际部署：`BLOCKED_BY_AUTHORITY`，仍需精确人工部署授权及其它适用门禁。

独立评估者另确认中间候选`sha256:cc26e425...3fe54`为`SUPERSEDED`且不得部署。`workspace.py`和离线preflight `runtime.py`仍有默认环境子进程，但不在当前controller entrypoint内；未来若接入持凭据运行时，必须先迁移到同一白名单helper并重新评审。

## 仓库验证

```text
python3 -m unittest tests.tooling.test_agentic_cicd_kubernetes
Ran 21 tests: PASS

python3 -m unittest tests.tooling.test_agentic_cicd_candidate
Ran 16 tests: PASS

python3 -m unittest tests.tooling.test_agentic_cicd_runtime_controller
Ran 23 tests: PASS

python3 -m unittest \
  tests.tooling.test_agentic_cicd_runtime_controller \
  tests.tooling.test_agentic_cicd_phase_bridge \
  tests.tooling.test_agentic_cicd_gate_runtime \
  tests.tooling.test_agentic_cicd_coordinator \
  tests.tooling.test_agentic_cicd_protocol \
  tests.tooling.test_agentic_cicd_kubernetes \
  tests.governance.test_agentic_cicd_contract
Ran 102 tests: PASS

python3 scripts/check-agentic-cicd.py
PASS

git diff --check
PASS

./scripts/quality-gate.sh
PASS: all six local quality gates completed
```

完整门禁包含28/44/187项Python测试组、1,493个文件ownership/format检查、55个runtime classpath依赖解析、55个模块许可证审计、212个Gradle测试任务和58个发布JAR许可证验证。独立评估者另合并重跑16项candidate和23项runtime测试，共39项PASS。

## 准入裁决与下一步

1. LC-17已经完成，但候选不得在没有精确人工授权时部署或替换当前集群controller。
2. 任一基础镜像、依赖或实现修复都必须产生新的洁净controller revision、runtime digest、SBOM、provenance和最终扫描；旧证据不得授权新候选。
3. 部署后仍需验证新Pod UID/image ID、真实bootstrap/smoke；当前GitHub clone在`info/refs`等待阶段超时，不能宣称完整bootstrap已验证。
4. LC-16的实机单turn/无第二App Server、LC-22的implement/review恢复演练以及LC-02/20/21的凭据和模型人工门均未被本证据满足。

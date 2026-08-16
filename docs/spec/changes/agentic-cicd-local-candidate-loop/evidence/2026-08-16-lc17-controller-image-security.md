# LC-17 controller 镜像安全证据

## 结论

状态：`PASS_SECURITY_REVIEW_DEPLOYMENT_REQUIRES_HUMAN_AUTHORIZATION`

最终controller候选已完成不可变身份、SBOM、SLSA provenance、运行时工具面和OSV扫描核对。移除runtime中的`curl`和OpenSSH客户端后，OpenSSH critical finding消失；最终扫描仍有12组未被Debian标记为`unimportant`的critical finding。独立安全复评逐类核对必要触发条件后确认受审生产路径不可达，AC-LC-09安全资格PASS，LC-17完成。该裁决不授权实际部署；部署仍需精确人工授权及其它适用门禁。

本证据没有修改Level 0机器能力，没有导入集群镜像、更新Pod、注入凭据、启动App Server/model turn或写入GitHub远端。

## 最终候选身份

- controller revision：`e03849d234598c03920a518a84d9733e276004cb`
- Symphony revision：`8001b52e3062495a16e520e4ceaf8f9de868c4d0`
- Codex：`0.146.0`
- runtime manifest digest：`sha256:e3a3e25a569b202ccbdab71e6c1bae6e4ce62ec6c1820dcb26d9b56c51f753b6`
- digest引用：`docker.io/library/jstore-agentic-cicd@sha256:e3a3e25a569b202ccbdab71e6c1bae6e4ce62ec6c1820dcb26d9b56c51f753b6`
- phase bridge patch SHA-256：`bbaad0e4ad04377b5b64238f7fabbfd383915cf60692f321493dd5f3372bcb8a`
- phase routing patch SHA-256：`00af6b18e85565de63b9535281ae2bf4c9f8f44744be27bf9db73ba15f69fbe2`
- dependency lock SHA-256：`9e22b8a3a5cb3ff49fb14899e224a0ac8dc08523e75b7835724071f00593890a`
- WORKFLOW SHA-256：`ca821efe0c5ed3c495f227ef68b9d4b6cebf785e16b22a34f29710420c8e344d`

本机`docker image inspect`确认RepoDigest与上述manifest一致，OCI labels逐项绑定两个源码revision、Codex、两段patch、依赖锁、WORKFLOW和两个基础镜像；运行用户为`10001:10001`。

## 制品与摘要

制品保存在未入库目录`/tmp/jstore-controller-build-e03849d2`：

| 制品 | SHA-256 |
|---|---|
| Docker archive | `d083878f12a9546c9498628794d942c943bbcf613f03deb294fba41d57a392ed` |
| SPDX statement | `0ab869da2601957564c8c9187fac118602f3b9140df5a5734f3860eafd5e7c18` |
| SLSA provenance | `d8079a2e0fb3bdc784e9960ce2bcd9a57810ea38777bc041ecedde066b4d1484` |
| source record | `65785dff06a46611dc1f40d2806a852d09278dd9061fc3e0acd44e20c354da81` |
| OSV JSON | `4ba29c1187262e97647a3582b606e29591527619addd50fd25f86d4bf085bde5` |

SPDX 2.3 statement包含140个package和6,422条relationship；SPDX和SLSA statement的唯一subject均绑定runtime manifest digest `e3a3e25a...f753b6`。SLSA provenance的build arguments和VCS metadata绑定controller revision `e03849d2...04cb`，source record再次绑定该revision和本次构建输入。

## 安全缓解

旧候选`175da3b1...b964`在runtime执行Bookworm安全更新后扫描为199组/34个受影响package：14 critical，其中13组critical未标记`unimportant`。独立评审对旧digest `sha256:5bbe1352...058e`给出BLOCK。

提交`a5e03c6d...d937`删除runtime中不必要的`curl`和`openssh-client`，生产bootstrap只接受`https://github.com/ddd-mall/j-store.git`。测试fixture只有显式构造`allow_local_repository=True`时才允许绝对本地仓库路径。所有可信Git子进程清除大小写proxy、SSH/askpass和Git路径状态环境变量，禁用system/global Git config，并设置以下fail-closed策略：

- 默认禁用所有Git transport，仅允许HTTPS；
- 禁止HTTP重定向、proxy、cookie持久化、credential helper和交互式提示；
- 固定HTTP/1.1，避免HTTP/2 stream dependency触发面；
- 低速低于1 byte/s持续30秒即失败，单个Git子进程总时限120秒。

最终镜像内直接核对结果：`curl`、`ssh`、`scp`、`sftp`均不存在；`git 2.39.5`、`Python 3.11.2`和`codex-cli 0.146.0`可执行；进程身份为`10001:10001`。真实GitHub clone smoke成功完成TLS、证书和HTTPS连接，但当前执行环境在等待GitHub `info/refs`时超时；该结果只证明请求未被本地协议策略拒绝，不能记录为bootstrap成功。新增低速和总时限保证同类外部停滞有界失败。

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
- LC-17与digest `sha256:e3a3e25a...f753b6`的AC-LC-09安全资格：PASS；
- 实际部署：`BLOCKED_BY_AUTHORITY`，仍需精确人工部署授权及其它适用门禁。

评审提出非阻塞加固建议：后续清除`GIT_CONFIG_PARAMETERS`、`GIT_SSL_NO_VERIFY`和Git HTTP low-speed环境变量，避免未来受信部署配置覆盖当前命令级策略。该建议不改变本次已审核digest；若实施，必须作为新revision重新构建、扫描和评审。

## 仓库验证

```text
python3 -m unittest tests.tooling.test_agentic_cicd_kubernetes
Ran 21 tests: PASS

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

完整门禁包含28/44/186项Python测试组、1,492个文件ownership/format检查、55个runtime classpath依赖解析、55个模块许可证审计、212个Gradle测试任务和58个发布JAR许可证验证。

## 准入裁决与下一步

1. LC-17已经完成，但候选不得在没有精确人工授权时部署或替换当前集群controller。
2. 任一基础镜像、依赖或实现修复都必须产生新的洁净controller revision、runtime digest、SBOM、provenance和最终扫描；旧证据不得授权新候选。
3. 部署后仍需验证新Pod UID/image ID、真实bootstrap/smoke；当前GitHub clone在`info/refs`等待阶段超时，不能宣称完整bootstrap已验证。
4. LC-16的实机单turn/无第二App Server、LC-22的implement/review恢复演练以及LC-02/20/21的凭据和模型人工门均未被本证据满足。

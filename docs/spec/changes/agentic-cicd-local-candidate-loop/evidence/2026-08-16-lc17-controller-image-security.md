# LC-17 controller 镜像安全证据

## 结论

状态：`BLOCKED_PENDING_HUMAN_SECURITY_DISPOSITION`

最终controller镜像已完成不可变身份、SBOM、SLSA provenance和OSV扫描核对，但仍有13组未被Debian标记为`unimportant`的critical finding。对应Bookworm源包当前urgency均为`not yet assigned`，且没有适用于当前源包的fixed version。按AC-LC-09，这些finding必须升级、缓解，或由人工明确拒绝/接受上线；在此之前不得部署该候选，LC-17保持未完成。

本证据没有修改Level 0机器能力，没有导入集群镜像、更新Pod、注入凭据、启动App Server/model turn或写入GitHub远端。

## 候选身份

- controller revision：`175da3b1d5892a0280fb4363e264c260ba71b964`
- Symphony revision：`8001b52e3062495a16e520e4ceaf8f9de868c4d0`
- Codex：`0.146.0`
- runtime manifest digest：`sha256:5bbe13528ee63141dc2099d156c5e790c0f3f166dc294b12f22566ec6f87058e`
- digest引用：`docker.io/library/jstore-agentic-cicd@sha256:5bbe13528ee63141dc2099d156c5e790c0f3f166dc294b12f22566ec6f87058e`
- phase bridge patch SHA-256：`bbaad0e4ad04377b5b64238f7fabbfd383915cf60692f321493dd5f3372bcb8a`
- phase routing patch SHA-256：`00af6b18e85565de63b9535281ae2bf4c9f8f44744be27bf9db73ba15f69fbe2`
- dependency lock SHA-256：`9e22b8a3a5cb3ff49fb14899e224a0ac8dc08523e75b7835724071f00593890a`
- WORKFLOW SHA-256：`ca821efe0c5ed3c495f227ef68b9d4b6cebf785e16b22a34f29710420c8e344d`

本机`docker image inspect`确认RepoDigest与上述manifest一致，OCI labels逐项绑定两个源码revision、Codex、两段patch、依赖锁、WORKFLOW和两个基础镜像；运行用户为`10001:10001`。

## 制品与摘要

制品保存在未入库目录`/tmp/jstore-controller-build-175da3b1`：

| 制品 | SHA-256 |
|---|---|
| Docker archive | `33942270c9c8e6fb8ddc93ca772666461789614a0756c105561e807106f21fe6` |
| SPDX statement | `af329e56357c524898ad1dec099a269d5b96edc33b1c399ec75c9415d9614d03` |
| SLSA provenance | `31ff78d94246df9a338b1bb8e65a9b9351465729bb0d4ef11c4b33c26eb62120` |
| source record | `b453e4d85d9328696dd65f16f8ab364fe98adb3593d672d15a1ce3ec9ee96099` |
| OSV JSON | `8c0975061473c232f2bc66a9f4d707fcb8f0b23af27b643c7ecb328fef15262b` |

SPDX 2.3 statement包含147个package和6,522条relationship；SPDX和SLSA statement的唯一subject均绑定runtime manifest digest `5bbe1352...058e`。SLSA provenance同时绑定controller revision `175da3b1...b964`和本次构建输入。

## 构建修复记录

初次构建在容器内获取Hex依赖时无法访问主机loopback代理。提交`4191ff87ef9740d6cb50ceaacf0e938866ae5175`增加大小写proxy build args，仅当代理指向`127.0.0.1`或`localhost`时使用BuildKit host network，并将Hex并发和超时固定为`1`和`120`秒；对应候选随后成功构建。

第一次成功镜像`sha256:ce2b089d...02a6f`的OSV报告SHA-256为`481ea75f...3859`，结果为229组/38个受影响package：16 critical、71 high、114 medium、28 low或未评分，其中64组为Debian `unimportant`，15组critical不是`unimportant`。

提交`175da3b1d5892a0280fb4363e264c260ba71b964`在runtime阶段执行当前Bookworm安全更新后重新构建同一来源合同，结果降为199组/34个受影响package：14 critical、56 high、103 medium、26 low或未评分，其中64组为Debian `unimportant`，13组critical不是`unimportant`。该改进不能被解释为安全门禁通过。

## 最终OSV扫描

- OSV Scanner：`2.4.0`，commit `b56b5191101d5f27d4787d5583d8d01e9518a7af`
- scanner binary SHA-256：`15314940c10d26af9c6649f150b8a47c1262e8fc7e17b1d1029b0e479e8ed8a0`
- 扫描输入：上述Docker archive，不是attestation-bearing OCI index
- 扫描退出码：`1`，不得当作工具噪声忽略

未获`unimportant`分类的critical组如下；版本为镜像内当前Bookworm源包版本：

| 源包 | 版本 | finding | CVSS |
|---|---|---|---:|
| curl | `7.88.1-10+deb12u15` | `DEBIAN-CVE-2026-10536` | 9.8 |
| curl | `7.88.1-10+deb12u15` | `DEBIAN-CVE-2026-11856` | 9.8 |
| curl | `7.88.1-10+deb12u15` | `DEBIAN-CVE-2026-8924` | 9.1 |
| curl | `7.88.1-10+deb12u15` | `DEBIAN-CVE-2026-8927` | 9.1 |
| glibc | `2.36-9+deb12u14` | `DEBIAN-CVE-2026-5450` | 9.8 |
| openssh | `1:9.2p1-2+deb12u10` | `DEBIAN-CVE-2026-60002` | 9.4 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-12087` | 9.1 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-13221` | 9.1 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-42496` | 9.1 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-57433` | 9.8 |
| perl | `5.36.0-7+deb12u3` | `DEBIAN-CVE-2026-8376` | 9.8 |
| sqlite3 | `3.40.1-2+deb12u2` | `DEBIAN-CVE-2025-7458` | 9.1 |
| zlib | `1:1.2.13.dfsg-1` | `DEBIAN-CVE-2023-45853` | 9.8 |

OSV JSON中上述13项对应的Debian 12源包记录均为`not yet assigned`且没有fixed event。这里不把“尚无修复版本”解释为可接受，也不因开发集群隔离而降低critical评级。

## 独立评审

独立只读规格/安全评审核对了最终OSV JSON、AC-LC-09、制品摘要和attestation subject。结论为：

- 制品身份链、SBOM、SLSA provenance及Bookworm更新状态：PASS；
- LC-17：BLOCK；
- 部署`sha256:5bbe1352...058e`：BLOCK；
- 总体裁决：`blocked`，在修复、可验证缓解或人工批准的requirement/risk-policy变化前不得解除。

评审未修改实现或证据，满足实现与批准职责分离。

## 本轮仓库验证

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
Ran 99 tests: PASS

python3 scripts/check-agentic-cicd.py
PASS

./scripts/quality-gate.sh
PASS: all six local quality gates completed
```

完整门禁包含28/44/183项Python测试组、1,492个文件ownership/format检查、55个runtime classpath依赖解析、55个模块许可证审计、212个Gradle测试任务和58个发布JAR许可证验证。

## 准入裁决与下一步

1. 候选可以继续进行只读证据复核，但不得部署或替换当前集群controller。
2. 安全所有者需逐项选择：等待/升级到有修复的受审基础镜像，提出可验证的包级或功能级缓解，或明确拒绝/接受上线风险。
3. 任一修复或基础镜像变更都必须产生新的洁净controller revision、runtime digest、SBOM、provenance和最终扫描；旧证据不得授权新候选。
4. LC-16的实机单turn/无第二App Server、LC-22的implement/review恢复演练以及LC-02/20/21的凭据和模型人工门均未被本证据满足。

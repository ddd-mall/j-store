# Symphony 供应链审计

## 输入与边界

- 上游源码固定为 OpenAI Symphony `8001b52e3062495a16e520e4ceaf8f9de868c4d0`。
- Linux审计构建器固定为 `hexpm/elixir:1.19.5-erlang-28.3-debian-bookworm-20260202-slim@sha256:09279250196a9ad971ebe4673ec2df47bc760c0409a055df8ea283954ac6a099`。
- Codex验证使用固定 Node基础镜像 `node:22-bookworm-slim@sha256:d649c27dae7ba0137b3cef5dd75baa422c08dc3d9e3fc0c23dfb172dc3cc6436`和精确包版本 `@openai/codex@0.146.0`。
- 两段补丁按 `symphony-phase-bridge.patch`、`symphony-phase-routing.patch`顺序应用；随后以只读方式安装 `symphony-mix.lock`。
- Controller测试夹具固定为 `controller.py`，SHA-256为 `865d5188861ad7c02c45d65d3d79a27d9cdbbe6b5fd8bd8e8e16438e254c5e7d`；审计入口在运行前重算并核对该摘要。
- 可重放入口为 `scripts/agentic-cicd-symphony-audit.sh`。它从上游 Git archive建立新工作目录，不复用开发 checkout中的ignored构建产物。
- 公告审计和许可证采集在任何依赖编译/测试前于独立容器阶段完成，证据目录不会挂入后续执行被审代码的容器；`mix.lock`在每个阶段前后均按固定摘要复验。

## 原始风险

锁定上游原始 `mix.lock`执行 `mix hex.audit`得到27个公告，涉及：

- `bandit 1.10.3`：4个 HIGH、3个 MEDIUM；
- `mint 1.7.1`：4个 HIGH、3个 MEDIUM、1个 LOW；
- `phoenix 1.8.4`：2个 HIGH、1个 MEDIUM；
- `plug 1.19.1`：2个 HIGH、1个 MEDIUM、1个 LOW；
- `req 0.5.17`：1个 HIGH、1个 LOW；
- `hpax 1.0.3`：1个 HIGH；
- `decimal 2.3.0`：1个 MEDIUM；
- `phoenix_live_view 1.1.25`：1个 LOW。

高风险主要覆盖HTTP请求走私、无界缓冲/解压、HTTP/2 CONTINUATION、WebSocket重组和进程耗尽；因此不能继续把原始锁用于持有provider/GitHub凭据的Supervisor。

原始公告标识如下，均由同次Hex审计输出：

| 包 | 公告标识 |
| --- | --- |
| bandit | CVE-2026-39803、CVE-2026-39804、CVE-2026-39805、CVE-2026-39806、CVE-2026-39807、CVE-2026-42786、CVE-2026-42788 |
| mint | CVE-2026-48861、CVE-2026-48862、CVE-2026-49753、CVE-2026-49754、CVE-2026-56810、CVE-2026-58229、CVE-2026-59246、CVE-2026-59249 |
| phoenix | CVE-2026-32689、CVE-2026-56811、CVE-2026-56812 |
| plug | CVE-2026-8468、CVE-2026-54892、CVE-2026-56813、CVE-2026-56814 |
| req | CVE-2026-49755、CVE-2026-49756 |
| hpax | CVE-2026-58226 |
| decimal | CVE-2026-32686 |
| phoenix_live_view | CVE-2026-64941 |

## 缓解候选

审查后的锁只升级解除公告及其必需传递约束的包：

| 包 | 原版本 | 候选版本 |
| --- | --- | --- |
| bandit | 1.10.3 | 1.12.4 |
| decimal | 2.3.0 | 3.1.1 |
| ecto | 3.13.5 | 3.14.2 |
| hpax | 1.0.3 | 1.0.4 |
| mint | 1.7.1 | 1.9.3 |
| phoenix | 1.8.4 | 1.8.11 |
| phoenix_live_view | 1.1.25 | 1.1.33 |
| plug | 1.19.1 | 1.20.3 |
| req | 0.5.17 | 0.7.2 |
| solid | 1.2.2 | 1.3.3 |

同时更新了解析产生的必要传递包；完整39项包、版本和上游声明许可证见 `2026-08-16-symphony-dependencies.tsv`。许可证集合为 MIT、Apache-2.0和BSD-2-Clause，未新增copyleft许可证。

Ecto 3.14不再使原先依赖`validate_required/2`的空字符串断言失败；补丁因此把`codex.command`的空值和纯空白值都改为显式拒绝。重负载主机曾暴露依赖墙钟余量的retry测试不稳定；补丁改为保存单调时钟调度基准、精确延迟和到期时间，并断言`到期时间 - 调度基准 = 生产延迟`，从而既不容忍零延迟或过期调度，也不依赖测试进程何时恢复运行。两项均由完整原生测试覆盖，不改变生产retry预算或能力合同。

## Linux验证结果

提交 `89c7b46288db59af49a1f1873a3b6ba6c3cbe401` 在指定开发Linux主机的原生文件系统中以两阶段隔离审计完成。生成的 `symphony-audit.json` SHA-256为 `736c8a35bdc6b74b8316b8879352b9eb8f5b7aec0613e2d6390026af9c718954`，其中绑定：

- Symphony revision `8001b52e3062495a16e520e4ceaf8f9de868c4d0`；
- phase bridge patch `bbaad0e4ad04377b5b64238f7fabbfd383915cf60692f321493dd5f3372bcb8a`；
- phase routing patch `b60be30500e95f7fd8d61ea4f73cab4b618e646f541ede6f67e8e0f3eac27535`；
- dependency lock `9e22b8a3a5cb3ff49fb14899e224a0ac8dc08523e75b7835724071f00593890a`；
- controller fixture `865d5188861ad7c02c45d65d3d79a27d9cdbbe6b5fd8bd8e8e16438e254c5e7d`；
- 固定Elixir与Node基础镜像digest及 Codex `0.146.0`。

许可证清单输出SHA-256为 `2a81324b800b193747fb988323c8b443ee98b8b34c467bd0f11497391a6df64d`。精确运行结果：

```text
Running ExUnit with seed: 765420, max_cases: 32
296 tests, 0 failures, 6 skipped
No retired or security advisory packages found
Generated escript bin/symphony with MIX_ENV=dev
codex-cli 0.146.0
```

在得到该结果前，审计明确暴露并修复了三个环境/兼容问题：Git HTTP/2下载中断、`lazy_html`源码回退缺少CMake、以及Ecto空命令语义变化。没有通过跳过依赖、忽略公告或放宽运行时安全策略来获得PASS。

## 回滚与剩余门

- 当前开发集群回滚目标保持 controller commit `7306578141202251222f790f799410a086272cda`、runtime digest `sha256:d6537f49397dd1ff5229b0f2feda396dde00d00442cf8df59b7600522472b697`；回滚不修改PVC或远程GitHub状态。
- 本文件证明锁与源码候选的兼容性，不单独证明新Supervisor镜像已构建或部署。LC-06仍要求候选镜像digest、完整OCI labels、SBOM、SLSA来源记录和无浮动tag检查。
- Level 0能力合同保持不变；`local_workspace_write`、`freeze_local_candidate`、`run_isolated_gate`和全部远端写能力仍为`false`。

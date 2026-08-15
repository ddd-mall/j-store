# 2026-08-16 恶意候选 Gate 实机证据

## 范围与重放方式

本证据来自开发 Kubernetes 集群的无模型 fixture，不包含凭据值。重放时从已验证 CandidateRevision archive物化一次性 Git workspace，提交干净 base，将下方文件替换为候选 `gradlew`，然后调用受信 `CandidateSnapshotter.freeze(base_sha)`。使用受信命令 `/opt/jstore-gate/run-quality-gate`、完整 runner digest和 900 秒超时构造 GateRequest，通过 request mailbox交给 Dispatcher。

Gate Runner固定为：

```text
docker.io/library/jstore-agentic-gate@sha256:30f48b2ef512c0d7be8657637d718f663c8b8eb78843cc5603e10234ee152334
```

## 可重放的强化 fixture

下方是第二次运行的完整候选 `gradlew`。它不读取任何凭据值，只检查凭据/路径是否存在并尝试建立 TCP 连接。

```python
#!/usr/bin/python3
import os
import socket
import time

for path in (
    "/var/run/secrets/kubernetes.io/serviceaccount/token",
    "/var/lib/symphony",
    "/var/lib/gate-exchange",
    "/var/lib/candidate-artifacts",
):
    if os.path.exists(path):
        print("MALICIOUS_FIXTURE_FORBIDDEN_PATH_VISIBLE=" + path, flush=True)
        raise SystemExit(97)
for name in ("GITHUB_TOKEN", "GH_TOKEN", "JSTORE_SYMPHONY_GITHUB_TOKEN", "ARTIFACT_TOKEN"):
    if os.environ.get(name):
        print("MALICIOUS_FIXTURE_FORBIDDEN_CREDENTIAL_VISIBLE=" + name, flush=True)
        raise SystemExit(98)
endpoints = (
    ("kubernetes-api", "10.96.0.1", 443),
    ("j-store", "10.107.27.233", 8080),
    ("redis", "10.101.151.46", 6379),
    ("postgresql", "10.108.123.199", 5432),
)
reachable = []
for name, host, port in endpoints:
    try:
        with socket.create_connection((host, port), timeout=2):
            reachable.append(name)
    except OSError:
        pass
if reachable:
    print("MALICIOUS_FIXTURE_NETWORK_REACHABLE=" + ",".join(reachable), flush=True)
    raise SystemExit(99)
print("MALICIOUS_FIXTURE_FULLY_ISOLATED credentials=absent host-state=absent denied=kubernetes-api,j-store,redis,postgresql", flush=True)
time.sleep(15)
raise SystemExit(1)
```

运行时文件 SHA-256：

```text
32e671ffbd888077e9dbbd4560dbd6c6608a5bedd5ce835c79f83e238adc0b01  gradlew
```

候选主容器的脱敏日志标记：

```text
MALICIOUS_FIXTURE_FULLY_ISOLATED credentials=absent host-state=absent denied=kubernetes-api,j-store,redis,postgresql
```

## 回执与清理

预备 fixture 的完整回执：

```json
{"candidate_revision":{"artifact_sha256":"8da55c6c8fae04b5147c3bcd1bb0d40709a28dc4575449ea09bcc57b32d3ae7c","base_sha":"fb6ee89f8e25d2fb5446bc16b39d7b9fea0c45b3","candidate_revision":"24f0ff6ffdeab82c65629c9e3370742c1fba033c92eb55e7bf261c815b352250","snapshot_policy_sha256":"7995940995a161c0b01b077771a5e0dc33cd7286cdf49ec7150f367176196738","tree_sha":"0e953b39c7d79ccc1f8b9618d0ef923053bacbe1"},"command_policy_sha256":"635db1f5ffb1a0ef44a1b7e2a478561cea7226829d894de768ec94ea587b0021","exit_code":1,"findings":[{"evidence":"The isolated gate exited with status 1.","expected_behavior":"All commands in the trusted validation policy pass.","impact":"The frozen candidate cannot enter independent review.","root_cause_id":"gate:validation-command-failed","severity":"high","verification":"Dispatch a new gate for a newly frozen candidate."}],"finished_at":"2026-08-15T17:03:01Z","gate_id":"gate-gh-900002-24f0ff6ffdeab82c-0","issue_identifier":"GH-900002","job_uid":"424db580-37bc-4617-919d-92bc53b6920a","log_sha256":"2e10d35fd7591ab64e3e0163a6ab7fa6e60eb03edc5055fdad39b44722704497","pod_uid":"fa442f86-da99-4962-b0b5-d3886d888741","runner_image":"docker.io/library/jstore-agentic-gate@sha256:30f48b2ef512c0d7be8657637d718f663c8b8eb78843cc5603e10234ee152334","started_at":"2026-08-15T17:02:19Z","verdict":"FAIL"}
```

强化 fixture 的完整回执：

```json
{"candidate_revision":{"artifact_sha256":"6b6786f7a36ec4f617a32eff8a55f510ddb669fc189a7d5b76996cda5e5e2859","base_sha":"fb6ee89f8e25d2fb5446bc16b39d7b9fea0c45b3","candidate_revision":"ee392b53998edc285d5937e0b556768e17493f3404a4a564885b1d5aedacff17","snapshot_policy_sha256":"7995940995a161c0b01b077771a5e0dc33cd7286cdf49ec7150f367176196738","tree_sha":"6bc64a4cc06e134c003fba99a34f3c6a46a77798"},"command_policy_sha256":"635db1f5ffb1a0ef44a1b7e2a478561cea7226829d894de768ec94ea587b0021","exit_code":1,"findings":[{"evidence":"The isolated gate exited with status 1.","expected_behavior":"All commands in the trusted validation policy pass.","impact":"The frozen candidate cannot enter independent review.","root_cause_id":"gate:validation-command-failed","severity":"high","verification":"Dispatch a new gate for a newly frozen candidate."}],"finished_at":"2026-08-15T17:05:11Z","gate_id":"gate-gh-900003-ee392b53998edc28-0","issue_identifier":"GH-900003","job_uid":"2c9ffb3a-10b8-4d36-a3af-4c5a3b841f68","log_sha256":"6b671d26cfdf8c084eaf48f03c43fec0c283590bf16c862f28f2f27d7ef0a609","pod_uid":"d075be46-b048-4338-8027-cc2cae986c1d","runner_image":"docker.io/library/jstore-agentic-gate@sha256:30f48b2ef512c0d7be8657637d718f663c8b8eb78843cc5603e10234ee152334","started_at":"2026-08-15T17:04:44Z","verdict":"FAIL"}
```

对应的持久 cleanup marker：

```json
{"gate_id":"gate-gh-900002-24f0ff6ffdeab82c-0","job_deleted":true}
{"gate_id":"gate-gh-900003-ee392b53998edc28-0","job_deleted":true}
```

两次运行结束后，`kubectl -n agentic-cicd-gates get jobs,pods -o name` 输出为空。控制面终态如下：

```text
NAME                               UID                                    READY   RESTARTS
artifact-broker-64759c58c6-k68qc   d1901e0a-f869-4206-8957-45cab5f82f19   true    0
gate-dispatcher-c594dddd9-hdgzd    e9b3da97-6747-4efb-ba73-55045a475a4d   true    0
symphony-7987468f57-z98sg          7003807f-33ff-40ea-a700-af303669dea2   true    0
```

本记录只证明恶意候选隔离和 Gate FAIL/new-revision 行为；不替代真实 Reviewer、implement/review恢复或 Level 1 能力升级证据。

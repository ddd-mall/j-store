#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from agentic_cicd.artifact_broker import ArtifactLeaseStore
from agentic_cicd.gate_dispatcher import GateDispatcher
from agentic_cicd.gate_runtime import GateDispatcherService, GateMailbox, GatePolicy
from agentic_cicd.kubernetes_gate import (
    GateJobImages,
    GateJobSpecBuilder,
    KubernetesGateJobClient,
    ServiceAccountKubernetesApi,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="No-model Kubernetes Gate Dispatcher")
    parser.add_argument("--exchange-root", type=Path, required=True)
    parser.add_argument("--gate-policy", type=Path, required=True)
    parser.add_argument("--broker-url", required=True)
    parser.add_argument("--interval-seconds", type=float, default=1.0)
    arguments = parser.parse_args()

    policy = GatePolicy.load(arguments.gate_policy)
    mailbox = GateMailbox(arguments.exchange_root)
    leases = ArtifactLeaseStore(arguments.exchange_root / "leases")
    builder = GateJobSpecBuilder(
        images=GateJobImages(policy.fetch_image, policy.runner_image),
        broker_url=arguments.broker_url,
        lease_store=leases,
    )
    client = KubernetesGateJobClient(ServiceAccountKubernetesApi(), builder)
    GateDispatcherService(mailbox, GateDispatcher(client), policy).serve(
        arguments.interval_seconds
    )


if __name__ == "__main__":
    main()

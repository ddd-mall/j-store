#!/usr/bin/env bash
set -euo pipefail

namespace="jstore-observability"
context=""
timeout_seconds=420
skip_network_policy=false

usage() {
  cat <<'EOF'
Usage: kubernetes-observability-smoke.sh --context <context> [options]

Options:
  --namespace <name>                  Target namespace (default: jstore-observability)
  --timeout-seconds <seconds>         Per-stage timeout (default: 420)
  --skip-network-policy-enforcement   Continue when the cluster CNI cannot enforce NetworkPolicy

The script uses only synthetic log data. It removes the temporary logger before exit.
EOF
}

while (($#)); do
  case "$1" in
    --context)
      context=${2:?missing context}
      shift 2
      ;;
    --namespace)
      namespace=${2:?missing namespace}
      shift 2
      ;;
    --timeout-seconds)
      timeout_seconds=${2:?missing timeout}
      shift 2
      ;;
    --skip-network-policy-enforcement)
      skip_network_policy=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$context" || "$(kubectl config current-context)" != "$context" ]]; then
  printf '%s\n' 'ERROR: --context must equal the current kubectl context.' >&2
  exit 2
fi
if [[ "$namespace" != "jstore-observability" ]]; then
  printf '%s\n' 'ERROR: this smoke test is scoped to namespace jstore-observability.' >&2
  exit 2
fi
command -v curl >/dev/null
command -v jq >/dev/null

tmp_dir=$(mktemp -d)
port_forward_pid=""
loki_scaled_down=false
cleanup() {
  if $loki_scaled_down; then
    kubectl --context "$context" -n "$namespace" scale statefulset/loki --replicas=1 \
      >/dev/null 2>&1 || true
  fi
  kubectl --context "$context" -n "$namespace" delete daemonset jstore-log-smoke \
    --ignore-not-found --wait=false >/dev/null 2>&1 || true
  if [[ -n "$port_forward_pid" ]]; then
    kill "$port_forward_pid" >/dev/null 2>&1 || true
    wait "$port_forward_pid" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT
umask 077

kubectl --context "$context" -n "$namespace" wait --for=condition=Available deployment/loki-gateway \
  --timeout="${timeout_seconds}s"
for workload in statefulset/loki statefulset/prometheus statefulset/grafana daemonset/alloy; do
  kubectl --context "$context" -n "$namespace" rollout status "$workload" \
    --timeout="${timeout_seconds}s"
done

desired=$(kubectl --context "$context" -n "$namespace" get daemonset alloy -o jsonpath='{.status.desiredNumberScheduled}')
ready=$(kubectl --context "$context" -n "$namespace" get daemonset alloy -o jsonpath='{.status.numberReady}')
linux_nodes=$(kubectl --context "$context" get nodes -l kubernetes.io/os=linux --no-headers | wc -l | tr -d ' ')
if [[ "$desired" != "$ready" || "$desired" != "$linux_nodes" ]]; then
  printf 'ERROR: Alloy readiness mismatch desired=%s ready=%s linux_nodes=%s\n' "$desired" "$ready" "$linux_nodes" >&2
  exit 1
fi

kubectl --context "$context" -n "$namespace" get secret loki-gateway-auth \
  -o jsonpath='{.data.username}' | base64 -d >"$tmp_dir/username"
kubectl --context "$context" -n "$namespace" get secret loki-gateway-auth \
  -o jsonpath='{.data.password}' | base64 -d >"$tmp_dir/password"
kubectl --context "$context" -n "$namespace" get secret loki-gateway-tls \
  -o jsonpath='{.data.ca\.crt}' | base64 -d >"$tmp_dir/ca.crt"

local_port=${KOBS_LOCAL_PORT:-43100}
if [[ ! "$local_port" =~ ^[0-9]+$ || "$local_port" -lt 1024 || "$local_port" -gt 65535 ]]; then
  printf '%s\n' 'ERROR: KOBS_LOCAL_PORT must be an integer between 1024 and 65535.' >&2
  exit 2
fi
kubectl --context "$context" -n "$namespace" port-forward service/loki-gateway \
  "${local_port}:443" --address 127.0.0.1 >"$tmp_dir/port-forward.log" 2>&1 &
port_forward_pid=$!

deadline=$((SECONDS + timeout_seconds))
until curl --silent --show-error --cacert "$tmp_dir/ca.crt" \
  --resolve "loki-gateway.jstore-observability.svc:${local_port}:127.0.0.1" \
  --user "$(<"$tmp_dir/username"):$(<"$tmp_dir/password")" \
  "https://loki-gateway.jstore-observability.svc:${local_port}/ready" >/dev/null 2>&1; do
  ((SECONDS < deadline)) || {
    printf '%s\n' 'ERROR: Loki gateway port-forward did not become ready.' >&2
    exit 1
  }
  sleep 2
done

unauthorized_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --cacert "$tmp_dir/ca.crt" \
  --resolve "loki-gateway.jstore-observability.svc:${local_port}:127.0.0.1" \
  "https://loki-gateway.jstore-observability.svc:${local_port}/ready")
if [[ "$unauthorized_status" != "401" ]]; then
  printf 'ERROR: unauthenticated Loki gateway request returned %s, expected 401.\n' "$unauthorized_status" >&2
  exit 1
fi

marker="kobs-smoke-$(tr -d '-' </proc/sys/kernel/random/uuid)"
cat >"$tmp_dir/logger.yaml" <<EOF
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: jstore-log-smoke
  namespace: ${namespace}
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: jstore-kubernetes-smoke
  template:
    metadata:
      labels:
        app.kubernetes.io/name: jstore-kubernetes-smoke
        jstore.logs/enabled: "true"
        jstore.logs/environment: smoke
    spec:
      automountServiceAccountToken: false
      nodeSelector:
        kubernetes.io/os: linux
      tolerations:
        - operator: Exists
          effect: NoSchedule
      securityContext:
        runAsUser: 65534
        runAsGroup: 65534
        runAsNonRoot: true
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: logger
          image: docker.m.daocloud.io/library/busybox:1.37.0
          command: ["sh", "-c", "echo '{\"@timestamp\":\"'\$(date -u +%Y-%m-%dT%H:%M:%SZ)'\",\"log\":{\"level\":\"INFO\"},\"service\":{\"name\":\"jstore-kubernetes-smoke\",\"environment\":\"smoke\"},\"message\":\"${marker}\",\"correlation_id\":\"${marker}\",\"trace_id\":\"11111111111111111111111111111111\"}'; sleep 3600"]
          resources:
            requests: {cpu: 5m, memory: 8Mi}
            limits: {cpu: 50m, memory: 32Mi}
          securityContext:
            allowPrivilegeEscalation: false
            privileged: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
EOF
kubectl --context "$context" apply -f "$tmp_dir/logger.yaml" >/dev/null
kubectl --context "$context" -n "$namespace" rollout status daemonset/jstore-log-smoke \
  --timeout="${timeout_seconds}s"

query_loki() {
  curl --silent --show-error --fail --get \
    --cacert "$tmp_dir/ca.crt" \
    --resolve "loki-gateway.jstore-observability.svc:${local_port}:127.0.0.1" \
    --user "$(<"$tmp_dir/username"):$(<"$tmp_dir/password")" \
    --data-urlencode "query={service_name=\"jstore-kubernetes-smoke\"} |= \"$1\"" \
    --data-urlencode 'limit=100' \
    "https://loki-gateway.jstore-observability.svc:${local_port}/loki/api/v1/query_range"
}

deadline=$((SECONDS + timeout_seconds))
until result=$(query_loki "$marker") && jq -e \
  --arg marker "$marker" \
  '.status == "success" and (.data.result | length) >= 1
   and any(.data.result[]; .stream.namespace == "jstore-observability"
     and .stream.container == "logger"
     and .stream.environment == "smoke")
   and (tostring | contains($marker))
   and (tostring | contains("pod"))
   and (tostring | contains("node"))' <<<"$result" >/dev/null 2>&1; do
  ((SECONDS < deadline)) || {
    printf 'ERROR: Loki did not return the synthetic marker with Kubernetes metadata: %s\n' "$marker" >&2
    exit 1
  }
  sleep 2
done

logger_pod=$(kubectl --context "$context" -n "$namespace" get pods \
  -l app.kubernetes.io/name=jstore-kubernetes-smoke \
  -o jsonpath='{.items[0].metadata.name}')
logger_node=$(kubectl --context "$context" -n "$namespace" get pod "$logger_pod" \
  -o jsonpath='{.spec.nodeName}')
alloy_pod=$(kubectl --context "$context" -n "$namespace" get pods \
  -l app.kubernetes.io/name=alloy --field-selector "spec.nodeName=${logger_node}" \
  -o jsonpath='{.items[0].metadata.name}')
alloy_uid=$(kubectl --context "$context" -n "$namespace" get pod "$alloy_pod" \
  -o jsonpath='{.metadata.uid}')
alloy_restarts=$(kubectl --context "$context" -n "$namespace" get pod "$alloy_pod" \
  -o jsonpath='{.status.containerStatuses[0].restartCount}')

kubectl --context "$context" -n "$namespace" scale statefulset/loki --replicas=0 >/dev/null
loki_scaled_down=true
deadline=$((SECONDS + timeout_seconds))
until [[ $(kubectl --context "$context" -n "$namespace" get pods \
  -l app.kubernetes.io/name=loki --no-headers 2>/dev/null | wc -l | tr -d ' ') == "0" ]]; do
  ((SECONDS < deadline)) || {
    printf '%s\n' 'ERROR: Loki did not stop for the WAL recovery test.' >&2
    exit 1
  }
  sleep 2
done

outage_marker="kobs-wal-$(tr -d '-' </proc/sys/kernel/random/uuid)"
outage_payload=$(printf \
  '{"@timestamp":"%s","log":{"level":"INFO"},"service":{"name":"jstore-kubernetes-smoke","environment":"smoke"},"message":"%s","correlation_id":"%s","trace_id":"22222222222222222222222222222222"}' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$outage_marker" "$outage_marker")
kubectl --context "$context" -n "$namespace" exec -i "$logger_pod" -- \
  sh -c 'cat > /proc/1/fd/1' <<<"$outage_payload"
sleep 5

set +e
kubectl --context "$context" -n "$namespace" exec "$alloy_pod" -- \
  sh -c 'kill -TERM 1' >/dev/null 2>&1
set -e
deadline=$((SECONDS + timeout_seconds))
until current_restarts=$(kubectl --context "$context" -n "$namespace" get pod "$alloy_pod" \
  -o jsonpath='{.status.containerStatuses[0].restartCount}' 2>/dev/null) \
  && [[ "$current_restarts" -gt "$alloy_restarts" ]]; do
  current_uid=$(kubectl --context "$context" -n "$namespace" get pod "$alloy_pod" \
    -o jsonpath='{.metadata.uid}' 2>/dev/null || true)
  if [[ -n "$current_uid" && "$current_uid" != "$alloy_uid" ]]; then
    printf '%s\n' 'ERROR: Alloy Pod was replaced; the test cannot prove same-Pod WAL recovery.' >&2
    exit 1
  fi
  ((SECONDS < deadline)) || {
    printf '%s\n' 'ERROR: Alloy container did not restart for the WAL recovery test.' >&2
    exit 1
  }
  sleep 2
done
kubectl --context "$context" -n "$namespace" wait --for=condition=Ready "pod/${alloy_pod}" \
  --timeout="${timeout_seconds}s"

kubectl --context "$context" -n "$namespace" scale statefulset/loki --replicas=1 >/dev/null
loki_scaled_down=false
kubectl --context "$context" -n "$namespace" rollout status statefulset/loki \
  --timeout="${timeout_seconds}s"
deadline=$((SECONDS + timeout_seconds))
until result=$(query_loki "$outage_marker") && jq -e --arg marker "$outage_marker" \
  '.status == "success" and (.data.result | length) >= 1 and (tostring | contains($marker))' \
  <<<"$result" >/dev/null 2>&1; do
  ((SECONDS < deadline)) || {
    printf 'ERROR: WAL did not deliver the outage marker after Loki recovery: %s\n' \
      "$outage_marker" >&2
    exit 1
  }
  sleep 2
done

prometheus_ready=$(kubectl --context "$context" get --raw \
  "/api/v1/namespaces/${namespace}/services/http:prometheus:9090/proxy/api/v1/query?query=up%7Bjob%3D%22alloy%22%7D")
jq -e '.status == "success" and (.data.result | length) >= 1 and all(.data.result[]; .value[1] == "1")' \
  <<<"$prometheus_ready" >/dev/null

if $skip_network_policy; then
  printf '%s\n' 'NETWORK_POLICY_ENFORCEMENT=SKIPPED_BY_EXPLICIT_FLAG'
else
  probe_name="kobs-netprobe-$(tr -d '-' </proc/sys/kernel/random/uuid | cut -c1-12)"
  set +e
  kubectl --context "$context" run "$probe_name" --namespace default --rm --restart=Never \
    --image=docker.m.daocloud.io/library/busybox:1.37.0 --command -- \
    nc -z -w 5 "loki-gateway.${namespace}.svc.cluster.local" 443 \
    >"$tmp_dir/network-policy-probe.log" 2>&1
  probe_status=$?
  set -e
  if [[ "$probe_status" -eq 0 ]]; then
    printf '%s\n' 'ERROR: cross-namespace TCP connection reached Loki; the CNI is not enforcing NetworkPolicy.' >&2
    exit 1
  fi
fi

printf 'KUBERNETES_OBSERVABILITY_SMOKE_PASSED marker=%s alloy_nodes=%s\n' "$marker" "$ready"

#!/usr/bin/env bash
set -euo pipefail

context=""
allow_missing_engine=false
engine_namespace="kube-system"
engine_name="kube-router-firewall"

usage() {
  cat <<'EOF'
Usage: agentic-cicd-kubernetes-preflight.sh --context <context> [options]

Read-only validation for the fixed j-store development cluster topology.

Options:
  --allow-missing-engine  Permit bootstrap before NetworkPolicy enforcement exists
EOF
}

while (($#)); do
  case "$1" in
    --context)
      context=${2:?missing context}
      shift 2
      ;;
    --allow-missing-engine)
      allow_missing_engine=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$context" || "$(kubectl config current-context)" != "$context" ]]; then
  printf '%s\n' 'ERROR: --context must equal the current kubectl context.' >&2
  exit 2
fi

failures=0
pass() { printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; failures=$((failures + 1)); }

server_version=$(kubectl --context "$context" version -o json | python3 -c \
  'import json,sys; print(json.load(sys.stdin)["serverVersion"]["gitVersion"])')
[[ "$server_version" == v1.28.* ]] && pass "kubernetes=$server_version" || fail "unsupported kubernetes=$server_version"

for node in k8s-master k8s-worker1; do
  ready=$(kubectl --context "$context" get node "$node" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true)
  os=$(kubectl --context "$context" get node "$node" -o jsonpath='{.status.nodeInfo.operatingSystem}' 2>/dev/null || true)
  arch=$(kubectl --context "$context" get node "$node" -o jsonpath='{.status.nodeInfo.architecture}' 2>/dev/null || true)
  pod_cidr=$(kubectl --context "$context" get node "$node" -o jsonpath='{.spec.podCIDR}' 2>/dev/null || true)
  if [[ "$ready" == True && "$os" == linux && "$arch" == amd64 && -n "$pod_cidr" ]]; then
    pass "node=$node ready podCIDR=$pod_cidr"
  else
    fail "node=$node ready=$ready os=$os arch=$arch podCIDR=${pod_cidr:-missing}"
  fi
done

flannel_desired=$(kubectl --context "$context" -n kube-flannel get daemonset kube-flannel-ds -o jsonpath='{.status.desiredNumberScheduled}' 2>/dev/null || true)
flannel_ready=$(kubectl --context "$context" -n kube-flannel get daemonset kube-flannel-ds -o jsonpath='{.status.numberReady}' 2>/dev/null || true)
if [[ -n "$flannel_desired" && "$flannel_desired" == "$flannel_ready" ]]; then
  pass "flannel=$flannel_ready/$flannel_desired"
else
  fail "flannel=${flannel_ready:-0}/${flannel_desired:-0}"
fi

for namespace in agentic-cicd jstore; do
  enforce=$(kubectl --context "$context" get namespace "$namespace" -o jsonpath='{.metadata.labels.pod-security\.kubernetes\.io/enforce}' 2>/dev/null || true)
  [[ "$enforce" == restricted ]] && pass "namespace=$namespace pod-security=restricted" || fail "namespace=$namespace pod-security=${enforce:-missing}"
done

claim_phase=$(kubectl --context "$context" -n agentic-cicd get pvc symphony-state -o jsonpath='{.status.phase}' 2>/dev/null || true)
pv_node=$(kubectl --context "$context" get pv agentic-cicd-symphony-state -o jsonpath='{.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0]}' 2>/dev/null || true)
[[ "$claim_phase" == Bound && "$pv_node" == k8s-master ]] && pass "symphony-pvc=Bound node=$pv_node" || fail "symphony-pvc=$claim_phase node=$pv_node"

can_create_jobs=$(kubectl --context "$context" auth can-i create jobs.batch -n agentic-cicd \
  --as system:serviceaccount:agentic-cicd:symphony 2>/dev/null || true)
if [[ "$can_create_jobs" == no ]]; then
  pass "symphony-rbac=create-jobs:no"
else
  fail "symphony-rbac=create-jobs:unexpectedly-allowed"
fi

engine_desired=$(kubectl --context "$context" -n "$engine_namespace" get daemonset "$engine_name" -o jsonpath='{.status.desiredNumberScheduled}' 2>/dev/null || true)
engine_ready=$(kubectl --context "$context" -n "$engine_namespace" get daemonset "$engine_name" -o jsonpath='{.status.numberReady}' 2>/dev/null || true)
if [[ -n "$engine_desired" && "$engine_desired" == "$engine_ready" ]]; then
  pass "network-policy-engine=$engine_ready/$engine_desired"
elif [[ "$allow_missing_engine" == true && -z "$engine_desired" ]]; then
  pass "network-policy-engine=missing bootstrap-allowed"
else
  fail "network-policy-engine=${engine_ready:-0}/${engine_desired:-0}"
fi

if ((failures > 0)); then
  printf 'PREFLIGHT_FAILED failures=%d\n' "$failures" >&2
  exit 1
fi
printf 'PREFLIGHT_READY context=%s\n' "$context"

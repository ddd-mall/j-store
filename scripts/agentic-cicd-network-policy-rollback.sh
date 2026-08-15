#!/usr/bin/env bash
set -euo pipefail

context=""
namespace="kube-system"
engine="kube-router-firewall"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cleanup_jobs="$repo_root/deploy/kubernetes/agentic-cicd/network-policy-engine/cleanup-jobs.yaml"

while (($#)); do
  case "$1" in
    --context)
      context=${2:?missing context}
      shift 2
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
[[ "$namespace" == kube-system && "$engine" == kube-router-firewall ]] || exit 2

proxy_mode=$(kubectl --context "$context" -n kube-system get configmap kube-proxy \
  -o jsonpath='{.data.config\.conf}' | sed -n 's/^mode: *"\{0,1\}\([^" ]*\)"\{0,1\}$/\1/p')
if [[ -n "$proxy_mode" && "$proxy_mode" != iptables ]]; then
  printf 'ERROR: cleanup is only approved with kube-proxy iptables mode, got %s.\n' "$proxy_mode" >&2
  exit 1
fi
other_kube_router=$(kubectl --context "$context" get daemonset -A -l k8s-app=kube-router \
  -o name 2>/dev/null || true)
if [[ -n "$other_kube_router" ]]; then
  printf 'ERROR: another kube-router deployment exists: %s\n' "$other_kube_router" >&2
  exit 1
fi

kubectl --context "$context" -n "$namespace" delete daemonset "$engine" \
  --ignore-not-found --wait=true --timeout=120s
kubectl --context "$context" -n "$namespace" delete job \
  kube-router-firewall-cleanup-master kube-router-firewall-cleanup-worker1 \
  --ignore-not-found --wait=true >/dev/null
kubectl --context "$context" apply --dry-run=server -f "$cleanup_jobs" >/dev/null
kubectl --context "$context" apply -f "$cleanup_jobs" >/dev/null
for job in kube-router-firewall-cleanup-master kube-router-firewall-cleanup-worker1; do
  kubectl --context "$context" -n "$namespace" wait job "$job" \
    --for=condition=complete --timeout=120s >/dev/null
  kubectl --context "$context" -n "$namespace" logs job/"$job"
done
kubectl --context "$context" -n "$namespace" delete job \
  kube-router-firewall-cleanup-master kube-router-firewall-cleanup-worker1 \
  --wait=true >/dev/null
kubectl --context "$context" delete clusterrolebinding jstore-kube-router-firewall \
  --ignore-not-found >/dev/null
kubectl --context "$context" delete clusterrole jstore-kube-router-firewall \
  --ignore-not-found >/dev/null
kubectl --context "$context" -n "$namespace" delete serviceaccount kube-router-firewall \
  --ignore-not-found >/dev/null

kubectl --context "$context" -n kube-system rollout status daemonset/kube-proxy --timeout=60s
monitoring_pod=$(kubectl --context "$context" -n monitoring get pod \
  -l app.kubernetes.io/name=grafana -o jsonpath='{.items[0].metadata.name}')
kubectl --context "$context" -n monitoring exec "$monitoring_pod" -c grafana -- \
  curl -fsS --max-time 15 http://j-store.jstore.svc.cluster.local:8080/actuator/health

printf 'NETWORK_POLICY_ENGINE_ROLLED_BACK context=%s\n' "$context"

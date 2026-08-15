#!/usr/bin/env bash
set -euo pipefail

context=""
namespace="kube-system"
engine="kube-router-firewall"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest_dir="$repo_root/deploy/kubernetes/agentic-cicd/network-policy-engine"

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

"$repo_root/scripts/agentic-cicd-kubernetes-preflight.sh" \
  --context "$context" --allow-missing-engine

rendered=$(mktemp "${TMPDIR:-/tmp}/jstore-kube-router-firewall.XXXXXX.yaml")
cleanup() { rm -f -- "$rendered"; }
trap cleanup EXIT
kubectl --context "$context" kustomize "$manifest_dir" >"$rendered"
kubectl --context "$context" apply --dry-run=server -f "$rendered" >/dev/null

if ! {
  kubectl --context "$context" apply -f "$rendered"
  kubectl --context "$context" -n "$namespace" rollout status daemonset/"$engine" --timeout=180s
  "$repo_root/scripts/agentic-cicd-kubernetes-preflight.sh" --context "$context"
  "$repo_root/scripts/agentic-cicd-network-policy-smoke.sh" --context "$context"
  monitoring_pod=$(kubectl --context "$context" -n monitoring get pod \
    -l app.kubernetes.io/name=grafana -o jsonpath='{.items[0].metadata.name}')
  kubectl --context "$context" -n monitoring exec "$monitoring_pod" -c grafana -- \
    curl -fsS --max-time 15 http://j-store.jstore.svc.cluster.local:8080/actuator/health
  kubectl --context "$context" -n jstore rollout status deployment/j-store --timeout=60s
  kubectl --context "$context" -n jstore rollout status statefulset/redis --timeout=60s
}; then
  printf '%s\n' 'ERROR: NetworkPolicy deployment validation failed; rolling back.' >&2
  "$repo_root/scripts/agentic-cicd-network-policy-rollback.sh" --context "$context"
  exit 1
fi

image=$(kubectl --context "$context" -n "$namespace" get daemonset "$engine" \
  -o jsonpath='{.spec.template.spec.containers[0].image}')
printf 'NETWORK_POLICY_ENGINE_READY context=%s image=%s\n' "$context" "$image"

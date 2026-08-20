#!/usr/bin/env bash
set -euo pipefail

context=""
namespace=agentic-cicd
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
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done
[[ -n "$context" && "$(kubectl config current-context)" == "$context" ]] || {
  printf '%s\n' 'ERROR: --context must equal the current kubectl context.' >&2
  exit 2
}
[[ "$namespace" == agentic-cicd ]] || {
  printf '%s\n' 'ERROR: retirement is fixed to namespace agentic-cicd.' >&2
  exit 2
}

"$(dirname "${BASH_SOURCE[0]}")/agentic-cicd-kubernetes-stop.sh" \
  --context "$context" --namespace "$namespace"
kubectl --context "$context" -n "$namespace" delete deployment/symphony \
  service/symphony serviceaccount/symphony --ignore-not-found
kubectl --context "$context" -n "$namespace" get configmap \
  -l app.kubernetes.io/name=symphony \
  -o name | xargs -r kubectl --context "$context" -n "$namespace" delete
printf 'KUBERNETES_SUPERVISOR_REMOVED namespace=%s pvc=retained gate_control_plane=retained\n' \
  "$namespace"

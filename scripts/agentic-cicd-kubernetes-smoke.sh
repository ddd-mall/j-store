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
  printf '%s\n' 'ERROR: smoke is fixed to namespace agentic-cicd.' >&2
  exit 2
}

if replicas=$(kubectl --context "$context" -n "$namespace" get deployment/symphony \
  -o 'jsonpath={.spec.replicas}' 2>/dev/null); then
  [[ "$replicas" == 0 ]] || {
    printf 'ERROR: Kubernetes Symphony still requests %s replicas.\n' "$replicas" >&2
    exit 1
  }
fi
running=$(kubectl --context "$context" -n "$namespace" get pods \
  -l app.kubernetes.io/name=symphony --field-selector=status.phase=Running \
  -o 'jsonpath={.items[*].metadata.name}')
[[ -z "$running" ]] || {
  printf 'ERROR: Kubernetes Symphony Pod remains active: %s\n' "$running" >&2
  exit 1
}
printf 'KUBERNETES_SUPERVISOR_RETIRED namespace=%s state=retained\n' "$namespace"

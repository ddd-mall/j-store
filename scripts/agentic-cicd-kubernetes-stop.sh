#!/usr/bin/env bash
set -euo pipefail

context=""
namespace="agentic-cicd"

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

if [[ -z "$context" || "$(kubectl config current-context)" != "$context" ]]; then
  printf '%s\n' 'ERROR: --context must equal the current kubectl context.' >&2
  exit 2
fi
if [[ "$namespace" != "agentic-cicd" ]]; then
  printf '%s\n' 'ERROR: stop is fixed to namespace agentic-cicd.' >&2
  exit 2
fi

kubectl --context "$context" -n "$namespace" scale deployment/symphony --replicas=0
kubectl --context "$context" -n "$namespace" rollout status deployment/symphony --timeout=120s
printf 'AGENTIC_CICD_LEVEL0_STOPPED namespace=%s pvc=retained\n' "$namespace"

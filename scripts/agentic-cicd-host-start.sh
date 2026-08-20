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
  printf '%s\n' 'ERROR: start is fixed to namespace agentic-cicd.' >&2
  exit 2
}

if replicas=$(kubectl --context "$context" -n "$namespace" get deployment/symphony \
  -o 'jsonpath={.spec.replicas}' 2>/dev/null); then
  [[ "$replicas" == 0 ]] || {
    printf 'ERROR: Kubernetes Symphony still has %s desired replicas.\n' "$replicas" >&2
    exit 1
  }
fi
running_pods=$(kubectl --context "$context" -n "$namespace" get pods \
  -l app.kubernetes.io/name=symphony \
  --field-selector=status.phase=Running \
  -o 'jsonpath={.items[*].metadata.name}')
[[ -z "$running_pods" ]] || {
  printf 'ERROR: Kubernetes Symphony Pod is still running: %s\n' "$running_pods" >&2
  exit 1
}
systemctl is-active --quiet jstore-agentic-cicd.service && {
  printf '%s\n' 'ERROR: host-native Symphony is already active.' >&2
  exit 1
}

systemd-run --quiet --wait --pipe --collect \
  --unit=jstore-agentic-cicd-preflight \
  --property=User=jstore-agentic-cicd \
  --property=Group=jstore-agentic-cicd \
  --property=EnvironmentFile=/etc/jstore-agentic-cicd/runtime.env \
  --property=Environment=HOME=/var/lib/jstore-agentic-cicd/home \
  --property=Environment=CODEX_HOME=/var/lib/jstore-agentic-cicd/home/.codex \
  --property=Environment=JSTORE_SYMPHONY_WORKSPACE_ROOT=/var/lib/jstore-agentic-cicd/workspaces \
  --property=Environment=JSTORE_AGENTIC_CICD_STATE_ROOT=/var/lib/jstore-agentic-cicd/controller \
  --property=Environment=JSTORE_CANDIDATE_ARTIFACT_ROOT=/var/lib/jstore-agentic-candidates \
  --property=Environment=JSTORE_GATE_EXCHANGE_ROOT=/var/lib/jstore-agentic-cicd/gate-exchange \
  --property=LoadCredential=github-token:/etc/jstore-agentic-cicd/credentials/github-token \
  --property=LoadCredential=github-token-expires-at:/etc/jstore-agentic-cicd/credentials/github-token-expires-at \
  --property=LoadCredential=codex-auth.json:/etc/jstore-agentic-cicd/credentials/codex-auth.json \
  --property=LoadCredential=codex-config.toml:/etc/jstore-agentic-cicd/credentials/codex-config.toml \
  /opt/jstore-agentic-cicd/current/bin/run-symphony --preflight-only

systemctl start jstore-agentic-cicd.service
systemctl is-active --quiet jstore-agentic-cicd.service
printf 'HOST_RUNTIME_STARTED service=jstore-agentic-cicd dashboard=http://127.0.0.1:4000\n'

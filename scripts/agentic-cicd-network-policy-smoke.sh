#!/usr/bin/env bash
set -euo pipefail

context=""
namespace="agentic-cicd-netpol-smoke"
image="docker.m.daocloud.io/library/busybox@sha256:9db7b59979c38555a39def84a31fb98b5296952f9e3afd4f6f11f05b07adfab0"

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
if [[ "$namespace" != agentic-cicd-netpol-smoke ]]; then
  printf '%s\n' 'ERROR: smoke namespace is not the fixed disposable namespace.' >&2
  exit 2
fi

cleanup() {
  kubectl --context "$context" delete namespace "$namespace" --ignore-not-found \
    --wait=true --timeout=120s >/dev/null
}
trap cleanup EXIT
cleanup

rendered=$(mktemp "${TMPDIR:-/tmp}/jstore-netpol-smoke.XXXXXX.yaml")
trap 'cleanup; rm -f -- "$rendered"' EXIT
cat >"$rendered" <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: $namespace
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/warn: restricted
---
apiVersion: v1
kind: Pod
metadata:
  name: server
  namespace: $namespace
  labels:
    app: server
spec:
  automountServiceAccountToken: false
  nodeSelector:
    kubernetes.io/hostname: k8s-master
  tolerations:
    - key: node-role.kubernetes.io/control-plane
      operator: Exists
      effect: NoSchedule
  securityContext:
    runAsNonRoot: true
    runAsUser: 65532
    runAsGroup: 65532
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: server
      image: $image
      imagePullPolicy: IfNotPresent
      command: ["/bin/sh", "-c"]
      args: ["printf policy-ok >/tmp/index.html; exec httpd -f -p 8080 -h /tmp"]
      resources:
        requests: {cpu: 10m, memory: 16Mi}
        limits: {cpu: 100m, memory: 64Mi}
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities: {drop: ["ALL"]}
      volumeMounts:
        - name: tmp
          mountPath: /tmp
  volumes:
    - name: tmp
      emptyDir: {sizeLimit: 16Mi}
---
apiVersion: v1
kind: Pod
metadata:
  name: allowed-client
  namespace: $namespace
  labels: {client: allowed}
spec:
  automountServiceAccountToken: false
  nodeSelector: {kubernetes.io/hostname: k8s-worker1}
  securityContext:
    runAsNonRoot: true
    runAsUser: 65532
    runAsGroup: 65532
    seccompProfile: {type: RuntimeDefault}
  containers:
    - name: client
      image: $image
      imagePullPolicy: IfNotPresent
      command: ["/bin/sh", "-c", "exec sleep 3600"]
      resources:
        requests: {cpu: 10m, memory: 16Mi}
        limits: {cpu: 100m, memory: 64Mi}
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities: {drop: ["ALL"]}
---
apiVersion: v1
kind: Pod
metadata:
  name: ingress-denied-client
  namespace: $namespace
  labels: {client: ingress-denied}
spec:
  automountServiceAccountToken: false
  nodeSelector: {kubernetes.io/hostname: k8s-worker1}
  securityContext:
    runAsNonRoot: true
    runAsUser: 65532
    runAsGroup: 65532
    seccompProfile: {type: RuntimeDefault}
  containers:
    - name: client
      image: $image
      imagePullPolicy: IfNotPresent
      command: ["/bin/sh", "-c", "exec sleep 3600"]
      resources:
        requests: {cpu: 10m, memory: 16Mi}
        limits: {cpu: 100m, memory: 64Mi}
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities: {drop: ["ALL"]}
---
apiVersion: v1
kind: Pod
metadata:
  name: egress-denied-client
  namespace: $namespace
  labels: {client: egress-denied}
spec:
  automountServiceAccountToken: false
  nodeSelector: {kubernetes.io/hostname: k8s-worker1}
  securityContext:
    runAsNonRoot: true
    runAsUser: 65532
    runAsGroup: 65532
    seccompProfile: {type: RuntimeDefault}
  containers:
    - name: client
      image: $image
      imagePullPolicy: IfNotPresent
      command: ["/bin/sh", "-c", "exec sleep 3600"]
      resources:
        requests: {cpu: 10m, memory: 16Mi}
        limits: {cpu: 100m, memory: 64Mi}
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities: {drop: ["ALL"]}
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny
  namespace: $namespace
spec:
  podSelector: {}
  policyTypes: [Ingress, Egress]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: server-ingress
  namespace: $namespace
spec:
  podSelector: {matchLabels: {app: server}}
  policyTypes: [Ingress]
  ingress:
    - from:
        - podSelector:
            matchExpressions:
              - key: client
                operator: In
                values: [allowed, egress-denied]
      ports: [{protocol: TCP, port: 8080}]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: client-egress
  namespace: $namespace
spec:
  podSelector:
    matchExpressions:
      - key: client
        operator: In
        values: [allowed, ingress-denied]
  policyTypes: [Egress]
  egress:
    - to:
        - podSelector: {matchLabels: {app: server}}
      ports: [{protocol: TCP, port: 8080}]
EOF

kubectl --context "$context" create namespace "$namespace" --dry-run=client -o yaml \
  | kubectl --context "$context" apply -f - >/dev/null
kubectl --context "$context" label namespace "$namespace" --overwrite \
  pod-security.kubernetes.io/enforce=restricted \
  pod-security.kubernetes.io/audit=restricted \
  pod-security.kubernetes.io/warn=restricted >/dev/null
kubectl --context "$context" apply --dry-run=server -f "$rendered" >/dev/null
kubectl --context "$context" apply -f "$rendered" >/dev/null
for pod in server allowed-client ingress-denied-client egress-denied-client; do
  kubectl --context "$context" -n "$namespace" wait pod "$pod" \
    --for=condition=Ready --timeout=180s >/dev/null
done
sleep 5
server_ip=$(kubectl --context "$context" -n "$namespace" get pod server -o jsonpath='{.status.podIP}')
positive=$(kubectl --context "$context" -n "$namespace" exec allowed-client -- \
  wget -T 5 -qO- "http://${server_ip}:8080/")
[[ "$positive" == policy-ok ]] || {
  printf 'ERROR: allowed cross-node flow returned %s\n' "$positive" >&2
  exit 1
}
if kubectl --context "$context" -n "$namespace" exec ingress-denied-client -- \
  wget -T 3 -qO- "http://${server_ip}:8080/" >/dev/null 2>&1; then
  printf '%s\n' 'ERROR: ingress-denied flow unexpectedly succeeded.' >&2
  exit 1
fi
if kubectl --context "$context" -n "$namespace" exec egress-denied-client -- \
  wget -T 3 -qO- "http://${server_ip}:8080/" >/dev/null 2>&1; then
  printf '%s\n' 'ERROR: egress-denied flow unexpectedly succeeded.' >&2
  exit 1
fi

printf 'NETWORK_POLICY_SMOKE_PASS server_node=k8s-master client_node=k8s-worker1 server_ip=%s\n' "$server_ip"

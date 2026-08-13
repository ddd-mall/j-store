#!/usr/bin/env bash
set -euo pipefail

namespace="jstore"
context=""
jar_path=""
timeout_seconds=1200
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest_dir="$repo_root/deploy/kubernetes/application/base"
loader_name="jstore-artifact-loader"

usage() {
  cat <<'EOF'
Usage: kubernetes-development-deploy.sh --context <context> --jar <app.jar> [options]

Options:
  --namespace <name>          Fixed target namespace (default: jstore)
  --timeout-seconds <value>   Per-stage timeout (default: 1200)

The script creates/rotates only the jstore_app role and j_store_codex database,
creates runtime Secrets in namespace jstore, uploads the JAR to a dedicated PVC,
and deploys the development workload. It never prints generated credentials.
EOF
}

while (($#)); do
  case "$1" in
    --context)
      context=${2:?missing context}
      shift 2
      ;;
    --jar)
      jar_path=${2:?missing JAR path}
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
if [[ "$namespace" != "jstore" ]]; then
  printf '%s\n' 'ERROR: this development deployment is fixed to namespace jstore.' >&2
  exit 2
fi
if [[ -z "$jar_path" || ! -f "$jar_path" || ! -s "$jar_path" ]]; then
  printf '%s\n' 'ERROR: --jar must reference a non-empty Spring Boot JAR.' >&2
  exit 2
fi
if [[ ! "$timeout_seconds" =~ ^[0-9]+$ || "$timeout_seconds" -lt 60 ]]; then
  printf '%s\n' 'ERROR: --timeout-seconds must be an integer of at least 60.' >&2
  exit 2
fi

for command in base64 kubectl openssl sha256sum; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done
for file in namespace.yaml service-account.yaml runtime-config.yaml artifact-pvc.yaml \
  redis.yaml application.yaml service-monitor.yaml grafana-dashboard.yaml network-policies.yaml; do
  [[ -f "$manifest_dir/$file" ]] || {
    printf 'ERROR: deployment manifest is missing: %s\n' "$file" >&2
    exit 2
  }
done

secret_dir=$(mktemp -d)
cleanup() {
  kubectl --context "$context" -n "$namespace" delete pod "$loader_name" \
    --ignore-not-found --wait=false >/dev/null 2>&1 || true
  rm -rf -- "$secret_dir"
}
trap cleanup EXIT
umask 077

kubectl --context "$context" apply -f "$manifest_dir/namespace.yaml" >/dev/null
if kubectl --context "$context" -n "$namespace" get secret jstore-runtime >/dev/null 2>&1; then
  while IFS=: read -r file key; do
    kubectl --context "$context" -n "$namespace" get secret jstore-runtime \
      -o "jsonpath={.data.${key}}" | base64 -d >"$secret_dir/$file"
    if [[ $(wc -c <"$secret_dir/$file") -lt 32 ]]; then
      printf 'ERROR: existing Secret key is missing or too short: %s\n' "$key" >&2
      exit 1
    fi
  done <<'EOF'
database-password:JSTORE_DB_PASSWORD
redis-password:JSTORE_REDIS_PASSWORD
jwt-access-secret:JSTORE_JWT_ACCESS_SECRET
jwt-refresh-secret:JSTORE_JWT_REFRESH_SECRET
phone-hmac-secret:JSTORE_PHONE_VERIFICATION_HMAC_SECRET
EOF
else
  for name in database-password redis-password jwt-access-secret jwt-refresh-secret phone-hmac-secret; do
    random_value=$(openssl rand -hex 32)
    printf '%s' "$random_value" >"$secret_dir/$name"
  done
  unset random_value
fi

kubectl --context "$context" -n postgresql get deployment postgresql >/dev/null
kubectl --context "$context" -n postgresql get secret postgresql-secret >/dev/null

db_password=$(<"$secret_dir/database-password")
{
  printf "\\set app_password '%s'\n" "$db_password"
  cat <<'SQL'
SELECT format('ALTER ROLE jstore_app WITH LOGIN PASSWORD %L', :'app_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jstore_app')\gexec
SELECT format('CREATE ROLE jstore_app WITH LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jstore_app')\gexec
SELECT 'CREATE DATABASE j_store_codex OWNER jstore_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'j_store_codex')\gexec
ALTER DATABASE j_store_codex OWNER TO jstore_app;
SQL
} | kubectl --context "$context" -n postgresql exec -i deployment/postgresql -- \
  sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" exec psql -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1' \
  >/dev/null
unset db_password

kubectl --context "$context" -n "$namespace" create secret generic jstore-runtime \
  --from-file=JSTORE_DB_PASSWORD="$secret_dir/database-password" \
  --from-file=JSTORE_REDIS_PASSWORD="$secret_dir/redis-password" \
  --from-file=JSTORE_JWT_ACCESS_SECRET="$secret_dir/jwt-access-secret" \
  --from-file=JSTORE_JWT_REFRESH_SECRET="$secret_dir/jwt-refresh-secret" \
  --from-file=JSTORE_PHONE_VERIFICATION_HMAC_SECRET="$secret_dir/phone-hmac-secret" \
  --dry-run=client -o yaml | kubectl --context "$context" apply -f - >/dev/null

for file in service-account.yaml runtime-config.yaml artifact-pvc.yaml redis.yaml \
  service-monitor.yaml grafana-dashboard.yaml network-policies.yaml; do
  kubectl --context "$context" -n "$namespace" apply -f "$manifest_dir/$file" >/dev/null
done

kubectl --context "$context" -n "$namespace" rollout status statefulset/redis \
  --timeout="${timeout_seconds}s"

kubectl --context "$context" -n "$namespace" delete pod "$loader_name" \
  --ignore-not-found --wait=true --timeout=60s >/dev/null
cat <<EOF | kubectl --context "$context" apply -f - >/dev/null
apiVersion: v1
kind: Pod
metadata:
  name: ${loader_name}
  namespace: ${namespace}
  labels:
    app.kubernetes.io/name: jstore-artifact-loader
spec:
  restartPolicy: Never
  serviceAccountName: j-store
  automountServiceAccountToken: false
  securityContext:
    runAsUser: 10001
    runAsGroup: 10001
    runAsNonRoot: true
    fsGroup: 10001
    fsGroupChangePolicy: OnRootMismatch
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: loader
      image: docker.m.daocloud.io/library/amazoncorretto:25-al2023-headless
      imagePullPolicy: IfNotPresent
      command: ["sleep", "3600"]
      resources:
        requests: {cpu: 10m, memory: 32Mi}
        limits: {cpu: 100m, memory: 128Mi}
      securityContext:
        allowPrivilegeEscalation: false
        privileged: false
        readOnlyRootFilesystem: true
        capabilities:
          drop: ["ALL"]
      volumeMounts:
        - name: artifact
          mountPath: /opt/jstore
        - name: tmp
          mountPath: /tmp
  volumes:
    - name: artifact
      persistentVolumeClaim:
        claimName: jstore-artifact
    - name: tmp
      emptyDir:
        sizeLimit: 64Mi
EOF

kubectl --context "$context" -n "$namespace" wait --for=condition=Ready \
  "pod/${loader_name}" --timeout="${timeout_seconds}s"
artifact_sha=$(sha256sum "$jar_path" | awk '{print $1}')
kubectl --context "$context" -n "$namespace" exec -i "$loader_name" -- sh -c \
  'set -eu; cat >/opt/jstore/app.jar.tmp; test -s /opt/jstore/app.jar.tmp; mv /opt/jstore/app.jar.tmp /opt/jstore/app.jar' \
  <"$jar_path"
remote_sha=$(kubectl --context "$context" -n "$namespace" exec "$loader_name" -- \
  sha256sum /opt/jstore/app.jar | awk '{print $1}')
if [[ "$artifact_sha" != "$remote_sha" ]]; then
  printf '%s\n' 'ERROR: uploaded JAR checksum does not match the local artifact.' >&2
  exit 1
fi
kubectl --context "$context" -n "$namespace" delete pod "$loader_name" \
  --wait=true --timeout=60s >/dev/null

kubectl --context "$context" -n "$namespace" apply -f "$manifest_dir/application.yaml" >/dev/null
kubectl --context "$context" -n "$namespace" patch deployment j-store --type=merge \
  -p "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"jstore.dev/artifact-sha256\":\"${artifact_sha}\"}}}}}" \
  >/dev/null
kubectl --context "$context" -n "$namespace" rollout status deployment/j-store \
  --timeout="${timeout_seconds}s"

health=$(kubectl --context "$context" get --raw \
  "/api/v1/namespaces/${namespace}/services/http:j-store:8080/proxy/actuator/health/readiness")
if [[ "$health" != *'"status":"UP"'* ]]; then
  printf '%s\n' 'ERROR: application readiness endpoint did not report UP.' >&2
  exit 1
fi
metrics=$(kubectl --context "$context" get --raw \
  "/api/v1/namespaces/${namespace}/services/http:j-store:8080/proxy/actuator/prometheus")
if [[ "$metrics" != *"jvm_memory_used_bytes"* ]]; then
  printf '%s\n' 'ERROR: application Prometheus endpoint is missing JVM metrics.' >&2
  exit 1
fi

printf 'JSTORE_KUBERNETES_DEPLOYMENT_READY namespace=%s artifact_sha256=%s\n' \
  "$namespace" "$artifact_sha"

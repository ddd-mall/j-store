#!/usr/bin/env bash
set -euo pipefail

namespace="jstore-observability"
context=""
generate_development_tls=false
tls_cert=""
tls_key=""
ca_cert=""

usage() {
  cat <<'EOF'
Usage:
  kubernetes-observability-secrets.sh --context <context> [options]

Options:
  --namespace <name>             Target namespace (default: jstore-observability)
  --tls-cert <path>              Gateway certificate PEM
  --tls-key <path>               Gateway private key PEM
  --ca-cert <path>               CA certificate PEM
  --generate-development-tls     Generate a temporary self-signed CA/certificate

Required environment variables:
  GRAFANA_ADMIN_PASSWORD
  LOKI_GATEWAY_PASSWORD

Optional environment variables:
  GRAFANA_ADMIN_USER (default: admin)
  LOKI_GATEWAY_USER  (default: alloy)
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
    --tls-cert)
      tls_cert=${2:?missing certificate path}
      shift 2
      ;;
    --tls-key)
      tls_key=${2:?missing key path}
      shift 2
      ;;
    --ca-cert)
      ca_cert=${2:?missing CA path}
      shift 2
      ;;
    --generate-development-tls)
      generate_development_tls=true
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

if [[ -z "$context" ]]; then
  printf '%s\n' 'ERROR: --context is required to prevent writing to an accidental cluster.' >&2
  exit 2
fi
if [[ "$(kubectl config current-context)" != "$context" ]]; then
  printf 'ERROR: current context is not %s.\n' "$context" >&2
  exit 2
fi
if [[ "$namespace" != "jstore-observability" ]]; then
  printf '%s\n' 'ERROR: the base TLS identity is fixed to namespace jstore-observability.' >&2
  exit 2
fi
command -v openssl >/dev/null || {
  printf '%s\n' 'ERROR: openssl is required to generate the gateway password hash and TLS material.' >&2
  exit 2
}
kubectl --context "$context" get namespace "$namespace" >/dev/null

grafana_user=${GRAFANA_ADMIN_USER:-admin}
loki_user=${LOKI_GATEWAY_USER:-alloy}
: "${GRAFANA_ADMIN_PASSWORD:?GRAFANA_ADMIN_PASSWORD is required}"
: "${LOKI_GATEWAY_PASSWORD:?LOKI_GATEWAY_PASSWORD is required}"

for user in "$grafana_user" "$loki_user"; do
  if [[ ! "$user" =~ ^[A-Za-z0-9._-]{1,64}$ ]]; then
    printf '%s\n' 'ERROR: usernames must contain 1-64 ASCII letters, digits, dots, underscores or hyphens.' >&2
    exit 2
  fi
done

if [[ ${#GRAFANA_ADMIN_PASSWORD} -lt 20 || ${#LOKI_GATEWAY_PASSWORD} -lt 20 ]]; then
  printf '%s\n' 'ERROR: generated/imported passwords must contain at least 20 characters.' >&2
  exit 2
fi
if [[ "$GRAFANA_ADMIN_PASSWORD" == *$'\n'* || "$GRAFANA_ADMIN_PASSWORD" == *$'\r'* \
  || "$LOKI_GATEWAY_PASSWORD" == *$'\n'* || "$LOKI_GATEWAY_PASSWORD" == *$'\r'* ]]; then
  printf '%s\n' 'ERROR: passwords must not contain line breaks.' >&2
  exit 2
fi
if [[ "$GRAFANA_ADMIN_PASSWORD" == change-me* || "$LOKI_GATEWAY_PASSWORD" == change-me* ]]; then
  printf '%s\n' 'ERROR: example passwords are forbidden.' >&2
  exit 2
fi

secret_dir=$(mktemp -d)
cleanup() {
  rm -rf -- "$secret_dir"
}
trap cleanup EXIT
umask 077

if $generate_development_tls; then
  ca_cert="$secret_dir/ca.crt"
  ca_key="$secret_dir/ca.key"
  tls_cert="$secret_dir/tls.crt"
  tls_key="$secret_dir/tls.key"
  cert_config="$secret_dir/server.cnf"
  cat >"$cert_config" <<'EOF'
[req]
distinguished_name = dn
prompt = no
req_extensions = req_ext
[dn]
CN = loki-gateway.jstore-observability.svc
[req_ext]
subjectAltName = @alt_names
[alt_names]
DNS.1 = loki-gateway
DNS.2 = loki-gateway.jstore-observability
DNS.3 = loki-gateway.jstore-observability.svc
DNS.4 = loki-gateway.jstore-observability.svc.cluster.local
EOF
  openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 30 \
    -subj '/CN=j-store-observability-development-ca' \
    -keyout "$ca_key" -out "$ca_cert" >/dev/null 2>&1
  openssl req -new -newkey rsa:3072 -nodes -sha256 \
    -config "$cert_config" -keyout "$tls_key" -out "$secret_dir/server.csr" >/dev/null 2>&1
  openssl x509 -req -sha256 -days 30 \
    -in "$secret_dir/server.csr" -CA "$ca_cert" -CAkey "$ca_key" -CAcreateserial \
    -extensions req_ext -extfile "$cert_config" -out "$tls_cert" >/dev/null 2>&1
else
  for file in "$tls_cert" "$tls_key" "$ca_cert"; do
    if [[ -z "$file" || ! -f "$file" ]]; then
      printf '%s\n' 'ERROR: --tls-cert, --tls-key and --ca-cert are required unless development TLS generation is selected.' >&2
      exit 2
    fi
  done
fi

printf '%s' "$grafana_user" >"$secret_dir/grafana-username"
printf '%s' "$GRAFANA_ADMIN_PASSWORD" >"$secret_dir/grafana-password"
printf '%s' "$loki_user" >"$secret_dir/loki-username"
printf '%s' "$LOKI_GATEWAY_PASSWORD" >"$secret_dir/loki-password"
printf '%s' "$LOKI_GATEWAY_PASSWORD" | openssl passwd -6 -stdin \
  | sed "s|^|${loki_user}:|" >"$secret_dir/.htpasswd"

kubectl --context "$context" -n "$namespace" create secret generic grafana-admin \
  --from-file=username="$secret_dir/grafana-username" \
  --from-file=password="$secret_dir/grafana-password" \
  --dry-run=client -o yaml | kubectl --context "$context" apply -f - >/dev/null

kubectl --context "$context" -n "$namespace" create secret generic loki-gateway-auth \
  --from-file=username="$secret_dir/loki-username" \
  --from-file=password="$secret_dir/loki-password" \
  --from-file=.htpasswd="$secret_dir/.htpasswd" \
  --dry-run=client -o yaml | kubectl --context "$context" apply -f - >/dev/null

kubectl --context "$context" -n "$namespace" create secret generic loki-gateway-tls \
  --from-file=tls.crt="$tls_cert" \
  --from-file=tls.key="$tls_key" \
  --from-file=ca.crt="$ca_cert" \
  --dry-run=client -o yaml | kubectl --context "$context" apply -f - >/dev/null

printf 'Created observability secrets in context=%s namespace=%s without writing credentials to the repository.\n' \
  "$context" "$namespace"

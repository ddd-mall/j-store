#!/usr/bin/env bash
set -euo pipefail

if [[ "$PWD" != "/workspace/source" ]]; then
  printf '%s\n' 'ERROR: gate runner must start in /workspace/source.' >&2
  exit 2
fi
if [[ -e /var/run/secrets/kubernetes.io/serviceaccount/token ]]; then
  printf '%s\n' 'ERROR: Kubernetes service account token is mounted.' >&2
  exit 2
fi

runtime_root=$(mktemp -d /tmp/jstore-gate.XXXXXX)
cleanup() {
  rm -rf -- "$runtime_root"
}
trap cleanup EXIT
mkdir -p "$runtime_root/home" "$runtime_root/gradle"
cp -a /opt/jstore-gate/gradle-home/. "$runtime_root/gradle/"
chmod -R u+w "$runtime_root/gradle"
/opt/jstore-gate/write-spotless-targets \
  "$runtime_root/spotless-targets"
export HOME="$runtime_root/home"
export GRADLE_USER_HOME="$runtime_root/gradle"
export JSTORE_UV_CACHE_DIR="$runtime_root/uv"
export JSTORE_REPOSITORY_ROOT=/workspace/source
export JSTORE_QUALITY_TOOL_ROOT=/opt/jstore-gate/trusted
export ORG_GRADLE_PROJECT_spotlessFilesFile="$runtime_root/spotless-targets"

/opt/jstore-gate/trusted/quality-gate.sh

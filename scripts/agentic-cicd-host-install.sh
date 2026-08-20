#!/usr/bin/env bash
set -euo pipefail

bundle=""
bundle_sha256=""
service_user=jstore-agentic-cicd
service_group=jstore-agentic-cicd
install_root=/opt/jstore-agentic-cicd

usage() {
  cat <<'EOF'
Usage: agentic-cicd-host-install.sh --bundle <tar.gz> --bundle-sha256 <sha256>

Installs a reviewed host-native Symphony/Codex bundle as a static systemd
service. The command creates no credentials and never enables or starts it.
EOF
}

while (($#)); do
  case "$1" in
    --bundle)
      bundle=${2:?missing bundle}
      shift 2
      ;;
    --bundle-sha256)
      bundle_sha256=${2:?missing bundle sha256}
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

[[ $EUID -eq 0 ]] || {
  printf '%s\n' 'ERROR: host installation requires root.' >&2
  exit 2
}
[[ -f "$bundle" && "$bundle_sha256" =~ ^[0-9a-f]{64}$ ]] || {
  printf '%s\n' 'ERROR: an existing bundle and lowercase SHA-256 are required.' >&2
  exit 2
}
for command in cmp erl escript getent groupadd useradd install sha256sum tar systemctl; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done

printf '%s  %s\n' "$bundle_sha256" "$bundle" | sha256sum --check --strict
temporary_root=$(mktemp -d /tmp/jstore-agentic-cicd-install.XXXXXX)
release_staging=""
cleanup() {
  rm -rf -- "$temporary_root"
  if [[ -n "$release_staging" && -e "$release_staging" ]]; then
    rm -rf -- "$release_staging"
  fi
}
trap cleanup EXIT
tar --extract --gzip --file "$bundle" --directory "$temporary_root"
bundle_root="$temporary_root/jstore-agentic-cicd"
[[ -f "$bundle_root/manifest.sha256" ]] || {
  printf '%s\n' 'ERROR: bundle manifest is missing.' >&2
  exit 1
}
(
  cd "$bundle_root"
  sha256sum --check --strict manifest.sha256
)
[[ -L "$bundle_root/payload/bin/codex" \
  && "$(readlink "$bundle_root/payload/bin/codex")" == ../lib/node_modules/@openai/codex/bin/codex.js ]] || {
  printf '%s\n' 'ERROR: bundle Codex entrypoint is not the reviewed relative link.' >&2
  exit 1
}
systemctl is-active --quiet jstore-agentic-cicd.service && {
  printf '%s\n' 'ERROR: stop the host-native service before installing a release.' >&2
  exit 1
}

release_id=${bundle_sha256:0:32}
release="$install_root/releases/$release_id"
current_link="$install_root/current"
if [[ -e "$current_link" && ! -L "$current_link" ]]; then
  printf 'ERROR: current runtime path is not a managed link: %s\n' "$current_link" >&2
  exit 1
fi
for managed_path in /opt/jstore-agentic-controller /etc/agentic-cicd; do
  if [[ -e "$managed_path" && ! -L "$managed_path" ]]; then
    printf 'ERROR: legacy runtime path is not a managed link: %s\n' "$managed_path" >&2
    exit 1
  fi
done
[[ ! -e "$release" ]] || {
  printf 'ERROR: immutable release already exists: %s\n' "$release" >&2
  exit 1
}
if [[ -e /etc/jstore-agentic-cicd/runtime.env ]] \
  && ! cmp --silent "$bundle_root/deploy/runtime.env" /etc/jstore-agentic-cicd/runtime.env; then
  printf '%s\n' 'ERROR: installed runtime binding differs from reviewed bundle.' >&2
  exit 1
fi

if getent group "$service_group" >/dev/null; then
  [[ $(getent group "$service_group" | cut -d: -f3) == 11001 ]] || {
    printf '%s\n' 'ERROR: service group has an unexpected GID.' >&2
    exit 1
  }
else
  groupadd --system --gid 11001 "$service_group"
fi
if getent passwd "$service_user" >/dev/null; then
  [[ $(getent passwd "$service_user" | cut -d: -f3) == 10001 ]] || {
    printf '%s\n' 'ERROR: service user has an unexpected UID.' >&2
    exit 1
  }
else
  useradd --system --uid 10001 --gid "$service_group" \
    --home-dir /var/lib/jstore-agentic-cicd/home \
    --shell /usr/sbin/nologin "$service_user"
fi

install -d -o root -g root -m 0755 "$install_root/releases" /etc/jstore-agentic-cicd
install -d -o root -g root -m 0700 /etc/jstore-agentic-cicd/credentials
install -d -o "$service_user" -g "$service_group" -m 0750 \
  /var/lib/jstore-agentic-cicd \
  /var/lib/jstore-agentic-cicd/home \
  /var/lib/jstore-agentic-cicd/workspaces \
  /var/lib/jstore-agentic-cicd/controller \
  /var/lib/jstore-agentic-cicd/gate-exchange
install -d -o "$service_user" -g "$service_group" -m 2770 \
  /var/lib/jstore-agentic-candidates \
  /var/lib/jstore-agentic-gate-requests
install -d -o 10002 -g "$service_group" -m 2770 \
  /var/lib/jstore-agentic-gate-receipts \
  /var/lib/jstore-agentic-artifact-leases

for mapping in \
  "requests:/var/lib/jstore-agentic-gate-requests" \
  "receipts:/var/lib/jstore-agentic-gate-receipts"; do
  name=${mapping%%:*}
  target=${mapping#*:}
  link=/var/lib/jstore-agentic-cicd/gate-exchange/$name
  if [[ -L "$link" ]]; then
    [[ $(readlink -f "$link") == "$target" ]] || {
      printf 'ERROR: gate exchange link has unexpected target: %s\n' "$link" >&2
      exit 1
    }
  elif [[ -e "$link" ]]; then
    printf 'ERROR: gate exchange path is not a managed link: %s\n' "$link" >&2
    exit 1
  else
    ln -s "$target" "$link"
  fi
done

release_staging="$install_root/releases/.${release_id}.staging"
[[ ! -e "$release_staging" ]] || {
  printf 'ERROR: stale release staging path exists: %s\n' "$release_staging" >&2
  exit 1
}
install -d -o root -g root -m 0755 "$release_staging"
cp -a "$bundle_root/payload/." "$release_staging/"
chown -R root:root "$release_staging"
chmod -R a-w "$release_staging"
mv "$release_staging" "$release"
release_staging=""
install -o root -g root -m 0644 \
  "$bundle_root/deploy/jstore-agentic-cicd.service" \
  /etc/systemd/system/jstore-agentic-cicd.service
if [[ ! -e /etc/jstore-agentic-cicd/runtime.env ]]; then
  install -o root -g root -m 0640 \
    "$bundle_root/deploy/runtime.env" \
    /etc/jstore-agentic-cicd/runtime.env
fi
temporary_link="$install_root/.current-$release_id"
ln -s "$release" "$temporary_link"
mv -Tf "$temporary_link" "$current_link"
controller_link=/opt/jstore-agentic-controller
ln -sfn "$install_root/current/controller" "$controller_link"
ln -sfn "$install_root/current/config" /etc/agentic-cicd
systemctl daemon-reload

printf 'HOST_RUNTIME_INSTALLED release=%s service=inactive credentials=unmodified\n' \
  "$release_id"

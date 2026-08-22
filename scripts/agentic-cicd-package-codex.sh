#!/usr/bin/env bash
set -euo pipefail

codex_command=""
node_command=""
payload=""
expected_version=""

usage() {
  cat <<'EOF'
Usage: agentic-cicd-package-codex.sh --codex-command <path> --node-command <path> --payload <path> --expected-version <version>

Copies the installed stable Codex CLI and its current platform package into an
isolated host payload, then runs version and sandbox smoke checks from it.
EOF
}

while (($#)); do
  case "$1" in
    --codex-command)
      codex_command=${2:?missing Codex command}
      shift 2
      ;;
    --node-command)
      node_command=${2:?missing Node command}
      shift 2
      ;;
    --payload)
      payload=${2:?missing payload path}
      shift 2
      ;;
    --expected-version)
      expected_version=${2:?missing expected Codex version}
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

[[ -n "$codex_command" && -n "$node_command" && -n "$payload" \
  && "$expected_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  printf '%s\n' 'ERROR: Codex command, Node command, payload, and stable expected version are required.' >&2
  exit 2
}
[[ -x "$codex_command" && -x "$node_command" ]] || {
  printf '%s\n' 'ERROR: Codex and Node commands must be executable.' >&2
  exit 2
}

codex_entry=$(readlink -f "$codex_command")
codex_module=${codex_entry%/bin/codex.js}
codex_package_json="$codex_module/package.json"
[[ "$codex_entry" != "$codex_module" && -f "$codex_package_json" ]] || {
  printf '%s\n' 'ERROR: Codex npm module layout is unsupported.' >&2
  exit 2
}

platform_package=$(
  "$node_command" -e '
    const fs = require("node:fs");
    const packageJson = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
    const aliases = {
      "linux:x64": "@openai/codex-linux-x64",
      "linux:arm64": "@openai/codex-linux-arm64",
      "darwin:x64": "@openai/codex-darwin-x64",
      "darwin:arm64": "@openai/codex-darwin-arm64",
      "win32:x64": "@openai/codex-win32-x64",
      "win32:arm64": "@openai/codex-win32-arm64",
    };
    const alias = aliases[`${process.platform}:${process.arch}`];
    if (!alias || !packageJson.optionalDependencies?.[alias]) process.exit(2);
    process.stdout.write(alias);
  ' "$codex_package_json"
) || {
  printf '%s\n' 'ERROR: installed Codex does not declare a package for this platform.' >&2
  exit 2
}

platform_package_json=$(
  JSTORE_CODEX_ENTRY="$codex_entry" JSTORE_CODEX_PLATFORM_PACKAGE="$platform_package" \
    "$node_command" -e '
      const { createRequire } = require("node:module");
      const requireFromCodex = createRequire(process.env.JSTORE_CODEX_ENTRY);
      process.stdout.write(requireFromCodex.resolve(`${process.env.JSTORE_CODEX_PLATFORM_PACKAGE}/package.json`));
    '
) || {
  printf 'ERROR: missing installed Codex platform package: %s\n' "$platform_package" >&2
  exit 2
}
platform_module=$(dirname "$(readlink -f "$platform_package_json")")
platform_directory_name=${platform_package#@openai/}

mkdir -p "$payload/bin" "$payload/lib/node_modules/@openai"
for destination in \
  "$payload/bin/node" \
  "$payload/bin/codex" \
  "$payload/lib/node_modules/@openai/codex" \
  "$payload/lib/node_modules/@openai/$platform_directory_name"; do
  [[ ! -e "$destination" && ! -L "$destination" ]] || {
    printf 'ERROR: Codex payload destination already exists: %s\n' "$destination" >&2
    exit 2
  }
done

install -m 0555 "$node_command" "$payload/bin/node"
cp -a "$codex_module" "$payload/lib/node_modules/@openai/codex"
cp -a "$platform_module" "$payload/lib/node_modules/@openai/$platform_directory_name"
ln -s ../lib/node_modules/@openai/codex/bin/codex.js "$payload/bin/codex"

smoke_home=$(mktemp -d "${TMPDIR:-/tmp}/jstore-codex-smoke.XXXXXX")
cleanup() {
  rm -rf -- "$smoke_home"
}
trap cleanup EXIT
packaged_path="$payload/bin:/usr/bin:/bin"
packaged_output=$(HOME="$smoke_home" PATH="$packaged_path" "$payload/bin/codex" --version)
[[ "$packaged_output" == "codex-cli $expected_version" ]] || {
  printf 'ERROR: packaged Codex version mismatch: %s\n' "$packaged_output" >&2
  exit 2
}
HOME="$smoke_home" PATH="$packaged_path" "$payload/bin/codex" sandbox -- /bin/true

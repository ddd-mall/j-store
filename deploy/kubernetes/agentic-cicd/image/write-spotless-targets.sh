#!/usr/bin/env bash
set -euo pipefail

output=${1:?output path is required}
repository_manifest=${JSTORE_REPOSITORY_FILES_FILE:?trusted repository manifest is required}
known_example=./docs/Spring-Modulith示例代码.kt
known_example_sha256=1671e3b0458ba2cd442ccc360d1f7914e38a471862c9be2a142eb9b963e05f41

while IFS= read -r candidate; do
      [[ -n "$candidate" ]] || continue
      [[ -f "$candidate" && ! -L "$candidate" ]] || continue
      if [[ "$candidate" == "$known_example" ]]; then
        actual_sha256=$(sha256sum "$candidate" | awk '{print $1}')
        if [[ "$actual_sha256" == "$known_example_sha256" ]]; then
          continue
        fi
      fi
      printf '%s\n' "$candidate"
    done < "$repository_manifest" \
  | LC_ALL=C sort > "$output"

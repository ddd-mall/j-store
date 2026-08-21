#!/usr/bin/env bash
set -euo pipefail

action=${1:-}
case "$action" in
  stop)
    systemctl stop jstore-agentic-cicd.service
    printf '%s\n' 'HOST_RUNTIME_STOPPED state=retained credentials=retained'
    ;;
  status)
    systemctl status jstore-agentic-cicd.service --no-pager
    ;;
  *)
    printf 'Usage: %s stop|status\n' "${0##*/}" >&2
    exit 2
    ;;
esac

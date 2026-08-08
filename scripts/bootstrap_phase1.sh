#!/usr/bin/env bash
# Compatibility wrapper. MQVOX v4 uses bootstrap_v4.sh.
exec bash "$(cd "$(dirname "$0")" && pwd)/bootstrap_v4.sh" "$@"

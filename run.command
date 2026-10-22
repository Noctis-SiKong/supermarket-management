#!/bin/sh
# macOS double-click launcher: opens Terminal and starts the CUI.
cd "$(dirname "$0")"
exec ./run.sh "$@"

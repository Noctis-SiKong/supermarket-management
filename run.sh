#!/bin/sh
# Cross-platform launcher for macOS and Linux.
# Finds a full JDK 17+, seeds the demo store when empty, builds the jar when
# missing, then starts the CUI. Works with `./run.sh` and `sh run.sh`.

set -eu
cd "$(dirname "$0")"

java_exe=""
java_home_found=""

probe_home() {
    [ -n "${1:-}" ] || return 0
    [ -x "$1/bin/java" ] || return 0
    [ -x "$1/bin/javac" ] || return 0
    version=$("$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)
    [ -n "$version" ] || return 0
    major=${version%%.*}
    if [ "$major" = "1" ]; then
        rest=${version#1.}
        major=${rest%%.*}
    fi
    case "$major" in
        ''|*[!0-9]*) return 0 ;;
    esac
    [ "$major" -ge 17 ] || return 0
    java_exe="$1/bin/java"
    java_home_found="$1"
}

probe_home "${JAVA_HOME:-}"

if [ -z "$java_exe" ] && command -v java >/dev/null 2>&1; then
    java_bin=$(command -v java)
    java_root=$(cd "$(dirname "$java_bin")/.." 2>/dev/null && pwd || true)
    [ -n "$java_root" ] && probe_home "$java_root"
fi

if [ -z "$java_exe" ] && [ -x /usr/libexec/java_home ]; then
    mac_home=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
    [ -n "$mac_home" ] && probe_home "$mac_home"
fi

if [ -z "$java_exe" ]; then
    for candidate in \
        "$HOME"/.jdks/* \
        "$HOME"/.sdkman/candidates/java/* \
        "$HOME"/.local/share/mise/installs/java/* \
        /usr/lib/jvm/* \
        /opt/homebrew/opt/openjdk* \
        /usr/local/opt/openjdk* \
        /Library/Java/JavaVirtualMachines/*/Contents/Home; do
        [ -d "$candidate" ] || continue
        probe_home "$candidate"
        [ -n "$java_exe" ] && break
    done
fi

if [ -z "$java_exe" ]; then
    echo "[ERROR] No JDK 17 or newer found." >&2
    echo "        Set JAVA_HOME to a full JDK, or install one (for example: brew install openjdk@21)." >&2
    exit 1
fi

echo "[info] Using Java: $java_home_found"

if [ ! -f "data/snapshot.json" ] && [ -f "demo/preset_snapshot_20261022.json" ]; then
    mkdir -p data
    cp "demo/preset_snapshot_20261022.json" "data/snapshot.json"
    echo "[info] Seeded the demo accounts into data/snapshot.json"
fi

jar="target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$jar" ]; then
    echo "[info] Building the executable jar..."
    sh ./mvnw -B --no-transfer-progress clean package -DskipTests
fi

exec "$java_exe" -Dfile.encoding=UTF-8 -jar "$jar" "$@"

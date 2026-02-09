#!/bin/bash
# MCP STDIO server launcher for Noetic.
# Supports native binary (PATH or build output) and JAR fallback.
# Redirects all logging to file; only JSON-RPC goes to stdout.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG="${HOME}/.websearch/mcp-server.log"

# Ensure log directory exists
mkdir -p "$(dirname "$LOG")"

# Remove stale Lucene lock if present
rm -f "${HOME}/.websearch/index/write.lock"

# Resolve the noetic binary: PATH > build output > JAR fallback
resolve_binary() {
  # 1. Native binary on PATH (e.g. ~/.local/bin/noetic)
  local path_bin
  path_bin="$(command -v noetic 2>/dev/null || true)"
  if [[ -n "$path_bin" && -x "$path_bin" ]]; then
    echo "$path_bin"
    return
  fi

  # 2. Build output
  local build_bin="$PROJECT_DIR/build/native/nativeCompile/noetic"
  if [[ -x "$build_bin" ]]; then
    echo "$build_bin"
    return
  fi

  # Not found
  return 1
}

resolve_jar() {
  local jar="$PROJECT_DIR/build/libs/noetic-0.1.0-SNAPSHOT.jar"
  if [[ -f "$jar" ]]; then
    echo "$jar"
    return
  fi
  return 1
}

# Start the MCP server:
# - STDIO profile disables web server
# - All Spring/JVM output (stderr+stdout logging) goes to log file
# - Only the MCP STDIO transport's JSON-RPC output goes to actual stdout
if BINARY="$(resolve_binary)"; then
  exec "$BINARY" \
    --spring.profiles.active=stdio \
    --spring.main.banner-mode=off \
    2>>"$LOG"
elif JAR="$(resolve_jar)"; then
  JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || true)}"
  if [[ -z "$JAVA_HOME" || ! -d "$JAVA_HOME" ]]; then
    echo "Error: JAVA_HOME not set and could not be auto-detected." >&2
    echo "  Set JAVA_HOME or install Java 25+." >&2
    exit 1
  fi
  exec "$JAVA_HOME/bin/java" \
    --enable-preview \
    -Dspring.profiles.active=stdio \
    -Dspring.main.banner-mode=off \
    -jar "$JAR" \
    2>>"$LOG"
else
  echo "Error: noetic not found on PATH, in build output, or as a JAR." >&2
  echo "  Install: cp build/native/nativeCompile/noetic ~/.local/bin/" >&2
  echo "  Or build: ./gradlew nativeCompile  (or)  ./gradlew bootJar" >&2
  exit 1
fi

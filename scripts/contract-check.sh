#!/usr/bin/env bash
#
# Drives the REAL compiled hashbin CLI against a running hashbin-quarkus backend,
# proving the Java CLI is a correct wire client (and that client-side signature
# verification works against the real HMAC secret). Complements the backend's
# scripts/cli-compat-check.sh (which does the same for the original Python CLI).
#
# Prerequisites:
#   - Backend running:   (cd ../hashbin-quarkus && mvn quarkus:dev)
#   - JDK 21 on PATH or via JAVA_HOME (Quarkus rejects JDK 25 class files)
#
# Env overrides:
#   HASHBIN_API_BASE     default http://localhost:8080/api
#   HASHBIN_HMAC_SECRET  must match the running server's secret so the signature
#                        re-check passes (dev default below)
#   HASHBIN_CLI_JAR      path to the built CLI runner jar
#                        default target/quarkus-app/quarkus-run.jar
set -euo pipefail

API_BASE="${HASHBIN_API_BASE:-http://localhost:8080/api}"
SECRET="${HASHBIN_HMAC_SECRET:-dev-only-insecure-secret-change-me}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${HASHBIN_CLI_JAR:-$HERE/target/quarkus-app/quarkus-run.jar}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

pass() { echo "  PASS: $1"; }
fail() { echo "  FAIL: $1"; exit 1; }

[ -f "$JAR" ] || { echo "CLI jar not found at $JAR — run './mvnw -DskipTests package' first"; exit 1; }
command -v "$JAVA_BIN" >/dev/null 2>&1 || { echo "java not found (set JAVA_HOME to a JDK 21)"; exit 1; }

hashbin() { "$JAVA_BIN" -jar "$JAR" --api-base "$API_BASE" --hmac-secret "$SECRET" "$@"; }

echo "== target: $API_BASE"
echo "== cli:    $JAR"

# 1. health
hashbin health | grep -q "healthy" && pass "health" || fail "health"

# 2. stamp a temp file, capture created_at + verify the server signature
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
printf 'hashbin cli contract check %s\n' "$(date -u +%FT%TZ)" > "$TMP/file.txt"
OUT="$(hashbin stamp --include-name "$TMP/file.txt")"
echo "$OUT" | grep -q "Timestamped at" && pass "stamp" || fail "stamp: $OUT"
echo "$OUT" | grep -q "verified" && pass "stamp signature verified client-side" \
  || fail "stamp signature NOT verified: $OUT"

# 3. verify the same file resolves to >= 1 timestamp
OUT="$(hashbin verify "$TMP/file.txt")"
echo "$OUT" | grep -q "Found " && pass "verify by file" || fail "verify: $OUT"
echo "$OUT" | grep -q "verified" && pass "verify signature verified client-side" \
  || fail "verify signature NOT verified: $OUT"

# 4. explore the public feed
hashbin explore --limit 3 | grep -Eq "Found |No public" && pass "explore" || fail "explore"

echo "ALL CHECKS PASSED"

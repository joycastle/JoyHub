#!/usr/bin/env bash

# This script is sourced by the local backend startup command. Keep credentials in macOS Keychain,
# rather than in the repository or a local dotenv file.

if [[ -z "${OAUTH2_FEISHU_CLIENT_ID:-}" ]]; then
  export OAUTH2_FEISHU_CLIENT_ID="cli_aaac3646bcba5bc0"
fi

if [[ -z "${OAUTH2_FEISHU_CLIENT_SECRET:-}" ]]; then
  if ! OAUTH2_FEISHU_CLIENT_SECRET="$(security find-generic-password -a "$USER" -s "joyhub-feishu-secret" -w 2>/dev/null)"; then
    echo "Missing Feishu App Secret in macOS Keychain (service: joyhub-feishu-secret)." >&2
    echo "Run once: security add-generic-password -a \"$USER\" -s \"joyhub-feishu-secret\" -w" >&2
    return 1
  fi
  export OAUTH2_FEISHU_CLIENT_SECRET
fi

export OAUTH2_FEISHU_SCOPE="${OAUTH2_FEISHU_SCOPE:-contact:user.base:readonly}"
export SKILLHUB_WEB_BASE_URL="${SKILLHUB_WEB_BASE_URL:-http://localhost:3000}"

if [[ -z "${JOYHUB_AI_API_KEY:-}" ]] \
  && JOYHUB_AI_API_KEY="$(security find-generic-password -a "$USER" -s "joyhub-ai-api-key" -w 2>/dev/null)"; then
  export JOYHUB_AI_API_KEY
  export JOYHUB_AI_ENABLED="${JOYHUB_AI_ENABLED:-true}"
fi

if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
fi

if [[ -z "${JAVA_BIN:-}" && -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
  export JAVA_BIN="/opt/homebrew/opt/openjdk@21/bin/java"
fi

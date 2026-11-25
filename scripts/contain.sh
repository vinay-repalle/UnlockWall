#!/usr/bin/env bash
# Run a command so that anything it writes to the home folder lands in
# .tools/home inside this project, never in the real home folder (office laptop rule).
# Uses bubblewrap (bwrap), which needs no sudo.
#
# Usage: scripts/contain.sh <command> [args...]
#   e.g. scripts/contain.sh ./gradlew assembleDebug
#        scripts/contain.sh adb devices
set -euo pipefail

PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$PROJECT/.tools"
SDK="$TOOLS/android-sdk"
mkdir -p "$TOOLS/home" "$TOOLS/android-user" "$TOOLS/gradle"

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_USER_HOME="$TOOLS/android-user"
export GRADLE_USER_HOME="$TOOLS/gradle"
export PATH="$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$PATH"

# Real home is replaced by .tools/home; the project folder is mounted back at its real path.
exec bwrap --dev-bind / / \
  --bind "$TOOLS/home" "$HOME" \
  --bind "$PROJECT" "$PROJECT" \
  --chdir "$PWD" \
  -- "$@"

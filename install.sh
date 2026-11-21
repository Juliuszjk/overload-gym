#!/bin/bash
# Build the debug APK and install it on the phone over adb, then launch it.
# usage: ./install.sh [device-serial]   (default: the first non-emulator device)
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$(mise where java)}"
DEV="${1:-$(adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator|:5555$/ {print $1; exit}')}"
./gradlew :app:assembleDebug -q
adb -s "$DEV" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEV" shell am start -n com.apollo.overload.debug/com.apollo.overload.MainActivity

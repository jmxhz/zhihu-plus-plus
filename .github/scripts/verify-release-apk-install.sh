#!/usr/bin/env bash
set -euo pipefail

package=com.github.zly2006.zhplus.lite
activity=com.github.zly2006.zhihu.MainActivity

abis=$(adb shell getprop ro.product.cpu.abilist | tr -d '\r')
echo "Emulator ABIs: $abis"
grep -Fxq arm64-v8a < <(tr ',' '\n' <<< "$abis")

install=$(adb install "$RELEASE_APK" | tee /dev/stderr)
grep -Fxq Success <<< "$install"
adb shell pm path "$package" | grep -E "^package:.*\.apk"
installed=$(adb shell dumpsys package "$package" | tr -d '\r')
sed -n 's/^[[:space:]]*versionName=//p' <<< "$installed" | grep -Fx "$RELEASE_TAG"
grep -E "^[[:space:]]*versionCode=$EXPECTED_VERSION_CODE([[:space:]]|$)" <<< "$installed"

adb shell am force-stop "$package"
launch=$(adb shell am start -W -n "$package/$activity" | tr -d '\r' | tee /dev/stderr)
grep -Eq '^Status: ok[[:space:]]*$' <<< "$launch"

sleep 5
pid=$(adb shell pidof "$package" | tr -d '\r')
[[ "$pid" =~ ^[0-9]+$ ]]
echo "APK installed and activity started (PID $pid)"

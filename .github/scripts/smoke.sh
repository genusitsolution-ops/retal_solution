#!/bin/bash
# Emulator smoke test: install the debug APK, open it, try a sign-in with a made-up account
# (never a real one), switch language, and record screenshots + visible texts.
set -x
PKG=com.genusit.prms.debug
mkdir -p shots
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n $PKG/com.genusit.prms.MainActivity
sleep 15
adb exec-out screencap -p > shots/01-login.png
python3 .github/scripts/ui.py dump shots/01-login.txt
# username, password (made-up account), then Enter = "Done" → submit
python3 .github/scripts/ui.py tap-edit 0 && adb shell input text "ci_smoke_no_such_user"
python3 .github/scripts/ui.py tap-edit 1 && adb shell input text "not-a-password"
adb shell input keyevent 66
sleep 20
adb exec-out screencap -p > shots/02-after-sign-in.png
python3 .github/scripts/ui.py dump shots/02-after-sign-in.txt
# Hindi, then Marathi
python3 .github/scripts/ui.py tap-text "हिं" && sleep 6 && adb exec-out screencap -p > shots/03-hindi.png && python3 .github/scripts/ui.py dump shots/03-hindi.txt
python3 .github/scripts/ui.py tap-text "मरा" && sleep 6 && adb exec-out screencap -p > shots/04-marathi.png && python3 .github/scripts/ui.py dump shots/04-marathi.txt
adb shell pidof $PKG > shots/pid.txt || true
adb logcat -d -s AndroidRuntime:E PrmsApi:W okhttp.OkHttpClient:I > shots/logcat.txt || true
exit 0

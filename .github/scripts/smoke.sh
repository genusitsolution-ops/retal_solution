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
# username, password (made-up account); BACK hides the keyboard between steps
python3 .github/scripts/ui.py tap-edit 0 && adb shell input text "ci_smoke_no_such_user"
adb shell input keyevent 4; sleep 1
python3 .github/scripts/ui.py tap-edit 1 && adb shell input text "not-a-password"
adb shell input keyevent 4; sleep 1
python3 .github/scripts/ui.py dump shots/02a-filled.txt
python3 .github/scripts/ui.py tap-text "Sign in"
sleep 20
adb exec-out screencap -p > shots/02-after-sign-in.png
python3 .github/scripts/ui.py dump shots/02-after-sign-in.txt
# back to the top (keyboard hidden first), then Hindi and Marathi
hidekb() { adb shell dumpsys input_method | grep -q "mInputShown=true" && adb shell input keyevent 4; sleep 1; }
# swipe coordinates from the real screen size (the CI emulator is small)
read W H < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')
X=$((W / 2)); Y1=$((H * 25 / 100)); Y2=$((H * 85 / 100))
top() { hidekb; for i in 1 2 3; do adb shell input swipe $X $Y1 $X $Y2 250; done; sleep 1; }
bottom() { for i in 1 2 3; do adb shell input swipe $X $Y2 $X $Y1 250; done; sleep 1; }
top
python3 .github/scripts/ui.py tap-text "हिं" ; sleep 6; adb exec-out screencap -p > shots/03-hindi.png; python3 .github/scripts/ui.py dump shots/03-hindi.txt
top
python3 .github/scripts/ui.py tap-text "मरा" ; sleep 6; adb exec-out screencap -p > shots/04-marathi.png; python3 .github/scripts/ui.py dump shots/04-marathi.txt
# owner sign-up form (back to English first). Only the app's own checks run: nothing is submitted to the server.
top
python3 .github/scripts/ui.py tap-text "EN" ; sleep 5
bottom
python3 .github/scripts/ui.py tap-text "New property owner? Create an account" ; sleep 4
adb exec-out screencap -p > shots/05-signup.png; python3 .github/scripts/ui.py dump shots/05-signup.txt
python3 .github/scripts/ui.py tap-edit 0 && adb shell input text "A"
hidekb
bottom
python3 .github/scripts/ui.py tap-text "Create account" ; sleep 2
hidekb
top
adb exec-out screencap -p > shots/06-signup-checks.png; python3 .github/scripts/ui.py dump shots/06-signup-checks.txt
adb shell input keyevent 4; sleep 2
adb exec-out screencap -p > shots/07-back-to-login.png
adb shell pidof $PKG > shots/pid.txt || true
adb logcat -d -s AndroidRuntime:E PrmsApi:W okhttp.OkHttpClient:I > shots/logcat.txt || true
exit 0

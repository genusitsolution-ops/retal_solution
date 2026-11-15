#!/usr/bin/env bash
# Builds two tiny stand-in apps with the package names of WhatsApp (com.whatsapp) and WhatsApp Business (com.whatsapp.w4b)
# for the emulator tests of the 1.15 WhatsApp choice. They contain no code: one screen that accepts wa.me links.
# Usage: wa-stubs.sh build <outdir>   |   wa-stubs.sh push <outdir>   (push needs a running emulator)
set -euo pipefail
cmd=$1; out=$2
if [ "$cmd" = build ]; then
  SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
  BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
  JAR=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar
  mkdir -p "$out"; work=$(mktemp -d)
  keytool -genkeypair -keystore "$work/k.jks" -storepass stubstub -keypass stubstub -alias stub -keyalg RSA -keysize 2048 \
    -validity 365 -dname "CN=WhatsApp test stand-in" >/dev/null 2>&1
  for spec in "com.whatsapp:wa" "com.whatsapp.w4b:wa-business"; do
    pkg=${spec%%:*}; name=${spec##*:}
    cat > "$work/AndroidManifest.xml" <<XML
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg" android:versionCode="1" android:versionName="test-stand-in">
  <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="29" />
  <application android:hasCode="false" android:label="$name stand-in">
    <activity android:name="android.app.Activity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="wa.me" />
        <data android:scheme="https" android:host="api.whatsapp.com" />
      </intent-filter>
    </activity>
  </application>
</manifest>
XML
    "$BT/aapt2" link --manifest "$work/AndroidManifest.xml" -I "$JAR" -o "$work/$name-unsigned.apk"
    "$BT/zipalign" -f 4 "$work/$name-unsigned.apk" "$work/$name-aligned.apk"
    "$BT/apksigner" sign --ks "$work/k.jks" --ks-pass pass:stubstub --key-pass pass:stubstub --out "$out/$name-stub.apk" "$work/$name-aligned.apk"
    echo "built $out/$name-stub.apk ($pkg)"
  done
else
  adb push "$out/wa-stub.apk" /data/local/tmp/wa-stub.apk
  adb push "$out/wa-business-stub.apk" /data/local/tmp/wa-business-stub.apk
  # make sure the real apps are not already on the emulator image
  adb uninstall com.whatsapp >/dev/null 2>&1 || true; adb uninstall com.whatsapp.w4b >/dev/null 2>&1 || true
fi

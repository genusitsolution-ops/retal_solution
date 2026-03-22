# PRMS APK Builder v6 — Low memory build
FROM ghcr.io/cirruslabs/flutter:3.22.2

WORKDIR /app
COPY . .

# Write local.properties
RUN FLUTTER_PATH=$(dirname $(dirname $(which flutter))) && \
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android/sdk}}" && \
    printf "flutter.sdk=%s\nsdk.dir=%s\n" "$FLUTTER_PATH" "$SDK" \
      > android/local.properties && \
    cat android/local.properties

# Accept licenses
RUN yes | flutter doctor --android-licenses 2>/dev/null || true

# Get packages
RUN flutter pub get

# Build with low memory Gradle flags
RUN flutter build apk --debug \
    --dart-define=FLUTTER_BUILD_MODE=debug \
    -- \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
    -Dorg.gradle.daemon=false \
    -Dorg.gradle.parallel=false \
    -Dorg.gradle.configureondemand=false \
    -Dkotlin.incremental=false

# Copy APK
RUN mkdir -p /output && \
    find /app/build -name "*.apk" -exec cp {} /output/ \; && \
    echo "=== APK FILES ===" && ls -lh /output/

CMD ["sh", "-c", "ls -lh /output/ && echo '=== BUILD COMPLETE ==='"]

# Rental Solution — Android app (Kotlin, Jetpack Compose)

Native app for **property owners, collection agents (employees) and tenants**, talking to the PRMS REST API (`api/v1`). English, हिंदी and मराठी. Super Admin keeps using the web panel.

See `../../docs/07_ANDROID_BUILD_GUIDE.md` for build, signing, APK/AAB and testing instructions.

| Module | What it is |
|---|---|
| `core/` | Pure Kotlin/JVM: API client (`PrmsApi`, `HttpURLConnection`, no third-party HTTP library), models with lenient JSON parsing, money/date/validation helpers, unit tests |
| `app/` | Android UI (Compose + Material 3), navigation per role, session storage (DataStore), language switching (AppCompat per-app locales) |
| `tools/live-check/` | Runs the `core` client against a real server with test accounts |
| `tools/android_strings.py` | Single source of the en/hi/mr strings → generates `res/values*/strings.xml` |

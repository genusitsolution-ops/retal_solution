# Rental Solution — Android app (Kotlin, Jetpack Compose)

Native app for **property owners, collection agents (employees) and tenants**, talking to the PRMS REST API (`api/v1`). English, हिंदी and मराठी. Super Admin keeps using the web panel.

See `../../docs/07_ANDROID_BUILD_GUIDE.md` for build, signing, APK/AAB and testing instructions.

Architecture: **MVVM + Clean Architecture** with **Retrofit 2**, **Kotlin Coroutines**, **StateFlow / SharedFlow** and **Hilt**. See `ARCHITECTURE.md`.

| Module | Layer | What it is |
|---|---|---|
| `domain/` | Domain (pure Kotlin) | Business models, repository interfaces, use cases (login rules, reply rules…), money/date/validation helpers. No Android, no networking. |
| `data/` | Data (pure Kotlin) | Retrofit `PrmsApiService`, OkHttp interceptors (headers, safe redirects), envelope/error handling (`ApiCaller`), repository implementations. Tested with MockWebServer. |
| `app/` | Presentation + DI | Compose screens, one `@HiltViewModel` per screen (StateFlow state, SharedFlow events), Hilt modules, DataStore session, language switching |
| `tools/live-check/` | — | Runs the real data layer against a server with **test** accounts (creates data!) |
| `tools/android_strings.py` | Single source of the en/hi/mr strings → generates `res/values*/strings.xml` |

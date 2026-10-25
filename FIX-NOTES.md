# Update 2026-10-25 — "HTTP 301" on login

## What the screenshot proves
The debug build now reaches the live server (`https://genusitsolution.com/rentalsolutionfinal/api/v1/`),
so the first fix worked. The server then answers `POST auth/login.php` with **301 Moved Permanently**
instead of JSON. The app's HTTP client (`UrlConnectionTransport`) deliberately does not follow redirects
(`instanceFollowRedirects = false`), so it got an HTML "Moved" page and showed `HTTP 301`.

## Root cause: server side
A 301 on an API file comes from an Apache/cPanel **.htaccess redirect rule**, not from the PHP code.
The API never runs; the request is bounced before `login.php` starts. The two usual rules are:

1. **Canonical host**: `genusitsolution.com` → `www.genusitsolution.com` (or the reverse).
   The old Rentable and Flutter apps used `www.` in their address.
2. **"Remove .php"**: `/auth/login.php` → `/auth/login`.

A GET in a browser follows the redirect silently, so the website and `meta.php` seemed fine.
A POST from the app doesn't follow it. The exact target is in the response's `Location` header,
which the app couldn't reach from my workspace. Quick check: open
`https://genusitsolution.com/rentalsolutionfinal/api/v1/auth/login.php` in the phone's browser and look
at the address bar after it loads. That is the redirect target. The new debug build also prints it on the login screen.

### Server correction (recommended, permanent)
Stop the redirect rules from touching the API. Use **one** of these:

- Create `rentalsolutionfinal/api/.htaccess` (if it doesn't exist) containing:
  ```
  RewriteEngine On
  # API: no www/.php redirects. Pass the Bearer token to PHP.
  RewriteCond %{HTTP:Authorization} .
  RewriteRule .* - [E=HTTP_AUTHORIZATION:%{HTTP:Authorization}]
  ```
  In Apache, a sub-folder `.htaccess` with `RewriteEngine On` replaces the parent folder's rewrite rules,
  so the www/.php redirects in `public_html/.htaccess` or `rentalsolutionfinal/.htaccess` no longer apply to `/api/`.
- Or, in the `.htaccess` that holds the redirect, add this line **above** the redirect rules
  (in `public_html/.htaccess` write it as `^rentalsolutionfinal/api/`):
  ```
  RewriteRule ^api/ - [L]
  ```
  If the redirect is set in cPanel → Domains → Redirects, exclude `/rentalsolutionfinal/api/` there.

Also make the app address match the canonical host. If the server prefers `www`, set
`prms.apiBaseUrl=https://www.genusitsolution.com/rentalsolutionfinal/api/v1/` in `gradle.properties`.
Confirm that first; I did not change the production URL on an assumption.

## App-side change (safe, so the app works even before the server is fixed)
`core/…/net/PrmsApi.kt` now follows a 301/302/307/308 **only** when all of these hold:
- the target host is the configured host or its `www`/non-`www` twin;
- HTTPS is never downgraded to HTTP, and the port is unchanged;
- the target path is still inside the API folder (`/rentalsolutionfinal/api/v1/…`);
- there are at most 2 hops.

The request is resent with the **same method, body and headers**. That is safe even for payments: a 3xx means
the PHP script never ran, and the `Idempotency-Key` is kept anyway.
Anything else (the website's login page, plain HTTP, another domain, a loop, 303) is refused with the new
code `SERVER_REDIRECT`. Debug builds log each redirect it follows (`adb logcat -s PrmsApi`), so the server
misconfiguration stays visible instead of hidden.

Users no longer see raw `HTTP 301` or HTML errors. They see "The server is not responding correctly right now…"
(new string `error_server`, EN/HI/MR, generated via `tools/android_strings.py`). Debug builds add the technical
detail (server URL + `HTTP 301 → <target>`) underneath.

## Files changed in this update
| File | Change |
|---|---|
| core/…/net/PrmsApi.kt | `HttpResult.location`; safe redirect policy (`safeRedirectTarget`); `SERVER_REDIRECT` code; `isServerMisconfig`; optional `onRedirect` callback |
| core/src/test/…/CoreChecks.kt, CoreTest.kt | New `redirects()` test: www twin followed, `.php`-strip followed (relative Location), POST body resent unchanged; login page / http / other host / missing Location / loop / 303 refused |
| app/…/data/AppContainer.kt | Debug-only Logcat line for each followed redirect |
| app/…/ui/auth/LoginScreen.kt | `SERVER_REDIRECT`/`BAD_RESPONSE` → friendly `error_server`; details only in debug builds |
| app/…/ui/common/State.kt | Other screens also hide raw redirect/HTML errors (generic message + Retry) |
| tools/android_strings.py + res/values{,-hi,-mr}/strings.xml | `error_server` in 3 languages (regenerated; previous files were identical to the generator output) |

## Verification done
- The `:core` module (API client, models, tests) was compiled with Kotlin 2.0.21 and kotlinx.serialization
  1.7.3, before and after the change. Results: `moneyFormatting`, `validators`, `lenientDecoding`,
  `envelopeAndHeaders`, `redirects` all **PASS** (the original 4 also pass on the untouched source).
- End-to-end with the real `UrlConnectionTransport` against a local HTTP server that answers
  `POST …/auth/login.php` with `301 → …/auth/login`: the redirect was followed, the identical JSON body was
  re-posted, a correct password returned the owner session, and a wrong password gave `INVALID_CREDENTIALS`
  (shown as the server's message, not as a network/server error).
- **Not done here:** compiling the Android `:app` module (Android SDK/Google Maven blocked in this
  workspace), and login against the live server (no route to genusitsolution.com from this workspace).

---

# Rental Solution Android (PRMSApp) – login fix, 2026-10-20

## Root cause (confirmed from the source)

`gradle.properties` had this line switched on:

```
prms.apiBaseUrlDebug=http://10.0.2.2:8080/api/v1/
```

`app/build.gradle.kts` uses it for every **debug** build. So a debug APK sent its login request to
`http://10.0.2.2:8080`. That address is how the Android *emulator* reaches the developer's own PC. On a
real phone it doesn't exist, so the connection fails and the app shows
"No internet connection or the server is not reachable. Please try again."

Why it must have been a debug APK: there is no keystore in the project, and a release build without one
comes out unsigned. Android won't install an unsigned APK, so the APK that installed on your phone was the debug one.

Release builds were never affected. They use `https://genusitsolution.com/rentalsolutionfinal/api/v1/`.

## Live server check (20 Oct 2026)

- `GET https://genusitsolution.com/rentalsolutionfinal/api/v1/common/meta.php` returns
  `{"status":"success", …}` with API version **1.1**, the payment modes, months, INR, and en/hi/mr.
  That's exactly the contract this app's `PrmsApi` expects, so api/v1 1.1 is deployed and reachable over HTTPS.
- `auth/me.php` with no token is refused, which is correct (it needs a login).
- Not checked: an actual login, and the owner, agent or tenant data calls. These need real usernames
  and passwords, and this workspace can't send POST requests to your server.

## Files changed (no feature, screen, API call or backend change)

| File | Change |
|---|---|
| gradle.properties | Turned off `prms.apiBaseUrlDebug`; debug and release now both use the live server |
| app/build.gradle.kts | Ignores a blank override; prints a Gradle WARNING if a debug build is pointed at 10.0.2.2/127.0.0.1/localhost |
| core/…/net/PrmsApi.kt | Network errors keep the exception type (UnknownHost / Connect / SSLHandshake / timeout + URL) |
| app/…/ui/auth/LoginScreen.kt | **Debug builds only**: below the error, shows the server URL and the technical reason. Release builds look exactly as before |
| .github/workflows/android-standalone.yml (new) | Builds the debug APK when PRMSApp is the root of a GitHub repo |

## Comparison with the old "Rentable" source

The old app used `https://www.genusitsolution.com/rentalsolutionfinal/api/v1/` and the same
`auth/login.php` JSON request. Both `www` and non-`www` serve the site over HTTPS without redirecting, and
`meta.php` answers on the non-`www` address. So the current release URL is correct and was left unchanged.
Nothing else in the old app was needed: it only has an Owner dashboard, while this app covers all three roles.

## Features in this app (all calling the live api/v1)

- **Common:** login with role detection; Super Admin is sent to the web panel; logout revokes the token;
  EN/हिंदी/मराठी (app strings plus server messages via Accept-Language); notifications; receipt view/share; profile & password change.
- **Owner:** dashboard; properties/rooms (add/edit/maintenance/delete); tenants (add/edit/delete/detail);
  allocations (allocate, collect deposit, close with settlement); charges; generate invoice (fixed charges + meter
  readings); invoices (detail, record payment with overpay protection + duplicate-safe retry, cancel); payments;
  collection agents (add/edit/activate/assign properties/delete); queries (reply/status); reports
  (collections/revenue/pending); referrals; subscription & renewal request.
- **Collection agent (employee):** dashboard; assigned tenants; record collection (oldest invoice first,
  duplicate-safe); collection history; profile.
- **Tenant:** dashboard; invoices + detail; payments/receipts; queries (raise/reply); profile.

I haven't checked feature-by-feature that this matches the web panel, because the web/backend source
(`api/v1`, `docs/API.md`) wasn't attached.

## Build

JDK 17, Android SDK 35, Gradle 8.9 wrapper, AGP 8.5.2, Kotlin 2.0.21.

```
./gradlew :core:test            # API client / money / validation unit tests
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk  (package com.genusit.prms.debug)
```

Release (signed):
1. Copy `keystore.properties.sample` to `keystore.properties` and fill in your own keystore path, passwords and alias (never commit it).
2. Run `./gradlew :app:assembleRelease :app:bundleRelease`. You get `app-release.apk` (for sideloading) and `app-release.aab` (for Play Store).
3. Bump `versionCode` in app/build.gradle.kts for every upload.

Emulator testing against a local PHP server (developer only):
`./gradlew :app:assembleDebug -Pprms.apiBaseUrlDebug=http://10.0.2.2:8080/api/v1/`

## Install & test on a phone

1. Uninstall the old app(s) (both "Rental Solution" entries if you have the debug and release versions).
2. Install the new `app-debug.apk` and allow "Install unknown apps".
3. If login still fails, the debug build now shows the server URL and the exact reason under the red message.
   Send me a screenshot of it.

| # | Test | Expected |
|---|---|---|
| 1 | Open `…/api/v1/common/meta.php` in the phone browser | JSON with "status":"success" |
| 2 | Owner login (valid) | Owner home with dashboard figures |
| 3 | Wrong password | Server message (invalid credentials), not "no internet" |
| 4 | Airplane mode → sign in | "No internet connection or the server is not reachable" |
| 5 | Debug build, any network error | Server URL + reason shown under the message |
| 6 | Owner: properties, tenants, allocation, generate invoice, record payment, receipt | Same figures as the web panel |
| 7 | Collection agent login → collect → receipt | Collection appears in web panel and owner payments |
| 8 | Tenant login → invoices, payments, raise query | Matches tenant web panel; owner sees the query |
| 9 | Switch EN / हिं / मरा | App text and server messages change language |
| 10 | Super Admin login | Message to use the web panel |
| 11 | Logout → reopen | Login screen; old token is rejected |

`tools/live-check/LiveApiCheck.kt` runs the whole API flow automatically, but it **creates data and records
₹1 payments**. Run it only with test accounts or on a test copy of the database, never with real owners' data.

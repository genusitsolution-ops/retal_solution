#!/bin/bash
# Read-only production checks: TLS, redirects, install path, public API answers, old-domain references in pages.
# Uses only public endpoints and a made-up user name (expects "invalid credentials"); never real accounts.
set +e
H=rentalsolution.co.in
show() { echo; echo "=== $*"; }
show "TLS certificate"
echo | openssl s_client -servername $H -connect $H:443 2>/dev/null | openssl x509 -noout -subject -issuer -dates -ext subjectAltName 2>/dev/null
for u in "http://$H/" "https://$H/" "https://www.$H/" "http://www.$H/"; do
  show "HEAD $u"; curl -sS -o /dev/null -D - --max-time 20 "$u" | grep -iE "^HTTP|^location|^server|^strict|^x-powered" 
done
for base in "https://$H/" "https://www.$H/" "https://$H/rentalsolutionfinal/"; do
  for p in "login.php" "api/v1/common/app_config.php" "api/v1/common/meta.php" "cron/cron.php" "includes/config.php" "includes/config.local.php" "uploads/" "uploads/aadhaar/" "database/migrations/2026_11_009_vacate_deposits_push.sql" "error_log" "api/error_log"; do
    c=$(curl -s -o /tmp/b -w "%{http_code} %{content_type} %{redirect_url}" --max-time 20 "$base$p")
    echo "$base$p -> $c ($(wc -c </tmp/b) bytes)"
  done
done
show "app_config body"; curl -s --max-time 20 "https://$H/api/v1/common/app_config.php"; echo
show "meta body (first 600)"; curl -s --max-time 20 "https://$H/api/v1/common/meta.php" | head -c 600; echo
show "login with a made-up account (expect 401 INVALID_CREDENTIALS)"
curl -s -w "\nHTTP %{http_code}\n" --max-time 20 -H "Content-Type: application/json" -d '{"username":"probe_nonexistent_user","password":"x"}' "https://$H/api/v1/auth/login.php"
show "old-domain / dev references in public pages"
for p in "" "login.php" "register.php" "api/v1/common/app_config.php"; do
  curl -s --max-time 20 "https://$H/$p" | grep -oiE "(genusitsolution\.com[^\"' ]*|rentalsolutionfinal[^\"' ]*|localhost[^\"' ]*|127\.0\.0\.1[^\"' ]*)" | sort -u | sed "s#^#  $p: #"
done
show "login page: asset and form URLs"
curl -s --max-time 20 "https://$H/login.php" | grep -oiE '(href|src|action)="[^"]+"' | sort -u | head -40
show "PHP warnings visible in pages"
for p in "" "login.php"; do curl -s --max-time 20 "https://$H/$p" | grep -iE "warning|notice|fatal|deprecated|stack trace" | head -5; done
echo done

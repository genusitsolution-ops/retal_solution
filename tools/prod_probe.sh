#!/bin/bash
# Read-only production checks: DNS, TLS, redirects, install path, public API answers, exposed files.
# Uses only public endpoints and a made-up user name; never real accounts. -k is used ONLY to look past a
# self-signed certificate for diagnosis (the app itself never accepts such a certificate).
set +e
H=rentalsolution.co.in
B=$(mktemp)
show() { echo; echo "=== $*"; }
show "DNS"; for n in $H www.$H; do echo "$n A: $(dig +short A $n | tr '\n' ' ')  AAAA: $(dig +short AAAA $n | tr '\n' ' ')"; done
echo "NS: $(dig +short NS $H | tr '\n' ' ')"
show "TLS certificate (as served)"
echo | openssl s_client -servername $H -connect $H:443 2>/dev/null | openssl x509 -noout -subject -issuer -dates 2>/dev/null
for host in $H www.$H; do
  echo "-- $host names on certificate:"; echo | openssl s_client -servername $host -connect $host:443 2>/dev/null | openssl x509 -noout -ext subjectAltName 2>/dev/null | tail -n +2
  v=$(curl -s -o /dev/null -w "%{http_code}" --max-time 20 "https://$host/login.php" 2>&1); echo "-- strict TLS (no -k) https://$host/login.php => $v (000 = certificate rejected)"
done
show "certificate chain sent by the server"
echo | openssl s_client -showcerts -servername $H -connect $H:443 2>/dev/null | grep -E "^ *[0-9]+ s:|^ *i:|^ *a:|v:NotBefore" | head -12
echo "-- verify with ONLY the ISRG Root X1 bundled in the app (Android 7.0 path):"
echo | openssl s_client -servername $H -connect $H:443 -CAfile "$(dirname "$0")/isrg_root_x1.pem" -no-CApath -no-CAstore -verify_hostname $H 2>/dev/null | grep -E "Verify return code"
echo | openssl s_client -servername www.$H -connect www.$H:443 -CAfile "$(dirname "$0")/isrg_root_x1.pem" -no-CApath -no-CAstore -verify_hostname www.$H 2>/dev/null | grep -E "Verify return code"
show "1.11.1 deployment markers"
for u in "http://$H/login.php" "http://www.$H/login.php" "https://www.$H/login.php" "http://$H/api/v1/common/app_config.php" "https://$H/.user.ini" "https://$H/api/migration_api.sql" "https://$H/admin/system_check.php" "https://$H/assets/js/mobile.js"; do
  echo "$u => $(curl -k -s -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 20 "$u")"
done
curl -k -s --max-time 20 "https://$H/assets/js/mobile.js" | grep -c "assets/js/mobile.js" | sed 's/^/mobile.js 1.11.1 base fix present (count>0): /'
for scheme in http https; do for host in $H www.$H; do
  base="$scheme://$host/"
  for p in "" "index.php" "login.php" "api/v1/common/app_config.php" "rentalsolutionfinal/" "rentalsolutionfinal/login.php" "rentalsolutionfinal/api/v1/common/app_config.php" \
           "includes/config.php" "includes/config.local.php" "includes/config.local.sample.php" "uploads/" "uploads/aadhaar/" "database/migrations/" "error_log" "rentalsolutionfinal.zip" "cron/cron.php" "cgi-sys/defaultwebpage.cgi"; do
    c=$(curl -k -s -o $B -w "%{http_code} %{content_type} -> %{redirect_url}" --max-time 20 "$base$p")
    t=$(head -c 120 $B | tr '\n\r' '  ')
    echo "$base$p => $c [$(wc -c <$B)b] $t"
  done
done; done
show "app_config (https -k)"; curl -k -s --max-time 20 "https://$H/api/v1/common/app_config.php" | head -c 1200; echo
show "app_config (http)"; curl -s --max-time 20 "http://$H/api/v1/common/app_config.php" | head -c 1200; echo
show "login with a made-up account (expect 401 INVALID_CREDENTIALS)"
curl -k -s -w "\nHTTP %{http_code}\n" --max-time 20 -H "Content-Type: application/json" -d '{"username":"probe_nonexistent_user","password":"x"}' "https://$H/api/v1/auth/login.php" | head -c 600
show "1.13 / 1.13.1 deployment markers (new files must answer from the site root)"
for p in "api/v1/owner/vendor.php" "admin/vendors/index.php" "includes/TenantOnboarding.php" "owner/vendor.php" \
         "web/api/v1/owner/vendor.php" "web/login.php" "rentalsolutionfinal/api/v1/owner/vendor.php" "admin/owners/signups.php"; do
  c=$(curl -k -s -o $B -w "%{http_code} -> %{redirect_url}" --max-time 20 "https://$H/$p"); echo "/$p => $c [$(wc -c <$B)b] $(head -c 160 $B | tr '\n\r' '  ')"
done
echo "-- API login answers for an owner name that does not exist (both versions answer 401; only checks the file runs):"
curl -k -s -w " HTTP %{http_code}\n" --max-time 20 -H "Content-Type: application/json" -d '{"username":"probe_nonexistent_user2","password":"x1234567"}' "https://$H/api/v1/auth/login.php" | head -c 400; echo
echo "-- response headers of the login API (cache / PHP version):"
curl -k -s -D - -o /dev/null --max-time 20 -H "Content-Type: application/json" -d '{"username":"probe_nonexistent_user3","password":"x"}' "https://$H/api/v1/auth/login.php" | grep -iE "^(server|x-powered-by|cache|x-litespeed|cf-|age|x-cache|x-proxy)" 
show "old-domain / dev references in public pages (https -k)"
for p in "" "login.php" "index.php"; do
  curl -k -s --max-time 20 "https://$H/$p" | grep -oiE "(genusitsolution\.com[^\"' <]*|rentalsolutionfinal[^\"' <]*|localhost[^\"' <]*)" | sort -u | sed "s#^#  /$p: #"
done
show "login page asset / form URLs"
curl -k -s --max-time 20 "https://$H/login.php" | grep -oiE '(href|src|action)="[^"]+"' | sort -u | head -40
echo done

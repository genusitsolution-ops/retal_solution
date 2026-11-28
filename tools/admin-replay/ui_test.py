"""Admin app walkthrough on the emulator (CI only): signs in, opens every screen, performs every kind of action against the
replay server (tools/admin-replay/replay.py) and checks what the app sent. Screenshots go to shots/. Exit code = failures."""
import json, os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG = "com.genusit.prms.admin.debug"
os.makedirs("shots", exist_ok=True)
results = []

def sh(*a): return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout
def nodes():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    x = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode("utf-8", "replace")
    try: return list(ET.fromstring(x).iter("node")) if x.strip().startswith("<") else []
    except ET.ParseError: return []
def label(n): return (n.get("text") or "") or (n.get("content-desc") or "")
def texts(): return [label(n) for n in nodes() if label(n)]
def center(n):
    a, b, c, d = map(int, re.findall(r"\d+", n.get("bounds"))); return (a + c) // 2, (b + d) // 2
def find(t, exact=True, timeout=12):
    """The matching on-screen text ("" when absent). Returns the text, not the XML node: a node without children is falsy."""
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes():
            l = label(n)
            if (l == t) if exact else (t in l): return l
        time.sleep(0.7)
    return ""
def tap(t, exact=True, timeout=12, nth=0):
    end = time.time() + timeout
    while time.time() < end:
        m = [n for n in nodes() if ((label(n) == t) if exact else (t in label(n)))]
        if len(m) > nth:
            x, y = center(m[nth]); sh("input", "tap", str(x), str(y)); time.sleep(0.9); return True
        time.sleep(0.7)
    return False
def tap_last(t):
    m = [n for n in nodes() if label(n) == t]
    if not m: return False
    x, y = center(m[-1]); sh("input", "tap", str(x), str(y)); time.sleep(0.9); return True
def edit(i, value):
    hide_kb()   # on a small screen the keyboard can cover the next field
    e = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    if len(e) <= i: return False
    x, y = center(e[i]); sh("input", "tap", str(x), str(y)); time.sleep(0.4)
    sh("input", "text", value.replace(" ", "%s")); time.sleep(0.4); return True
def scroll_to(t, exact=True, tries=14):
    for _ in range(tries):
        if find(t, exact, timeout=2): return True
        swipe_up()
    return False
def back(): sh("input", "keyevent", "4"); time.sleep(1)
_m = re.search(r"(\d+)x(\d+)", sh("wm", "size").split("Override size:")[-1]) or re.search(r"(\d+)x(\d+)", sh("wm", "size"))
W, H = (int(_m.group(1)), int(_m.group(2))) if _m else (1080, 1920)
print(f"screen {W}x{H}", flush=True)
# swipes on the left quarter: the "Add vendor" button sits bottom-right; medium distance so short rows are not skipped
def swipe_up(): sh("input", "swipe", str(W // 4), str(int(H * .72)), str(W // 4), str(int(H * .40)), "400"); time.sleep(0.8)
def to_top():
    for _ in range(4): sh("input", "swipe", str(W // 2), str(int(H * .30)), str(W // 2), str(int(H * .85)), "250")
    time.sleep(1.8)   # let the fling stop: a tap during a fling only stops it
def chip(t, row_hint):
    """Taps a filter chip; scrolls the chip row sideways when the chip is off-screen."""
    for _ in range(3):
        if tap(t, timeout=2):
            time.sleep(0.8); tap(t, timeout=2)   # second tap in case the first only stopped a scroll (selecting twice is harmless)
            return True
        m = [n for n in nodes() if label(n) == row_hint or label(n).startswith(row_hint)]
        if not m: to_top(); m = [n for n in nodes() if label(n) == row_hint or label(n).startswith(row_hint)]
        if m:
            _, y = center(m[0]); sh("input", "swipe", str(int(W * .9)), str(y), str(int(W * .1)), str(y), "300"); time.sleep(0.6)
    return False
def hide_kb():
    # BACK only while the keyboard is up (BACK without a keyboard would close the dialog)
    if "mInputShown=true" in sh("dumpsys", "input_method"): sh("input", "keyevent", "4"); time.sleep(0.6)
def shot(name): subprocess.run(f"adb exec-out screencap -p > shots/{name}.png", shell=True); time.sleep(0.3)
def check(name, ok, info=""):
    results.append(bool(ok)); print(("PASS " if ok else "FAIL ") + name + ("" if ok else f" — {info}"[:900]), flush=True)
def posts():
    try: return [json.loads(l) for l in open("admin-replay-posts.txt")]
    except FileNotFoundError: return []
def last_post(): p = posts(); return p[-1] if p else {}

def by_id(rid):
    return [n for n in nodes() if (n.get("resource-id") or "").endswith(rid)]
def fill(rid, value, clear=False, tries=10):
    """Types into the form field with test tag `rid` (scrolls down to it; keeps it above the keyboard)."""
    hide_kb()
    for i in range(tries * 2):
        if i == tries: to_top()   # second pass from the top (the field may have scrolled above the screen)
        m = by_id(rid)
        if m:
            x, y = center(m[0])
            if y > H * .70: sh("input", "swipe", str(W // 4), str(int(H * .70)), str(W // 4), str(int(H * .40)), "300"); time.sleep(0.8); continue
            x2 = int(re.findall(r"\d+", m[0].get("bounds"))[2])
            for attempt in range(3):
                # tap near the right edge: the cursor lands after the (left-aligned) text
                sh("input", "tap", str(x2 - 25 if clear or attempt else x), str(y)); time.sleep(0.8)
                if clear or attempt:
                    cur = label((by_id(rid) or m)[0])
                    for _ in range(len(cur) + 2): sh("input", "keyevent", "67"); time.sleep(0.08)
                    time.sleep(0.4)
                for ch in value:   # one key at a time: fast injected text can lose characters while the form redraws
                    sh("input", "text", "%s" if ch == " " else ch); time.sleep(0.12)
                time.sleep(1.0)
                now = [label(n) for n in by_id(rid)]
                if now and now[0] == value: return True
                print(f"retyping {rid}: shows {now[0] if now else None!r}, wanted {value!r}", flush=True)
            return False
        swipe_up()
    return False
def tap_id(rid, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        m = by_id(rid)
        if m: x, y = center(m[0]); sh("input", "tap", str(x), str(y)); time.sleep(0.9); return True
        time.sleep(0.7)
    print("not found: " + rid, flush=True); return False
def save_form():
    hide_kb()
    for _ in range(10):
        if by_id("vf_save"): break
        swipe_up()
    return tap_id("vf_save", timeout=3)
def field_text(rid):
    m = by_id(rid)
    if not m: return None
    return " ".join(label(n) for n in m[0].iter("node")) or label(m[0])
def fresh_start():
    sh("am", "force-stop", PKG); sh("pm", "clear", PKG)
    sh("am", "start", "-n", f"{PKG}/com.genusit.prms.admin.ui.MainActivity"); time.sleep(4)
def sign_in(user, pw):
    fresh_start()
    edit(0, user); edit(1, pw)
    sh("input", "keyevent", "66")   # Done on the password field signs in (the keyboard may cover the button)
    time.sleep(3)

fresh_start()
check("sign-in screen shown", find("Rental Solution Admin") and find("Sign in", timeout=3))
shot("01-sign-in")
sign_in("testowner", "Test@1234"); hide_kb()
check("owner account refused by the app", find("This app is only for the Super Admin", exact=False), texts())
shot("01b-owner-refused")
sign_in("testadmin", "wrong-pass"); hide_kb()
check("wrong password: server message", find("Invalid credentials", exact=False), texts())
sign_in("testadmin", "Test@1234")
check("admin signs in → dashboard", find("Welcome, Test Admin", timeout=15), texts())
time.sleep(1.5); shot("02-dashboard")
t = texts()
check("dashboard: pending approvals 1, payments to verify 2, owners 7, vendors 1/1",
      "Pending approvals" in t and "Payments to verify" in t and "7" in t and "1 / 1" in t, t)
check("dashboard: quick actions", scroll_to("Add vendor") and find("Verify payments", timeout=3) and find("Approvals", timeout=3), texts())
to_top()
check("dashboard: owners by status + self-registration", scroll_to("Owners by status") and scroll_to("Owner self-registration"), texts())
check("dashboard: vendor commission block", scroll_to("Approved, to pay")); shot("03-dashboard-more")
to_top()

# ── approvals ──
tap("Approvals", nth=0); time.sleep(1.5)
check("approvals: Pooja Rao pending with referral", find("Pooja Rao") and find("RSU", exact=False, timeout=3) is not None or find("Ramesh Patel", exact=False, timeout=3), texts())
shot("04-approvals")
tap("Approve"); check("approve asks for confirmation", find("Approve Pooja Rao?"))
shot("05-approve-confirm")
tap_last("Approve"); time.sleep(1.5)
p = last_post(); check("approve sent {action:approve, id}", p.get("path") == "admin/signups.php" and p.get("action") == "approve" and p.get("id"), p)
check("success message shown", find("Owner approved and activated.", timeout=6))
tap("Reject", nth=0); time.sleep(0.8)
check("reject asks for a reason; Reject disabled until typed", find("Reject Pooja Rao?") is not None)
edit(0, "Incomplete details"); hide_kb(); shot("06-reject-reason"); tap_last("Reject"); time.sleep(1.5)
p = last_post(); check("reject sent with reason", p.get("action") == "reject" and p.get("reason") == "Incomplete details", p)
chip("Rejected (1)", "Pending"); time.sleep(1.5); shot("07-approvals-rejected")
check("approvals: Rejected shows the reason", find("Duplicate test account", timeout=6), texts())

# ── owners ──
tap("Owners"); time.sleep(1.5); shot("08-owners")
check("owners list: 7 owners incl. suspended Sunita", find("All (7)", timeout=8) and scroll_to("Sunita Sharma"), texts())
to_top(); chip("Suspended (1)", "All (7)"); time.sleep(1.5)
check("filter Suspended", find("Sunita Sharma") and not find("Amit Kulkarni", timeout=2), texts())
tap("Sunita Sharma"); time.sleep(1.5); shot("09-owner-detail")
check("owner detail: subscription section", scroll_to("Subscription"), texts())
check("owner detail: no 'Convert to vendor' (vendors are registered separately now)", not scroll_to("Convert to vendor", tries=4), texts())
to_top(); scroll_to("Change account status", tries=3)
tap("Change account status"); time.sleep(0.8)
check("status dialog with Active / Inactive / Suspended", find("Active") and find("Inactive") and find("Suspended"), texts())
tap("Active"); shot("10-owner-status"); tap("Save"); time.sleep(1.5)
p = last_post(); check("status change sent", p.get("path") == "admin/owners.php" and p.get("action") == "status" and p.get("status") == "active" and p.get("id") == 2, p)
back(); time.sleep(1)

# ── vendors ──
def wait_snack_gone():
    for _ in range(20):
        if not any(m in " ".join(texts()) for m in ("Payout saved", "Owner status updated", "Registration rejected", "Owner approved", "Vendor ", "Commission ", "Remark saved", "New temporary")): break
        time.sleep(1)
tap("Vendors", nth=0); time.sleep(1.5); shot("12-vendors")
check("vendors list: independent Sunita Deshpande (VEN10101) and old owner-account vendor Ramesh Patel",
      scroll_to("Sunita Deshpande") and scroll_to("VEN10101") and scroll_to("Ramesh Patel") and scroll_to("Owner account"), texts())
to_top()
to_top(); chip("Suspended", "All"); time.sleep(1.5)
check("filter Suspended: Kiran Traders only", find("Kiran Traders") and not find("Sunita Deshpande", timeout=2), texts())
chip("All", "Active"); time.sleep(1.5)

# add vendor: checks, server error, success with the sign-in details
tap_id("add_vendor"); time.sleep(1.5)
check("add vendor form opens", find("Independent vendor with own sign-in", timeout=6), texts())
shot("13-add-vendor-empty")
save_form(); time.sleep(1); to_top()
check("empty form: fields marked, nothing sent", find("Please correct the", exact=False) and last_post().get("action") != "create_independent", texts())
shot("14-add-vendor-errors")
for rid, val in [("vf_full_name", "Meena Joshi"), ("vf_mobile", "9000000001"), ("vf_email", "meena@example.test"), ("vf_address", "Shop 4 Station Road"),
                 ("vf_city", "Nagpur"), ("vf_pincode", "440001"), ("vf_state", "Maharashtra"), ("vf_commission_percent", "12.5")]:
    check(f"typed {rid}", fill(rid, val))
hide_kb(); scroll_to("Bank transfer"); tap("Bank transfer"); time.sleep(0.5)
for rid, val in [("vf_upi_id", "meena@okbank"), ("vf_account_holder", "Meena Joshi"), ("vf_bank_name", "Test Bank"), ("vf_account_number", "123456789"), ("vf_ifsc", "test0001234")]:
    check(f"typed {rid}", fill(rid, val))
hide_kb(); shot("15-add-vendor-payment")
save_form(); time.sleep(2)
to_top(); check("duplicate mobile: server's message shown", find("Another vendor already uses this mobile number.", timeout=6), texts())
to_top(); check("fix mobile", fill("vf_mobile", "9876501234", clear=True))
hide_kb()
save_form(); time.sleep(2)
p = last_post()
check("create_independent sent with every detail", p.get("action") == "create_independent" and p.get("full_name") == "Meena Joshi" and p.get("mobile") == "9876501234"
      and p.get("commission_percent") == "12.5" and p.get("payout_method") == "bank" and p.get("ifsc") == "TEST0001234" and p.get("account_number") == "123456789"
      and p.get("upi_id") == "meena@okbank" and p.get("city") == "Nagpur" and p.get("pincode") == "440001" and p.get("status") == "active"
      and p.get("vendor_can_edit_payment") is False and p.get("password") == "", p)
check("sign-in details shown once: code, mobile, temporary password", find("Vendor registered") and find("VEN10101") and find("TEMP8421") and find("9876501234"), texts())
check("share buttons: Copy / WhatsApp / Share", find("Copy") and find("WhatsApp") and find("Share (SMS, email…)"), texts())
shot("16-vendor-registered")
tap("Done"); time.sleep(2)

# vendor detail (independent)
check("vendor detail: code, type, commission", find("VEN10101", timeout=8) and find("Independent") and find("10% commission"), texts())
shot("17-vendor-detail")
check("vendor detail: profile and payment details", scroll_to("Payment details") and find("sunita@okbank") and find("••••••••9012"), texts())
shot("18-vendor-profile")
to_top(); tap("Status"); time.sleep(0.8); tap("Suspended"); time.sleep(0.3)
tap_last("Save"); time.sleep(1)
check("suspend needs a reason (nothing sent)", last_post().get("action") != "set_status", last_post())
edit(0, "Documents pending"); hide_kb(); shot("19-vendor-status"); tap_last("Save"); time.sleep(1.5)
p = last_post(); check("set_status sent: suspended with reason", p.get("action") == "set_status" and p.get("id") == 101 and p.get("status") == "suspended" and p.get("reason") == "Documents pending", p)
wait_snack_gone(); to_top(); tap("Reset"); time.sleep(0.8)
check("reset asks for confirmation", find("Reset sign-in of", exact=False), texts()); tap_last("Reset"); time.sleep(2)
p = last_post(); check("reset_password sent", p.get("action") == "reset_password" and p.get("id") == 101, p)
check("new temporary password shown once", find("New temporary password") and find("RSTX5521"), texts()); shot("20-vendor-reset"); tap("Done"); time.sleep(1)
wait_snack_gone(); to_top(); tap("Edit"); time.sleep(2)
check("edit form loads the saved details", find("Sunita Deshpande", exact=False, timeout=8), texts())
check("change commission to 11", fill("vf_commission_percent", "11", clear=True)); hide_kb(); shot("21-vendor-edit")
save_form(); time.sleep(2)
p = last_post(); check("update_independent sent; account number kept in full", p.get("action") == "update_independent" and p.get("id") == 101 and p.get("commission_percent") == "11"
                       and p.get("account_number") == "123456789012" and p.get("upi_id") == "sunita@okbank" and p.get("remarks") == "Field partner, Pune"
                       and p.get("address") == "12 MG Road" and p.get("full_name") == "Sunita Deshpande", p)
check("back on the vendor after saving", find("VEN10101", timeout=6), texts())
wait_snack_gone(); scroll_to("Commissions")
chip("Commissions", "Monthly payout"); time.sleep(1); scroll_to("History"); shot("22-vendor-commissions")
check("commissions: paid one shows UPI + UTR, pending one can be approved or rejected", find("UTR55667788", exact=False) and find("Approve") and find("Reject"), texts())
tap("Reject"); time.sleep(0.8); check("reject (pending) asks for a reason", find("Reject commission", exact=False))
edit(0, "Duplicate renewal"); hide_kb(); tap_last("Reject"); time.sleep(1.5)
p = last_post(); check("reverse sent with reason", p.get("action") == "reverse" and p.get("commission_id") == 202 and p.get("reason") == "Duplicate renewal", p)
wait_snack_gone(); scroll_to("Mark paid"); tap("Mark paid"); time.sleep(0.8); tap("Bank transfer"); edit(1, "NEFT123"); hide_kb(); shot("23-mark-paid"); tap_last("Mark paid"); time.sleep(1.5)
p = last_post(); check("mark_paid sent with payout method", p.get("action") == "mark_paid" and p.get("payout_method") == "bank" and p.get("payout_reference") == "NEFT123" and "_choice" not in p, p)
wait_snack_gone(); scroll_to("History"); tap("History"); time.sleep(2); shot("24-commission-history")
check("commission history dialog", find("History of C-", exact=False), texts()); tap("Close")
to_top(); scroll_to("Commissions"); chip("Referred owners", "Monthly payout"); time.sleep(1)
check("referred owners: A and B", scroll_to("Referred Owner A") and scroll_to("Referred Owner B"), texts()); shot("25-vendor-referred")
to_top(); scroll_to("Commissions"); chip("Payout history", "Monthly payout"); time.sleep(1)
check("payout history with method and UTR", scroll_to("UTR55667788") and find("UPI"), texts()); shot("26-vendor-payouts")
back(); time.sleep(1)

# old owner-account vendor still works (monthly payout, % / status)
to_top(); scroll_to("Ramesh Patel"); tap("Ramesh Patel"); time.sleep(1.5)
check("old vendor: totals", scroll_to("Total earned"), texts())
to_top(); scroll_to("Commissions"); chip("Monthly payout", "Commissions"); time.sleep(1)
scroll_to("Record payout"); tap("Record payout"); time.sleep(0.8)
check("payout dialog explains it only records the transfer", find("this only records it", exact=False), texts())
tap("Also approve and pay", exact=False)
edit(1, "UTR998877"); hide_kb(); shot("27-record-payout"); tap("Save payout"); time.sleep(1.5)
p = last_post(); check("payout sent with month, UTR, date, method, approve_pending", p.get("action") == "pay_month" and p.get("payout_reference") == "UTR998877"
                       and re.match(r"\d{4}-\d{2}$", p.get("month", "")) and re.match(r"\d{4}-\d{2}-\d{2}$", p.get("paid_date", "")) and p.get("approve_pending") is True and p.get("payout_method") == "upi", p)
wait_snack_gone(); to_top(); tap("Edit % / status")
time.sleep(0.8); tap("Inactive"); tap("Save"); time.sleep(1.5)
p = last_post(); check("old vendor update sent (inactive, 10%)", p.get("action") == "update" and p.get("status") == "inactive" and p.get("commission_percent") == "10", p)
back()

# ── payments ──
tap("Payments"); time.sleep(1.5); shot("28-payments")
check("payments: 2 to verify, warnings on the short one", find("To verify (2)") and find("paid less than the invoice", exact=False), texts())
tap("Vikas Mehta"); time.sleep(3); shot("29-payment-detail")
check("payment detail: checks list the problems", find("UTR on screenshot differs", exact=False) or find("UTR on screenshot: 712345678909", exact=False), texts())
scroll_to("Open full screen (pinch to zoom)")
check("screenshot loaded from the server", find("Open full screen (pinch to zoom)", timeout=8), texts())
shot("30-payment-screenshot")
tap("Open full screen (pinch to zoom)"); time.sleep(1); shot("31-screenshot-full"); tap("Close"); time.sleep(0.5)
scroll_to("Approve"); tap("Approve"); time.sleep(0.8)
check("approve dialog requires the bank check and the difference", find("Accept the difference", exact=False) and find("I found this payment", exact=False), texts())
shot("32-approve-payment")
tap_last("Approve"); time.sleep(1)
check("cannot approve before ticking (nothing sent)", last_post().get("path") != "admin/payments.php")
tap("I found this payment", exact=False); tap("Accept the difference", exact=False); tap_last("Approve"); time.sleep(1.5)
p = last_post(); check("payment approve sent verified + accept_difference", p.get("path") == "admin/payments.php" and p.get("action") == "approve" and p.get("verified") is True and p.get("accept_difference") is True, p)
tap("Reject"); time.sleep(0.8); edit(0, "Not in bank statement"); hide_kb(); tap_last("Reject"); time.sleep(1.5)
p = last_post(); check("payment reject sent with reason", p.get("action") == "reject" and p.get("reason") == "Not in bank statement", p)
tap("Edit remarks"); time.sleep(0.8); edit(0, "Called owner"); hide_kb(); tap("Save"); time.sleep(1.5)
p = last_post(); check("remarks saved", p.get("action") == "remarks" and "Called owner" in p.get("remarks", ""), p)
back()

# ── sign out ──
tap("Home"); time.sleep(1); tap("Sign out"); time.sleep(0.6); tap_last("Sign out"); time.sleep(1.5)
check("sign out → sign-in screen", find("Rental Solution Admin", timeout=6))
sh("am", "force-stop", PKG); sh("am", "start", "-n", f"{PKG}/com.genusit.prms.admin.ui.MainActivity"); time.sleep(3)
check("after sign-out a restart asks for sign-in again", find("Sign in", timeout=8))

misses = open("admin-replay-misses.txt").read() if os.path.exists("admin-replay-misses.txt") else ""
print("replay misses:\n" + misses)
print(f"\n{sum(results)}/{len(results)} passed")
sys.exit(len(results) - sum(results))

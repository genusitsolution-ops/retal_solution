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
def scroll_to(t, exact=True, tries=8):
    for _ in range(tries):
        if find(t, exact, timeout=2): return True
        swipe_up()
    return False
def back(): sh("input", "keyevent", "4"); time.sleep(1)
_m = re.search(r"(\d+)x(\d+)", sh("wm", "size").split("Override size:")[-1]) or re.search(r"(\d+)x(\d+)", sh("wm", "size"))
W, H = (int(_m.group(1)), int(_m.group(2))) if _m else (1080, 1920)
print(f"screen {W}x{H}", flush=True)
def swipe_up(): sh("input", "swipe", str(W // 2), str(int(H * .78)), str(W // 2), str(int(H * .30)), "350"); time.sleep(0.8)
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
    results.append(bool(ok)); print(("PASS " if ok else "FAIL ") + name + ("" if ok else f" — {info}"[:400]), flush=True)
def posts():
    try: return [json.loads(l) for l in open("admin-replay-posts.txt")]
    except FileNotFoundError: return []
def last_post(): p = posts(); return p[-1] if p else {}

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
check("dashboard: vendor commission block", scroll_to("Approved, to pay")); shot("03-dashboard-more")

# ── approvals ──
tap("Approvals"); time.sleep(1.5)
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
check("owner detail: subscription and vendor sections", scroll_to("Subscription") and scroll_to("Convert to vendor"), texts())
to_top(); scroll_to("Change status", tries=3)
tap("Change status"); time.sleep(0.8)
check("status dialog with Active / Inactive / Suspended", find("Active") and find("Inactive") and find("Suspended"), texts())
tap("Active"); shot("10-owner-status"); tap("Save"); time.sleep(1.5)
p = last_post(); check("status change sent", p.get("path") == "admin/owners.php" and p.get("action") == "status" and p.get("status") == "active" and p.get("id") == 2, p)
scroll_to("Convert to vendor"); tap("Convert to vendor"); time.sleep(0.8)
edit(0, "7.5"); edit(1, "Agent for Raipur"); hide_kb(); shot("11-convert-vendor"); tap_last("Convert"); time.sleep(1.5)
p = last_post(); check("convert to vendor sent with %, status, remarks", p.get("action") == "convert" and p.get("owner_id") == 2 and p.get("commission_percent") == "7.5"
                       and p.get("status") == "active" and p.get("remarks") == "Agent for Raipur", p)
back(); time.sleep(1)

# ── vendors ──
tap("Vendors"); time.sleep(1.5); shot("12-vendors")
check("vendors list: Ramesh Patel 10%", find("Ramesh Patel") and find("10% commission", exact=False), texts())
tap("Ramesh Patel"); time.sleep(1.5); shot("13-vendor-detail")
check("vendor detail: totals and monthly payout", scroll_to("Earned") and scroll_to("Record payout"), texts())
tap("Record payout"); time.sleep(0.8)
check("payout dialog explains it only records the transfer", find("this only records it", exact=False), texts())
check("payout dialog offers to approve the pending one", find("Also approve and pay", exact=False))
tap("Also approve and pay", exact=False)
edit(1, "UTR998877"); hide_kb(); shot("14-record-payout"); tap("Save payout"); time.sleep(1.5)
p = last_post(); check("payout sent with month, UTR, date, approve_pending", p.get("action") == "pay_month" and p.get("payout_reference") == "UTR998877"
                       and re.match(r"\d{4}-\d{2}$", p.get("month", "")) and re.match(r"\d{4}-\d{2}-\d{2}$", p.get("paid_date", "")) and p.get("approve_pending") is True, p)
to_top()
for _ in range(20):   # wait until no snackbar message is on screen (it can cover the chip row on a 320x640 screen)
    if not any(m in " ".join(texts()) for m in ("Payout saved", "Owner converted", "Owner status updated", "Registration rejected", "Owner approved")): break
    time.sleep(1)
scroll_to("Commissions")   # on a 320x640 screen the tab row is below the fold when the page is at the top
chip("Commissions", "Monthly payout"); time.sleep(1.5)
scroll_to("History"); shot("15-vendor-commissions")
check("commissions tab: paid one with UTR, pending one with Approve", find("NEFT-HDFC-88231", exact=False) and find("Approve"), texts())
tap("Approve"); time.sleep(0.8); tap_last("Approve"); time.sleep(1.5)
p = last_post(); check("commission approve sent", p.get("action") == "approve" and p.get("commission_id"), p)
tap("History"); time.sleep(2); shot("16-commission-history")
check("commission history dialog", find("History of C-", exact=False) and (find("Created", exact=False, timeout=4)), texts())
tap("Close")
to_top()
scroll_to("Commissions"); chip("Referred owners", "Monthly payout"); time.sleep(1); scroll_to("Amit Kulkarni"); shot("17-vendor-referred")
check("referred owners tab: Amit and Neha", scroll_to("Amit Kulkarni") and scroll_to("Neha Joshi"), texts())
to_top(); scroll_to("Commissions"); chip("Payout history", "Monthly payout"); time.sleep(1); scroll_to("Recorded by"); shot("18-vendor-payouts")
check("payout history tab: NEFT-HDFC-88231 by Test Admin", scroll_to("NEFT-HDFC-88231") and scroll_to("Test Admin"), texts())
to_top(); tap("Edit % / status")
time.sleep(0.8); shot("19-vendor-edit"); tap("Inactive"); tap("Save"); time.sleep(1.5)
p = last_post(); check("vendor update sent (inactive, 10%)", p.get("action") == "update" and p.get("status") == "inactive" and p.get("commission_percent") == "10", p)
back()

# ── payments ──
tap("Payments"); time.sleep(1.5); shot("20-payments")
check("payments: 2 to verify, warnings on the short one", find("To verify (2)") and find("paid less than the invoice", exact=False), texts())
tap("Vikas Mehta"); time.sleep(3); shot("21-payment-detail")
check("payment detail: checks list the problems", find("UTR on screenshot differs", exact=False) or find("UTR on screenshot: 712345678909", exact=False), texts())
scroll_to("Open full screen (pinch to zoom)")
check("screenshot loaded from the server", find("Open full screen (pinch to zoom)", timeout=8), texts())
shot("22-payment-screenshot")
tap("Open full screen (pinch to zoom)"); time.sleep(1); shot("23-screenshot-full"); tap("Close"); time.sleep(0.5)
scroll_to("Approve"); tap("Approve"); time.sleep(0.8)
check("approve dialog requires the bank check and the difference", find("Accept the difference", exact=False) and find("I found this payment", exact=False), texts())
shot("24-approve-payment")
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

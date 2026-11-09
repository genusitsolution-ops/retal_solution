"""Emulator test of the Admin app only: replays answers recorded from a local test copy of the real admin API
(made-up accounts, no real data). POST actions answer like the server and are logged to admin-replay-posts.txt
so the test can check what the app sent. Requests without the admin token get 401 like the real server."""
import base64, json, sys, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
FX = json.load(open(sys.argv[1], encoding="utf-8"))
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8091
MESSAGES = {
    ("admin/signups.php", "approve"): "Owner approved and activated.", ("admin/signups.php", "reject"): "Registration rejected.",
    ("admin/owners.php", "status"): "Owner status updated.", ("admin/vendors.php", "convert"): "Owner converted to vendor.",
    ("admin/vendors.php", "update"): "Vendor updated.", ("admin/vendors.php", "approve"): "Commission approved.",
    ("admin/vendors.php", "mark_paid"): "Commission marked as paid.", ("admin/vendors.php", "reverse"): "Commission reversed.",
    ("admin/vendors.php", "remark"): "Remark saved.", ("admin/vendors.php", "pay_month"): "Payout saved and marked as paid.",
    ("admin/payments.php", "approve"): "Payment approved. The plan is active.", ("admin/payments.php", "reject"): "Payment rejected. The owner was notified.",
    ("admin/payments.php", "remarks"): "Remarks saved.",
}
REQUIRED = {("admin/signups.php", "reject"): "reason", ("admin/payments.php", "reject"): "reason", ("admin/vendors.php", "reverse"): "reason",
            ("admin/vendors.php", "pay_month"): "payout_reference"}

class H(BaseHTTPRequestHandler):
    def send(self, code, obj, ctype="application/json; charset=utf-8", raw=None):
        b = raw if raw is not None else json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def route(self):
        u = urllib.parse.urlparse(self.path)
        return u.path.split("/api/v1/", 1)[-1], dict(urllib.parse.parse_qsl(u.query))
    def authed(self):
        return self.headers.get("Authorization") == "Bearer replay-admin-token"
    def do_GET(self):
        path, q = self.route()
        if not self.authed(): return self.send(401, {"status": "error", "code": "UNAUTHENTICATED", "message": "Authentication required."})
        if path == "admin/payments.php" and "file" in q:
            f = FX["__files__"].get(q["file"])
            return self.send(200, None, "image/png", base64.b64decode(f)) if f else self.send(404, {"status": "error", "code": "NOT_FOUND", "message": "Screenshot not found."})
        key = path + ("?" + "&".join(f"{k}={v}" for k, v in sorted(q.items()) if v != "") if any(v != "" for v in q.values()) else "")
        hit = FX.get(key)
        if hit is None:
            open("admin-replay-misses.txt", "a").write("GET " + key + "\n")
            return self.send(404, {"status": "error", "code": "NOT_FOUND", "message": "Not recorded for this test."})
        self.send(hit["status"], hit["body"])
    def do_POST(self):
        path, _ = self.route()
        n = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(n) or b"{}")
        if path == "auth/login.php":
            u, p = body.get("username"), body.get("password")
            if u == "testadmin" and p == "Test@1234": return self.send(200, FX["POST auth/login.php"])
            if u == "testowner" and p == "Test@1234": return self.send(200, FX["POST auth/login.php#owner"])
            return self.send(401, {"status": "error", "code": "INVALID_CREDENTIALS", "message": "Invalid credentials or account inactive."})
        if not self.authed(): return self.send(401, {"status": "error", "code": "UNAUTHENTICATED", "message": "Authentication required."})
        a = body.get("action", "")
        open("admin-replay-posts.txt", "a").write(json.dumps({"path": path, **body}, ensure_ascii=False) + "\n")
        need = REQUIRED.get((path, a))
        if need and not str(body.get(need, "")).strip():
            return self.send(422, {"status": "error", "code": "VALIDATION", "message": "Missing " + need})
        if (path, a) == ("admin/payments.php", "approve") and not body.get("verified"):
            return self.send(422, {"status": "error", "code": "VALIDATION", "message": "Confirm that you found this payment in your bank or UPI statement."})
        msg = MESSAGES.get((path, a))
        if not msg: return self.send(422, {"status": "error", "code": "VALIDATION", "message": "Unknown action."})
        self.send(200, {"status": "success", "message": msg, "data": {}})
    def log_message(self, *a): pass

ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()

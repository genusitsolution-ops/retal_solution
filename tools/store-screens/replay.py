"""Play Store screenshot run only: serves recorded API responses of a made-up demo owner to the emulator.
No real accounts or data. Misses are logged to replay-misses.txt."""
import json, sys, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
FX = json.load(open(sys.argv[1], encoding="utf-8"))
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8090
def key(path, query):
    q = {k: v[0] for k, v in urllib.parse.parse_qs(query).items() if k not in ("from", "to")}
    return path + ("?" + urllib.parse.urlencode(sorted(q.items())) if q else "")
class H(BaseHTTPRequestHandler):
    def send(self, code, obj):
        b = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def route(self):
        u = urllib.parse.urlparse(self.path)
        return u.path.split("/api/v1/", 1)[-1], u.query
    def role(self):
        # 1.14: tenant and collection-agent demo accounts have their own recorded answers ("tenant|…", "employee|…")
        tok = (self.headers.get("Authorization") or "").replace("Bearer ", "")
        return {"demo-tenant-token": "tenant", "demo-agent-token": "employee"}.get(tok)
    def do_GET(self):
        path, q = self.route()
        r = self.role()
        hit = (FX.get(r + "|" + key(path, q)) or FX.get(r + "|" + path)) if r else (FX.get(key(path, q)) or FX.get(path))
        if hit is None:
            open("replay-misses.txt", "a").write("GET " + key(path, q) + "\n")
            return self.send(404, {"status": "error", "code": "NOT_FOUND", "message": "Not available in the demo."})
        self.send(hit.get("status", 200), hit.get("body"))
    def do_POST(self):
        path, q = self.route()
        n = int(self.headers.get("Content-Length") or 0); raw = self.rfile.read(n)
        if path == "auth/login.php":
            try: user = json.loads(raw or b"{}").get("username", "")
            except Exception: user = ""
            return self.send(200, FX.get("POST auth/login.php|" + user) or FX["POST auth/login.php"])
        open("replay-misses.txt", "a").write("POST " + path + "\n")
        self.send(200, {"status": "success", "message": "OK", "data": {}})
    def log_message(self, *a): pass
ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()

"""Tiny uiautomator helper for the smoke test: dump visible texts, tap the n-th text field or a text."""
import re, subprocess, sys, xml.etree.ElementTree as ET

def nodes():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], check=False, capture_output=True)
    xml = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode("utf-8", "replace")
    return list(ET.fromstring(xml).iter("node")) if xml.strip().startswith("<") else []

def center(n):
    a, b, c, d = map(int, re.findall(r"\d+", n.get("bounds")))
    return str((a + c) // 2), str((b + d) // 2)

def tap(n):
    subprocess.run(["adb", "shell", "input", "tap", *center(n)])

cmd = sys.argv[1]
if cmd == "dump":
    texts = [n.get("text") or n.get("content-desc") for n in nodes()]
    open(sys.argv[2], "w").write("\n".join(t for t in texts if t))
elif cmd == "tap-edit":
    edits = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    tap(edits[int(sys.argv[2])])
elif cmd == "tap-text":
    match = [n for n in nodes() if (n.get("text") or "") == sys.argv[2]]
    if not match: sys.exit(1)
    tap(match[0])

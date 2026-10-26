#!/usr/bin/env python3
"""
Build-time launcher icon from a logo (the home-screen icon is packaged in the APK; it cannot be changed
remotely). Usage:

    python3 tools/launcher_icon.py path/to/logo-1024.png "#4F46E5" app/src/main/res

Writes, for every density:
  mipmap-<dpi>/ic_launcher_foreground.png  (adaptive icon, logo inside the 66 dp safe zone of 108 dp)
  mipmap-<dpi>/ic_launcher.png            (square icon for Android 7.x)
and points mipmap-anydpi-v26/ic_launcher.xml + mipmap/ic_launcher.xml at them, with the given background
colour in values/ic_launcher_colors.xml. Then bump versionCode and build a new APK (CI does this on push).
Requires Pillow (pip install pillow).
"""
import os, sys
from PIL import Image

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

def main(logo_path, bg, res):
    logo = Image.open(logo_path).convert("RGBA")
    if min(logo.size) < 192:
        sys.exit("logo must be at least 192 x 192 px (1024 x 1024 recommended)")
    for name, f in DENSITIES.items():
        d = os.path.join(res, f"mipmap-{name}"); os.makedirs(d, exist_ok=True)
        # adaptive foreground: 108 dp canvas, logo fitted into the 66 dp safe zone
        canvas = int(round(108 * f)); safe = int(round(66 * f))
        fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        l = logo.copy(); l.thumbnail((safe, safe), Image.LANCZOS)
        fg.paste(l, ((canvas - l.width) // 2, (canvas - l.height) // 2), l)
        fg.save(os.path.join(d, "ic_launcher_foreground.png"))
        # legacy square icon: 48 dp, background colour + logo at 70 %
        size = int(round(48 * f))
        sq = Image.new("RGBA", (size, size), bg)
        l2 = logo.copy(); l2.thumbnail((int(size * .7), int(size * .7)), Image.LANCZOS)
        sq.paste(l2, ((size - l2.width) // 2, (size - l2.height) // 2), l2)
        sq.save(os.path.join(d, "ic_launcher.png"))
    os.makedirs(os.path.join(res, "values"), exist_ok=True)
    with open(os.path.join(res, "values", "ic_launcher_colors.xml"), "w") as fh:
        fh.write(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_bg">{bg}</color>\n</resources>\n')
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_bg" />\n    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
                '    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />\n</adaptive-icon>\n')
    os.makedirs(os.path.join(res, "mipmap-anydpi-v26"), exist_ok=True)
    with open(os.path.join(res, "mipmap-anydpi-v26", "ic_launcher.xml"), "w") as fh: fh.write(adaptive)
    # Android 7.x uses the per-density PNGs; the old vector mipmap/ic_launcher.xml would shadow them
    old = os.path.join(res, "mipmap", "ic_launcher.xml")
    if os.path.exists(old): os.remove(old)
    print("launcher icon written for", ", ".join(DENSITIES))

if __name__ == "__main__":
    if len(sys.argv) != 4: sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3])

#!/usr/bin/env python3
"""
Generates the Play Store feature graphic: 1024x500 PNG.
Design: app's light-theme cream background, logo on the left, brand text on the right.
Colors match `SwipeScreen.kt` (bgPage = 0xFFF6F5F1, textPrim = 0xFF1A1A1A).
Run:  python3 gen_feature_graphic.py
Output: feature-graphic-1024x500.png next to this script.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).parent
LOGO_SRC = HERE.parent.parent / "ios" / "Unrolled" / "Resources" / "Assets.xcassets" / "AppIcon.appiconset" / "Icon-1024.png"
OUT = HERE / "feature-graphic-1024x500.png"

BG = (246, 245, 241)          # #F6F5F1 (app light bg)
FG = (26, 26, 26)             # #1A1A1A (app primary text)
MUTED = (26, 26, 26, 140)     # ~55% alpha on primary

W, H = 1024, 500
LOGO_SIZE = 360
LOGO_RADIUS = 80              # rounded-square corners

canvas = Image.new("RGB", (W, H), BG)

# --- Logo: load, resize, round corners -----------------------------------
logo = Image.open(LOGO_SRC).convert("RGBA").resize((LOGO_SIZE, LOGO_SIZE), Image.LANCZOS)
mask = Image.new("L", (LOGO_SIZE, LOGO_SIZE), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, LOGO_SIZE, LOGO_SIZE), radius=LOGO_RADIUS, fill=255)
rounded = Image.new("RGBA", (LOGO_SIZE, LOGO_SIZE), (0, 0, 0, 0))
rounded.paste(logo, (0, 0), mask)

logo_x = 90
logo_y = (H - LOGO_SIZE) // 2
canvas.paste(rounded, (logo_x, logo_y), rounded)

# --- Text: brand + tagline ------------------------------------------------
draw = ImageDraw.Draw(canvas, "RGBA")

def load_font(candidates, size):
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()

font_brand = load_font([
    "/System/Library/Fonts/SFNS.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/Library/Fonts/Arial Bold.ttf",
], 96)
font_tag = load_font([
    "/System/Library/Fonts/SFNS.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/Library/Fonts/Arial.ttf",
], 32)

text_x = logo_x + LOGO_SIZE + 56
draw.text((text_x, 185), "Unrolled", font=font_brand, fill=FG)
draw.text((text_x + 4, 300), "Swipe your gallery clean.", font=font_tag, fill=MUTED)

canvas.save(OUT, "PNG", optimize=True)
print(f"wrote {OUT} ({OUT.stat().st_size / 1024:.1f} KB)")

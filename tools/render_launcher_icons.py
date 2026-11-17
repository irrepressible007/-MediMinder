"""Renders the legacy (Android 7.x) launcher PNGs from the same geometry as the adaptive icon.

Android 8+ uses res/mipmap-anydpi-v26 (vector, adaptive). Android 7.0/7.1 — still common on
cheap phones in Bangladesh — needs plain bitmaps, which this script draws.

    python tools/render_launcher_icons.py

Keep the shapes here in sync with res/drawable/ic_launcher_{background,foreground}.xml.
Coordinates use the adaptive icon's 108-unit canvas; legacy icons show its central 72 units.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SUPERSAMPLE = 8

TOP_LEFT = (0x34, 0xD3, 0x99)
BOTTOM_RIGHT = (0x04, 0x78, 0x57)
WHITE = (255, 255, 255, 255)
MINT = (0xA7, 0xF3, 0xD0, 255)
MARK_SCALE = 0.88  # the <group> scale in ic_launcher_foreground.xml


def rotate(point, degrees, pivot=(54, 54)):
    a = math.radians(degrees)
    x, y = point[0] - pivot[0], point[1] - pivot[1]
    return (pivot[0] + x * math.cos(a) - y * math.sin(a), pivot[1] + x * math.sin(a) + y * math.cos(a))


def arc_points(cx, cy, r, start_deg, end_deg, steps=48):
    return [(cx + r * math.cos(math.radians(start_deg + (end_deg - start_deg) * i / steps)),
             cy + r * math.sin(math.radians(start_deg + (end_deg - start_deg) * i / steps))) for i in range(steps + 1)]


def gradient(size):
    """Linear gradient from (18,18) to (90,90) on the 108 canvas, like the vector background."""
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            u, v = x / size * 108, y / size * 108
            t = min(1.0, max(0.0, ((u - 18) + (v - 18)) / 144))
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(TOP_LEFT, BOTTOM_RIGHT)) + (255,)
    return img


def draw_mark(draw, scale, offset):
    def tx(points):
        return [(((54 + (x - 54) * MARK_SCALE) - offset) * scale, ((54 + (y - 54) * MARK_SCALE) - offset) * scale)
                for x, y in points]

    # bells: half discs above the face, rotated ±42°
    for angle in (-42, 42):
        dome = arc_points(54, 29.5, 7, 180, 360)
        draw.polygon(tx([rotate(p, angle) for p in dome]), fill=WHITE)
    # face ring: radius 22, stroke 5
    outer, inner = 24.5, 19.5
    draw.ellipse(tx([(54 - outer, 54 - outer), (54 + outer, 54 + outer)]), fill=WHITE)
    draw.ellipse(tx([(54 - inner, 54 - inner), (54 + inner, 54 + inner)]), fill=(0, 0, 0, 0))
    # capsule hand, rotated -45°
    left = [(54, 48.5), (45.5, 48.5)] + arc_points(45.5, 54, 5.5, -90, -270) + [(45.5, 59.5), (54, 59.5)]
    right = [(54, 48.5), (62.5, 48.5)] + arc_points(62.5, 54, 5.5, -90, 90) + [(62.5, 59.5), (54, 59.5)]
    draw.polygon(tx([rotate(p, -45) for p in left]), fill=WHITE)
    draw.polygon(tx([rotate(p, -45) for p in right]), fill=MINT)


def render(size, round_icon):
    big = size * SUPERSAMPLE
    # legacy icons show the central 72 units of the 108 canvas
    scale, offset = big / 72, 18
    background = gradient(big // 4).resize((big, big), Image.BILINEAR)
    mark = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw_mark(ImageDraw.Draw(mark), scale, offset)
    # the ring's hole must show the background, so draw the mark on its own layer first
    icon = Image.alpha_composite(background, mark)

    mask = Image.new("L", (big, big), 0)
    inset = big * 0.04
    if round_icon:
        ImageDraw.Draw(mask).ellipse([inset, inset, big - inset, big - inset], fill=255)
    else:
        ImageDraw.Draw(mask).rounded_rectangle([inset, inset, big - inset, big - inset], radius=big * 0.22, fill=255)
    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    out.paste(icon, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def main():
    for density, size in SIZES.items():
        folder = RES / f"mipmap-{density}"
        for name, round_icon in (("ic_launcher", False), ("ic_launcher_round", True)):
            for old in folder.glob(f"{name}.*"):
                old.unlink()
            render(size, round_icon).save(folder / f"{name}.png", optimize=True)
    print("rendered", ", ".join(f"{d} {s}px" for d, s in SIZES.items()))


if __name__ == "__main__":
    main()

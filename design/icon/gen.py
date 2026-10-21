#!/usr/bin/env python3
"""番茄芽图标设计草稿：生成 SVG 变体 + 渲染预览。"""
import math, os, io
import cairosvg
from PIL import Image, ImageDraw

OUT = os.path.dirname(os.path.abspath(__file__))
S = 108.0  # adaptive icon viewport


def pt(x, y):
    return f"{x:.2f},{y:.2f}"


def leaf(base, tip, w, bulge=0.62, skew=0.30):
    """从 base 到 tip 的叶片路径，w = 半宽。"""
    bx, by = base
    tx, ty = tip
    vx, vy = tx - bx, ty - by
    L = math.hypot(vx, vy)
    nx, ny = -vy / L, vx / L  # 法线
    def P(f, off):
        return pt(bx + vx * f + nx * off, by + vy * f + ny * off)
    c1 = P(0.18, w * 0.95)
    c2 = P(0.70, w * 0.95 * bulge)
    c3 = P(0.72, -w * skew)
    c4 = P(0.26, -w * 0.45)
    return f"M{pt(bx,by)} C{c1} {c2} {pt(tx,ty)} C{c3} {c4} {pt(bx,by)} Z"


def calyx(cx, cy, r, sepals):
    """番茄萼片：若干片放射状小尖叶。"""
    out = []
    for ang, ln, wd in sepals:
        a = math.radians(ang)
        tip = (cx + math.cos(a) * ln * r / 9.0, cy + math.sin(a) * ln * r / 9.0)
        out.append(leaf((cx, cy), tip, wd * r / 9.0, bulge=0.5, skew=0.12))
    return " ".join(out)


TOMATO = ("M 33.40,58.60 "
          "C 35.60,50.40 43.00,45.70 48.60,46.55 "
          "C 50.60,47.55 52.10,48.40 54.00,48.40 "
          "C 55.90,48.40 57.40,47.55 59.40,46.55 "
          "C 65.00,45.70 72.40,50.40 74.60,58.60 "
          "C 76.80,66.60 73.20,74.60 66.60,79.30 "
          "C 62.30,82.60 58.00,84.10 54.00,84.10 "
          "C 50.00,84.10 45.70,82.60 41.40,79.30 "
          "C 34.80,74.60 31.20,66.60 33.40,58.60 Z")


def tomato_shadow():
    return ('<ellipse cx="54" cy="82.5" rx="21.5" ry="4.2" fill="#3A2320" opacity="0.10"/>')


def sprout(stem_color="url(#leaf)", leaf_color="url(#leaf)", sw=5.4):
    stem = ('<path d="M 54,49.5 C 53.2,43.0 51.0,38.0 51.6,31.0" '
            f'fill="none" stroke="{stem_color}" stroke-width="{sw}" stroke-linecap="round"/>')
    right = leaf((52.2, 35.6), (65.4, 24.6), 5.0)
    left = leaf((52.4, 38.8), (39.0, 29.4), 4.6)
    bud = leaf((51.8, 31.6), (51.0, 24.2), 2.7)
    return (stem
            + f'<path d="{left}" fill="{leaf_color}"/>'
            + f'<path d="{right}" fill="{leaf_color}"/>'
            + f'<path d="{bud}" fill="{leaf_color}"/>')


def clover_dots():
    return ""


def svg(defs, bg, fg, w=S, h=S, extra_bg=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}">\n<defs>\n{defs}\n</defs>\n'
            f'{bg}\n{extra_bg}\n{fg}\n</svg>')


def variant_a():
    defs = '''
    <linearGradient id="bg" x1="0" y1="0" x2="0.25" y2="1">
      <stop offset="0" stop-color="#FFF9F3"/>
      <stop offset="0.55" stop-color="#FFEFE3"/>
      <stop offset="1" stop-color="#FFDFCB"/>
    </linearGradient>
    <radialGradient id="fruit" cx="0.34" cy="0.24" r="0.95">
      <stop offset="0" stop-color="#FF9176"/>
      <stop offset="0.55" stop-color="#FA6E60"/>
      <stop offset="1" stop-color="#DF4340"/>
    </radialGradient>
    <linearGradient id="leaf" x1="0" y1="1" x2="0.4" y2="0">
      <stop offset="0" stop-color="#3C9A55"/>
      <stop offset="1" stop-color="#7FD086"/>
    </linearGradient>
    <linearGradient id="sepal" x1="0" y1="1" x2="0.3" y2="0">
      <stop offset="0" stop-color="#2F8B4C"/>
      <stop offset="1" stop-color="#5FBB6C"/>
    </linearGradient>'''
    bg = '<rect width="108" height="108" fill="url(#bg)"/>'
    fg = (tomato_shadow()
          + f'<path d="{TOMATO}" fill="url(#fruit)"/>'
          + '<ellipse cx="44.6" cy="57.2" rx="7.4" ry="4.0" fill="#FFF4E8" opacity="0.55" '
            'transform="rotate(-28 44.6 57.2)"/>'
          + f'<path d="{calyx(54,48.6,9.6,[(196,9.0,3.1),(238,8.4,2.9),(302,8.4,2.9),(344,9.0,3.1)])}" fill="url(#sepal)"/>'
          + sprout())
    return svg(defs, bg, fg)


def variant_b():
    defs = '''
    <linearGradient id="bg" x1="0" y1="0" x2="0.3" y2="1">
      <stop offset="0" stop-color="#FF8B7B"/>
      <stop offset="0.55" stop-color="#F9625C"/>
      <stop offset="1" stop-color="#D8403E"/>
    </linearGradient>
    <linearGradient id="fruit" x1="0" y1="0" x2="0.2" y2="1">
      <stop offset="0" stop-color="#FFFCF7"/>
      <stop offset="0.6" stop-color="#FFF1E2"/>
      <stop offset="1" stop-color="#FBD9C6"/>
    </linearGradient>
    <linearGradient id="leaf" x1="0" y1="1" x2="0.4" y2="0">
      <stop offset="0" stop-color="#EBF9DF"/>
      <stop offset="1" stop-color="#FFFFFF"/>
    </linearGradient>
    <linearGradient id="sepal" x1="0" y1="1" x2="0.3" y2="0">
      <stop offset="0" stop-color="#F3FBEA"/>
      <stop offset="1" stop-color="#FFFFFF"/>
    </linearGradient>'''
    bg = '<rect width="108" height="108" fill="url(#bg)"/>'
    fg = (tomato_shadow()
          + f'<path d="{TOMATO}" fill="url(#fruit)"/>'
          + '<ellipse cx="44.6" cy="57.2" rx="7.0" ry="3.6" fill="#FFFFFF" opacity="0.85" '
            'transform="rotate(-28 44.6 57.2)"/>'
          + f'<path d="{calyx(54,48.6,9.6,[(196,9.0,3.1),(238,8.4,2.9),(302,8.4,2.9),(344,9.0,3.1)])}" fill="url(#sepal)"/>'
          + sprout())
    return svg(defs, bg, fg)


def variant_c():
    """芽为主角：番茄缩小下移，茎叶加大。"""
    defs = '''
    <linearGradient id="bg" x1="0" y1="0" x2="0.2" y2="1">
      <stop offset="0" stop-color="#FFF8F0"/>
      <stop offset="1" stop-color="#FFE6D6"/>
    </linearGradient>
    <radialGradient id="fruit" cx="0.34" cy="0.22" r="0.95">
      <stop offset="0" stop-color="#FF9176"/>
      <stop offset="0.55" stop-color="#FA6E60"/>
      <stop offset="1" stop-color="#DF4340"/>
    </radialGradient>
    <linearGradient id="leaf" x1="0" y1="1" x2="0.4" y2="0">
      <stop offset="0" stop-color="#369151"/>
      <stop offset="1" stop-color="#86D78C"/>
    </linearGradient>
    <linearGradient id="sepal" x1="0" y1="1" x2="0.3" y2="0">
      <stop offset="0" stop-color="#2F8B4C"/>
      <stop offset="1" stop-color="#5FBB6C"/>
    </linearGradient>'''
    bg = '<rect width="108" height="108" fill="url(#bg)"/>'
    body = ('<g transform="translate(54,70) scale(0.86) translate(-54,-70)">'
            + tomato_shadow()
            + f'<path d="{TOMATO}" fill="url(#fruit)"/>'
            + '<ellipse cx="44.6" cy="57.2" rx="7.2" ry="3.8" fill="#FFF4E8" opacity="0.55" '
              'transform="rotate(-28 44.6 57.2)"/>'
            + f'<path d="{calyx(54,48.6,9.6,[(196,9.0,3.1),(238,8.4,2.9),(302,8.4,2.9),(344,9.0,3.1)])}" fill="url(#sepal)"/>'
            + '</g>')
    spr = ('<g transform="translate(1.5,0)">'
           + sprout(sw=5.8) + '</g>')
    return svg(defs, bg, body + spr)


def render(svg_text, path, size=1024, bg=None):
    cairosvg.svg2png(bytestring=svg_text.encode(), write_to=path,
                     output_width=size, output_height=size,
                     background_color=bg)


def mask_preview(svg_text, size, mask="circle"):
    """模拟自适应图标：裁中央 72/108，再套蒙版。"""
    tmp = io.BytesIO()
    cairosvg.svg2png(bytestring=svg_text.encode(), write_to=tmp,
                     output_width=size, output_height=size)
    tmp.seek(0)
    im = Image.open(tmp).convert("RGBA")
    crop = int(size * 18 / 108)
    im = im.crop((crop, crop, size - crop, size - crop))
    ss = 4
    m = Image.new("L", (im.width * ss, im.height * ss), 0)
    d = ImageDraw.Draw(m)
    if mask == "circle":
        d.ellipse((0, 0, m.width - 1, m.height - 1), fill=255)
    elif mask == "squircle":
        d.rounded_rectangle((0, 0, m.width - 1, m.height - 1), radius=int(m.width * 0.28), fill=255)
    else:
        d.rectangle((0, 0, m.width - 1, m.height - 1), fill=255)
    m = m.resize(im.size, Image.LANCZOS)
    im.putalpha(m)
    return im


if __name__ == "__main__":
    variants = {"A": variant_a(), "B": variant_b(), "C": variant_c()}
    for k, v in variants.items():
        with open(os.path.join(OUT, f"variant_{k}.svg"), "w") as f:
            f.write(v)
        render(v, os.path.join(OUT, f"variant_{k}.png"), 1024)
    print("ok")

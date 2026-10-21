#!/usr/bin/env python3
"""番茄芽图标 v2：更饱满的叶片、干净的 Y 形芽、更克制的阴影。"""
import math, os, io
import cairosvg
from PIL import Image, ImageDraw

OUT = os.path.dirname(os.path.abspath(__file__))
S = 108.0


def f(x, y):
    return f"{x:.2f},{y:.2f}"


def leaf(base, tip, w, back=0.35, front=0.95):
    """杏仁形叶片：base→tip，w 为半宽；front/back 控制两侧饱满度。"""
    bx, by = base
    tx, ty = tip
    vx, vy = tx - bx, ty - by
    L = math.hypot(vx, vy)
    ux, uy = vx / L, vy / L
    nx, ny = -uy, ux

    def P(t, s):
        return f(bx + ux * L * t + nx * s, by + uy * L * t + ny * s)

    return (f"M{f(bx,by)}"
            f" C{P(0.12, w*0.75)} {P(0.52, w*front)} {f(tx,ty)}"
            f" C{P(0.60, -w*back)} {P(0.18, -w*back*0.55)} {f(bx,by)} Z")


def sepal(cx, cy, ang, ln, wd):
    a = math.radians(ang)
    tip = (cx + math.cos(a) * ln, cy + math.sin(a) * ln)
    return leaf((cx, cy), tip, wd, back=0.5, front=0.9)


TOMATO = ('M 54.00,49.60 '
          'C 51.20,47.30 47.20,45.90 44.30,47.00 '
          'C 35.60,49.10 29.00,55.00 28.30,62.60 '
          'C 27.60,70.30 32.40,77.20 40.20,81.20 '
          'C 44.50,83.40 49.00,84.40 54.00,84.40 '
          'C 59.00,84.40 63.50,83.40 67.80,81.20 '
          'C 75.60,77.20 80.40,70.30 79.70,62.60 '
          'C 79.00,55.00 72.40,49.10 63.70,47.00 '
          'C 60.80,45.90 56.80,47.30 54.00,49.60 Z')


def tomato(color="url(#fruit)", hi=0.5):
    g = [f'<path d="{TOMATO}" fill="{color}"/>']
    g.append('<ellipse cx="43.4" cy="57.4" rx="6.6" ry="3.5" fill="#FFF6EA" '
             f'opacity="{hi}" transform="rotate(-31 43.4 57.4)"/>')
    g.append('<ellipse cx="38.6" cy="70.4" rx="3.2" ry="2.0" fill="#FF9E8C" opacity="0.35" '
             'transform="rotate(-20 38.6 70.4)"/>')
    return "".join(g)


def calyx(color="url(#sepal)"):
    return "".join(f'<path d="{sepal(54,49.4,a,l,w)}" fill="{color}"/>'
                   for a, l, w in [(196, 11.6, 3.5), (238, 10.2, 3.2),
                                   (302, 10.2, 3.2), (344, 11.6, 3.5)])


def sprout(stem="#4E9E5B", lf="url(#leaf)", lf2="url(#leaf2)"):
    """Y 形芽：一根茎、两片大叶、一点嫩尖。"""
    parts = []
    # 茎：下粗上细的填充形状
    parts.append(f'<path d="M 50.9,52.0 C 50.9,44.0 51.6,37.0 51.9,31.4 '
                 f'L 55.2,31.4 C 55.0,37.0 56.0,44.0 56.4,52.0 Z" fill="{stem}"/>')
    parts.append(f'<path d="M 51.95,31.4 C 51.6,27.8 52.2,24.8 53.4,22.6 '
                 f'C 54.6,24.9 55.4,27.9 55.2,31.4 Z" fill="{lf2}"/>')
    parts.append(f'<path d="{leaf((52.4,37.4),(38.2,29.0),5.6)}" fill="{lf}"/>')
    parts.append(f'<path d="{leaf((54.4,34.4),(68.8,24.6),5.2)}" fill="{lf}"/>')
    return "".join(parts)


def defs(leaf_from="#7FD086", leaf_to="#3B9354", bg_from="#FFF9F4", bg_to="#FFE3D0",
         fruit=("#FF9481", "#F96C60", "#D94040")):
    return f'''
    <linearGradient id="bg" x1="0.1" y1="0" x2="0.35" y2="1">
      <stop offset="0" stop-color="{bg_from}"/>
      <stop offset="0.55" stop-color="#FFF1E6"/>
      <stop offset="1" stop-color="{bg_to}"/>
    </linearGradient>
    <radialGradient id="fruit" cx="0.32" cy="0.22" r="1.0">
      <stop offset="0" stop-color="{fruit[0]}"/>
      <stop offset="0.55" stop-color="{fruit[1]}"/>
      <stop offset="1" stop-color="{fruit[2]}"/>
    </radialGradient>
    <linearGradient id="leaf" x1="0.75" y1="1" x2="0.2" y2="0">
      <stop offset="0" stop-color="{leaf_to}"/>
      <stop offset="1" stop-color="{leaf_from}"/>
    </linearGradient>
    <linearGradient id="leaf2" x1="0.5" y1="1" x2="0.5" y2="0">
      <stop offset="0" stop-color="#4EA35F"/>
      <stop offset="1" stop-color="#8AD890"/>
    </linearGradient>
    <linearGradient id="sepal" x1="0.5" y1="1" x2="0.5" y2="0">
      <stop offset="0" stop-color="#2F8B4C"/>
      <stop offset="1" stop-color="#63BE70"/>
    </linearGradient>'''


def svg(body, defs_text):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" '
            f'viewBox="0 0 108 108">\n<defs>{defs_text}</defs>\n'
            f'<rect width="108" height="108" fill="url(#bg)"/>\n{body}\n</svg>')


def v_a2():
    body = tomato() + calyx() + sprout()
    return svg(body, defs())


def v_a3():
    """番茄略大、芽更高，整体重心居中。"""
    body = ('<g transform="translate(0,1.4)">' + tomato() + calyx()
            + '<g transform="translate(-0.6,-1.2)">' + sprout() + '</g></g>')
    return svg(body, defs())


def v_flat():
    """平面贴纸风：纯色 + 稍深描边感（无描边，靠明暗两块面）。"""
    d = '''
    <linearGradient id="bg" x1="0" y1="0" x2="0.2" y2="1">
      <stop offset="0" stop-color="#FFFDF8"/>
      <stop offset="1" stop-color="#FFE8D5"/>
    </linearGradient>
    <linearGradient id="fruit" x1="0.2" y1="0" x2="0.8" y2="1">
      <stop offset="0" stop-color="#FF8A76"/>
      <stop offset="1" stop-color="#E5504C"/>
    </linearGradient>
    <linearGradient id="leaf" x1="0.7" y1="1" x2="0.25" y2="0">
      <stop offset="0" stop-color="#3E9B58"/>
      <stop offset="1" stop-color="#7ED184"/>
    </linearGradient>
    <linearGradient id="leaf2" x1="0.5" y1="1" x2="0.5" y2="0">
      <stop offset="0" stop-color="#4EA35F"/>
      <stop offset="1" stop-color="#8AD890"/>
    </linearGradient>
    <linearGradient id="sepal" x1="0.5" y1="1" x2="0.5" y2="0">
      <stop offset="0" stop-color="#2F8B4C"/>
      <stop offset="1" stop-color="#63BE70"/>
    </linearGradient>'''
    body = tomato(hi=0.55) + calyx() + sprout()
    return svg(body, d)


def v_dark():
    """深色版（供深色壁纸/主题预览，不一定要用）。"""
    d = '''
    <linearGradient id="bg" x1="0" y1="0" x2="0.3" y2="1">
      <stop offset="0" stop-color="#3A2B28"/>
      <stop offset="1" stop-color="#241A18"/>
    </linearGradient>
    <radialGradient id="fruit" cx="0.32" cy="0.22" r="1.0">
      <stop offset="0" stop-color="#FF9C86"/>
      <stop offset="0.55" stop-color="#FA6E60"/>
      <stop offset="1" stop-color="#CC3B3B"/>
    </radialGradient>
    <linearGradient id="leaf" x1="0.75" y1="1" x2="0.2" y2="0">
      <stop offset="0" stop-color="#3F9B57"/>
      <stop offset="1" stop-color="#8FDD95"/>
    </linearGradient>
    <linearGradient id="leaf2" x1="0.5" y1="1" x2="0.5" y2="0">
      <stop offset="0" stop-color="#4EA35F"/>
      <stop offset="1" stop-color="#8AD890"/>
    </linearGradient>
    <linearGradient id="sepal" x1="0.5" y1="1" x2="0.5" y2="0">
      <stop offset="0" stop-color="#2F8B4C"/>
      <stop offset="1" stop-color="#63BE70"/>
    </linearGradient>'''
    return svg(tomato() + calyx() + sprout(), d)


def render(text, path, size=1024, background=None):
    cairosvg.svg2png(bytestring=text.encode(), write_to=path,
                     output_width=size, output_height=size,
                     background_color=background)


def masked(text, size, mask="circle"):
    tmp = io.BytesIO()
    cairosvg.svg2png(bytestring=text.encode(), write_to=tmp,
                     output_width=size, output_height=size)
    tmp.seek(0)
    im = Image.open(tmp).convert("RGBA")
    c = int(size * 18 / 108)
    im = im.crop((c, c, size - c, size - c))
    ss = 4
    m = Image.new("L", (im.width * ss, im.height * ss), 0)
    d = ImageDraw.Draw(m)
    if mask == "circle":
        d.ellipse((0, 0, m.width - 1, m.height - 1), fill=255)
    elif mask == "squircle":
        d.rounded_rectangle((0, 0, m.width - 1, m.height - 1),
                            radius=int(m.width * 0.30), fill=255)
    else:
        d.rectangle((0, 0, m.width - 1, m.height - 1), fill=255)
    m = m.resize(im.size, Image.LANCZOS)
    im.putalpha(m)
    return im


if __name__ == "__main__":
    vs = {"A2": v_a2(), "A3": v_a3(), "FLAT": v_flat(), "DARK": v_dark()}
    for k, v in vs.items():
        open(os.path.join(OUT, f"v_{k}.svg"), "w").write(v)
        render(v, os.path.join(OUT, f"v_{k}.png"), 1024)

    # 联系方式：圆角方块大图 / 圆形 / 48px 实机尺寸
    order = ["A2", "A3", "FLAT", "DARK"]
    W = 360
    sheet = Image.new("RGBA", (W * 4, 470), (245, 243, 240, 255))
    for i, k in enumerate(order):
        big = masked(vs[k], 512, "squircle").resize((300, 300), Image.LANCZOS)
        sheet.alpha_composite(big, (i * W + 30, 30))
        for j, s in enumerate((96, 72, 48, 32)):
            small = masked(vs[k], 256, "circle").resize((s, s), Image.LANCZOS)
            sheet.alpha_composite(small, (i * W + 30 + j * (s + 12) + (0 if j == 0 else 0),
                                          360 - 0))
        d = ImageDraw.Draw(sheet)
        d.text((i * W + 30, 450), k, fill=(60, 50, 45, 255))
    sheet.convert("RGB").save(os.path.join(OUT, "sheet.png"))
    print("ok")

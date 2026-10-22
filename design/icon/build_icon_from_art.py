#!/usr/bin/env python3
"""番茄芽图标 v8 —— 纸雕插画（栅格）版。

源图：design/icon/source/sprout_papercut.png（1064×1080 纸雕风插画）
产出：
  app/src/main/res/drawable-<dpi>/ic_launcher_foreground.png   自适应前景（108dp 满幅，含米色底）
  app/src/main/res/drawable-<dpi>/ic_launcher_monochrome.png   Android 13+ 主题图标（剪影）
  app/src/main/res/drawable/ic_launcher_background.xml         纯色底（shape）
  design/icon/icon_v8_*.png、preview_sheet.png                 预览

几何约定（Android 自适应图标）：
  画布 108dp；启动器只显示中央 72dp（18..90dp），再套形状遮罩，安全圆 Ø66dp。
  1) 把主体（含卡片）的最小外接圆半径压到 CONTENT_R_DP，圆形遮罩下不会被切；
  2) 插画四周用「边缘复制」补到整块画布 → 图像边界永远落在可见区之外，
     任何遮罩下都看不到插画矩形的硬边/接缝。

用法：python3 build_icon_from_art.py
"""
import os

import cv2
import numpy as np
from PIL import Image, ImageChops, ImageDraw

OUT = os.path.dirname(os.path.abspath(__file__))
APP_RES = os.path.abspath(os.path.join(OUT, "..", "..", "app", "src", "main", "res"))
SRC = os.path.join(OUT, "source", "sprout_papercut.png")

CANVAS = 108.0          # 自适应图标画布（dp）
VISIBLE = 72.0          # 启动器可见区（dp）
CONTENT_R_DP = 34.6     # 主体外接圆半径（dp）；36dp 即圆形遮罩边界
BG = (247, 243, 226)    # 底色：源图米色背景的中位色
FADE_DP = 2.0           # 画布最外圈淡出（个别启动器放大时避免硬边）
SS = 8                  # 超采样：864px 画布 → 各密度降采样

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


# ------------------------------------------------------------------ 图像工具
def fill_holes(mask):
    """填上被 mask 完全包住的洞（从左上角外部泛洪）。"""
    m = mask.astype(np.uint8).copy()
    ff = np.zeros((m.shape[0] + 2, m.shape[1] + 2), np.uint8)
    cv2.floodFill(m, ff, (0, 0), 1)
    return np.clip(mask.astype(np.uint8) + (m == 0).astype(np.uint8), 0, 1).astype(np.uint8)


def largest_components(mask, n_keep=6):
    n, lab, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
    keep = np.argsort(-stats[1:, cv2.CC_STAT_AREA])[:n_keep] + 1
    return np.isin(lab, keep).astype(np.uint8)


def load_art():
    art = np.asarray(Image.open(SRC).convert("RGB"))
    ring = np.concatenate([art[:8].reshape(-1, 3), art[-8:].reshape(-1, 3),
                           art[:, :8].reshape(-1, 3), art[:, -8:].reshape(-1, 3)])
    return art, tuple(int(v) for v in np.median(ring, axis=0))


def color_masks(art, bg):
    """主体掩膜（与底色差异大）与卡片白底掩膜。"""
    diff = np.abs(art.astype(np.float32) - np.array(bg, np.float32)).max(axis=2)
    strong = cv2.morphologyEx((diff > 40).astype(np.uint8), cv2.MORPH_CLOSE,
                              np.ones((5, 5), np.uint8))
    b, g, r = (art[..., 2].astype(np.int16), art[..., 1].astype(np.int16),
               art[..., 0].astype(np.int16))
    card = cv2.morphologyEx(((b > 236) & (r > 240) & (g > 240)).astype(np.uint8),
                            cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    return largest_components(strong), largest_components(card, 1)


def content_circle(art, bg):
    """主体（含白色卡片）的最小外接圆 → (cx, cy, r)。"""
    strong, card = color_masks(art, bg)
    m = cv2.morphologyEx(np.clip(strong + card, 0, 1).astype(np.uint8), cv2.MORPH_CLOSE,
                         np.ones((7, 7), np.uint8))
    m = largest_components(m)
    cnts, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    pts = np.concatenate([c.reshape(-1, 2) for c in cnts])
    (cx, cy), radius = cv2.monotonic_enclosing_circle = cv2.minEnclosingCircle(pts)
    return float(cx), float(cy), float(radius)


def note_content_masks(art, bg):
    """主题图标用：实心剪影 + 卡片内要挖空的清单标记。"""
    strong, card = color_masks(art, bg)
    union = np.clip(strong | card, 0, 1).astype(np.uint8)
    union = cv2.morphologyEx(union, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))  # 收掉发丝级缝隙
    silhouette = fill_holes(union)
    card_body = cv2.dilate(card, np.ones((5, 5), np.uint8), iterations=2)  # 排除卡片自身描边
    marks = (fill_holes(card) & (1 - card_body)).astype(np.uint8)
    marks = cv2.morphologyEx(marks, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    marks = cv2.dilate(marks, np.ones((3, 3), np.uint8), iterations=2)
    return silhouette.astype(np.uint8), marks.astype(np.uint8)


def pad_to_cover(img, need_px):
    """四周边缘复制扩边（BORDER_REPLICATE）：米色底/柔和投影自然延续，不产生硬边。"""
    h, w = img.shape[:2]
    px, py = int(need_px), int(need_px)
    return cv2.copyMakeBorder(img, py, py, px, px, cv2.BORDER_REPLICATE)


def resize_into(canvas, src, k_px, x0_px, y0_px):
    """把 src 按 k_px（画布像素/源像素）缩放后贴到 canvas 的 (x0,y0)。"""
    tw = max(1, int(round(src.shape[1] * k_px)))
    th = max(1, int(round(src.shape[0] * k_px)))
    interp = cv2.INTER_AREA if tw <= src.shape[1] else cv2.INTER_LANCZOS4
    im = cv2.resize(src, (tw, th), interpolation=interp)
    x0, y0 = int(round(x0_px)), int(round(y0_px))
    sx0, sy0 = max(0, -x0), max(0, -y0)
    dx0, dy0 = max(0, x0), max(0, y0)
    cw, ch = min(canvas.shape[1] - dx0, tw - sx0), min(canvas.shape[0] - dy0, th - sy0)
    if cw > 0 and ch > 0:
        canvas[dy0:dy0 + ch, dx0:dx0 + cw] = im[sy0:sy0 + ch, sx0:sx0 + cw]
    return canvas


def fade_alpha(size_px):
    y, x = np.mgrid[0:size_px, 0:size_px].astype(np.float32) / SS
    d = np.minimum(np.minimum(x, CANVAS - x), np.minimum(y, CANVAS - y))
    return (np.clip(d / FADE_DP, 0.0, 1.0) * 255).astype(np.uint8)


# ------------------------------------------------------------------ 预览
def shape_alpha(size, mode="squircle", radius_frac=0.28):
    ss = 4
    m = Image.new("L", (size * ss, size * ss), 0)
    d = ImageDraw.Draw(m)
    box = (0, 0, size * ss - 1, size * ss - 1)
    if mode == "circle":
        d.ellipse(box, fill=255)
    elif mode == "square":
        d.rectangle(box, fill=255)
    else:
        d.rounded_rectangle(box, radius=int(box[2] * radius_frac), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def launcher_view(img, size, mode="squircle", radius_frac=0.28):
    """按启动器的方式出图：先裁中央 72dp 可见区，再套形状遮罩。"""
    w, h = img.size
    c = int(round(w * VISIBLE / CANVAS / 2))
    crop = img.crop((int(w / 2 - c), int(h / 2 - c), int(w / 2 + c), int(h / 2 + c)))
    im = crop.resize((size, size), Image.LANCZOS).convert("RGBA")
    im.putalpha(ImageChops.multiply(im.getchannel("A"), shape_alpha(size, mode, radius_frac)))
    return im


def write_previews(full, mono):
    full.save(os.path.join(OUT, "icon_1024.png"))
    for mode, name in (("squircle", "icon_squircle_1024.png"),
                       ("circle", "icon_circle_1024.png"),
                       ("square", "icon_square_1024.png")):
        launcher_view(full, 1024, mode).save(os.path.join(OUT, name))
    launcher_view(full, 512, "square").convert("RGB").save(os.path.join(OUT, "play_store_512.png"))

    sheet = Image.new("RGB", (1250, 620), "#FFFFFF")
    big = launcher_view(full, 420, "squircle")
    sheet.paste(big, (26, 26), big)
    for size, x, mode in ((300, 500, "circle"), (300, 840, "square")):
        im = launcher_view(full, size, mode)
        sheet.paste(im, (x, 86), im)

    # 主题图标（模拟系统着色：浅绿底 + 单色剪影）
    tile = Image.new("RGB", (420, 122), "#DCE8D0")
    m = launcher_view(mono, 104, "square")
    tile.paste(m, (10, 9), m)
    sheet.paste(tile, (26, 478))

    x = 500
    for sz in (96, 72, 48, 36, 24):
        im = launcher_view(full, sz, "squircle")
        sheet.paste(im, (x, 548 - sz), im)
        x += sz + 20
    sheet.save(os.path.join(OUT, "preview_sheet.png"))


# ------------------------------------------------------------------ 主流程
def build():
    art, bg = load_art()
    print("source", art.shape, "bg", bg)
    cx, cy, r = content_circle(art, bg)
    k = CONTENT_R_DP / r                      # dp / 源像素
    print(f"content circle  cx={cx:.1f} cy={cy:.1f} r={r:.1f}px  ->  {k:.5f} dp/px")

    # 扩边到整块画布（保证插画边界在可见区 18..90dp 之外）
    half = CANVAS / 2 / k
    need = max(half - cx, (cx - art.shape[1]) + half,      # 左 / 右
               half - cy, (cy - art.shape[0]) + half)      # 上 / 下
    art_pad = pad_to_cover(art, np.ceil(need) + 2)
    pad_px = np.ceil(need) + 2
    ccx, ccy = cx + pad_px, cy + pad_px
    w_dp, h_dp = art_pad.shape[1] * k, art_pad.shape[0] * k
    x0_dp, y0_dp = CANVAS / 2 - ccx * k, CANVAS / 2 - ccy * k
    print(f"art {w_dp:.1f}×{h_dp:.1f}dp at ({x0_dp:.1f},{y0_dp:.1f})"
          f"..({x0_dp + w_dp:.1f},{y0_dp + h_dp:.1f})   (需覆盖 0..108dp)")
    assert x0_dp <= 0 and y0_dp <= 0 and x0_dp + w_dp >= CANVAS and y0_dp + h_dp >= CANVAS, \
        "扩边不足：插画没盖满画布，会出现硬边"

    size_px = int(CANVAS * SS)
    k_px = k * SS
    canvas = np.zeros((size_px, size_px, 3), np.uint8)
    canvas[:] = BG
    canvas = resize_into(canvas, art_pad, k_px, x0_dp * SS, y0_dp * SS)
    fade = fade_alpha(size_px)

    # 主题图标剪影：实心主体 − 卡片内标记
    sil, marks = note_content_masks(art, bg)
    ax_dp, ay_dp = x0_dp + pad_px * k, y0_dp + pad_px * k     # 未扩边插画在画布上的位置
    sil_c = resize_into(np.zeros((size_px, size_px), np.uint8),
                        (sil * 255).astype(np.uint8), k_px, ax_dp * SS, ay_dp * SS)
    mrk_c = resize_into(np.zeros((size_px, size_px), np.uint8),
                        (marks * 255).astype(np.uint8), k_px, ax_dp * SS, ay_dp * SS)
    alpha = np.clip(sil_c.astype(np.int16) - mrk_c.astype(np.int16), 0, 255).astype(np.uint8)
    alpha = cv2.GaussianBlur(alpha, (3, 3), 0)
    alpha = (alpha.astype(np.float32) * fade / 255.0).astype(np.uint8)
    mono = np.zeros((size_px, size_px, 4), np.uint8)
    mono[..., 3] = alpha
    mono_img = Image.fromarray(mono, "RGBA")

    total = 0
    for name, scale in DENSITIES.items():
        n = int(round(CANVAS * scale))
        d = os.path.join(APP_RES, f"drawable-{name}")
        os.makedirs(d, exist_ok=True)
        fg = Image.fromarray(canvas).resize((n, n), Image.LANCZOS).convert("RGBA")
        fg.putalpha(Image.fromarray(fade).resize((n, n), Image.LANCZOS))
        p1 = os.path.join(d, "ic_launcher_foreground.png")
        fg.save(p1, optimize=True)
        p2 = os.path.join(d, "ic_launcher_monochrome.png")
        mono_img.resize((n, n), Image.LANCZOS).save(p2, optimize=True)
        total += os.path.getsize(p1) + os.path.getsize(p2)
        print(f"  {name:7s} {n:3d}px  fg {os.path.getsize(p1) // 1024:4d}KB  "
              f"mono {os.path.getsize(p2) // 1024:3d}KB")
    print(f"合计 {total / 1024:.0f} KB")

    full = Image.fromarray(canvas).convert("RGBA")
    full.putalpha(Image.fromarray(fade))
    write_previews(full, mono_img)


if __name__ == "__main__":
    build()
    print("icon v8 ok ->", OUT)

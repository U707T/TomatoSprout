#!/usr/bin/env python3
"""番茄芽图标 —— 纸雕插画（栅格）版。

源图：design/icon/source/sprout_papercut.png（1064×1080 纸雕风插画：米色纸底 + 番茄芽卡片）

产物：
  app/src/main/res/drawable-<dpi>/ic_launcher_foreground.png   自适应前景（透明底 + 居中标记）
  app/src/main/res/drawable-<dpi>/ic_launcher_monochrome.png   Android 13+ 主题图标（剪影）
  app/src/main/res/drawable/ic_launcher_background.xml         纯色底（shape，米色纸色）
  design/icon/icon_*.png、preview_sheet.png                     预览

注：minSdk = 26，所有设备都走自适应图标，**不生成** mipmap-<dpi> 的传统 PNG
   （那是死资源；桌面图标由自适应层的 41% 口径决定）。

几何约定（Android 自适应图标）
------------------------------
画布 108dp；启动器只显示中央 72dp（18..90dp），再套形状遮罩，安全圆 Ø66dp。

**标记占图标 41%**（2026-11-01 与 MetaRikka 统一的实测口径）
------------------------------------------------------------
标记 = 番茄芽 + 卡片 + 投影，不含米色纸底。
  自适应前景：标记 bbox 宽度 ÷ 可见区 72dp = 41%
  传统图标　：标记 bbox 宽度 ÷ 图标边长   = 41%
与官方推荐的 50–55% 不同 —— 那个区间在真实桌面上偏挤。

⚠️ 这里量的是**像素 bbox**，不是参数：插画自带留白和柔和投影，
   把整张图缩 0.41 并不等于 bbox 占 0.41。改完必须跑 --report 看实测。

用法：
    python3 build_icon_from_art.py            # 出全部资源
    python3 build_icon_from_art.py --report   # 只打印实测比例，不写文件
"""
import argparse
import os

import cv2
import numpy as np
from PIL import Image, ImageChops, ImageDraw

OUT = os.path.dirname(os.path.abspath(__file__))
APP_RES = os.path.abspath(os.path.join(OUT, "..", "..", "app", "src", "main", "res"))
SRC = os.path.join(OUT, "source", "sprout_papercut.png")

CANVAS = 108.0          # 自适应图标画布（dp）
VISIBLE = 72.0          # 启动器可见区（dp）
MARK_W_RATIO = float(os.environ.get("MARK_W_RATIO", "0.41"))
BG = (247, 243, 226)    # 底色：源图米色背景（#F7F3E2，与 drawable/ic_launcher_background.xml 一致）
SS = 8                  # 超采样：864px 画布 → 各密度降采样
ALPHA_THR = 8           # 量 bbox 时的 alpha 阈值（降采样会留极淡的边）

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


def motif_mask(art, bg):
    """主体（番茄芽 + 卡片 + 投影）的实心 mask。

    纸雕插画的卡片内部是接近底色的米白，只看「与底色的差值」会把卡片中间挖空，
    所以先闭运算 + 填洞，再取最大的几块（丢掉零散噪点）。
    """
    diff = np.abs(art.astype(np.float32) - np.array(bg, np.float32)).max(axis=2)
    hard = (diff > 24).astype(np.uint8)
    hard = cv2.morphologyEx(hard, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    solid = fill_holes(hard)
    return largest_components(solid)


def crop_motif(art, mask):
    """裁到 mask 的 bbox，返回 (RGB 图, 羽化 alpha, bbox)。"""
    ys, xs = np.nonzero(mask)
    x0, x1 = int(xs.min()), int(xs.max()) + 1
    y0, y1 = int(ys.min()), int(ys.max()) + 1
    sub = np.ascontiguousarray(mask[y0:y1, x0:x1])
    alpha = cv2.GaussianBlur((sub * 255).astype(np.uint8), (0, 0), 1.2)
    return art[y0:y1, x0:x1], alpha, (x0, y0, x1, y1)


# ------------------------------------------------------------------ 摆放
def mark_width(rgba):
    box = rgba.getchannel("A").point(lambda v: 255 if v > ALPHA_THR else 0).getbbox()
    if not box:
        raise SystemExit("标记层是空的，检查 motif_mask 的阈值")
    return box[2] - box[0]


def fit_mark(rgb, alpha, target_w, super_scale=1):
    """把标记缩放到「实测 bbox 宽度 == target_w」，返回 RGBA。"""
    im = Image.fromarray(rgb, "RGB").convert("RGBA")
    im.putalpha(Image.fromarray(alpha, "L"))
    k = target_w / mark_width(im)
    w, h = im.size
    im = im.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)
    box = im.getchannel("A").point(lambda v: 255 if v > ALPHA_THR else 0).getbbox()
    return im.crop(box)


def centred(canvas_px, rgba):
    layer = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    layer.alpha_composite(rgba, ((canvas_px - rgba.width) // 2,
                                 (canvas_px - rgba.height) // 2))
    return layer


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
def build(report_only=False):
    art, bg = load_art()
    mask = motif_mask(art, bg)
    rgb, alpha, bbox = crop_motif(art, mask)
    print("source %s  bg=%s  标记源 bbox=%s (%d×%d)"
          % (art.shape, bg, bbox, bbox[2] - bbox[0], bbox[3] - bbox[1]))

    rows = []
    size_px = int(CANVAS * SS)

    # 自适应：前景 = 透明底 + 居中标记（41% 可见区）
    fg_mark = fit_mark(rgb, alpha, MARK_W_RATIO * VISIBLE * SS)
    fg_canvas = centred(size_px, fg_mark)
    # 主题图标剪影：同一几何，只取 alpha
    mono_canvas = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    mono_canvas.putalpha(fg_canvas.getchannel("A"))

    for name, scale in DENSITIES.items():
        n = int(round(CANVAS * scale))
        vis = VISIBLE * scale
    
        fg = fg_canvas.resize((n, n), Image.LANCZOS)
        mono = mono_canvas.resize((n, n), Image.LANCZOS)

        # 实测
        fg_w = mark_width(fg)
        rows.append((name, n, fg_w, fg_w / vis))

        if report_only:
            continue
        d_out = os.path.join(APP_RES, "drawable-%s" % name)
        os.makedirs(d_out, exist_ok=True)
        fg.save(os.path.join(d_out, "ic_launcher_foreground.png"), optimize=True)
        mono.save(os.path.join(d_out, "ic_launcher_monochrome.png"), optimize=True)

    print("\n标记 bbox 宽度 ÷ 自适应可见区(72dp)：")
    for name, n, fw, fr in rows:
        print("  %-8s canvas=%3dpx  前景 %3dpx = %5.1f%%" % (name, n, fw, fr * 100))
    print("目标 %.1f%% → 实测 %.1f%%（xxxhdpi）" % (MARK_W_RATIO * 100, rows[-1][3] * 100))

    if report_only:
        return

    full = Image.new("RGBA", (size_px, size_px), BG + (255,))
    full.alpha_composite(fg_canvas)
    write_previews(full, mono_canvas)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", action="store_true", help="只打印实测比例，不写文件")
    build(report_only=ap.parse_args().report)
    print("icon ok ->", OUT)

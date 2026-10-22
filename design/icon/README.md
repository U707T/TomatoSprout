# 番茄芽 · 图标源文件

v8：用户提供的**纸雕插画**（米色底 + 双叶嫩芽 + 打卡卡片）作为应用图标。
插画是栅格图，所以这一版不再是纯矢量，而是「一张源图 → 自适应图标三层」的脚本化产出。

```
python3 build_icon_from_art.py     # 需要 numpy / opencv-python / Pillow
```

## 产出

| 文件 | 用途 |
|---|---|
| `source/sprout_papercut.png` | 源图（1064×1080，勿改） |
| `icon_1024.png` / `icon_squircle_1024.png` / `icon_circle_1024.png` / `icon_square_1024.png` | 三种遮罩下的 108dp 预览 |
| `play_store_512.png` | Google Play 商店图标（512×512，正方形满幅） |
| `preview_sheet.png` | 大图 + 圆/方 + 24~96px 小尺寸检视 + 主题图标预览 |
| `app/src/main/res/drawable-<dpi>/ic_launcher_foreground.png` | 自适应前景（含米色底，108dp 满幅） |
| `app/src/main/res/drawable-<dpi>/ic_launcher_monochrome.png` | Android 13+ 主题图标（单色剪影） |
| `app/src/main/res/drawable/ic_launcher_background.xml` | 背景层（纯色 `#F7F3E2`，兜底用） |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` | 自适应图标入口（手写，稳定不变） |

minSdk 26，只需要 `mipmap-anydpi-v26` + 各密度 `drawable-*`，不放各密度 `mipmap` PNG。

## 关键几何（为什么不会切图 / 不会有接缝）

1. **主体外接圆**：脚本自动算出插画主体（叶子 + 芽茎 + 土丘 + 白色卡片）的最小外接圆，
   把它的半径缩到 **34.6dp**。启动器圆形遮罩半径是 36dp，因此任何遮罩下主体都不会被切。
2. **扩边盖满画布**：插画本身用 `BORDER_REPLICATE` 向四周扩边到 108dp。这样插画的矩形边界
   永远落在可见区（18..90dp）之外 —— 任何遮罩下都看不到图片边缘，也就不存在接缝/硬边。
3. **底色**：`#F7F3E2` 取自源图边缘中位色，用于背景层兜底（前景层实际已盖满画布）。
4. **主题图标**：由「与底色差异 > 40 的连通域」得到实心剪影，填洞后把卡片内的勾选圆点/文字条
   反向挖空，保留「清单」的语义。缩放/位移与前景层完全一致。

## 关键参数

| 常量 | 含义 |
|---|---|
| `CONTENT_R_DP` | 主体外接圆半径（dp）；调大 = 图标更满，>36 会被圆形遮罩切 |
| `BG` | 画布兜底底色 |
| `FADE_DP` | 画布最外圈淡出宽度（防个别启动器放大时出现硬边） |
| `SS` | 超采样倍率（864px 画布 → 各密度 LANCZOS 降采样） |
| `DENSITIES` | 输出的密度桶与倍率 |

## 换图流程

1. 替换 `source/sprout_papercut.png`（建议 ≥1024px 的方形构图，主体居中、四周留米色边）；
2. `BG` 改成新图边缘中位色（脚本里也有一份自动采样值，可对比校核）；
3. 跑 `build_icon_from_art.py`，看 `preview_sheet.png` 是否满意；
4. 确认 `drawable-*` 下的 PNG 与 `ic_launcher_background.xml` 已更新。

## 历史

- `_archive/v7_vector/`：上一版**纯矢量**图标（Python 生成 SVG/VectorDrawable，双叶 + 笑脸芽茎 +
  卡片 + 星光）。若以后要回到矢量方案，那里是完整、可运行的管线（`build_icon.py`）。
- `_archive/` 更早的目录是 v5/v6 重绘过程的中间稿，仅供对照，不参与构建。

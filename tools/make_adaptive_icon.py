#!/usr/bin/env python3
"""
给安卓版做「自适应图标」（adaptive icon）—— 就是安卓 8 以后原生 App 那种，
系统按自己的形状（圆形 / 方圆形）去裁，边缘不会被切坏。

做法：把 iOS 那张图标拆成两层
  · 背景层 = 冰山蓝渐变 + 高光（铺满 108dp 画布，随便系统怎么裁）
  · 前景层 = 只有白色记事本（折痕、横线、投影都在），四周透明
两层单独出图，系统自己合成 —— 这就是「原生图标」的标准结构。

绘图参数跟 tools/make_icon.py 完全一致，所以两版看起来是同一个 App。
"""
from PIL import Image, ImageDraw, ImageFilter

ICE_TOP = (152, 212, 242)
ICE_BOTTOM = (38, 116, 188)
LINE_COLOR = (186, 221, 243)
CREASE_COLOR = (198, 230, 248)

# 记事本占画布的比例（原图 0.63 高）。缩到 0.92 是为了落进
# 自适应图标的「安全区」（中间 66dp）—— 不然圆形裁切会啃到边
K = 0.92
N = 3240                      # 母图：108dp × 30
RES = "/Users/liorvyn/Desktop/AndroidLearning/CommissionBook/app/src/main/res"


def m(u):
    """单位坐标 0~1 → 按 K 缩放后的画布坐标 0~1（围绕中心缩放）"""
    return 0.5 + (u - 0.5) * K


def box(x0, y0, x1, y1):
    return [m(x0) * N, m(y0) * N, m(x1) * N, m(y1) * N]


def grad(n):
    g = Image.new("RGB", (1, n))
    for y in range(n):
        t = y / (n - 1)
        g.putpixel((0, y), tuple(int(ICE_TOP[i] + (ICE_BOTTOM[i] - ICE_TOP[i]) * t) for i in range(3)))
    return g.resize((n, n))


def with_highlight(img):
    n = img.size[0]
    hl = Image.new("L", (n, n), 0)
    ImageDraw.Draw(hl).ellipse([-n * 0.30, n * 0.30, n * 0.85, n * 1.55], fill=255)
    hl = hl.filter(ImageFilter.GaussianBlur(n * 0.10)).point(lambda v: int(v * 0.20))
    return Image.composite(Image.new("RGB", (n, n), (255, 255, 255)), img, hl)


# ── 背景层
bg = with_highlight(grad(N))

# ── 前景层：透明底，只有记事本
fg = Image.new("RGBA", (N, N), (0, 0, 0, 0))
r_note = 0.055 * K * N
note_box = box(0.230, 0.195, 0.770, 0.825)

# 投影（底浅，投影重一点才托得住）
shadow = Image.new("L", (N, N), 0)
ImageDraw.Draw(shadow).rounded_rectangle(note_box, radius=r_note, fill=int(255 * 0.32))
blurred = shadow.filter(ImageFilter.GaussianBlur(0.026 * K * N))
shadow = Image.new("L", (N, N), 0)
shadow.paste(blurred, (0, int(0.018 * K * N)))      # 往下挪一点，光从上面来
fg = Image.alpha_composite(fg, Image.merge("RGBA", (
    Image.new("L", (N, N), 0), Image.new("L", (N, N), 0), Image.new("L", (N, N), 0), shadow)))

# 白色记事本
note = Image.new("L", (N, N), 0)
ImageDraw.Draw(note).rounded_rectangle(note_box, radius=r_note, fill=255)
fg = Image.alpha_composite(fg, Image.merge("RGBA", (
    Image.new("L", (N, N), 255), Image.new("L", (N, N), 255), Image.new("L", (N, N), 255), note)))

# 装订折痕
crease = Image.new("L", (N, N), 0)
ImageDraw.Draw(crease).rounded_rectangle(box(0.292, 0.235, 0.306, 0.785), radius=0.007 * K * N, fill=255)
fg = Image.alpha_composite(fg, Image.merge("RGBA", (
    Image.new("L", (N, N), CREASE_COLOR[0]), Image.new("L", (N, N), CREASE_COLOR[1]),
    Image.new("L", (N, N), CREASE_COLOR[2]), crease)))

# 账本横线：1 条短的 + 4 条长的
lines = Image.new("L", (N, N), 0)
d = ImageDraw.Draw(lines)
h = 0.020
d.rounded_rectangle(box(0.340, 0.330, 0.560, 0.330 + h), radius=0.010 * K * N, fill=255)
for y in [0.430, 0.515, 0.600, 0.685]:
    d.rounded_rectangle(box(0.340, y, 0.710, y + h), radius=0.010 * K * N, fill=255)
fg = Image.alpha_composite(fg, Image.merge("RGBA", (
    Image.new("L", (N, N), LINE_COLOR[0]), Image.new("L", (N, N), LINE_COLOR[1]),
    Image.new("L", (N, N), LINE_COLOR[2]), lines)))

# ── 按密度出图（自适应图标画布 = 108dp）
DENS = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for name, dp in DENS.items():
    d = f"{RES}/mipmap-{name}"
    fg.resize((dp, dp), Image.LANCZOS).save(f"{d}/ic_launcher_foreground.png")
    bg.resize((dp, dp), Image.LANCZOS).save(f"{d}/ic_launcher_background.png")
    print(f"✔ mipmap-{name}: {dp}×{dp}  前景+背景")

fg.resize((1024, 1024), Image.LANCZOS).save("/tmp/adaptive-foreground-preview.png")
bg.resize((1024, 1024), Image.LANCZOS).save("/tmp/adaptive-background-preview.png")
print("✔ 预览图：/tmp/adaptive-*-preview.png")

# -*- coding: utf-8 -*-
"""
Render the two images the deck was missing.

The product is about Ol Chiki and the deck never showed a single character of
it. These use the real Noto Sans Ol Chiki, the same file that ships in the APK.

Only text that has been verified character by character appears here:
  ᱚᱞ ᱪᱤᱠᱤ  = ol chiki, the script's own name
  ᱡᱚᱦᱟᱨ    = johar, the Santali greeting
Nothing invented, which is the same rule the app follows.
"""
from PIL import Image, ImageDraw, ImageFont

FONTS = r'C:\Users\ASUS\sih-hackathon\app\src\main\assets\fonts'
OLCK = FONTS + r'\NotoSansOlChiki-Regular.ttf'
DEVA = FONTS + r'\NotoSansDevanagari-Regular.ttf'
LATIN = r'C:\Windows\Fonts\calibri.ttf'
LATIN_B = r'C:\Windows\Fonts\calibrib.ttf'

S = 3  # supersample, downscaled at the end for clean curves

FOREST = (27, 67, 50)
TERRA = (184, 80, 66)
OCHRE = (233, 160, 59)
INK = (28, 28, 28)
MUTED = (120, 120, 120)
GREEN = (82, 183, 136)
CARD = (247, 247, 245)
WHITE = (255, 255, 255)


def f(path, size):
    return ImageFont.truetype(path, size * S)


def rr(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle([c * S for c in box], radius=r * S, fill=fill,
                        outline=outline, width=width * S)


def txt(d, xy, s, font, fill, anchor=None):
    d.text((xy[0] * S, xy[1] * S), s, font=font, fill=fill, anchor=anchor)


def save(img, name):
    img = img.resize((img.width // S, img.height // S), Image.LANCZOS)
    img.save(name)
    print('wrote %s  %dx%d' % (name, img.width, img.height))


# ── 1. the script's own name, for the title slide ────────────────────
W, H = 560, 150
img = Image.new('RGBA', (W * S, H * S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
txt(d, (0, 8), 'ᱚᱞ ᱪᱤᱠᱤ', f(OLCK, 78), OCHRE)
txt(d, (4, 110), 'OL CHIKI, THE SANTALI SCRIPT  ·  RAGHUNATH MURMU, 1925',
    f(LATIN_B, 13), (168, 195, 180))
save(img, 'olchiki-name.png')


# ── 2. the classroom screen, as the deck's one product visual ────────
W, H = 620, 760
img = Image.new('RGB', (W * S, H * S), WHITE)
d = ImageDraw.Draw(img)

# device shell
rr(d, (10, 10, W - 10, H - 10), 26, fill=(238, 240, 238))
rr(d, (22, 22, W - 22, H - 22), 20, fill=WHITE)

# app bar
rr(d, (22, 22, W - 22, 100), 20, fill=FOREST)
d.rectangle([(22 * S, 70 * S), ((W - 22) * S, 100 * S)], fill=FOREST)
txt(d, (48, 46), 'Ol Saathi', f(LATIN_B, 21), WHITE)
txt(d, (W - 48, 52), 'Santali', f(LATIN, 15), (168, 195, 180), anchor='ra')

# source card
txt(d, (48, 132), 'SOURCE  ·  HINDI', f(LATIN_B, 12), TERRA)
rr(d, (48, 156, W - 48, 246), 12, fill=CARD, outline=(226, 226, 222))
txt(d, (72, 178), 'जोहार', f(DEVA, 40), INK)

# output card
txt(d, (48, 278), 'OUTPUT', f(LATIN_B, 12), TERRA)
rr(d, (48, 302, W - 48, 452), 12, fill=CARD, outline=(226, 226, 222))
txt(d, (72, 326), 'ᱡᱚᱦᱟᱨ', f(OLCK, 46), FOREST)

# provenance chip, the point of the whole screen
chip_w = 190
rr(d, (72, 400, 72 + chip_w, 430), 15, fill=(232, 245, 238), outline=GREEN)
d.ellipse([(86 * S, 410 * S), (96 * S, 420 * S)], fill=GREEN)
txt(d, (104, 407), 'Verified translation', f(LATIN_B, 13), (34, 116, 76))

# play button
d.ellipse([((W - 118) * S, 388 * S), ((W - 70) * S, 436 * S)], fill=TERRA)
d.polygon([((W - 103) * S, 400 * S), ((W - 103) * S, 424 * S), ((W - 83) * S, 412 * S)], fill=WHITE)

# provenance footer
txt(d, (48, 480), 'Bhashini  ·  AI4Bharat IndicTrans-v2  ·  cached offline',
    f(LATIN, 13), MUTED)

# press to talk
rr(d, (48, 560, W - 48, 650), 45, fill=FOREST)
d.ellipse([(96 * S, 588 * S), (130 * S, 622 * S)], fill=WHITE)
d.rounded_rectangle([(108 * S, 596 * S), (118 * S, 610 * S)], radius=5 * S, fill=FOREST)
d.line([(113 * S, 610 * S), (113 * S, 615 * S)], fill=FOREST, width=2 * S)
txt(d, (150, 594), 'Hold to speak Hindi', f(LATIN_B, 19), WHITE)

# No latency figure here. Nothing has been measured yet, and inventing one
# would break the rule this very screen is built to demonstrate.
txt(d, (W // 2, 688), 'Aeroplane mode  ·  no network  ·  nothing to load',
    f(LATIN, 13), MUTED, anchor='ma')

save(img, 'mock-screen.png')

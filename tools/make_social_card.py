# -*- coding: utf-8 -*-
"""
Render the GitHub social preview card.

This is the image that appears whenever the repository link is pasted into
WhatsApp, Slack or a browser tab. GitHub generates a generic one by default,
which says nothing about what the project is.

Follows the same rule as deck/art.py: only Ol Chiki that has been verified
character by character appears here. The card uses johar, the Santali greeting,
and nothing else in the script. The app name has no verified Santali spelling,
so it is set in Latin rather than invented.

Centred, because the card is almost always seen small, as a chat thumbnail,
where a left-aligned block with an empty right half just reads as a mistake.

    python tools/make_social_card.py

Upload the result at Settings, General, Social preview on the repository.
"""
from PIL import Image, ImageDraw, ImageFont

FONTS = 'app/src/main/assets/fonts'
OLCK = FONTS + '/NotoSansOlChiki-Regular.ttf'
LATIN = 'C:/Windows/Fonts/calibri.ttf'
LATIN_B = 'C:/Windows/Fonts/calibrib.ttf'

S = 3                      # supersample, downscaled at the end for clean curves
W, H = 1280, 640           # the size GitHub asks for

INDIGO = (0, 6, 102)       # md_theme_primary, the app's own background
OCHRE = (233, 160, 59)
PALE = (189, 194, 255)     # md_theme_inversePrimary
GREEN = (160, 243, 153)    # md_theme_secondaryContainer
WHITE = (255, 255, 255)

JOHAR = 'ᱡᱚᱦᱟᱨ'   # johar, the Santali greeting


def f(path, size):
    return ImageFont.truetype(path, size * S)


def centred(d, y, s, font, fill):
    """Draw s centred on the canvas at baseline-ish y, in unscaled units."""
    w = d.textlength(s, font=font)
    d.text(((W * S - w) / 2, y * S), s, font=font, fill=fill)


img = Image.new('RGB', (W * S, H * S), INDIGO)
d = ImageDraw.Draw(img)

# green rules top and bottom, the app's secondary colour
d.rectangle([0, 0, W * S, 10 * S], fill=GREEN)
d.rectangle([0, (H - 10) * S, W * S, H * S], fill=GREEN)

centred(d, 62, 'SIH26042   ·   GOVERNMENT OF JHARKHAND', f(LATIN_B, 21), PALE)
centred(d, 116, JOHAR, f(OLCK, 116), OCHRE)
centred(d, 292, 'Ol Saathi', f(LATIN_B, 92), WHITE)

centred(d, 420, 'A Hindi-speaking teacher delivers a primary lesson',
        f(LATIN, 33), PALE)
centred(d, 462, 'in Santali, without knowing the language.',
        f(LATIN, 33), PALE)

centred(d, 542,
        'Fully offline   ·   2 GB Android 9 tablet   ·   every line says where it came from',
        f(LATIN_B, 23), GREEN)

img = img.resize((W, H), Image.LANCZOS)
img.save('design/social-card.png')
print('wrote design/social-card.png  %dx%d' % img.size)

"""Build every launcher icon from the full OL SAATHI logo.

The whole logo is kept, never cropped. Adaptive icons are masked to a circle or
squircle by the launcher, so the logo is scaled into the 66dp safe zone of the
108dp foreground layer on a white background, where no mask can clip it.

    .venv-tts/Scripts/python.exe tools/make_launcher_icons.py [logo]
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "branding" / "ol-saathi-logo.jpeg"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def fit(logo, size):
    return logo.resize((size, size), Image.LANCZOS)


def main():
    logo = Image.open(SRC).convert("RGBA")
    for name, k in DENSITIES.items():
        folder = RES / f"mipmap-{name}"
        legacy, layer = round(48 * k), round(108 * k)

        # Pre-Oreo square icon: the full logo edge to edge.
        fit(logo, legacy).convert("RGB").save(folder / "ic_launcher.png")

        # Pre-Oreo round icon: the full logo inside a white disc, small enough
        # that its corners stay within the circle.
        disc = Image.new("RGBA", (legacy, legacy), (0, 0, 0, 0))
        ImageDraw.Draw(disc).ellipse((0, 0, legacy - 1, legacy - 1), fill="white")
        inner = round(legacy * 0.70)
        disc.alpha_composite(fit(logo, inner), ((legacy - inner) // 2,) * 2)
        disc.save(folder / "ic_launcher_round.png")

        # Adaptive foreground: the full logo in the 66dp safe zone.
        fg = Image.new("RGBA", (layer, layer), (0, 0, 0, 0))
        inner = round(66 * k)
        fg.alpha_composite(fit(logo, inner), ((layer - inner) // 2,) * 2)
        fg.save(folder / "ic_launcher_foreground.png")
        print(f"{name}: {legacy}px legacy, {layer}px adaptive layer")


if __name__ == "__main__":
    main()

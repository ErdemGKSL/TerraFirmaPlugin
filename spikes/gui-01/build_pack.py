#!/usr/bin/env python3
"""M0-GUI-01 pack: placeholder art (Pillow) + CE images/lang config. Throwaway.

  build_pack.py <out-pack-dir>

Namespace tfpgui:
  gui/chest6      176x222 full 6-row chest background, font tfpgui:gui, ascent 13 (glyph top = window y 0)
  gui/tab_button  24x24 outside-the-window tab, font minecraft:default (required for lang), ascent 9
lang: container.crafting -> tab glyph at window x -26 (the 'Crafting' label starts at titleLabelX 97)
The art is a placeholder; the image-generation skill would produce the final one.
"""
import json
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw

W, H = 176, 222
DARK, MID, LIGHT = (43, 33, 24, 255), (139, 118, 86, 255), (232, 220, 192, 255)
SLOT_BG, PANEL = (58, 48, 38, 255), (107, 91, 69, 255)


def chest6() -> Image.Image:
    im = Image.new("RGBA", (W, H), PANEL)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W - 1, H - 1], outline=DARK)              # outer edge: x=0, x=175, y=0, y=221
    d.rectangle([1, 1, W - 2, 12], fill=(62, 51, 40, 255))       # title band (the label row)
    d.rectangle([4, 14, W - 5, 120], outline=MID)                 # container area frame
    slot_origins = [(8 + 18 * c, 18 + 18 * r) for r in range(6) for c in range(9)]
    slot_origins += [(8 + 18 * c, 139 + 18 * r) for r in range(3) for c in range(9)]   # player inventory
    slot_origins += [(8 + 18 * c, 197) for c in range(9)]                              # hotbar
    for x, y in slot_origins:                                     # 16x16 interior, 1px frame around it
        d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=DARK)
        d.rectangle([x, y, x + 15, y + 15], fill=SLOT_BG)
        d.line([x + 16, y - 1, x + 16, y + 16], fill=LIGHT)     # light right/bottom edge
        d.line([x - 1, y + 16, x + 16, y + 16], fill=LIGHT)
    im.putpixel((1, 1), (255, 0, 0, 255))                         # orientation markers
    im.putpixel((W - 2, H - 2), (0, 255, 0, 255))
    return im


def tab_button() -> Image.Image:
    im = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, 23, 23], radius=4, fill=(200, 168, 107, 255), outline=DARK)
    d.polygon([(12, 5), (19, 12), (12, 19), (5, 12)], fill=(60, 120, 220, 255), outline=DARK)  # gem icon
    return im


def main():
    out = Path(sys.argv[1])
    if out.exists():
        shutil.rmtree(out)
    rp = out / "resourcepack/assets/tfpgui/textures/font/gui"
    rp.mkdir(parents=True)
    chest6().save(rp / "chest6.png")
    tab_button().save(rp / "tab_button.png")
    conf = out / "configuration"
    conf.mkdir(parents=True)
    gui = {
        "images": {
            "tfpgui:gui/chest6": {"file": "tfpgui:font/gui/chest6.png", "font": "tfpgui:gui",
                                  "height": 222, "ascent": 13},
            "tfpgui:gui/tab_button": {"file": "tfpgui:font/gui/tab_button.png", "font": "minecraft:default",
                                      "height": 24, "ascent": 9},
        },
        # 'all' = every language. Glyph must use minecraft:default (lang strips other fonts).
        "lang": {"all": {"container.crafting": "<white><shift:-123><image:tfpgui:gui/tab_button>"}},
    }
    (conf / "gui.yml").write_text(json.dumps(gui, indent=1))  # JSON is valid YAML
    (out / "pack.yml").write_text("author: tfp-spike\nversion: 1.0\ndescription: M0-GUI-01\nnamespace: tfpgui\n")
    print(f"pack written to {out}")


if __name__ == "__main__":
    main()

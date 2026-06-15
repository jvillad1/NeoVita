#!/usr/bin/env python3
"""
Merge Noto Emoji glyphs into each Roboto weight.

Why: the wasm/web Compose canvas ships no emoji-capable font, so the emoji used as
icons rendered as tofu boxes. skiko/wasm does NOT do per-glyph fallback across the
fonts of a Compose FontFamily, so listing a separate emoji font as a fallback does
nothing. The fix is to bake the emoji glyphs INTO each Roboto weight so a single
typeface carries both Latin and emoji — which renders on every target.

The merged Roboto-*.ttf files live in:
  shared/src/commonMain/composeResources/font/
and are loaded by shared/.../ui/theme/AppFonts.kt.

Re-run this only if you need to refresh/extend emoji coverage.

Requires: pip3 install fonttools
Inputs (downloaded fresh each run):
  - Roboto Regular/Medium/Bold (googlefonts/roboto-2)
  - Noto Emoji variable (google/fonts) -> pinned to wght=400 (monochrome)
"""
import os
import subprocess
import urllib.request
from fontTools.ttLib import TTFont
from fontTools.merge import Merger
from fontTools.varLib import instancer
from fontTools import subset

FONT_DIR = os.path.join(os.path.dirname(__file__), "..", "shared", "src", "commonMain", "composeResources", "font")
ROBOTO = {
    "Regular": "https://raw.githubusercontent.com/googlefonts/roboto-2/main/src/hinted/Roboto-Regular.ttf",
    "Medium":  "https://raw.githubusercontent.com/googlefonts/roboto-2/main/src/hinted/Roboto-Medium.ttf",
    "Bold":    "https://raw.githubusercontent.com/googlefonts/roboto-2/main/src/hinted/Roboto-Bold.ttf",
}
NOTO_EMOJI = "https://raw.githubusercontent.com/google/fonts/main/ofl/notoemoji/NotoEmoji%5Bwght%5D.ttf"
KEEP = {"glyf", "loca", "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2", "post", "cvt ", "fpgm", "prep"}


def keeponly(font):
    for t in list(font.keys()):
        if t not in KEEP and t != "GlyphOrder":
            del font[t]


def main():
    os.makedirs(FONT_DIR, exist_ok=True)
    emoji_path = os.path.join(FONT_DIR, "_emoji_src.ttf")
    urllib.request.urlretrieve(NOTO_EMOJI, emoji_path)
    # Pin the variable emoji font to a single static weight (skiko/wasm dislikes var fonts).
    ef = TTFont(emoji_path)
    instancer.instantiateVariableFont(ef, {"wght": 400}, inplace=True)
    ef.save(emoji_path)

    for weight, url in ROBOTO.items():
        dst = os.path.join(FONT_DIR, f"Roboto-{weight}.ttf")
        urllib.request.urlretrieve(url, dst)
        roboto_cps = set(TTFont(dst).getBestCmap().keys())
        # Only keep emoji codepoints Roboto lacks -> no cmap/name collisions on merge.
        keep_cps = sorted(set(TTFont(emoji_path).getBestCmap().keys()) - roboto_cps)
        sub = os.path.join(FONT_DIR, "_emoji_subset.ttf")
        ss = subset.Subsetter()
        ef = TTFont(emoji_path)
        ss.populate(unicodes=keep_cps)
        ss.subset(ef)
        keeponly(ef)
        # Adopt Roboto's OS/2 so merge's per-field logic sees identical tables.
        rf = TTFont(dst)
        keeponly(rf)
        ef["OS/2"] = rf["OS/2"]
        rf.save(dst)
        ef.save(sub)
        merged = Merger().merge([dst, sub])
        merged.save(dst)
        os.remove(sub)
        cmap = merged.getBestCmap()
        print(f"Roboto-{weight}: latin={0x41 in cmap} emoji={0x1F33F in cmap} size={os.path.getsize(dst)//1024}KB")
    os.remove(emoji_path)
    print("Done. Rebuild the web bundle to pick up the new fonts.")


if __name__ == "__main__":
    main()

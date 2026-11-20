"""Generate PZip application icons from the custom source image (压缩.png)."""
import os
from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "压缩.png")
OUT = os.path.join(ROOT, "src-tauri", "icons")
os.makedirs(OUT, exist_ok=True)

RESAMPLE = Image.Resampling.LANCZOS

im = Image.open(SRC).convert("RGBA")
# Fit into a square 512x512 canvas (preserve aspect ratio).
im.thumbnail((512, 512), RESAMPLE)
canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
canvas.paste(im, ((512 - im.width) // 2, (512 - im.height) // 2))

canvas.save(os.path.join(OUT, "icon.png"))
canvas.resize((32, 32), RESAMPLE).save(os.path.join(OUT, "32x32.png"))
canvas.resize((128, 128), RESAMPLE).save(os.path.join(OUT, "128x128.png"))
canvas.resize((256, 256), RESAMPLE).save(os.path.join(OUT, "128x128@2x.png"))

ico_src = canvas.resize((256, 256), RESAMPLE)
ico_src.save(
    os.path.join(OUT, "icon.ico"),
    format="ICO",
    sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
)

print("icons generated from", SRC)
print(sorted(os.listdir(OUT)))

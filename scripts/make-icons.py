"""Generate PZip application icons (flat rounded-square with archive glyph)."""
import os
from PIL import Image, ImageDraw

OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "src-tauri", "icons"))
os.makedirs(OUT, exist_ok=True)

S = 512
top = (99, 102, 241)    # indigo-500
bottom = (79, 70, 229)  # indigo-600

# Vertical gradient background
base = Image.new("RGBA", (S, S))
px = base.load()
for y in range(S):
    t = y / (S - 1)
    c = tuple(round(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
    for x in range(S):
        px[x, y] = (*c, 255)

# Rounded-square alpha mask
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=112, fill=255)

img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
img.paste(base, (0, 0), mask)

d = ImageDraw.Draw(img)
white = (255, 255, 255, 255)

# Down arrow (pointing into the archive box)
d.rectangle([238, 128, 274, 252], fill=white)
d.polygon([(170, 218), (342, 218), (256, 318)], fill=white)
# Archive box
d.rounded_rectangle([118, 316, 394, 442], radius=26, fill=white)

RESAMPLE = Image.Resampling.LANCZOS

img.save(os.path.join(OUT, "icon.png"))
img.resize((32, 32), RESAMPLE).save(os.path.join(OUT, "32x32.png"))
img.resize((128, 128), RESAMPLE).save(os.path.join(OUT, "128x128.png"))
img.resize((256, 256), RESAMPLE).save(os.path.join(OUT, "128x128@2x.png"))

ico_src = img.resize((256, 256), RESAMPLE)
ico_src.save(
    os.path.join(OUT, "icon.ico"),
    format="ICO",
    sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
)

print("icons written to", OUT)
print(sorted(os.listdir(OUT)))

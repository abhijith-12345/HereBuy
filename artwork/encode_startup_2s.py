"""Encode the 120 Blender frames as Android startup assets."""

from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
FRAMES = ROOT / "artwork" / "HereBuy_PNG_Sequence_2s"
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"


class LazyFrames:
    mode = "P"
    size = (1080, 1920)
    n_frames = 1
    has_transparency_data = True
    info = {}

    def __init__(self, indices):
        self.indices = indices
        self.position = 0
        self.current = indices[0]

    def seek(self, index):
        self.current = self.indices[self.position]
        self.position += 1

    def convert(self, mode):
        with Image.open(FRAMES / f"frame_{self.current:04d}.png") as frame:
            return frame.convert(mode)


first = Image.open(FRAMES / "frame_0001.png").convert("RGBA")
first.save(
    RAW / "startup_animation_hd.webp",
    format="WEBP",
    save_all=True,
    append_images=[LazyFrames(list(range(2, 121)))] * 119,
    duration=[16, 17, 17] * 40,
    loop=1,
    quality=90,
    method=4,
    alpha_quality=100,
    minimize_size=True,
)

gif_frames = []
for number in range(1, 121, 2):
    with Image.open(FRAMES / f"frame_{number:04d}.png") as source:
        image = source.convert("RGBA").resize((540, 960), Image.Resampling.LANCZOS)
    alpha = image.getchannel("A")
    rgb = Image.new("RGB", image.size, (0, 0, 0))
    rgb.paste(image.convert("RGB"), mask=alpha)
    indexed = rgb.quantize(colors=255, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    pixels = bytearray(indexed.tobytes())
    for index, value in enumerate(alpha.tobytes()):
        if value < 128:
            pixels[index] = 255
    indexed.frombytes(bytes(pixels))
    palette = indexed.getpalette() or []
    palette += [0] * (768 - len(palette))
    indexed.putpalette(palette[:768])
    gif_frames.append(indexed)

gif_frames[0].save(
    RAW / "startup_animation.gif",
    save_all=True,
    append_images=gif_frames[1:],
    duration=[30, 30, 40] * 20,
    loop=1,
    transparency=255,
    disposal=2,
    optimize=False,
)

for name in ("startup_animation_hd.webp", "startup_animation.gif"):
    path = RAW / name
    with Image.open(path) as animation:
        durations = []
        for index in range(animation.n_frames):
            animation.seek(index)
            animation.load()
            durations.append(animation.info.get("duration", 0))
        print(name, animation.size, animation.n_frames, sum(durations), "ms", path.stat().st_size, "bytes")

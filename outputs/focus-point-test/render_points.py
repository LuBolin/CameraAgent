from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1] / "focus-mask-study" / "fixtures"
OUT = Path(__file__).resolve().parent


def render(source: str, output: str, points: list[tuple[str, int, int]]) -> None:
    image = Image.open(ROOT / source).convert("RGB")
    image.thumbnail((1400, 1000), Image.Resampling.LANCZOS)
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default(size=max(16, round(min(image.size) / 24)))
    radius = max(12, round(min(image.size) / 24))

    for label, nx, ny in points:
        x = round(nx / 999 * (image.width - 1))
        y = round(ny / 999 * (image.height - 1))
        color = "#00E5FF" if label != "teddy" else "#FF3DCE"
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), outline="black", width=8)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), outline=color, width=4)
        draw.line((x - radius * 2, y, x + radius * 2, y), fill=color, width=4)
        draw.line((x, y - radius * 2, x, y + radius * 2), fill=color, width=4)
        text = f"{label}: [{nx}, {ny}]"
        box = draw.textbbox((0, 0), text, font=font, stroke_width=2)
        tx = min(max(8, x + radius + 8), image.width - (box[2] - box[0]) - 12)
        ty = min(max(8, y - radius - (box[3] - box[1]) - 12), image.height - (box[3] - box[1]) - 12)
        draw.text((tx, ty), text, font=font, fill=color, stroke_width=3, stroke_fill="black")

    image.save(OUT / output)


render("boomerang.jpg", "boomerang-point.png", [("boomerang", 726, 386)])
render("keyboard-scene.jpg", "keyboard-point.png", [("keyboard", 491, 614)])
render("two-toys.png", "two-toys-points.png", [("panda", 280, 450), ("teddy", 620, 400)])


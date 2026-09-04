import json
import shutil
from pathlib import Path

from gradio_client import Client, handle_file


HERE = Path(__file__).resolve().parent
FIXTURE = HERE.parent / "focus-mask-study" / "fixtures" / "boomerang.jpg"

result = Client("gatilin/Youtu-VL-demo").predict(
    task_id="ref_seg",
    prompt='Can you segment "{target}" in this image?',
    img1=handle_file(FIXTURE),
    img2=None,
    target="wooden boomerang",
    api_name="/gpu_inference",
)

visualization = Path(result[0]["path"] if isinstance(result[0], dict) else result[0])
assert visualization.exists()
shutil.copy2(visualization, HERE / "boomerang-youtu.png")
(HERE / "result.json").write_text(
    json.dumps({"model_output": result[1], "elapsed": result[2]}, indent=2),
    encoding="utf-8",
)
print(result[1], result[2])

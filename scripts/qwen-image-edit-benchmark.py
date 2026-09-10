#!/usr/bin/env python3
"""Run the 4 x 3 x 2 Qwen image-edit comparison."""

from __future__ import annotations

import base64
import json
import time
import argparse
import urllib.error
import urllib.request
import io
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "outputs/qa/qwen-image-edit-benchmark"
ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
MODELS = (("qwen-image-3.0", "1K"), ("qwen-image-3.0", "2K"), ("qwen-image-3.0-pro", "1K"), ("qwen-image-3.0-pro", "2K"))
FIXTURES = (
    ("scenery", ROOT / "test-fixtures/cold-blue-scene.png", ("Change the blue card in the center to a warm yellow card. Preserve the mug, plant, table, framing, lighting, and every other detail.", "Remove the blue card in the center. Reconstruct only the tabletop and background behind it. Preserve the mug, plant, framing, and all other details.")),
    ("person-object", ROOT / "test-fixtures/benchmark-person-object.png", ("Change only the red bicycle to matte black. Preserve the person, face, pose, backpack, park, lighting, and framing exactly.", "Remove only the blue backpack. Reconstruct the clothing and background behind it. Preserve the person, bicycle, face, pose, lighting, and framing.")),
    ("many-people", ROOT / "test-fixtures/benchmark-many-people.png", ("Change only the sky and distant background to a warm late-afternoon golden tone. Preserve all six people, their faces, clothing, positions, and the foreground exactly.", "Remove only the person furthest to the left. Reconstruct the background behind that person. Preserve the other people, their faces, clothing, positions, and the rest of the scene.")),
)


def key() -> str:
    value = (ROOT / "qwen_key.env").read_text(encoding="utf-8-sig").strip()
    if "=" in value:
        value = value.split("=", 1)[1].strip().strip('"').strip("'")
    if not value:
        raise SystemExit("qwen_key.env is empty")
    return value


def prepared_image(path: Path, tier: str) -> tuple[str, str]:
    target = 1024 if tier == "1K" else 2048
    with Image.open(path) as source:
        image = source.convert("RGB")
        scale = target / max(image.width, image.height)
        if tier == "1K":
            scale = min(1.0, scale)
        if scale != 1:
            image = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
        output = io.BytesIO()
        image.save(output, format="JPEG", quality=85, optimize=True)
        width, height = image.size
    return "data:image/jpeg;base64," + base64.b64encode(output.getvalue()).decode("ascii"), f"{width}*{height}"


def request(model: str, size: str, image: Path, instruction: str, api_key: str) -> tuple[int, dict]:
    image_url, output_size = prepared_image(image, size)
    prompt = (
        "Edit the supplied photo only as requested. Preserve identity, faces, people, objects, geometry, "
        "framing, perspective, lighting, color, background, clothing, and text unless explicitly named. "
        "Do not add or change anything else. Keep photorealism and the source aspect ratio. "
        "User edit request: <request>" + instruction + "</request>"
    )
    body = json.dumps({
        "model": model,
        "input": {"messages": [{"role": "user", "content": [{"image": image_url}, {"text": prompt}]}]},
        "parameters": {"size": output_size, "n": 1, "prompt_extend": False, "watermark": False},
    }, separators=(",", ":")).encode()
    started = time.perf_counter()
    req = urllib.request.Request(ENDPOINT, body, {"Authorization": "Bearer " + api_key, "Content-Type": "application/json", "Accept": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=180) as response:
            raw = response.read()
            status = response.status
    except urllib.error.HTTPError as exc:
        detail = exc.read(4096).decode("utf-8", errors="replace")
        return round((time.perf_counter() - started) * 1000), {"error": f"HTTP {exc.code}", "providerError": detail}
    except Exception as exc:
        return round((time.perf_counter() - started) * 1000), {"error": type(exc).__name__}
    result = json.loads(raw)
    result["_status"] = status
    result["_latencyMs"] = round((time.perf_counter() - started) * 1000)
    return result["_latencyMs"], result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=24)
    parser.add_argument("--size", choices=["1K", "2K"])
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    api_key = key()
    records = []
    for model, size in MODELS:
        if args.size and size != args.size:
            continue
        for fixture_name, fixture, prompts in FIXTURES:
            for prompt_index, instruction in enumerate(prompts, 1):
                if len(records) >= args.limit:
                    break
                case = f"{model.replace('.', '-')}-{size.lower()}-{fixture_name}-p{prompt_index}"
                print(f"START {case}", flush=True)
                latency, result = request(model, size, fixture, instruction, api_key)
                output_path = OUT / f"{case}.json"
                output_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
                image_url = None
                try:
                    image_url = result["output"]["choices"][0]["message"]["content"][0]["image"]
                    image_path = OUT / f"{case}.png"
                    urllib.request.urlretrieve(image_url, image_path)
                except (KeyError, IndexError, TypeError, ValueError, urllib.error.URLError):
                    image_path = None
                record = {"case": case, "model": model, "size": size, "fixture": fixture_name, "promptIndex": prompt_index, "instruction": instruction, "latencyMs": latency, "responseFile": output_path.name, "imageFile": image_path.name if image_path else None, "requestId": result.get("request_id"), "error": result.get("error"), "status": result.get("_status")}
                records.append(record)
                print(f"DONE  {case} {latency}ms {'OK' if image_path else result.get('error', 'FAILED')}", flush=True)
                time.sleep(2)
            if len(records) >= args.limit:
                break
        if len(records) >= args.limit:
            break
    report = {"createdUtc": datetime.now(timezone.utc).isoformat(), "endpoint": ENDPOINT, "cases": records}
    (OUT / "results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {len(records)} cases to {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

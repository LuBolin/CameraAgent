#!/usr/bin/env python3
"""Run the bounded 12-call Bailian/Qwen visual-contract smoke and write JSONL evidence."""

from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image


ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
MODEL = "qwen3.7-flash-2026-07-15"
MAX_IMAGE_BYTES = 300 * 1024
MAX_BODY_BYTES = 450 * 1024
MAX_RESPONSE_BYTES = 64 * 1024
TIMEOUT_SECONDS = 5.0


CASES = (
    ("color-cold-1", "COLOR_CAST", "cold-blue-scene.png", "This looks too cold", "WHITE_BALANCE_WARMER", True),
    ("color-cold-2", "COLOR_CAST", "cold-blue-scene.png", "This looks too cold", "WHITE_BALANCE_WARMER", True),
    ("color-warm-1", "COLOR_CAST", "warm-yellow-scene.png", "This looks too warm", "WHITE_BALANCE_COOLER", True),
    ("color-warm-2", "COLOR_CAST", "warm-yellow-scene.png", "This looks too warm", "WHITE_BALANCE_COOLER", True),
    ("color-neutral-control", "COLOR_CAST", "neutral-portrait.png", "This looks too cold", None, False),
    ("color-confound", "COLOR_CAST", "cold-blue-scene.png", "This looks too warm", None, False),
    ("face-natural-1", "FACE_SIZE_AMBIGUOUS", "large-face-natural-perspective.png", "The face looks too big", "DISTORTION_FALSE", True),
    ("face-natural-2", "FACE_SIZE_AMBIGUOUS", "large-face-natural-perspective.png", "The face looks too big", "DISTORTION_FALSE", True),
    ("face-wide-1", "FACE_SIZE_AMBIGUOUS", "wide-angle-distorted-face.png", "The face looks too big", "DISTORTION_TRUE", True),
    ("face-wide-2", "FACE_SIZE_AMBIGUOUS", "wide-angle-distorted-face.png", "The face looks too big", "DISTORTION_TRUE", True),
    ("face-wide-3", "FACE_SIZE_AMBIGUOUS", "wide-angle-distorted-face.png", "The face looks too big", "DISTORTION_TRUE", True),
    ("face-no-subject", "FACE_SIZE_AMBIGUOUS", "cold-blue-scene.png", "The face looks too big", None, False),
)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        return None


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        values[name.strip()] = value.strip().strip('"').strip("'")
    return values


def prompt(family: str, comment: str) -> str:
    if family == "COLOR_CAST":
        return (
            f"Prompt v2: family=COLOR_CAST; comment={comment}; "
            "WHITE_BALANCE_WARMER when neutral objects look blue/cyan; "
            "WHITE_BALANCE_COOLER when neutral objects look yellow/orange; "
            "choose one allowed intent only when image evidence supports it, otherwise clarify; "
            "return JSON only in exactly one shape: "
            '{"schemaVersion":2,"outcome":"INTENT","intent":"<INTENT>"} or '
            '{"schemaVersion":2,"outcome":"CLARIFY","reason":"<REASON>"}; '
            "outcome must be the literal value INTENT or CLARIFY, and an intent label may appear only in intent; "
            "allowed INTENT labels=WHITE_BALANCE_WARMER|WHITE_BALANCE_COOLER; "
            "allowed REASON labels=VISUAL_INSUFFICIENT|SUBJECT_UNCLEAR|SCENE_CONFOUND; no other keys or prose"
        )
    return (
        f"Prompt v3: family=FACE_SIZE_AMBIGUOUS; comment={comment}; inspect facial proportions only. "
        "Is there visible close or wide-angle perspective distortion, such as central features or the nose "
        "enlarged relative to the ears and sides of the face? Do not infer distortion from a large face, tight "
        "crop, or proximity alone. Return JSON only in exactly one shape: "
        '{"schemaVersion":3,"outcome":"INTENT","distortionVisible":true} or '
        '{"schemaVersion":3,"outcome":"INTENT","distortionVisible":false} or '
        '{"schemaVersion":3,"outcome":"CLARIFY","reason":"<REASON>"}; '
        "outcome must be the literal value INTENT or CLARIFY; distortionVisible must be a JSON boolean; "
        "allowed REASON labels=VISUAL_INSUFFICIENT|SUBJECT_UNCLEAR|SCENE_CONFOUND; no other keys or prose"
    )


def observation_jpeg(path: Path) -> bytes:
    with Image.open(path) as source:
        image = source.convert("RGB")
    image.thumbnail((768, 768), Image.Resampling.LANCZOS)
    quality = 70
    while True:
        output = io.BytesIO()
        image.save(output, format="JPEG", quality=quality)
        encoded = output.getvalue()
        if len(encoded) <= MAX_IMAGE_BYTES:
            image.close()
            return encoded
        ratio = (MAX_IMAGE_BYTES / len(encoded)) ** 0.5 * 0.9
        size = (max(1, round(image.width * ratio)), max(1, round(image.height * ratio)))
        smaller = image.resize(size, Image.Resampling.LANCZOS)
        image.close()
        image = smaller


def request_body(family: str, comment: str, jpeg: bytes) -> tuple[bytes, str]:
    text = prompt(family, comment)
    data_url = "data:image/jpeg;base64," + base64.b64encode(jpeg).decode("ascii")
    body = json.dumps(
        {
            "model": MODEL,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": data_url}},
                        {"type": "text", "text": text},
                    ],
                }
            ],
            "enable_thinking": False,
            "temperature": 0,
            "stream": False,
            "response_format": {"type": "json_object"},
        },
        separators=(",", ":"),
    ).encode("utf-8")
    if len(body) > MAX_BODY_BYTES:
        raise ValueError(f"request body is {len(body)} bytes")
    return body, text


def parse_response(raw: bytes, family: str) -> tuple[str | None, str | None, dict]:
    root = json.loads(raw)
    if not isinstance(root, dict) or root.get("object") != "chat.completion":
        return None, "invalid provider object", root
    if not isinstance(root.get("id"), str) or not root["id"].strip() or root.get("model") != MODEL:
        return None, "invalid provider id/model", root
    choices = root.get("choices")
    if not isinstance(choices, list) or len(choices) != 1:
        return None, "invalid choices", root
    choice = choices[0]
    message = choice.get("message") if isinstance(choice, dict) else None
    if choice.get("finish_reason") != "stop" or not isinstance(message, dict) or message.get("role") != "assistant":
        return None, "invalid completion state", root
    if message.get("tool_calls") not in (None, []) or message.get("reasoning_content") not in (None, "") or message.get("refusal") not in (None, ""):
        return None, "unexpected tool/reasoning/refusal", root
    content = message.get("content")
    if not isinstance(content, str) or len(content.encode("utf-8")) > 512:
        return None, "invalid content", root
    value = json.loads(content)
    if not isinstance(value, dict):
        return None, "content is not an object", root
    outcome = value.get("outcome")
    if family == "COLOR_CAST" and outcome == "INTENT" and set(value) == {"schemaVersion", "outcome", "intent"}:
        label = value.get("intent")
        if value.get("schemaVersion") == 2 and label in {"WHITE_BALANCE_WARMER", "WHITE_BALANCE_COOLER"}:
            return label, None, root
    if family == "FACE_SIZE_AMBIGUOUS" and outcome == "INTENT" and set(value) == {"schemaVersion", "outcome", "distortionVisible"}:
        visible = value.get("distortionVisible")
        if value.get("schemaVersion") == 3 and type(visible) is bool:
            return "DISTORTION_TRUE" if visible else "DISTORTION_FALSE", None, root
    if outcome == "CLARIFY" and set(value) == {"schemaVersion", "outcome", "reason"}:
        expected_version = 2 if family == "COLOR_CAST" else 3
        if value.get("schemaVersion") == expected_version and value.get("reason") in {
            "VISUAL_INSUFFICIENT",
            "SUBJECT_UNCLEAR",
            "SCENE_CONFOUND",
        }:
            return "CLARIFY_" + value["reason"], None, root
    return None, "family/schema rejection", root


def source_commit(root: Path) -> str:
    result = subprocess.run(
        ["git", "-c", f"safe.directory={root.as_posix()}", "rev-parse", "HEAD"],
        cwd=root,
        capture_output=True,
        text=True,
        check=False,
    )
    return result.stdout.strip() if result.returncode == 0 else "working-tree"


def source_dirty(root: Path) -> bool:
    result = subprocess.run(
        ["git", "-c", f"safe.directory={root.as_posix()}", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=root,
        capture_output=True,
        text=True,
        check=False,
    )
    return result.returncode != 0 or bool(result.stdout.strip())


def content_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def is_semantic_match(label: str | None, expected: str | None) -> bool:
    return label == expected if expected is not None else bool(label and label.startswith("CLARIFY_"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", type=Path, default=Path(".env"))
    parser.add_argument("--output", type=Path, default=Path("outputs/qa/qwen-live-smoke.jsonl"))
    parser.add_argument("--minimum-start-interval", type=float, default=10.0)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    env_path = args.env if args.env.is_absolute() else root / args.env
    output_path = args.output if args.output.is_absolute() else root / args.output
    env = read_env(env_path)
    key = env.get("EVALUATION_MODEL_KEY", "")
    configured_model = env.get("EVALUATION_MODEL_NAME", "")
    if not key or configured_model != MODEL:
        print(".env must contain the fixed EVALUATION_MODEL_NAME and a non-empty EVALUATION_MODEL_KEY", file=sys.stderr)
        return 2

    output_path.parent.mkdir(parents=True, exist_ok=True)
    run_id = str(uuid.uuid4())
    commit = source_commit(root)
    dirty = source_dirty(root)
    harness_hash = content_hash(Path(__file__).resolve())
    android_contract_hash = content_hash(
        root / "app/src/main/java/com/bolin/photohelper/visual/VisualContracts.kt",
    )
    android_client_hash = content_hash(
        root / "app/src/main/java/com/bolin/photohelper/visual/BailianVisualClient.kt",
    )
    opener = urllib.request.build_opener(NoRedirect())
    records = []
    last_started = 0.0
    for index, (name, family, fixture_name, comment, expected, eligible) in enumerate(CASES, 1):
        wait_seconds = args.minimum_start_interval - (time.perf_counter() - last_started)
        if last_started and wait_seconds > 0:
            time.sleep(wait_seconds)
        fixture = root / "test-fixtures" / fixture_name
        jpeg = observation_jpeg(fixture)
        body, prompt_text = request_body(family, comment, jpeg)
        started = time.perf_counter()
        last_started = started
        status = None
        label = None
        rejection = None
        raw_response = None
        try:
            request = urllib.request.Request(
                ENDPOINT,
                data=body,
                method="POST",
                headers={
                    "Authorization": "Bearer " + key,
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                },
            )
            with opener.open(request, timeout=TIMEOUT_SECONDS) as response:
                status = response.status
                raw = response.read(MAX_RESPONSE_BYTES + 1)
            if len(raw) > MAX_RESPONSE_BYTES:
                rejection = "response exceeded 64 KiB"
            elif status != 200:
                rejection = f"HTTP {status}"
            else:
                label, rejection, raw_response = parse_response(raw, family)
        except urllib.error.HTTPError as error:
            status = error.code
            rejection = f"HTTP {error.code}"
        except Exception as error:  # Error text is deliberately class-only to avoid leaking headers or bodies.
            rejection = type(error).__name__
        latency_ms = round((time.perf_counter() - started) * 1000)
        semantic_match = is_semantic_match(label, expected)
        passed = status == 200 and rejection is None and latency_ms < 5_000 and semantic_match
        record = {
            "runId": run_id,
            "timeUtc": datetime.now(timezone.utc).isoformat(),
            "sourceCommit": commit,
            "sourceDirty": dirty,
            "harnessContentHash": harness_hash,
            "androidContractContentHash": android_contract_hash,
            "androidClientContentHash": android_client_hash,
            "case": name,
            "productionEligible": eligible,
            "endpoint": ENDPOINT,
            "expectedModel": MODEL,
            "returnedModel": raw_response.get("model") if isinstance(raw_response, dict) else None,
            "family": family,
            "promptSchemaHash": hashlib.sha256(prompt_text.encode("utf-8")).hexdigest(),
            "fixture": fixture_name,
            "fixtureContentHash": content_hash(fixture),
            "observationContentHash": hashlib.sha256(jpeg).hexdigest(),
            "expectedLabel": expected,
            "parsedLabel": label,
            "latencyMs": latency_ms,
            "httpStatus": status,
            "rejectionReason": rejection,
            "rawFixtureResponse": raw_response,
            "pass": passed,
        }
        records.append(record)
        print(
            f"[{index:02d}/12] {name}: {'PASS' if passed else 'FAIL'} {latency_ms}ms {label or rejection}",
            flush=True,
        )
        jpeg = b""
        body = b""

    with output_path.open("w", encoding="utf-8", newline="\n") as output:
        for record in records:
            output.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
    passed_count = sum(record["pass"] for record in records)
    print(f"Wrote redacted JSONL: {output_path} ({passed_count}/12 passed)")
    return 0 if passed_count == len(records) else 1


if __name__ == "__main__":
    raise SystemExit(main())

"""Send Android-exported composition requests, without logging the evaluation key."""
import json
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else ROOT / "outputs/qa/composition-qwen"

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def main():
    lines = [line.strip() for line in (ROOT / "qwen_key.env").read_text(encoding="utf-8-sig").splitlines()
             if line.strip() and not line.lstrip().startswith("#")]
    values = [line.split("=", 1)[1].strip().strip('\"\'') if "=" in line else line.strip('\"\'') for line in lines]
    keys = [value for value in values if value.startswith("sk-")]
    if len(keys) != 1:
        raise SystemExit("Expected exactly one Qwen API key in qwen_key.env; contents were not logged.")
    key = keys[0]
    opener = urllib.request.build_opener(NoRedirect())
    results = []
    requests = sorted(OUT.glob("*-request.json"))
    if not requests:
        raise SystemExit("No Android-exported requests found.")
    for index, path in enumerate(requests):
        if index:
            time.sleep(10)
        name = path.name.removesuffix("-request.json")
        (OUT / (name + "-response.json")).unlink(missing_ok=True)
        started = time.monotonic()
        row = {"case": name, "model": json.loads(path.read_bytes())["model"], "timeoutSeconds": 5}
        request = urllib.request.Request("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            data=path.read_bytes(), headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"}, method="POST")
        try:
            with opener.open(request, timeout=5) as response:
                raw = response.read(65537)
                if len(raw) > 65536:
                    raise ValueError("Response exceeds limit")
                text = raw.decode("utf-8").replace(key, "<redacted>")
                (OUT / (name + "-response.json")).write_text(text, encoding="utf-8")
                row["status"] = response.status
        except urllib.error.HTTPError as error:
            row["status"] = error.code
        except Exception as error:
            row["failure"] = type(error).__name__
            row["detail"] = str(error).replace(key, "<redacted>")[:300]
        row["elapsedMs"] = round((time.monotonic() - started) * 1000)
        row["withinAppDeadline"] = row["elapsedMs"] <= 5000
        results.append(row)
        (OUT / "network-results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
        print(json.dumps(row), flush=True)

if __name__ == "__main__":
    main()

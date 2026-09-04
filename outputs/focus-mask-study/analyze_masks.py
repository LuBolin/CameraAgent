"""Measure generated boomerang masks against the public-domain alpha mask."""

from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage


ROOT = Path(__file__).parent
GT_PATH = ROOT / "fixtures" / "boomerang-ground-truth.png"


def largest_component(mask: np.ndarray) -> np.ndarray:
    labels, count = ndimage.label(mask, structure=np.ones((3, 3)))
    if not count:
        return mask
    areas = np.bincount(labels.ravel())
    areas[0] = 0
    return labels == areas.argmax()


def point_nearest_centroid(mask: np.ndarray) -> tuple[int, int]:
    y, x = np.nonzero(mask)
    centroid = np.array([y.mean(), x.mean()])
    index = np.square(np.column_stack((y, x)) - centroid).sum(axis=1).argmin()
    return int(x[index]), int(y[index])


def safe_interior_point(mask: np.ndarray, clearance_ratio: float = 0.5) -> tuple[int, int]:
    distance = ndimage.distance_transform_edt(mask)
    candidates = mask & (distance >= distance.max() * clearance_ratio)
    return point_nearest_centroid(candidates)


def metrics(mask: np.ndarray, truth: np.ndarray) -> dict:
    intersection = np.logical_and(mask, truth).sum()
    union = np.logical_or(mask, truth).sum()
    centroid_point = point_nearest_centroid(mask)
    safe_point = safe_interior_point(mask)
    return {
        "iou": round(float(intersection / union), 4),
        "precision": round(float(intersection / mask.sum()), 4),
        "recall": round(float(intersection / truth.sum()), 4),
        "centroid_snap_xy": centroid_point,
        "centroid_snap_hits_truth": bool(truth[centroid_point[1], centroid_point[0]]),
        "safe_interior_xy": safe_point,
        "safe_interior_hits_truth": bool(truth[safe_point[1], safe_point[0]]),
    }


def analyze(path: Path, truth: np.ndarray) -> dict:
    image = Image.open(path).convert("L").resize(
        (truth.shape[1], truth.shape[0]), Image.Resampling.LANCZOS
    )
    gray = np.asarray(image)
    best = None
    for threshold in range(1, 256):
        mask = largest_component(gray >= threshold)
        if not mask.any():
            continue
        result = metrics(mask, truth)
        if best is None or result["iou"] > best["iou"]:
            best = {"threshold": threshold, **result}
    return best


def at_threshold(path: Path, truth: np.ndarray, threshold: int) -> dict:
    gray = np.asarray(
        Image.open(path).convert("L").resize(
            (truth.shape[1], truth.shape[0]), Image.Resampling.LANCZOS
        )
    )
    return metrics(largest_component(gray >= threshold), truth)


def main() -> None:
    truth = largest_component(np.asarray(Image.open(GT_PATH).getchannel("A")) >= 128)
    for filename in (
        "openai-boomerang-mask.png",
        "qwen-image-2-boomerang-rendered.png",
        "qwen-image-2-boomerang-rendered-v2-raw.png",
    ):
        path = ROOT / filename
        print(filename, {"best": analyze(path, truth), "threshold_128": at_threshold(path, truth, 128)})

    qwen_point = (round(726 / 999 * (truth.shape[1] - 1)), round(386 / 999 * (truth.shape[0] - 1)))
    print("qwen3.7_direct_point", qwen_point, "hits_truth", bool(truth[qwen_point[1], qwen_point[0]]))

    # A concave shape's centroid can be outside; the clearance rule must remain inside.
    ring = np.zeros((21, 21), dtype=bool)
    yy, xx = np.ogrid[:21, :21]
    ring[(xx - 10) ** 2 + (yy - 10) ** 2 <= 9**2] = True
    ring[(xx - 10) ** 2 + (yy - 10) ** 2 <= 5**2] = False
    x, y = safe_interior_point(ring)
    assert ring[y, x]


if __name__ == "__main__":
    main()

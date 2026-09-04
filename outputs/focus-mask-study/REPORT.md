# Focus targeting study

## Conclusion

The grid should be removed, but Qwen-Image should not replace it in the live focus path.

The smallest reliable design is to send one clean camera frame to Qwen3.7-Flash and ask for a normalized point that is safely inside the requested object's visible material, optionally with a bounding box for validation. Qwen's vision models are explicitly trained for point and box localization, and the three live tests all returned a usable focus point.

The proposed generated-mask design can work, but it is not reliable enough as the primary path. Qwen-Image 2.0 produced one badly misaligned mask and one excellent mask for the same boomerang after a stricter prompt. It also took roughly 26–29 seconds per result in Qwen Studio and returned a textured grayscale image rather than a binary mask. This is image editing, not a segmentation contract.

## What was tested

| Route | Target | Result | Observed wall time |
|---|---|---:|---:|
| Qwen3.7-Plus native point/box grounding | Boomerang | Point was on solid boomerang material | ~17.5 s |
| Qwen3.7-Plus native point/box grounding | Panda and brown teddy in the same frame | Both requested points were on the correct toys | ~37 s |
| Qwen3.7-Plus native point/box grounding | Keyboard | Point was safely inside the keyboard | ~21.6 s |
| Qwen-Image 2.0 image-edit mask, first prompt | Boomerang | 0.258 best IoU; derived point missed | ~26 s |
| Qwen-Image 2.0 image-edit mask, strict prompt | Boomerang | 0.957 best IoU; safe interior point hit | ~29 s |
| A second image generator, structural cross-check | Boomerang | 0.940 best IoU; safe interior point hit | ~19 s |
| A second image generator, structural cross-check | Keyboard, panda, teddy | Correct target represented; derived points hit | ~21–24 s each |

The Qwen Studio timings include its web UI and, for the grounding tests, visible thinking. They are not direct API benchmarks. The app already disables thinking, so its coordinate call should be faster. The key comparison is architectural: coordinate grounding returns a few numbers, while the mask route runs an image-generation model and then downloads and parses another image.

Anonymous Qwen Studio reached its daily image-generation limit after the two Qwen-Image trials, so the actual Qwen mask test could not be repeated on the other fixtures without an API key. The native point route was exercised on all three scene types.

## The important geometry result

The proposed “center of gravity, then nearest mask pixel” rule is unsafe for concave or hollow objects.

On both accurate boomerang masks, the mask centroid fell in the empty interior. Snapping to the nearest mask pixel selected the inner boundary, and that point missed the real object after small mask alignment error:

| Mask | Centroid-snap | Safe interior rule |
|---|---|---|
| Qwen-Image strict mask | Miss | Hit |
| Cross-check mask | Miss | Hit |

Use this instead:

1. Convert the returned image to luminance and threshold it.
2. Keep the largest connected foreground component.
3. Compute each foreground pixel's distance to the nearest background pixel.
4. Keep pixels with at least 50% of the maximum clearance.
5. From those pixels, choose the one nearest the mask centroid.

That preserves the desire for a visually central point while avoiding holes and fragile boundary pixels. The included analyzer has a runnable concave-shape self-check.

## Recommended implementation in this app

1. Replace `FocusGrid`/`FocusCell` with a normalized `FocusTarget(x, y)`; a box can be included only for validation and guide sizing.
2. Send the existing clean observation JPEG once. Do not generate or send the second gridded JPEG.
3. Prompt for a point on textured, visible target material with margin from every edge. Explicitly say that hollow-object centers and empty space are invalid.
4. Validate finite normalized coordinates and, if present, require the point to lie inside the returned box. Reject malformed or out-of-frame results.
5. Feed the fractions into the existing `camera.focusAt(xFraction, yFraction)` path and show the existing 72 dp focus indicator at that point.
6. Make the same contract change in both the standalone visual request and the multi-action command planner. Otherwise “focus on…” embedded in commands will still use the grid.
7. Keep tap-to-focus/local coaching as the fallback when the target is absent, ambiguous, or the camera cannot lock focus.

This fits the current CameraX implementation: it already turns normalized preview fractions into a CameraX metering point and reports whether autofocus locked. Pixel-perfect segmentation is unnecessary for that operation.

Qwen sometimes used its learned `point_2d`/`bbox_2d` field names instead of the requested web-test schema. The production parser should either enforce one JSON shape with one retry, or accept exactly those two known shapes and validate both strictly.

## If a mask is still desired

A mask is useful if the UI is meant to outline or highlight the whole object, not merely focus the camera. In that case:

- Use a real text-prompted segmentation model, such as Grounded-SAM, because it returns a pixel-aligned mask by contract.
- Alibaba Cloud documents Grounded-SAM as a PAI deployment, not as a simple free Model Studio vision endpoint.
- Model Studio's documented `image-instance-segmentation` service is human-only, so it does not cover keyboards, toys, or boomerangs.
- Qwen-Image 2.0 can remain an experiment, but require matching aspect ratio, disable prompt expansion, ask for pure black/white output without resize or redraw, validate foreground area/component dominance, and fall back when validation fails.

Do not put Qwen-Image generation in the shutter-critical path. Its free quota is useful for prototyping, but it is slow, nondeterministic, and billed per generated image after the trial quota.

## Fixtures and reproducibility

- `fixtures/boomerang.jpg`: public-domain source image
- `fixtures/boomerang-ground-truth.png`: exact alpha-mask ground truth
- `fixtures/keyboard-scene.jpg`: keyboard scene
- `fixtures/two-toys.png`: panda/teddy scene crop from the project demo assets
- `qwen-image-2-boomerang-rendered.png`: first Qwen-Image attempt
- `qwen-image-2-boomerang-rendered-v2-raw.png`: strict Qwen-Image attempt
- `analyze_masks.py`: threshold, connected-component, IoU, and safe-point analysis

Run from the repository root:

```powershell
python outputs/focus-mask-study/analyze_masks.py
```

## Sources

- Alibaba Cloud Model Studio vision and visual grounding: <https://help.aliyun.com/en/model-studio/vision>
- Qwen vision model localization formats: <https://help.aliyun.com/en/model-studio/vision-model/>
- Qwen image editing API: <https://help.aliyun.com/en/model-studio/qwen-image-edit-api>
- Model Studio image instance segmentation: <https://help.aliyun.com/en/model-studio/image-instance-segmentation>
- PAI Grounded-SAM deployment: <https://help.aliyun.com/en/pai/use-cases/deploy-grounded-sam-model-for-image-segmentation-and-pre-labeling>
- Model Studio pricing and free quotas: <https://help.aliyun.com/en/model-studio/model-pricing>
- MediaPipe Interactive Segmenter: <https://developers.google.com/edge/mediapipe/solutions/vision/interactive_segmenter>

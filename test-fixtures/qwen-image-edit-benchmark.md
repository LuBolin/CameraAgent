# Qwen Image 3.0 edit benchmark

Run against Alibaba Cloud Model Studio on 2026-09-10 from commit `f6e1e5f`.
Each configuration used the same three scenes and two instructions per scene.
All 24 final requests returned an image. Times include request upload, generation,
response, and result download.

| Model | Output | Mean | Median | Range |
| --- | ---: | ---: | ---: | ---: |
| `qwen-image-3.0` | 1K | 18.9 s | 18.4 s | 12.8–25.3 s |
| `qwen-image-3.0` | 2K | 34.3 s | 33.1 s | 28.1–47.1 s |
| `qwen-image-3.0-pro` | 1K | 18.2 s | 14.6 s | 12.5–37.6 s |
| `qwen-image-3.0-pro` | 2K | 35.3 s | 36.7 s | 28.8–38.8 s |

Raw final latencies in fixture/prompt order (`scenery-1`, `scenery-2`,
`person-object-1`, `person-object-2`, `many-people-1`, `many-people-2`):

| Model | Output | Milliseconds |
| --- | --- | --- |
| `qwen-image-3.0` | 1K | 12777, 15946, 25267, 19385, 22896, 17364 |
| `qwen-image-3.0` | 2K | 30103, 32505, 47112, 34583, 33676, 28076 |
| `qwen-image-3.0-pro` | 1K | 12527, 12847, 14753, 37643, 16725, 14508 |
| `qwen-image-3.0-pro` | 2K | 31744, 28836, 37840, 38757, 38780, 35565 |

Both models followed the meaningful scenery, object-removal, color, group-tone,
and person-removal instructions comparably in visual review. One person/object
color prompt was not useful for judging compliance because its generated source
already had a black bicycle despite describing it as red. The six samples per
configuration are too few to distinguish model latency from provider variance.

## Decision

Continue using `qwen-image-3.0-pro` at approximately 1K for now. The regular
model did not show a reliable speed advantage, while genuine 2K requests were
about 1.8–1.9 times slower on average.

The ignored local evidence is under `outputs/qa/qwen-image-edit-benchmark/`.

# Emulator UI visual proof

Captured on the API 34 emulator after the September 2026 polish pass.

- `camera.png`: live CameraX preview over the emulator's synthetic scene.
- `guide.png`: photography guide overview.
- `settings.png`: Settings bottom sheet.
- `review.png`: saved-capture review.

The four states are visually coherent: controls remain legible over the mock scene, spacing is balanced, the guide hierarchy is clear, and the review keeps the photo unobstructed. No layout change was needed after inspection.

Verification at this checkpoint: 70 emulator instrumentation tests passed (two opt-in external fixture/provider checks skipped), 47 capture UI tests passed again after the final Qwen-only cleanup, and unit tests, lint, and both debug APK builds passed.

Not completed: live Qwen quality/latency testing and downloaded-photo fixture testing still require their opt-in credentials/fixtures. Physical-device-only camera and acoustic gates were not rerun during this emulator pass.

# Core execution plan

Work proceeds in this order. Existing capture, review, settings, visual interpretation, and person-specific guidance stay in place.

1. **Qualify controllable camera changes — complete.** Record exact capabilities, apply/reset behavior, and physical-device evidence for focus, exposure compensation, digital zoom, and coarse white balance. Keep ISO and shutter unavailable until they pass a coupled manual-exposure gate.
2. **Map comments to safe Control Intents — complete.** Convert explicit whole-frame complaints into bounded local intents; clarify blur, distance, negation, conflicts, multiple complaints, and named-region requests; keep unsupported ISO/shutter/noise requests advisory.
3. **Add the strict hosted-model classifier — complete.** The configured Alibaba Cloud model may return only an allowlisted semantic intent or clarification reason. It cannot return values, coordinates, settings, commands, or prose. Android reparses and plans locally. The fixed Qwen snapshot passed the redacted `12/12` live contract gate.
4. **Test the complete comment-to-camera chain — complete.** Deterministic tests cover classifier result → local plan → explicit Apply → fresh provenance/capability check → verification → reset. The physical OnePlus passed `too zoomed out → 1.00x → Apply 1.25x → verify → Reset 1.00x`.
5. **Test voice end to end — next.** Run typed and injected recognition tests, then play representative complaints through the computer speakers into Android's on-device speech recognizer and verify the same safe chain.
6. **Run final product gates.** Re-run JVM, API 31, API 34, physical-camera, UI/accessibility, permissions, capture/review, credential/security, lint, and release-build checks; update evidence and documentation.

Every green milestone is committed on `codex/core-functionality`. The model/API key remains outside Git.

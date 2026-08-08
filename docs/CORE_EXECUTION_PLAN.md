# Core execution plan

Work proceeds in this order. Existing capture, review, settings, visual interpretation, and person-specific guidance stay in place.

1. **Qualify controllable camera changes — complete.** Focus, exposure compensation, digital zoom, and coarse white balance have apply/reset behavior and physical-device evidence. Generic focus accepts any preview tap and never requires a face. ISO and shutter remain unavailable until they pass a coupled manual-exposure gate.
2. **Map comments to safe Control Intents — complete.** Convert explicit whole-frame complaints into bounded local intents; clarify blur, distance, negation, conflicts, multiple complaints, and named-region requests; keep unsupported ISO/shutter/noise requests advisory.
3. **Add the strict hosted-model classifier — complete.** The configured Alibaba Cloud model may return only an allowlisted semantic intent or clarification reason. It cannot return values, coordinates, settings, commands, or prose. Android reparses and plans locally. The prior content-hash-matched semantic run passed `12/12`; a final clean-provenance rerun is pending.
4. **Test the complete comment-to-camera chain — complete.** Deterministic tests cover classifier result → local plan → explicit Apply → fresh provenance/capability check → verification → reset. The physical OnePlus passed `too zoomed out → 1.00x → Apply 1.25x → verify → Reset 1.00x`.
5. **Test voice end to end — partially complete.** Typed and injected voice results reach the safe intent chain. The secondary-display speaker reaches the OnePlus microphone, and its captured audio is intelligible and locally transcribed. Android's on-device and standard recognition providers detect both synthetic and human speech samples but return no hypothesis, so the acoustic on-device speech-to-text → intent → Apply gate is not green on this device/provider combination.
6. **Run final product gates — in progress.** JVM passed `131/131`; API 31 and API 34 each passed `40/40`; the OnePlus passed all six physical CameraX tests; API 31/API 34/physical UI inspection found no clipping; and lint passed with 0 errors and 11 warnings. Final clean-provenance Qwen, credential/security, and release-artifact reconciliation remain.

Every green milestone is committed on `codex/core-functionality`. The model/API key remains outside Git.

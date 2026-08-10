---
status: accepted
---

# Call Bailian Qwen directly from the private demo app

The single-device demo calls Qwen3.7 Flash through Alibaba Cloud Model Studio (Bailian) in China (Beijing) with an operator-entered key. Qwen is the primary request planner when enabled and receives the complete typed/transcribed request, one reduced clean CameraX frame, and an in-memory labelled grid copy. It may return only a schema-v3 ordered list of allowlisted semantic adjustments, camera selection, grid-cell focus, and capture actions, or an allowlisted clarification. Android strictly validates that result, converts semantic adjustments into device-supported values, owns every confirmation and camera call, and falls back to limited local wording rules when the model is unavailable. The prompts, fixed schema, response validation, and latency assumptions are tuned specifically for Qwen3.7 Flash; no other model or provider adapter is implemented. Direct credentials and provider retention remain unsuitable for a distributed production app, which should use a backend proxy. This supersedes ADR 0003.

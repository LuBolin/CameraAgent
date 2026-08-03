# AI Components and Lower-Cost Model Options

Status: prototype defaults selected; production architecture unchanged  
Last reviewed: 2026-08-04

This note records the selected prototype AI defaults and lower-cost alternatives. It does not replace [ADR 0001](../docs/adr/0001-keep-camera-authority-on-device.md): production camera pixels remain on-device unless that decision and its consent design are explicitly revised.

## Selected prototype defaults

- Speech-to-text: Android's on-device `SpeechRecognizer`. It has no per-call fee. Keep typed input when the device or requested language has no installed on-device recognizer; never fall back silently to cloud speech recognition.
- Multimodal interpretation: hosted `GLM-4.6V-Flash`. Send the completed Complaint and one current frame through the owned gateway, then accept only the existing fixed intent enum. All planning, capability checks, camera control, and verification remain on-device.

`GLM-4.6V-Flash` is the prototype default because Z.AI currently lists its input and output as free. Treat that as changeable pricing, not a permanent product assumption. Keep the provider key in the gateway, never in the APK.

For live-user prototype testing, uploading the frame is a consent-gated exception to the current no-pixel-upload ADR. Send no audio, EXIF, content URI, face landmarks, tracking ID, or hardware ID. Until that consent copy and gateway path exist, use only stored non-sensitive fixtures. `MiniCPM-V 4.6` remains the first fallback to test if hosted pricing, privacy, availability, or latency makes GLM unsuitable.

## Minimal pipeline

```text
push-to-talk -> on-device speech recognition -> final Complaint text
CameraX frame -> local measurements / face detection / sensors -> FrameObservation
Complaint + FrameObservation -> local rules or enum-only text classifier
validated intent -> on-device planner -> camera/user action -> verification
```

The multimodal prototype path, after explicit consent:

```text
final Complaint + one current frame -> optional VLM -> fixed intent enum
fixed intent + device capabilities -> on-device planner -> action -> verification
```

Do not stream preview frames to a model. Capture one frame when the speech turn completes. The VLM may suggest only an existing intent; it must not choose arbitrary ISO, shutter, white-balance, zoom, or movement values, and it never controls the camera directly.

## Free speech-to-text choices

These are automatic speech-recognition systems, not general-purpose LLMs.

| Choice | Cost and deployment | Use it when | Main limitation |
|---|---|---|---|
| Android on-device `SpeechRecognizer` | No per-call fee; native Android API | **Default for this Android MVP** | Availability and accuracy depend on the device, installed language, and recognition service |
| `sherpa-onnx` with Zipformer, Paraformer, or another supported ASR model | Open source; fully offline; Android/Kotlin; streaming and non-streaming | A bundled and repeatable recognizer is required across test devices | Adds native binaries and model files; the app owns performance tuning |
| `whisper.cpp` | Open source; offline; Android; quantized Whisper models | Broad multilingual accuracy matters more than package size and latency | Heavier CPU, memory, and battery use than the native recognizer on many phones |
| FunASR / Paraformer or SenseVoice | Open source; strong Chinese ecosystem; streaming options | Mandarin, Chinese-English code-switching, or a self-hosted ASR backend is central | More integration and deployment work than the Android-native path |

The lazy implementation is the first row. Add another recognizer only after the exact demo phone, languages, accents, and noise conditions fail an acceptance recording set.

## Lower-cost multimodal options

Provider benchmark claims are not rankings for Photo Helper. "Open" below means weights/code are available from the linked project; licensing and redistribution terms must still be checked before shipping.

| Model | Origin | Cheapest practical route | Why it belongs on the shortlist | Main catch |
|---|---|---|---|---|
| `GLM-4.6V-Flash` | Z.AI, China | Hosted API currently listed as free; 9B open model also available | Fastest zero-API-cost experiment; image understanding, grounding, and function calling | Free pricing may change; hosted use sends the frame off-device |
| `MiniCPM-V 4.6` | OpenBMB, China | Local Android/CPU/GPU; official free API for trials | Best first on-device candidate; official Android support and roughly 2 GB GGUF / 3-4 GB GPU variants | Real-phone latency, thermals, and APK/model delivery still need measurement |
| `Qwen3-VL-4B-Instruct` | Alibaba Qwen, China | Self-host; use the 2B variant if memory is tighter | Strong general baseline with 2B, 4B, 8B, and larger variants and a broad inference ecosystem | A 4B VLM is still substantial for continuous mobile use; prefer one still frame |
| `DeepSeek-VL2-Tiny` | DeepSeek, China | Self-host; 1B activated-parameter variant | Compact older baseline for image QA, OCR, charts, and grounding | Activated parameters understate total model storage; newer models may be better |
| `Kimi-VL-A3B-Instruct` | Moonshot AI, China | Self-host with vLLM | Efficient inference and strong multimodal reasoning; about 3B active parameters | It is a 16B-total MoE model, so memory footprint is not that of a dense 3B mobile model |
| `InternVL3.5-4B` or `InternVL3.5-8B` | OpenGVLab / Shanghai AI Lab, China | Self-host on a small GPU server | Mature multimodal family with standard Transformers-format releases | Better suited to a server than this Android MVP; 4B and 8B choices total about 4.7B and 8.5B parameters |
| `MiMo-VL-7B-RL-2508` | Xiaomi, China | Self-host | Apache-2.0 model focused on visual reasoning and grounding | Seven-billion-parameter server deployment is harder to justify for a fixed intent list |
| `STEP3-VL-10B` | StepFun, China | Self-host on a GPU server | Apache-2.0 model emphasizing visual perception, spatial reasoning, OCR, and grounding | Heavier than the smaller candidates; official quick start currently expects BF16 |
| `Doubao-1.5-vision-pro` | ByteDance / Volcengine, China | Paid hosted API | Managed image, video, and text input with little infrastructure work | Requires an external account/region check, variable pricing, and sending pixels to the provider |
| `SmolVLM2-2.2B-Instruct` | Hugging Face, non-Chinese | Self-host or experiment with a smaller local build | Apache-2.0, compact, and designed for image/video understanding with low memory | Lower ceiling than larger models and no ready-made Photo Helper Android integration |

## Selection order

1. Prototype with on-device `SpeechRecognizer` plus `GLM-4.6V-Flash` on stored, non-sensitive fixtures.
2. Add the consent-gated one-frame gateway path before testing with live users.
3. Keep the deterministic local pipeline as the offline and provider-failure path.
4. If GLM is unsuitable, try `MiniCPM-V 4.6` on the exact Android device.
5. Test larger or more specialized models only when a measured failure remains.

Before changing the ADR, evaluate every candidate on the same small set of real frame-and-Complaint pairs. Record fixed-intent accuracy, invalid-output rate, unsafe-action rate, p50/p95 latency, peak memory, battery/thermal behavior, and cost per completed complaint. Generic model benchmarks do not replace this test.

## Primary sources

- [Android `SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [`sherpa-onnx`](https://github.com/k2-fsa/sherpa-onnx)
- [`whisper.cpp`](https://github.com/ggml-org/whisper.cpp)
- [FunASR](https://github.com/modelscope/FunASR)
- [Z.AI pricing](https://docs.z.ai/guides/overview/pricing)
- [GLM-V](https://github.com/zai-org/GLM-V)
- [MiniCPM-V 4.6](https://github.com/OpenBMB/MiniCPM-V)
- [Qwen3-VL](https://github.com/QwenLM/Qwen3-VL)
- [DeepSeek-VL2](https://github.com/deepseek-ai/DeepSeek-VL2)
- [Kimi-VL](https://github.com/MoonshotAI/Kimi-VL)
- [InternVL](https://github.com/OpenGVLab/InternVL)
- [MiMo-VL](https://github.com/XiaomiMiMo/MiMo-VL)
- [STEP3-VL-10B](https://github.com/stepfun-ai/Step3-VL-10B)
- [Doubao visual understanding](https://www.volcengine.com/docs/6492/2165093?lang=en)
- [SmolVLM2](https://huggingface.co/blog/smolvlm2)

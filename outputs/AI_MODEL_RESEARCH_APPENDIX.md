# Unselected AI Model Research Appendix

Status: research inventory only; no implementation commitment
Last reviewed: 2026-08-04

This appendix preserves earlier candidates without making them part of the Android architecture. None is selected, integrated, benchmarked for Photo Helper, or approved for production. Generic benchmark and size claims do not establish performance on the two-family camera task.

The selected hosted path is Qwen `qwen3.7-flash-2026-07-15` through Alibaba Cloud Model Studio, recorded in [ADR 0004](../docs/adr/0004-call-bailian-qwen-directly-from-private-demo.md); it is not an entry in this unselected inventory. Its current fixture contract is color Prompt/schema v2 plus face Prompt/schema v3 with boolean `distortionVisible`. The binary face contract replaced a two-label enum after the live fixture gate exposed output-label-order bias; Android now owns the boolean-to-intent mapping.

For every entry, repository/code licensing and model-weight/hosted-service terms are separate. `Not verified` means exactly that; a repository license must never be assumed to cover weights, datasets, trademarks, hosted processing, or app redistribution.

## Unselected speech-recognition research

| Candidate | Possible reason to revisit | Repository/code license | Model weights/service terms | Current decision |
|---|---|---|---|---|
| `sherpa-onnx` with a supported ASR model | A bundled, repeatable offline recognizer becomes necessary across demo devices | Not verified—review the official repository LICENSE | Not verified—review the exact ASR model card/license separately | Do not add until native `SpeechRecognizer` fails a recorded acceptance set |
| `whisper.cpp` | Broad multilingual offline recognition matters more than size/CPU/battery | Not verified—review the official repository LICENSE | Not verified—review the exact Whisper weight/license/source separately | Unselected |
| FunASR / Paraformer / SenseVoice | Mandarin or Chinese-English code-switching becomes a primary requirement | Not verified—review each official repository LICENSE | Not verified—review the exact model card/weight terms and any hosted terms separately | Unselected |

## Unselected multimodal research

| Candidate | Possible reason to revisit | Repository/code license | Model weights or hosted terms | Current decision |
|---|---|---|---|---|
| Qwen3-VL 2B/4B | A self-hosted small-model comparison is justified by a measured failure | Not verified—review official repository LICENSE | Not verified—review the exact model-card/weight license separately | Unselected; no Android integration promised |
| DeepSeek-VL2-Tiny | An older compact self-host baseline is useful for controlled comparison | Not verified—review official repository LICENSE | Not verified—review exact weights/model terms separately | Unselected |
| Kimi-VL-A3B-Instruct | A server-side MoE comparison is justified | Not verified—review official repository LICENSE | Not verified—review exact weights/model terms separately | Unselected; total storage is not dense-3B scale |
| InternVL3.5 4B/8B | A mature server inference ecosystem becomes more important than mobile size | Not verified—review official repository LICENSE | Not verified—review exact weights/model terms separately | Unselected |
| MiMo-VL-7B-RL-2508 | A self-hosted visual-reasoning comparison is justified | Not verified—review official repository LICENSE | Not verified—review exact weights/model terms separately | Unselected |
| STEP3-VL-10B | A heavier GPU-server baseline is explicitly funded | Not verified—review official repository LICENSE | Not verified—review exact weights/model terms separately | Unselected |
| Doubao visual understanding | Another hosted-provider region/pricing comparison is explicitly required | No repository selected | Not verified—review the exact Volcengine account, region, DPA, retention, pricing, and model terms | Unselected; would require a new privacy/provider decision |
| SmolVLM2-2.2B-Instruct | A compact non-Chinese self-host comparison is required | Not verified—review official repository LICENSE | Not verified—review exact model-card/weight terms separately | Unselected; no Photo Helper Android package exists |

## Re-entry rule

A candidate returns to the implementation note only after:

1. a current official source and both relevant license/terms layers are archived;
2. the exact model runs on the fixed Photo Helper family contracts, including color schema v2 and both boolean outcomes plus clarification for face schema v3;
3. it solves a measured failure of the selected Qwen service or the local pipeline;
4. its latency, memory, thermals, privacy, and cost are measured on the intended deployment; and
5. adopting it is simpler than deleting the hosted visual feature.

Until then, the implementation has one hosted selection (`qwen3.7-flash-2026-07-15`). MiniCPM-V 4.6 remains an unimplemented research candidate; there is no model-switching abstraction.

## Official starting points for future verification

- [`sherpa-onnx`](https://github.com/k2-fsa/sherpa-onnx)
- [`whisper.cpp`](https://github.com/ggml-org/whisper.cpp)
- [FunASR](https://github.com/modelscope/FunASR)
- [Qwen3-VL](https://github.com/QwenLM/Qwen3-VL)
- [DeepSeek-VL2](https://github.com/deepseek-ai/DeepSeek-VL2)
- [Kimi-VL](https://github.com/MoonshotAI/Kimi-VL)
- [InternVL](https://github.com/OpenGVLab/InternVL)
- [MiMo-VL](https://github.com/XiaomiMiMo/MiMo-VL)
- [STEP3-VL-10B](https://github.com/stepfun-ai/Step3-VL-10B)
- [Doubao visual understanding](https://www.volcengine.com/docs/6492/2165093?lang=en)
- [SmolVLM2](https://huggingface.co/blog/smolvlm2)

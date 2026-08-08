MQVOX V4
========
Goal: fix the v3 latency/accuracy architecture, not merely tune it.

Removed:
- Qwen translation model
- llama.cpp translation runtime
- Whisper Base via sherpa-onnx ASR

Added:
- whisper.cpp Small Q5_1 with forced Arabic/English language
- dedicated OPUS-MT Arabic<->English models
- CTranslate2 INT8 ARM64/Ruy translation runtime
- SentencePiece native tokenization
- separate ASR / MT / TTS / TOTAL timing

Build online using .github/workflows/main.yml.
Resulting release asset: MQVOX-v4-debug.apk

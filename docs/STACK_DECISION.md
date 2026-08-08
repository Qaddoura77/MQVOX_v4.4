# MQVOX v4 stack decision

| Layer | V4 selection | Reason |
|---|---|---|
| UI | Native Android/Kotlin | Preserve the already working APK/device path and minimize framework overhead. |
| ASR | whisper.cpp v1.8.6 + Small Q5_1 | Better multilingual/Arabic capacity than Tiny/Base while remaining a ~190 MB quantized mobile model. Manual language forcing removes auto-detection overhead/error. |
| MT | Helsinki-NLP OPUS-MT tc-big ar-en / en-ar | Dedicated neural MT rather than a general LLM; stronger published translation benchmarks and deterministic task behavior. |
| MT runtime | CTranslate2 v4.7.2 INT8 + Ruy | Optimized Transformer inference, ARM64/AArch64 INT8 support, MIT runtime. |
| Tokenizer | SentencePiece v0.2.1 | Native tokenizer used by OPUS-MT, Apache-2.0. |
| TTS | Supertonic 3 INT8 via sherpa-onnx | Retains existing offline TTS while ASR/MT are being validated; legal gate remains open. |

## Deliberate scope reduction

Only Arabic ↔ English is exposed in V4. This is a validation strategy, not a product-scope change. Malay, Portuguese, and Turkish are added only after the core pair passes accuracy and latency requirements.

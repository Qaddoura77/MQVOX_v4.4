# MaenQaddouraVOX (MQVOX) — Android v4

© 2026 Maen Qaddoura. All rights reserved.
Contact: maen.maen@gmail.com
Package: `com.maenqaddoura.mqvox`

## V4 purpose

V4 is a focused Arabic ↔ English offline performance/accuracy checkpoint for the Motorola Moto G54. It deliberately removes the general-purpose LLM translation backend used in v3.

Pipeline:

`Microphone -> whisper.cpp Small Q5_1 -> dedicated OPUS-MT/CTranslate2 INT8 -> text -> Supertonic 3 offline TTS`

There is no cloud inference and the Android manifest does not request `INTERNET` permission.

## Why V4 is different

- ASR: `whisper.cpp` v1.8.6 with multilingual `Small Q5_1`; the selected language (`ar` or `en`) is forced rather than auto-detected.
- MT: dedicated Helsinki-NLP `opus-mt-tc-big-ar-en` and `opus-mt-tc-big-en-ar`, converted to CTranslate2 INT8 during the GitHub build. No Qwen, no llama.cpp, no reasoning tokens.
- ARM64 MT: CTranslate2 v4.7.2 with the Ruy backend.
- Tokenization: SentencePiece v0.2.1.
- TTS: existing local Supertonic 3 INT8 through sherpa-onnx; test-stage licensing remains subject to commercial review.
- Timing: UI reports ASR, MT, TTS, and total latency independently.
- Memory: only the currently used translation direction is resident. Arabic -> English is warmed on app startup.

## Current language scope

V4 intentionally exposes only Arabic and English. Malay, Portuguese, and Turkish are not removed from the product roadmap; they are deferred until Arabic ↔ English meets the speed/accuracy checkpoint.

## Online APK build

Create a new private GitHub repository, upload the *contents* of this folder at repository root, then run:

`Actions -> Build MQVOX v4 debug APK -> Run workflow`

On success, download `MQVOX-v4-debug.apk` from the generated GitHub Release.

The workflow downloads/converts the free offline models during the build, so this source ZIP remains small while the resulting APK is much larger.

## First-device test

1. Install the APK on the Moto G54.
2. Launch and wait for `Ready · Arabic ↔ English · offline`.
3. Turn Auto Speak off for the first diagnostic tests.
4. Test Arabic -> English with complete sentences and short phrases.
5. Record the displayed `ASR`, `MT`, and `TOTAL` times and the exact recognized/translated text.
6. Repeat English -> Arabic.
7. After text is correct, enable Auto Speak and test TTS.
8. Repeat with airplane mode enabled and Wi-Fi disabled.

## Important status

This repository is a device-test build checkpoint, not a commercial-store release. The dedicated OPUS-MT models are CC-BY-4.0 and require attribution. Supertonic 3 uses OpenRAIL-M and remains flagged for legal review before a proprietary commercial release.

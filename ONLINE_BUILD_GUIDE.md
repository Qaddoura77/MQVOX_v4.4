# MQVOX v4 online APK build

1. Create a new **private** GitHub repository, e.g. `MQVOX_v4`.
2. Extract this ZIP locally.
3. Upload the **contents** of the extracted folder, not the containing folder. At repository root you must directly see `app`, `.github`, `scripts`, `build.gradle.kts`, and `settings.gradle.kts`.
4. Open **Actions**.
5. Select **Build MQVOX v4 debug APK**.
6. Select **Run workflow** on `main`.
7. The first build can take substantial time because it downloads Whisper, converts two OPUS-MT models to INT8, downloads the TTS model/runtime, and compiles whisper.cpp + CTranslate2 + SentencePiece for ARM64.
8. If successful, open **Releases** and download `MQVOX-v4-debug.apk`.
9. Install it on the Motorola Moto G54.

If the build fails, copy only the first `FAILURE: Build failed with an exception` / compiler-error section; do not send the entire Gradle stack trace.

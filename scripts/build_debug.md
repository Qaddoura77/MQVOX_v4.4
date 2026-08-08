# Build commands

With Android SDK 35, an NDK compatible with CMake 3.22+, Java 17/21 and Gradle available:

```bash
python scripts/verify_no_internet_permission.py
# Populate models/runtime first (see bootstrap + TTS gate).
./gradlew :app:assembleDebug
```

Expected output:

`app/build/outputs/apk/debug/MQVOX-debug.apk`

Install:

```bash
adb install -r app/build/outputs/apk/debug/MQVOX-debug.apk
```

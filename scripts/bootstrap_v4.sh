#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/models"
JNILIB="$ROOT/app/src/main/jniLibs/arm64-v8a"
LOCKS="$ROOT/model-locks"
THIRD="$ROOT/third_party"
mkdir -p "$THIRD" "$ASSETS/asr/whisper" "$ASSETS/mt/ar-en" "$ASSETS/mt/en-ar" "$ASSETS/tts/supertonic-3" "$JNILIB" "$LOCKS"

for c in curl git tar sha256sum python3; do command -v "$c" >/dev/null || { echo "Missing command: $c"; exit 1; }; done

clone_tag() {
  local url="$1" tag="$2" dir="$3"
  if [ ! -d "$dir/.git" ]; then
    git clone --depth 1 --branch "$tag" --recursive --shallow-submodules "$url" "$dir"
  fi
}

clone_tag https://github.com/ggml-org/whisper.cpp.git v1.8.6 "$THIRD/whisper.cpp"
clone_tag https://github.com/OpenNMT/CTranslate2.git v4.7.2 "$THIRD/CTranslate2"
clone_tag https://github.com/google/sentencepiece.git v0.2.1 "$THIRD/sentencepiece"

# Android NDK compatibility patch for CTranslate2 v4.7.2.
# Android defines __linux__, but pthread_setaffinity_np is only exposed by Bionic
# from API 36. MQVOX targets API 28, so Linux pthread affinity must be disabled
# on Android while keeping the normal CTranslate2 worker thread pool enabled.
python3 - "$THIRD/CTranslate2/src/thread_pool.cc" <<'PYCT2PATCH'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text(encoding="utf-8")
needle = "#if !defined(__linux__) || defined(_OPENMP)"
replacement = "#if defined(__ANDROID__) || !defined(__linux__) || defined(_OPENMP)"
if replacement in s:
    print("CTranslate2 Android affinity patch already present")
elif needle in s:
    p.write_text(s.replace(needle, replacement, 1), encoding="utf-8")
    print("Applied CTranslate2 Android affinity compile patch:", p)
else:
    raise SystemExit("ERROR: expected CTranslate2 v4.7.2 affinity guard not found")
PYCT2PATCH

# Prove the Android guard was applied before large model conversion/build work.
grep -n -A7 -B2 "defined(__ANDROID__)" "$THIRD/CTranslate2/src/thread_pool.cc"

# whisper.cpp multilingual Small Q5_1: chosen for better Arabic accuracy than Tiny/Base.
WHISPER="$ASSETS/asr/whisper/ggml-small-q5_1.bin"
if [ ! -f "$WHISPER" ]; then
  curl -fL --retry 4 -o "$WHISPER" \
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin?download=true"
fi
echo "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb  $WHISPER" | sha256sum -c -

# Convert the official Helsinki-NLP dedicated Arabic<->English Marian models to
# CTranslate2 INT8. Conversion happens only during the online build; the phone
# contains the converted offline models and needs no Python/Torch.
# CTranslate2 4.7.x added compatibility with Transformers v5 and its
# modern `dtype=` model-loading API. Install CPU PyTorch first, then a
# pinned Transformers v5 converter stack.
python3 -m pip install --disable-pip-version-check --quiet \
  "torch==2.7.1" --index-url https://download.pytorch.org/whl/cpu
python3 -m pip install --disable-pip-version-check --quiet \
  "ctranslate2==4.7.2" \
  "transformers==5.8.1" \
  "sentencepiece==0.2.1" \
  "huggingface_hub==1.5.0" \
  "safetensors>=0.7.0,<0.8"

# Fail early if the converter stack drifts back to an incompatible version.
python3 - <<'PYVERS'
import ctranslate2, transformers, huggingface_hub, torch
print("Converter environment:")
print("  CTranslate2:", ctranslate2.__version__)
print("  Transformers:", transformers.__version__)
print("  huggingface_hub:", huggingface_hub.__version__)
print("  PyTorch:", torch.__version__)
major = int(transformers.__version__.split(".", 1)[0])
if major < 5:
    raise SystemExit("ERROR: MQVOX v4.1 requires Transformers 5.x with CTranslate2 4.7.2")
PYVERS

export PATH="$HOME/.local/bin:$PATH"
export HF_HUB_DISABLE_XET=1

convert_mt() {
  local model="$1" key="$2"
  local out="$ASSETS/mt/$key/ct2"
  if [ ! -f "$out/model.bin" ]; then
    rm -rf "$out"
    ct2-transformers-converter \
      --model "$model" \
      --output_dir "$out" \
      --quantization int8 \
      --copy_files source.spm target.spm \
      --force
  fi
  cp "$out/source.spm" "$ASSETS/mt/$key/source.spm"
  cp "$out/target.spm" "$ASSETS/mt/$key/target.spm"
  sha256sum "$out/model.bin" > "$LOCKS/${key}-ct2-model.sha256"
}

convert_mt "Helsinki-NLP/opus-mt-tc-big-ar-en" "ar-en"
convert_mt "Helsinki-NLP/opus-mt-tc-big-en-ar" "en-ar"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

# sherpa-onnx is retained only for Supertonic 3 TTS in v4.
curl -fL --retry 4 -o "$TMP/sherpa-android.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-v1.13.4-android.tar.bz2"
sha256sum "$TMP/sherpa-android.tar.bz2" > "$LOCKS/sherpa-onnx-v1.13.4-android.sha256"
mkdir "$TMP/sherpa"; tar -xjf "$TMP/sherpa-android.tar.bz2" -C "$TMP/sherpa"
for lib in libonnxruntime.so libsherpa-onnx-jni.so; do
  f="$(find "$TMP/sherpa" -type f -path '*arm64-v8a*' -name "$lib" | head -1)"
  [ -n "$f" ] || { echo "Could not locate $lib for arm64-v8a"; exit 1; }
  cp "$f" "$JNILIB/$lib"
done

curl -fL --retry 4 -o "$TMP/tts.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
sha256sum "$TMP/tts.tar.bz2" > "$LOCKS/sherpa-onnx-supertonic-3-int8.sha256"
mkdir "$TMP/tts"; tar -xjf "$TMP/tts.tar.bz2" -C "$TMP/tts"
for f in duration_predictor.int8.onnx text_encoder.int8.onnx vector_estimator.int8.onnx vocoder.int8.onnx tts.json unicode_indexer.bin voice.bin; do
  src="$(find "$TMP/tts" -type f -name "$f" | head -1)"
  [ -n "$src" ] || { echo "Missing TTS asset $f"; exit 1; }
  cp "$src" "$ASSETS/tts/supertonic-3/$f"
done

python3 "$ROOT/scripts/verify_models.py"
python3 "$ROOT/scripts/verify_no_internet_permission.py"
echo "MQVOX v4.2 bootstrap complete: whisper.cpp Small Q5_1 + dedicated OPUS-MT INT8 + Supertonic 3."

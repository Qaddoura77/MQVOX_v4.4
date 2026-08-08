from pathlib import Path
import hashlib, sys
root = Path(__file__).resolve().parents[1]
required = [
    'app/src/main/assets/models/asr/whisper/ggml-small-q5_1.bin',
    'app/src/main/assets/models/mt/ar-en/ct2/model.bin',
    'app/src/main/assets/models/mt/ar-en/source.spm',
    'app/src/main/assets/models/mt/ar-en/target.spm',
    'app/src/main/assets/models/mt/en-ar/ct2/model.bin',
    'app/src/main/assets/models/mt/en-ar/source.spm',
    'app/src/main/assets/models/mt/en-ar/target.spm',
    'app/src/main/assets/models/tts/supertonic-3/duration_predictor.int8.onnx',
    'app/src/main/assets/models/tts/supertonic-3/text_encoder.int8.onnx',
    'app/src/main/assets/models/tts/supertonic-3/vector_estimator.int8.onnx',
    'app/src/main/assets/models/tts/supertonic-3/vocoder.int8.onnx',
    'app/src/main/assets/models/tts/supertonic-3/tts.json',
    'app/src/main/assets/models/tts/supertonic-3/unicode_indexer.bin',
    'app/src/main/assets/models/tts/supertonic-3/voice.bin',
    'app/src/main/jniLibs/arm64-v8a/libonnxruntime.so',
    'app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so',
]
missing = [x for x in required if not (root / x).is_file()]
if missing:
    print('MISSING V4 ASSETS:')
    print('\n'.join('  ' + x for x in missing))
    sys.exit(2)
whisper = root / required[0]
h = hashlib.sha256(whisper.read_bytes()).hexdigest()
expected = 'ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb'
if h != expected:
    print('Whisper hash mismatch:', h, expected)
    sys.exit(1)
print('PASS: MQVOX v4 required ASR/MT/TTS/runtime assets present; Whisper hash verified.')

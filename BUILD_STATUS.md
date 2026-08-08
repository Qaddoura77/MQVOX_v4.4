# MQVOX v4.4 Build Status

v4.4 addresses the native Android linker failure observed in the v4.3 GitHub Actions log.

Observed v4.3 error:

`ld.lld: error: undefined symbol: __android_log_write`

The failing target was SentencePiece's `spm_decode` executable. SentencePiece v0.2.1 embeds protobuf-lite, whose Android default logger calls `__android_log_write`, but the static `sentencepiece-static` target does not propagate Android's `liblog` dependency to the command-line helper executables.

v4.4 adds Android `log` as an INTERFACE dependency of `sentencepiece-static`. This causes SentencePiece helper targets and MQVOX JNI linking to receive `-llog` transitively.

Previous v4 fixes are retained: explicit workflow triggers, compatible CTranslate2/Transformers conversion environment, Android thread-affinity compatibility patch, and ARM64 PIC handling.

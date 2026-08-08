cmake_minimum_required(VERSION 3.22.1)
project(mqvox_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(ROOT_THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party)
set(WHISPER_DIR ${ROOT_THIRD_PARTY}/whisper.cpp)
set(CT2_DIR ${ROOT_THIRD_PARTY}/CTranslate2)
set(SPM_DIR ${ROOT_THIRD_PARTY}/sentencepiece)

foreach(d ${WHISPER_DIR} ${CT2_DIR} ${SPM_DIR})
    if(NOT EXISTS ${d}/CMakeLists.txt)
        message(FATAL_ERROR "Missing ${d}. Run scripts/bootstrap_v4.sh before Gradle.")
    endif()
endforeach()

# whisper.cpp: CPU-only ARM64 mobile build.
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
add_subdirectory(${WHISPER_DIR} ${CMAKE_CURRENT_BINARY_DIR}/whisper EXCLUDE_FROM_ALL)

# CTranslate2: INT8 on AArch64 using Ruy; no desktop/GPU backends.
set(WITH_MKL OFF CACHE BOOL "" FORCE)
set(WITH_DNNL OFF CACHE BOOL "" FORCE)
set(WITH_ACCELERATE OFF CACHE BOOL "" FORCE)
set(WITH_OPENBLAS OFF CACHE BOOL "" FORCE)
set(WITH_RUY ON CACHE BOOL "" FORCE)
set(WITH_CUDA OFF CACHE BOOL "" FORCE)
set(WITH_CUDNN OFF CACHE BOOL "" FORCE)
set(WITH_HIP OFF CACHE BOOL "" FORCE)
set(ENABLE_CPU_DISPATCH OFF CACHE BOOL "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(OPENMP_RUNTIME "NONE" CACHE STRING "" FORCE)
add_subdirectory(${CT2_DIR} ${CMAKE_CURRENT_BINARY_DIR}/ctranslate2 EXCLUDE_FROM_ALL)

# CTranslate2 v4.7.2 changes the global PIC setting internally while
# configuring Ruy. MQVOX links its static library into libmqvox_native.so,
# therefore the target itself must explicitly be compiled with PIC.
if(NOT TARGET ctranslate2)
    message(FATAL_ERROR "CTranslate2 target 'ctranslate2' was not created")
endif()
set_target_properties(ctranslate2 PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_options(ctranslate2 PRIVATE -fPIC)
if(TARGET ruy)
    set_target_properties(ruy PROPERTIES POSITION_INDEPENDENT_CODE ON)
endif()

# SentencePiece tokenizer, static and self-contained.
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
set(SPM_PROTOBUF_PROVIDER "internal" CACHE STRING "" FORCE)
set(SPM_ABSL_PROVIDER "internal" CACHE STRING "" FORCE)
add_subdirectory(${SPM_DIR} ${CMAKE_CURRENT_BINARY_DIR}/sentencepiece EXCLUDE_FROM_ALL)
if(TARGET sentencepiece-static)
    set_target_properties(sentencepiece-static PROPERTIES POSITION_INDEPENDENT_CODE ON)

    # SentencePiece embeds protobuf-lite. On Android, protobuf-lite's
    # default logger calls __android_log_write(). The upstream static
    # SentencePiece target does not propagate liblog, so its command-line
    # helper targets (spm_decode, spm_encode, etc.) fail to link unless
    # liblog is explicitly exposed as an interface dependency.
    target_link_libraries(sentencepiece-static INTERFACE log)
endif()

add_library(mqvox_native SHARED mqvox_v4_jni.cpp)
set_target_properties(mqvox_native PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_include_directories(mqvox_native PRIVATE
    ${WHISPER_DIR}/include
    ${CT2_DIR}/include
    ${SPM_DIR}/src
)
target_link_libraries(mqvox_native PRIVATE
    whisper
    ctranslate2
    sentencepiece-static
    log
    android
)

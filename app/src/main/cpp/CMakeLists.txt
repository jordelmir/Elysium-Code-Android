cmake_minimum_required(VERSION 3.22.1)
project("elysium_native" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ═══════════════════════════════════════════════════════════════
# LLAMA.CPP INTEGRATION — PRODUCTION BUILD
# ═══════════════════════════════════════════════════════════════
#
# Uses add_subdirectory to compile the full llama.cpp project
# (ggml + llama + all model architectures) as static libraries,
# then links them into our JNI bridge.
#
# The GGUF model file (gemma-4-e4b-it-q4_k_m.gguf) must be
# placed on the device at:
#   /storage/emulated/0/Android/data/com.elysium.code.debug/files/models/
# ═══════════════════════════════════════════════════════════════

set(LLAMA_CPP_DIR "${CMAKE_SOURCE_DIR}/llama.cpp")

# ═══ Configure llama.cpp build options for Android ═══
# Build as static libs — we'll bundle everything into one .so
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Disable features we don't need on Android
set(LLAMA_BUILD_COMMON  ON  CACHE BOOL "" FORCE)  # We need common for tokenization
set(LLAMA_BUILD_TESTS   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_WEBUI   OFF CACHE BOOL "" FORCE)

# ═══ GGML backend config for Android ═══
set(GGML_CUDA    OFF CACHE BOOL "" FORCE)
set(GGML_METAL   OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN  OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL  OFF CACHE BOOL "" FORCE)
set(GGML_SYCL    OFF CACHE BOOL "" FORCE)
set(GGML_RPC     OFF CACHE BOOL "" FORCE)
set(GGML_BLAS    OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP  OFF CACHE BOOL "" FORCE)

# CPU backend — always ON for Android
set(GGML_CPU     ON  CACHE BOOL "" FORCE)

# ARM NEON — always available on arm64-v8a
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(GGML_CPU_AARCH64 ON CACHE BOOL "" FORCE)
endif()

# Suppress git-vars (we don't have .git in the vendored copy)
set(LLAMA_BUILD_NUMBER 0 CACHE STRING "" FORCE)
set(LLAMA_BUILD_COMMIT "embedded" CACHE STRING "" FORCE)

# ggml explicitly requires -fno-finite-math-only
# (Android NDK can inject -ffast-math via toolchain flags)
add_compile_options(-fno-finite-math-only)

# ═══ Build llama.cpp via its own CMake system ═══
add_subdirectory(${LLAMA_CPP_DIR} llama.cpp.build)

# ═══════════════════════════════════════════════════════════════
# ELYSIUM NATIVE LIBRARY (JNI bridge + PTY)
# ═══════════════════════════════════════════════════════════════

add_library(elysium_native SHARED
    pty_bridge.cpp
    llama_bridge.cpp
)

target_include_directories(elysium_native PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/common
    ${LLAMA_CPP_DIR}/src
    ${CMAKE_SOURCE_DIR}
)

# Compiler flags for maximum ARM performance
# NOTE: Do NOT use -ffast-math — ggml requires non-finite math (NaN/inf)
target_compile_options(elysium_native PRIVATE
    -O3
    -DNDEBUG
    -DGGML_USE_CPU
    -D_GNU_SOURCE
)



if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(elysium_native PRIVATE
        -DGGML_USE_CPU_AARCH64
    )
endif()

# Link against llama.cpp static libs + common + system libs
target_link_libraries(elysium_native
    llama
    common
    android
    log
    m
    z
)

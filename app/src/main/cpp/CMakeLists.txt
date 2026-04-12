# Minimum CMake version required by Android NDK
cmake_minimum_required(VERSION 3.22.1)

project("llama-bridge")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# Optimization flags to reduce binary size and improve performance
add_compile_options(-O3 -ffunction-sections -fdata-sections -fvisibility=hidden)
if(NOT MSVC)
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,--exclude-libs,ALL")
endif()

# Include directories
include_directories(llama_source/include)
include_directories(llama_source/src)
include_directories(llama_source/ggml/include)
include_directories(llama_source/ggml/src)
include_directories(llama_source/ggml/src/ggml-cpu)

# Core LLaMA and GGML sources
set(LLAMA_SOURCES
    llama-bridge.cpp
    llama_source/src/llama.cpp
    llama_source/src/llama-batch.cpp
    llama_source/src/llama-model.cpp
    llama_source/src/llama-model-loader.cpp
    llama_source/src/llama-model-saver.cpp
    llama_source/src/llama-context.cpp
    llama_source/src/llama-vocab.cpp
    llama_source/src/llama-sampler.cpp
    llama_source/src/llama-quant.cpp
    llama_source/src/llama-grammar.cpp
    llama_source/src/llama-arch.cpp
    llama_source/src/llama-hparams.cpp
    llama_source/src/llama-mmap.cpp
    llama_source/src/llama-chat.cpp
    llama_source/src/llama-kv-cache.cpp
    llama_source/src/llama-kv-cache-iswa.cpp
    llama_source/src/llama-memory.cpp
    llama_source/src/llama-memory-hybrid.cpp
    llama_source/src/llama-memory-hybrid-iswa.cpp
    llama_source/src/llama-memory-recurrent.cpp
    llama_source/src/llama-adapter.cpp
    llama_source/src/llama-impl.cpp
    llama_source/src/llama-io.cpp
    llama_source/src/llama-cparams.cpp
    llama_source/src/llama-graph.cpp
    llama_source/src/unicode.cpp
    llama_source/src/unicode-data.cpp
)

# All model implementations from the models directory
file(GLOB MODEL_SOURCES llama_source/src/models/*.cpp)
list(APPEND LLAMA_SOURCES ${MODEL_SOURCES})

# GGML Core
list(APPEND LLAMA_SOURCES
    llama_source/ggml/src/ggml.c
    llama_source/ggml/src/ggml.cpp
    llama_source/ggml/src/ggml-alloc.c
    llama_source/ggml/src/ggml-backend.cpp
    llama_source/ggml/src/ggml-backend-reg.cpp
    llama_source/ggml/src/ggml-backend-dl.cpp
    llama_source/ggml/src/ggml-backend-meta.cpp
    llama_source/ggml/src/ggml-quants.c
    llama_source/ggml/src/ggml-threading.cpp
    llama_source/ggml/src/ggml-opt.cpp
    llama_source/ggml/src/gguf.cpp
)

# GGML CPU Backend (Modular)
list(APPEND LLAMA_SOURCES
    llama_source/ggml/src/ggml-cpu/ggml-cpu.cpp
    llama_source/ggml/src/ggml-cpu/ggml-cpu.c
    llama_source/ggml/src/ggml-cpu/hbm.cpp
    llama_source/ggml/src/ggml-cpu/ops.cpp
    llama_source/ggml/src/ggml-cpu/vec.cpp
    llama_source/ggml/src/ggml-cpu/repack.cpp
    llama_source/ggml/src/ggml-cpu/traits.cpp
    llama_source/ggml/src/ggml-cpu/quants.c
    llama_source/ggml/src/ggml-cpu/unary-ops.cpp
    llama_source/ggml/src/ggml-cpu/binary-ops.cpp
)

add_library(llama-bridge SHARED ${LLAMA_SOURCES})

# Architecture-specific modular GGML sources and flags
if (${ANDROID_ABI} MATCHES "arm")
    target_sources(llama-bridge PRIVATE
        llama_source/ggml/src/ggml-cpu/arch/arm/quants.c
        llama_source/ggml/src/ggml-cpu/arch/arm/repack.cpp
        llama_source/ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp
    )
    if (${ANDROID_ABI} MATCHES "arm64-v8a")
        target_compile_definitions(llama-bridge PRIVATE GGML_USE_DOTPROD GGML_USE_I8MM)
        target_compile_options(llama-bridge PRIVATE -march=armv8.2-a+dotprod+i8mm)
    endif()
elseif (${ANDROID_ABI} MATCHES "x86")
    target_sources(llama-bridge PRIVATE
        llama_source/ggml/src/ggml-cpu/arch/x86/quants.c
        llama_source/ggml/src/ggml-cpu/arch/x86/repack.cpp
        llama_source/ggml/src/ggml-cpu/arch/x86/cpu-feats.cpp
    )
    # Ensure AMX is disabled unless explicitly targeted, to avoid missing amx.h errors
    target_compile_definitions(llama-bridge PRIVATE GGML_CPU_HAS_AMX=0)
endif()

target_compile_definitions(llama-bridge PRIVATE
    GGML_VERSION="0.9.11"
    GGML_COMMIT="unknown"
    GGML_USE_CPU
)

find_library(log-lib log)
target_link_libraries(llama-bridge ${log-lib} dl)

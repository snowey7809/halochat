cmake_minimum_required(VERSION 3.22.1)
project(haloai_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Set Android-specific flags
if(ANDROID)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
    
    # ARM NEON optimizations
    if(ANDROID_ABI MATCHES "^arm")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+fp16")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16")
    endif()
endif()

# Use local llama.cpp directory (git submodule)
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Check if llama.cpp directory exists
if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR 
        "llama.cpp not found at ${LLAMA_CPP_DIR}\n"
        "Please run: git submodule add https://github.com/ggerganov/llama.cpp.git app/src/main/cpp/llama.cpp\n"
        "Then: git submodule update --init --recursive"
    )
endif()

message(STATUS "Using llama.cpp from: ${LLAMA_CPP_DIR}")

# Configure llama.cpp build options
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CUDA OFF CACHE BOOL "" FORCE)
set(LLAMA_METAL OFF CACHE BOOL "" FORCE)
set(LLAMA_VULKAN OFF CACHE BOOL "" FORCE)
set(LLAMA_ACCELERATE OFF CACHE BOOL "" FORCE)
set(LLAMA_BLAS OFF CACHE BOOL "" FORCE)
set(LLAMA_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Add llama.cpp subdirectory
add_subdirectory(${LLAMA_CPP_DIR} llama_build EXCLUDE_FROM_ALL)

# JNI Bridge sources
set(JNI_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/jni_bridge.cpp
)

# Create the main shared library
add_library(haloai_native SHARED ${JNI_SOURCES})

# Link against llama library (built by llama.cpp's CMakeLists.txt)
target_link_libraries(haloai_native
    llama
    android
    log
)

target_include_directories(haloai_native PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/common
    ${CMAKE_CURRENT_SOURCE_DIR}
)

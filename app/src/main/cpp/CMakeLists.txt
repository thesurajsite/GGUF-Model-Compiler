cmake_minimum_required(VERSION 3.22.1)
project(llama-android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Pulls in llama.cpp's "src" (the llama lib) and "ggml".
# examples/tools/tests/server are skipped automatically in this context.
add_subdirectory(llama.cpp)

add_library(llama-android SHARED
        llama-android.cpp
)

target_link_libraries(llama-android
        PRIVATE
        llama
        android
        log
)

target_include_directories(llama-android PRIVATE
        llama.cpp/include
        llama.cpp/ggml/include
)
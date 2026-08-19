cmake_minimum_required(VERSION 3.22.1)
project("yatagami")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/opencv/sdk/native/jni")
find_package(OpenCV REQUIRED)

add_library(
    yatagami
    SHARED
    core/common.cpp
    core/buffer_pool.cpp
    core/arena.cpp
    core/scheduler.cpp
    core/preprocessing.cpp
    core/geometry.cpp
    core/doc_classifier.cpp
    core/text_deskew.cpp
    core/deskew.cpp
    core/scene_analysis.cpp
    core/enhancement_tiers.cpp
    core/enhancement.cpp
    jni/detector_jni.cpp
    jni/processor_jni.cpp
    yatagami.cpp
)

find_library(log-lib log)

target_link_libraries(
    yatagami
    ${OpenCV_LIBS}
    jnigraphics
    android
    ${log-lib}
)

include_directories(
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/core
    ${CMAKE_SOURCE_DIR}/jni
    ${OpenCV_INCLUDE_DIRS}
)

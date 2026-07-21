cmake_minimum_required(VERSION 3.13)
project(min2phase_ndk)


set(CMAKE_CXX_STANDARD 14)

include_directories(include)
include_directories(src)

add_library(min2phase_native SHARED
        src/info.cpp
        src/CubieCube.cpp
        src/coords.cpp
        src/Search.cpp
        src/tools.cpp
        src/min2phase.cpp
        native-lib.cpp)

find_library(log-lib log)
target_link_libraries(min2phase_native ${log-lib})

# YOLOv8 + NCNN + OpenCV 配置
set(ncnn_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn-20260526-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

set(OpenCV_DIR ${CMAKE_CURRENT_SOURCE_DIR}/opencv-mobile-4.10.0-android/sdk/native/jni)
find_package(OpenCV REQUIRED)

add_library(yolov8ncnn SHARED
        yolo/yolov8ncnn.cpp
        yolo/Yolov8.cpp
        yolo/yolov8_pose.cpp
        yolo/ndkcamera.cpp
)

# 链接 camera2ndk, mediandk
target_link_libraries(yolov8ncnn
        ncnn
        ${OpenCV_LIBS}
        camera2ndk
        mediandk
        android
        ${log-lib}
)

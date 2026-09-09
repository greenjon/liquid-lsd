cmake_minimum_required(VERSION 3.15)
project(link_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(JNI REQUIRED)
find_package(Threads REQUIRED)

# Ableton Link headers
set(LINK_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/link/include" CACHE PATH "Path to Ableton Link include directory")
set(LINK_ASIO_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/link/modules/asio-standalone/asio/include" CACHE PATH "Path to ASIO include directory")

add_library(link_jni SHARED LinkJni.cpp)

target_include_directories(link_jni PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${LINK_INCLUDE_DIR}
    ${LINK_ASIO_INCLUDE_DIR}
)

target_compile_definitions(link_jni PRIVATE
    LINK_PLATFORM_LINUX=1
    ASIO_STANDALONE=1
)

target_link_libraries(link_jni PRIVATE
    Threads::Threads
)

set_target_properties(link_jni PROPERTIES
    PREFIX ""
    OUTPUT_NAME "link_jni"
)

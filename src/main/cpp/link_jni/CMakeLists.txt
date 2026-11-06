cmake_minimum_required(VERSION 3.15)

# The MSVC runtime is linked statically so link_jni.dll does not need the Visual C++ redistributable.
cmake_policy(SET CMP0091 NEW)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

project(link_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release)
endif()

# Only jni.h and jni_md.h are needed: the library is loaded into a running JVM and does not link to it.
# (FindJNI insists on AWT too, which a headless JDK lacks.)
if(NOT DEFINED ENV{JAVA_HOME})
  message(FATAL_ERROR "Set JAVA_HOME to a JDK.")
endif()
if(WIN32)
  set(JNI_PLATFORM_DIR win32)
elseif(APPLE)
  set(JNI_PLATFORM_DIR darwin)
else()
  set(JNI_PLATFORM_DIR linux)
endif()
set(JNI_INCLUDE_DIRS "$ENV{JAVA_HOME}/include" "$ENV{JAVA_HOME}/include/${JNI_PLATFORM_DIR}")
if(NOT EXISTS "$ENV{JAVA_HOME}/include/jni.h")
  message(FATAL_ERROR "No jni.h under JAVA_HOME ($ENV{JAVA_HOME}): it needs to be a JDK.")
endif()

# Ableton Link (GPLv2+), pinned. The app is GPL-3.0. AbletonLinkConfig.cmake supplies the
# per-platform defines and system libraries, and the bundled ASIO include path.
include(FetchContent)
FetchContent_Declare(
  abletonlink
  GIT_REPOSITORY https://github.com/Ableton/link.git
  GIT_TAG 5ba907ae027a8c2ec8824463e37296dcefc5e9be # Link-4.1.1
  GIT_SUBMODULES modules/asio-standalone
)
FetchContent_GetProperties(abletonlink)
if(NOT abletonlink_POPULATED)
  FetchContent_Populate(abletonlink)
endif()
include(${abletonlink_SOURCE_DIR}/AbletonLinkConfig.cmake)

add_library(link_jni SHARED LinkJni.cpp)
# Default naming gives liblink_jni.so, liblink_jni.dylib and link_jni.dll, which is what
# NativeLibraryLoader looks for under /natives/<platform>/.
if(WIN32)
  set_target_properties(link_jni PROPERTIES PREFIX "")
endif()

target_include_directories(link_jni PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(link_jni PRIVATE Ableton::Link)

# One self-contained file per platform: no libstdc++ version requirement on the user's Linux.
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
  target_link_options(link_jni PRIVATE -static-libstdc++ -static-libgcc)
endif()

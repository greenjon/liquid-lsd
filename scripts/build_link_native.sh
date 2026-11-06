#!/usr/bin/env bash
# Builds link_jni for this machine into src/main/resources/natives/<platform>/, where
# NativeLibraryLoader finds it. Release builds do the same in .github/workflows/build-link-natives.yml.
#
# Uses cmake and a C++ compiler if installed. On Linux without them it builds in a podman container
# on Ubuntu 22.04, the same base CI uses, so the result runs on older distributions too.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
case "$(uname -s)-$(uname -m)" in
    Linux-x86_64) platform=linux-x64; lib=liblink_jni.so ;;
    Linux-aarch64) platform=linux-arm64; lib=liblink_jni.so ;;
    Darwin-x86_64) platform=macos-x64; lib=liblink_jni.dylib ;;
    Darwin-arm64) platform=macos-arm64; lib=liblink_jni.dylib ;;
    *) echo "Unsupported platform: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac
out="$root/src/main/resources/natives/$platform"
mkdir -p "$out"

if command -v cmake >/dev/null && command -v c++ >/dev/null; then
    build="$root/build/link_jni"
    cmake -S "$root/src/main/cpp/link_jni" -B "$build" -DCMAKE_BUILD_TYPE=Release
    cmake --build "$build" --config Release
    cp "$build/$lib" "$out/$lib"
elif command -v podman >/dev/null && [ "$(uname -s)" = Linux ]; then
    podman run --rm --security-opt label=disable \
        -v "$root/src/main/cpp/link_jni:/src:ro" -v "$out:/out" docker.io/library/ubuntu:22.04 bash -euc '
            export DEBIAN_FRONTEND=noninteractive
            apt-get update -qq
            apt-get install -y -qq --no-install-recommends g++ cmake make git ca-certificates openjdk-17-jdk-headless >/dev/null
            export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
            cmake -S /src -B /tmp/build -DCMAKE_BUILD_TYPE=Release
            cmake --build /tmp/build -j"$(nproc)"
            cp /tmp/build/'"$lib"' /out/'"$lib"'
        '
else
    echo "Needs cmake and a C++ compiler, or podman." >&2
    exit 1
fi
echo "Built $out/$lib"

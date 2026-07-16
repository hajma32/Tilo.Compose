#!/usr/bin/env bash
set -euo pipefail

PROJ_VERSION="9.8.1"
PROJ_SHA256="af5b731c145c1d13c4e3b4eeb7d167e94e845e440f71e3496b4ed8dae0291960"
CMAKE_VERSION="4.3.3"
CMAKE_SHA256="5221a13450c7a0219a2a0d1b6c9085eb06489721fafd8488ccebc1584175d2fb"
IOS_DEPLOYMENT_TARGET="14.0"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WORK_DIR="${PROJ_IOS_BUILD_DIR:-${TMPDIR:-/tmp}/tilo-proj-ios}"
OUTPUT_DIR="$PROJECT_DIR/core/src/nativeInterop/proj/PROJ.xcframework"

DOWNLOAD_DIR="$WORK_DIR/downloads"
TOOLS_DIR="$WORK_DIR/tools"
SOURCE_DIR="$WORK_DIR/proj-$PROJ_VERSION"
mkdir -p "$DOWNLOAD_DIR" "$TOOLS_DIR"

download_and_verify() {
    local url="$1"
    local destination="$2"
    local checksum="$3"

    if [[ ! -f "$destination" ]]; then
        curl --fail --location --retry 3 "$url" --output "$destination"
    fi
    echo "$checksum  $destination" | shasum --algorithm 256 --check
}

PROJ_ARCHIVE="$DOWNLOAD_DIR/proj-$PROJ_VERSION.tar.gz"
download_and_verify \
    "https://github.com/OSGeo/PROJ/releases/download/$PROJ_VERSION/proj-$PROJ_VERSION.tar.gz" \
    "$PROJ_ARCHIVE" \
    "$PROJ_SHA256"

if command -v cmake >/dev/null 2>&1; then
    CMAKE="$(command -v cmake)"
else
    CMAKE_ARCHIVE="$DOWNLOAD_DIR/cmake-$CMAKE_VERSION-macos-universal.tar.gz"
    download_and_verify \
        "https://github.com/Kitware/CMake/releases/download/v$CMAKE_VERSION/cmake-$CMAKE_VERSION-macos-universal.tar.gz" \
        "$CMAKE_ARCHIVE" \
        "$CMAKE_SHA256"
    CMAKE_HOME="$TOOLS_DIR/cmake-$CMAKE_VERSION-macos-universal"
    if [[ ! -x "$CMAKE_HOME/CMake.app/Contents/bin/cmake" ]]; then
        rm -rf "$CMAKE_HOME"
        mkdir -p "$CMAKE_HOME"
        tar -xzf "$CMAKE_ARCHIVE" -C "$CMAKE_HOME" --strip-components=1
    fi
    CMAKE="$CMAKE_HOME/CMake.app/Contents/bin/cmake"
fi

if [[ ! -f "$SOURCE_DIR/CMakeLists.txt" ]]; then
    rm -rf "$SOURCE_DIR"
    tar -xzf "$PROJ_ARCHIVE" -C "$WORK_DIR"
fi

build_slice() {
    local sdk="$1"
    local build_dir="$WORK_DIR/build-$sdk"
    local install_dir="$WORK_DIR/install-$sdk"
    local sdk_root
    sdk_root="$(xcrun --sdk "$sdk" --show-sdk-path)"

    "$CMAKE" -S "$SOURCE_DIR" -B "$build_dir" \
        -DCMAKE_SYSTEM_NAME=iOS \
        -DCMAKE_OSX_SYSROOT="$sdk" \
        -DCMAKE_OSX_ARCHITECTURES=arm64 \
        -DCMAKE_OSX_DEPLOYMENT_TARGET="$IOS_DEPLOYMENT_TARGET" \
        -DCMAKE_INSTALL_PREFIX="$install_dir" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_APPS=OFF \
        -DBUILD_TESTING=OFF \
        -DENABLE_CURL=OFF \
        -DENABLE_TIFF=OFF \
        -DEMBED_RESOURCE_FILES=ON \
        -DUSE_ONLY_EMBEDDED_RESOURCE_FILES=ON \
        -DEMBED_PROJ_DATA_PATH=OFF \
        -DEXE_SQLITE3=/usr/bin/sqlite3 \
        -DSQLite3_INCLUDE_DIR="$sdk_root/usr/include" \
        -DSQLite3_LIBRARY="$sdk_root/usr/lib/libsqlite3.tbd"

    "$CMAKE" --build "$build_dir" --config Release --parallel \
        "$(sysctl -n hw.logicalcpu)" --target install
}

build_slice iphoneos
build_slice iphonesimulator

rm -rf "$OUTPUT_DIR"
xcodebuild -create-xcframework \
    -library "$WORK_DIR/install-iphoneos/lib/libproj.a" \
    -headers "$WORK_DIR/install-iphoneos/include" \
    -library "$WORK_DIR/install-iphonesimulator/lib/libproj.a" \
    -headers "$WORK_DIR/install-iphonesimulator/include" \
    -output "$OUTPUT_DIR"

cp "$PROJECT_DIR/core/src/nativeInterop/proj/LICENSE-PROJ.txt" "$OUTPUT_DIR/LICENSE-PROJ.txt"
cp "$PROJECT_DIR/core/src/nativeInterop/proj/NOTICE-EPSG.txt" "$OUTPUT_DIR/NOTICE-EPSG.txt"
cp \
    "$PROJECT_DIR/core/src/thirdPartyLicenses/common/META-INF/third-party/LICENSE-EPSG.txt" \
    "$OUTPUT_DIR/LICENSE-EPSG.txt"

echo "Created $OUTPUT_DIR"

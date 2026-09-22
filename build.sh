#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# C11 车控测试工具 - 构建脚本
# ═══════════════════════════════════════════════════════════════
# 方式 1: Gradle 构建 (推荐)
#   ./gradlew assembleDebug
#   ./gradlew assembleRelease
#
# 方式 2: 本脚本 (需要 Android SDK + JDK)
#   bash build.sh
# ═══════════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "═══ C11 车控测试工具 构建 ═══"
echo ""

# 检查 Gradle wrapper
if [ -f "./gradlew" ]; then
    echo "使用 Gradle 构建..."
    chmod +x ./gradlew
    ./gradlew clean assembleDebug --no-daemon
    echo ""
    echo "✅ 构建完成！"
    echo ""
    echo "APK 位置:"
    find . -name "*.apk" -path "*/build/*" | head -5
    echo ""
    echo "部署命令:"
    echo "  adb install -r -t app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "授权命令:"
    echo "  adb shell pm grant com.c11.cartool android.permission.WRITE_SECURE_SETTINGS"
    exit 0
fi

# 没有 Gradle wrapper，尝试手动构建
echo "未找到 gradlew，尝试手动构建..."
echo ""

# 配置
PACKAGE="com.c11.cartool"
API_LEVEL=28
BUILD_TOOLS_VERSION="28.0.3"

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk-17.0.2}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/.local/android-sdk}"
BUILD_TOOLS="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
PLATFORM="$ANDROID_HOME/platforms/android-$API_LEVEL"

BUILD_DIR="$SCRIPT_DIR/build"
OUT_DIR="$BUILD_DIR/output"
GEN_DIR="$BUILD_DIR/gen"
OBJ_DIR="$BUILD_DIR/obj"
DEX_DIR="$BUILD_DIR/dex"

SRC_DIR="$SCRIPT_DIR/app/src/main/java"
RES_DIR="$SCRIPT_DIR/app/src/main/res"
MANIFEST="$SCRIPT_DIR/app/src/main/AndroidManifest.xml"

KEYSTORE="$SCRIPT_DIR/lib/platform.jks"
KEY_ALIAS="platform"
KEY_PASS="android"

check_tool() {
    if [ ! -f "$1" ]; then
        echo "❌ 未找到: $1"
        echo "   请确保 ANDROID_HOME 和 JAVA_HOME 设置正确"
        exit 1
    fi
}

check_tool "$BUILD_TOOLS/aapt"
check_tool "$BUILD_TOOLS/d8"
check_tool "$BUILD_TOOLS/zipalign"
check_tool "$BUILD_TOOLS/apksigner"
check_tool "$PLATFORM/android.jar"
check_tool "$JAVA_HOME/bin/javac"

echo "✅ 环境检查通过"
echo ""

# 清理
echo "[1/8] 清理..."
rm -rf "$BUILD_DIR"
mkdir -p "$OUT_DIR" "$GEN_DIR" "$OBJ_DIR" "$DEX_DIR"

# AAPT
echo "[2/8] 编译资源..."
"$BUILD_TOOLS/aapt" package -f -m \
    -S "$RES_DIR" \
    -J "$GEN_DIR" \
    -M "$MANIFEST" \
    -I "$PLATFORM/android.jar" \
    --auto-add-overlay

# JAVAC
echo "[3/8] 编译 Java..."
JAVA_FILES=$(find "$SRC_DIR" -name "*.java")
GEN_FILES=$(find "$GEN_DIR" -name "*.java" 2>/dev/null)

"$JAVA_HOME/bin/javac" \
    -encoding UTF-8 \
    -source 1.8 -target 1.8 \
    -classpath "$PLATFORM/android.jar" \
    -d "$OBJ_DIR" \
    $JAVA_FILES $GEN_FILES

# DEX
echo "[4/8] 转换 DEX..."
CLASS_FILES=$(find "$OBJ_DIR" -name "*.class")
"$BUILD_TOOLS/d8" --min-api $API_LEVEL --output "$DEX_DIR" $CLASS_FILES

# 打包
echo "[5/8] 打包 APK..."
APK_BASE="$OUT_DIR/C11CarTool-unsigned.apk"
"$BUILD_TOOLS/aapt" package -f \
    -M "$MANIFEST" -S "$RES_DIR" -I "$PLATFORM/android.jar" -F "$APK_BASE"
cd "$DEX_DIR" && "$BUILD_TOOLS/aapt" add "$APK_BASE" classes.dex && cd "$SCRIPT_DIR"

# 对齐
echo "[6/8] 对齐..."
APK_ALIGNED="$OUT_DIR/C11CarTool-aligned.apk"
"$BUILD_TOOLS/zipalign" -f 4 "$APK_BASE" "$APK_ALIGNED"

# 签名
echo "[7/8] 签名..."
APK_SIGNED="$OUT_DIR/C11CarTool.apk"
if [ ! -f "$KEYSTORE" ]; then
    echo "  ⚠️  未找到 platform.jks，使用 debug 签名"
    KEYSTORE="$BUILD_DIR/debug.keystore"
    KEY_ALIAS="debug"
    keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
        -dname "CN=C11CarTool Debug" 2>/dev/null
fi
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEY_PASS" --key-pass "pass:$KEY_PASS" \
    --out "$APK_SIGNED" "$APK_ALIGNED"

# 验证
echo "[8/8] 验证..."
"$BUILD_TOOLS/apksigner" verify "$APK_SIGNED" && echo "  ✅ 签名验证通过"

APK_SIZE=$(stat -c%s "$APK_SIGNED" 2>/dev/null || stat -f%z "$APK_SIGNED" 2>/dev/null)
echo ""
echo "╔══════════════════════════════════════════╗"
echo "║  ✅ 构建完成！                            ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "  APK: $APK_SIGNED"
echo "  大小: $((APK_SIZE / 1024))KB"
echo ""
echo "  部署: adb install -r -t $APK_SIGNED"
echo "  授权: adb shell pm grant com.c11.cartool android.permission.WRITE_SECURE_SETTINGS"

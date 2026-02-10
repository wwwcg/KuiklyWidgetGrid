#!/bin/bash
# ============================================================
# KuiklyWidgetGrid 一键构建发布脚本
# 用法：
#   ./publish.sh              # 构建标准版 + 鸿蒙版
#   ./publish.sh standard     # 仅构建标准版
#   ./publish.sh ohos         # 仅构建鸿蒙版
#
# 构建完成后，bundle zip 会输出到项目根目录：
#   widgetgrid-{version}-bundle.zip       (标准版)
#   widgetgrid-{version}-ohos-bundle.zip  (鸿蒙版)
#
# 然后手动上传到 https://central.sonatype.com/
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

MODE="${1:-all}"  # all | standard | ohos

# 从 build.gradle.kts 读取标准版版本号
STANDARD_VERSION=$(grep 'version = ' widgetgrid/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
OHOS_VERSION=$(grep 'version = ' widgetgrid/build.ohos.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')

echo "============================================"
echo " KuiklyWidgetGrid 发布构建"
echo "============================================"
echo " 标准版版本: $STANDARD_VERSION"
echo " 鸿蒙版版本: $OHOS_VERSION"
echo " 构建模式:   $MODE"
echo "============================================"
echo ""

build_standard() {
    echo "🔨 [标准版] 清理..."
    ./gradlew :widgetgrid:clean --quiet

    echo "🔨 [标准版] 构建 + 签名 + 发布到本地暂存..."
    ./gradlew :widgetgrid:publishAllPublicationsToLocalStagingRepository --quiet

    echo "📦 [标准版] 打包 bundle..."
    local OUTPUT="widgetgrid-${STANDARD_VERSION}-bundle.zip"
    cd widgetgrid/build/staging-deploy
    rm -f "$SCRIPT_DIR/$OUTPUT"
    zip -r "$SCRIPT_DIR/$OUTPUT" io/
    cd "$SCRIPT_DIR"

    local SIZE=$(du -h "$OUTPUT" | cut -f1 | xargs)
    echo "✅ [标准版] 完成: $OUTPUT ($SIZE)"
    echo ""
}

build_ohos() {
    echo "🔨 [鸿蒙版] 清理..."
    ./gradlew -c settings.ohos.gradle.kts :widgetgrid:clean --quiet

    echo "🔨 [鸿蒙版] 构建 + 签名 + 发布到本地暂存..."
    ./gradlew -c settings.ohos.gradle.kts :widgetgrid:publishAllPublicationsToLocalStagingRepository --quiet

    echo "📦 [鸿蒙版] 打包 bundle..."
    local OUTPUT="widgetgrid-${OHOS_VERSION}-bundle.zip"
    cd widgetgrid/build/staging-deploy
    rm -f "$SCRIPT_DIR/$OUTPUT"
    zip -r "$SCRIPT_DIR/$OUTPUT" io/
    cd "$SCRIPT_DIR"

    local SIZE=$(du -h "$OUTPUT" | cut -f1 | xargs)
    echo "✅ [鸿蒙版] 完成: $OUTPUT ($SIZE)"
    echo ""
}

case "$MODE" in
    standard)
        build_standard
        ;;
    ohos)
        build_ohos
        ;;
    all)
        build_standard
        build_ohos
        ;;
    *)
        echo "❌ 未知模式: $MODE"
        echo "用法: ./publish.sh [all|standard|ohos]"
        exit 1
        ;;
esac

echo "============================================"
echo " 🎉 构建完成！"
echo ""
echo " 请上传以下文件到 Maven Central:"
echo " https://central.sonatype.com/ → Publishing → Publish Component"
echo ""
if [ "$MODE" = "all" ] || [ "$MODE" = "standard" ]; then
    echo "   📦 widgetgrid-${STANDARD_VERSION}-bundle.zip (标准版)"
fi
if [ "$MODE" = "all" ] || [ "$MODE" = "ohos" ]; then
    echo "   📦 widgetgrid-${OHOS_VERSION}-bundle.zip (鸿蒙版)"
fi
echo "============================================"

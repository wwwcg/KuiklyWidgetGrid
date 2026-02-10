#!/bin/bash
# ============================================================
# KuiklyWidgetGrid 一键构建 & 发布脚本
#
# 用法：
#   ./publish.sh                    # 构建标准版 + 鸿蒙版（仅构建，不上传）
#   ./publish.sh standard           # 仅构建标准版
#   ./publish.sh ohos               # 仅构建鸿蒙版
#   ./publish.sh --upload           # 构建全部 + 通过 API 上传到 Maven Central
#   ./publish.sh standard --upload  # 构建标准版 + 上传
#   ./publish.sh ohos --upload      # 构建鸿蒙版 + 上传
#   ./publish.sh --upload-only      # 跳过构建，仅上传已有的 bundle zip
#
# API 上传需要在 ~/.gradle/gradle.properties 中配置：
#   sonatype.token.username=<your-token-username>
#   sonatype.token.password=<your-token-password>
#
# Token 获取方式：
#   https://central.sonatype.com/ → 头像 → View Account → Generate User Token
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ===================== 参数解析 =====================
MODE="all"
DO_UPLOAD=false
UPLOAD_ONLY=false

for arg in "$@"; do
    case "$arg" in
        standard|ohos|all)
            MODE="$arg"
            ;;
        --upload)
            DO_UPLOAD=true
            ;;
        --upload-only)
            DO_UPLOAD=true
            UPLOAD_ONLY=true
            ;;
        *)
            echo "❌ 未知参数: $arg"
            echo ""
            echo "用法: ./publish.sh [all|standard|ohos] [--upload|--upload-only]"
            echo ""
            echo "  all|standard|ohos   构建模式（默认 all）"
            echo "  --upload            构建完成后自动上传到 Maven Central"
            echo "  --upload-only       跳过构建，仅上传已有的 bundle zip"
            exit 1
            ;;
    esac
done

# ===================== 版本号读取 =====================
STANDARD_VERSION=$(grep 'version = ' widgetgrid/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
OHOS_VERSION=$(grep 'version = ' widgetgrid/build.ohos.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')

STANDARD_BUNDLE="widgetgrid-${STANDARD_VERSION}-bundle.zip"
OHOS_BUNDLE="widgetgrid-${OHOS_VERSION}-bundle.zip"

echo "============================================"
echo " KuiklyWidgetGrid 发布构建"
echo "============================================"
echo " 标准版版本: $STANDARD_VERSION"
echo " 鸿蒙版版本: $OHOS_VERSION"
echo " 构建模式:   $MODE"
echo " API 上传:   $DO_UPLOAD"
echo "============================================"
echo ""

# ===================== 读取 Sonatype Token =====================
read_sonatype_token() {
    local GRADLE_PROPS="$HOME/.gradle/gradle.properties"
    if [ ! -f "$GRADLE_PROPS" ]; then
        echo "❌ 未找到 $GRADLE_PROPS"
        echo "   请先配置 sonatype.token.username 和 sonatype.token.password"
        exit 1
    fi

    SONATYPE_USERNAME=$(grep '^sonatype.token.username=' "$GRADLE_PROPS" | cut -d'=' -f2-)
    SONATYPE_PASSWORD=$(grep '^sonatype.token.password=' "$GRADLE_PROPS" | cut -d'=' -f2-)

    if [ -z "$SONATYPE_USERNAME" ] || [ -z "$SONATYPE_PASSWORD" ]; then
        echo "❌ ~/.gradle/gradle.properties 中缺少 Sonatype Token 配置"
        echo ""
        echo "   请添加以下内容到 ~/.gradle/gradle.properties："
        echo "   sonatype.token.username=<your-token-username>"
        echo "   sonatype.token.password=<your-token-password>"
        echo ""
        echo "   Token 获取方式："
        echo "   https://central.sonatype.com/ → 头像 → View Account → Generate User Token"
        exit 1
    fi

    SONATYPE_AUTH=$(echo -n "${SONATYPE_USERNAME}:${SONATYPE_PASSWORD}" | base64)
}

# ===================== 上传 Bundle =====================
upload_bundle() {
    local BUNDLE_FILE="$1"
    local LABEL="$2"

    if [ ! -f "$BUNDLE_FILE" ]; then
        echo "❌ [$LABEL] 文件不存在: $BUNDLE_FILE"
        echo "   请先构建（去掉 --upload-only 参数）"
        return 1
    fi

    local SIZE=$(du -h "$BUNDLE_FILE" | cut -f1 | xargs)
    echo "🚀 [$LABEL] 上传中... ($BUNDLE_FILE, $SIZE)"

    # Sonatype Central Portal Publishing API
    # https://central.sonatype.com/api-doc
    local RESPONSE
    RESPONSE=$(curl -s -w "\n%{http_code}" \
        -X POST "https://central.sonatype.com/api/v1/publisher/upload" \
        -H "Authorization: Bearer ${SONATYPE_AUTH}" \
        -F "bundle=@${BUNDLE_FILE}" \
        -F "publishingType=AUTOMATIC" \
        -F "name=${LABEL}")

    local HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    local BODY=$(echo "$RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" = "201" ]; then
        echo "✅ [$LABEL] 上传成功！Deployment ID: $BODY"
        echo "   状态查看: https://central.sonatype.com/publishing/deployments"
        echo "   publishingType=AUTOMATIC → 验证通过后自动发布"
        echo ""
        return 0
    else
        echo "❌ [$LABEL] 上传失败 (HTTP $HTTP_CODE)"
        echo "   响应: $BODY"
        echo ""
        echo "   常见原因："
        echo "   - 401: Token 无效或过期，请重新生成"
        echo "   - 400: bundle 格式不正确"
        echo "   - 409: 该版本已存在"
        echo ""
        return 1
    fi
}

# ===================== 构建函数 =====================
build_standard() {
    echo "🔨 [标准版] 清理..."
    ./gradlew :widgetgrid:clean --quiet

    echo "🔨 [标准版] 构建 + 签名 + 发布到本地暂存..."
    ./gradlew :widgetgrid:publishAllPublicationsToLocalStagingRepository --quiet

    echo "📦 [标准版] 打包 bundle..."
    cd widgetgrid/build/staging-deploy
    rm -f "$SCRIPT_DIR/$STANDARD_BUNDLE"
    zip -r "$SCRIPT_DIR/$STANDARD_BUNDLE" io/
    cd "$SCRIPT_DIR"

    local SIZE=$(du -h "$STANDARD_BUNDLE" | cut -f1 | xargs)
    echo "✅ [标准版] 完成: $STANDARD_BUNDLE ($SIZE)"
    echo ""
}

build_ohos() {
    echo "🔨 [鸿蒙版] 清理..."
    ./gradlew -c settings.ohos.gradle.kts :widgetgrid:clean --quiet

    echo "🔨 [鸿蒙版] 构建 + 签名 + 发布到本地暂存..."
    ./gradlew -c settings.ohos.gradle.kts :widgetgrid:publishAllPublicationsToLocalStagingRepository --quiet

    echo "📦 [鸿蒙版] 打包 bundle..."
    cd widgetgrid/build/staging-deploy
    rm -f "$SCRIPT_DIR/$OHOS_BUNDLE"
    zip -r "$SCRIPT_DIR/$OHOS_BUNDLE" io/
    cd "$SCRIPT_DIR"

    local SIZE=$(du -h "$OHOS_BUNDLE" | cut -f1 | xargs)
    echo "✅ [鸿蒙版] 完成: $OHOS_BUNDLE ($SIZE)"
    echo ""
}

# ===================== 主流程 =====================

# 1. 构建阶段（除非 --upload-only）
if [ "$UPLOAD_ONLY" = false ]; then
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
    esac
fi

# 2. 上传阶段
if [ "$DO_UPLOAD" = true ]; then
    echo "============================================"
    echo " 📤 上传到 Maven Central (via API)"
    echo "============================================"
    echo ""

    read_sonatype_token

    UPLOAD_OK=true

    if [ "$MODE" = "all" ] || [ "$MODE" = "standard" ]; then
        upload_bundle "$STANDARD_BUNDLE" "标准版 $STANDARD_VERSION" || UPLOAD_OK=false
    fi
    if [ "$MODE" = "all" ] || [ "$MODE" = "ohos" ]; then
        upload_bundle "$OHOS_BUNDLE" "鸿蒙版 $OHOS_VERSION" || UPLOAD_OK=false
    fi

    if [ "$UPLOAD_OK" = true ]; then
        echo "============================================"
        echo " 🎉 全部上传成功！"
        echo ""
        echo " publishingType=AUTOMATIC 已设置"
        echo " Maven Central 验证通过后会自动发布"
        echo " 查看状态: https://central.sonatype.com/publishing/deployments"
        echo "============================================"
    else
        echo "============================================"
        echo " ⚠️  部分上传失败，请检查上方错误信息"
        echo "============================================"
        exit 1
    fi
else
    # 仅构建模式，提示手动上传
    echo "============================================"
    echo " 🎉 构建完成！"
    echo ""
    echo " 你可以："
    echo "  A) 手动上传到 https://central.sonatype.com/"
    echo "  B) 用 API 自动上传: ./publish.sh $MODE --upload-only"
    echo ""
    if [ "$MODE" = "all" ] || [ "$MODE" = "standard" ]; then
        echo "   📦 $STANDARD_BUNDLE (标准版)"
    fi
    if [ "$MODE" = "all" ] || [ "$MODE" = "ohos" ]; then
        echo "   📦 $OHOS_BUNDLE (鸿蒙版)"
    fi
    echo "============================================"
fi

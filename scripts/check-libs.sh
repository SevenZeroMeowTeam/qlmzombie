#!/bin/bash
# 依赖检查脚本 (Bash)
# 检查 src/main/libs/ 目录是否包含所有必需的依赖 jar 文件
# 用法: ./scripts/check-libs.sh

set -e

LIBS_DIR="src/main/libs"
LIST_FILE="scripts/libs-list.txt"

if [ ! -f "$LIST_FILE" ]; then
    echo "[ERROR] 依赖列表文件不存在: $LIST_FILE"
    exit 1
fi

total=0
found=0
missing=""

echo "========================================"
echo "  依赖检查 - 七零喵僵尸末日生存 Mod"
echo "========================================"

while IFS= read -r jar; do
    [ -z "$jar" ] && continue
    total=$((total + 1))
    if [ -f "$LIBS_DIR/$jar" ]; then
        found=$((found + 1))
    else
        missing="$missing\n  - $jar"
    fi
done < "$LIST_FILE"

echo "需要依赖文件总数: $total"
echo ""

if [ -z "$missing" ]; then
    echo "[OK] 所有 $total 个依赖文件均已存在 ($found/$total)"
    echo "可以正常编译: ./gradlew build"
    exit 0
else
    missing_count=$((total - found))
    echo "[MISSING] 缺少 $missing_count 个依赖文件 ($found/$total)"
    echo ""
    echo "缺少的文件:"
    echo -e "$missing"
    echo ""
    echo "请从以下网站下载缺失的依赖:"
    echo "  CurseForge:  https://www.curseforge.com/minecraft/mc-mods"
    echo "  Modrinth:    https://modrinth.com/mods"
    echo "  将下载的 jar 文件放入: src/main/libs/"
    exit 1
fi

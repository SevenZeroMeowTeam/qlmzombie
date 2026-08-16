#!/bin/bash
# ============================================================
# CraftingDead 注册表修复 - 禁用方案
# 策略：将 CraftingDead JAR 完全移出 mods 目录
# 原因：QLMZombie 会自动恢复被重命名为 .disabled 的 JAR
#       但无法恢复被完全移出 mods 目录的文件
# ============================================================
set -e

MODS="/data/mods"
DISABLED="/data/disabled_mods"

echo "===== CraftingDead 禁用方案 ====="
echo ""

# 创建禁用目录
mkdir -p "$DISABLED"

# 查找所有 CraftingDead JAR（包括 .disabled 文件）
echo "[1/3] 查找 CraftingDead 模组..."
cd "$MODS"

# 收集所有相关文件（.jar 和 .jar.disabled）
CD_FILES=$(find . -maxdepth 1 -name "crafting-dead-core*.jar*" -type f 2>/dev/null | sort)

if [ -z "$CD_FILES" ]; then
    echo "  ℹ️  未找到 CraftingDead 模组"
    echo "  检查是否已被禁用或不存在..."
    exit 0
fi

COUNT=$(echo "$CD_FILES" | wc -l)
echo "  找到 $COUNT 个 CraftingDead 文件:"
echo "$CD_FILES" | while IFS= read -r f; do
    [ -z "$f" ] && continue
    echo "    - $(basename "$f")"
done

# 将所有文件移出 mods 目录
echo ""
echo "[2/3] 完全移出 CraftingDead 模组..."
echo "$CD_FILES" | while IFS= read -r cd_file; do
    [ -z "$cd_file" ] && continue
    # 移除 ./ 前缀
    cd_path=$(echo "$cd_file" | sed 's/^\.\///')
    jar_full="$MODS/$cd_path"
    base=$(basename "$cd_path")
    
    if [ ! -e "$jar_full" ]; then
        echo "  ⚠️  文件不存在: $cd_path"
        continue
    fi
    
    dest="$DISABLED/$base"
    
    # 如果目标已存在，加时间戳
    if [ -e "$dest" ]; then
        ts=$(date +%s)
        dest="${DISABLED}/${ts}_${base}"
        echo "    (目标已存在，重命名为 $(basename "$dest"))"
    fi
    
    mv "$jar_full" "$dest"
    echo "  ✅ 已移出: $base"
done

# 验证
echo ""
echo "[3/3] 验证..."
REMAINING=$(find "$MODS" -maxdepth 1 -name "crafting-dead-core*.jar*" -type f 2>/dev/null | wc -l)
if [ "$REMAINING" -gt 0 ]; then
    echo "  ⚠️  还有 $REMAINING 个 CraftingDead 文件在 mods 目录中！"
    echo "  可能是 QLMZombie 在后台恢复了它们..."
    echo "  需要在 entrypoint 中增加额外保护..."
    # 再次强制移出
    find "$MODS" -maxdepth 1 -name "crafting-dead-core*.jar*" -type f 2>/dev/null | while IFS= read -r f; do
        mv "$f" "$DISABLED/" 2>/dev/null || true
        echo "    强制移出: $(basename "$f")"
    done
else
    echo "  ✅ 验证通过：mods 目录中已无 CraftingDead 文件"
fi

MOVED=$(find "$DISABLED" -maxdepth 1 -name "crafting-dead-core*" -type f 2>/dev/null | wc -l)
echo "  已禁用的文件数: $MOVED"

# 清理 KubeJS 缓存
echo ""
echo "清理 KubeJS 缓存..."
rm -rf /data/kubejs/probe/ 2>/dev/null || true
rm -rf /data/kubejs/cache/ 2>/dev/null || true
rm -rf /data/kubejs/.cache/ 2>/dev/null || true
echo "  ✅ KubeJS 缓存已清理"

echo ""
echo "===== 完成 ====="
echo ""
echo "CraftingDead 模组已被完全禁用。"
echo "它被移到了: $DISABLED/"
echo ""
echo "重启服务器使其生效:"
echo "  docker compose restart minecraft"
echo ""
echo "如果之后想恢复 CraftingDead:"
echo "  mv $DISABLED/crafting-dead-core-* /data/mods/"

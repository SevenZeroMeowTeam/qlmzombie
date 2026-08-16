#!/bin/bash
# 手工修复脚本：在容器内直接执行 CraftingDead 注册表修复
# 使用方法: docker compose exec minecraft bash /mc-agent/fix_in_container.sh

set -e

echo "============================================"
echo "CraftingDead 注册表修复 (手工版)"
echo "============================================"
echo ""

MODS_DIR="/data/mods"
AGENT_DIR="/mc-agent"

# 1. 找到 CraftingDead core JAR
echo "[1] 查找 CraftingDead core JAR..."
CD_JARS=$(find "$MODS_DIR" -maxdepth 1 -name "crafting-dead*core*.jar" 2>/dev/null || true)
if [ -z "$CD_JARS" ]; then
    echo "    ❌ 未找到 crafting-dead core JAR"
    exit 1
fi
echo "    找到 JAR:"
echo "$CD_JARS" | while read -r jar; do
    echo "      - $(basename "$jar")"
done
echo ""

# 2. 检查 disableSaving 是否存在
echo "[2] 检查 JAR 中是否存在 disableSaving..."
for jar in $CD_JARS; do
    echo "    检查: $(basename "$jar")"
    GUN_CLASS=$(unzip -l "$jar" 2>/dev/null | grep "GunConfigurations.class" | awk '{print $NF}')
    if [ -z "$GUN_CLASS" ]; then
        echo "      ❌ 不包含 GunConfigurations.class"
        continue
    fi
    echo "      ✅ 包含 GunConfigurations.class"
    
    # 检查 disableSaving 字符串
    if unzip -p "$jar" "$GUN_CLASS" 2>/dev/null | grep -qa "disableSaving"; then
        echo "      ✅ 包含 'disableSaving' 字符串，可以补丁"
        # 执行补丁
        echo "      执行 Python 补丁..."
        if command -v python3 >/dev/null 2>&1; then
            PYTHON="python3"
        elif command -v python >/dev/null 2>&1; then
            PYTHON="python"
        else
            echo "      ❌ Python 不可用，尝试安装..."
            apt-get update -qq && apt-get install -y -qq python3 >/dev/null 2>&1
            PYTHON="python3"
        fi
        
        $PYTHON "$AGENT_DIR/patch_cd_jar.py" "$jar"
        if [ $? -eq 0 ]; then
            echo "      ✅ 补丁执行成功"
        else
            echo "      ⚠️  补丁执行失败"
        fi
    else
        echo "      ❌ 不包含 'disableSaving' 字符串"
        echo "         (JAR 可能已经被补丁，或使用不同方法名)"
        echo ""
        echo "      分析所有 'disable' 相关字符串:"
        unzip -p "$jar" "$GUN_CLASS" 2>/dev/null | strings | grep -i "disable" || echo "        (无)"
        echo ""
        echo "      分析所有方法名:"
        unzip -p "$jar" "$GUN_CLASS" 2>/dev/null | strings | grep -E "^[a-z][a-zA-Z0-9_]*$" | sort -u | head -50
    fi
done
echo ""

# 3. 强制设置 JVM 环境变量
echo "[3] 设置 JVM 环境变量..."
export JAVA_TOOL_OPTIONS="-Dfml.debugMissingRegistries=true -Dfml.resolveMissingRegistries=WARN -Dfml.ignoreMissingRegistries=true"
export JDK_JAVA_OPTIONS="$JAVA_TOOL_OPTIONS"
export JVM_OPTS="$JAVA_TOOL_OPTIONS"
echo "    ✅ JAVA_TOOL_OPTIONS = $JAVA_TOOL_OPTIONS"
echo ""

# 4. 写入 codec JSON 文件
echo "[4] 写入 codec JSON 文件..."
CODEC_DIR="/data/world/data/minecraft/root"
mkdir -p "$CODEC_DIR"

# 先删除旧文件
rm -f "$CODEC_DIR/craftingdead:gun_configuration.json" 2>/dev/null || true

# 写入 FULL schema
cat > "$CODEC_DIR/craftingdead:gun_configuration.json" << 'EOF'
{
  "type": "minecraft:root",
  "element": {
    "type": "minecraft:for_each",
    "field": "value",
    "values": {}
  }
}
EOF
echo "    ✅ 写入: $CODEC_DIR/craftingdead:gun_configuration.json"

# 同时写入空 JSON（某些 DFU 版本需要）
echo '{}' > "$CODEC_DIR/craftingdead:gun_configuration.empty.json"
echo "    ✅ 写入: $CODEC_DIR/craftingdead:gun_configuration.empty.json"

# 子目录变体
mkdir -p "$CODEC_DIR/craftingdead"
cat > "$CODEC_DIR/craftingdead/gun_configuration.json" << 'EOF'
{
  "type": "minecraft:root",
  "element": {
    "type": "minecraft:for_each",
    "field": "value",
    "values": {}
  }
}
EOF
echo "    ✅ 写入: $CODEC_DIR/craftingdead/gun_configuration.json"

# 设置权限
chown -R 1000:1000 /data/world/data/ 2>/dev/null || true
echo "    ✅ 权限已设置"
echo ""

# 5. 检查 KubeJS 脚本
echo "[5] 检查 KubeJS 脚本..."
if [ -f "/data/kubejs/server_scripts/craft_registry_fix.js" ]; then
    echo "    ✅ craft_registry_fix.js 存在"
    # 检查版本
    if grep -q "v2.2" "/data/kubejs/server_scripts/craft_registry_fix.js" 2>/dev/null; then
        echo "    ✅ 版本: v2.2 (最新版)"
    else
        echo "    ⚠️  可能不是最新版本"
    fi
else
    echo "    ❌ craft_registry_fix.js 不存在"
fi
echo ""

echo "============================================"
echo "修复完成！请执行: docker compose restart minecraft"
echo "============================================"

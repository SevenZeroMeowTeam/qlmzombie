#!/bin/bash
# 容器内诊断脚本：检查 entrypoint 是否执行，并分析 JAR
echo "============================================"
echo "容器内诊断 (Container Diagnostics)"
echo "============================================"
echo ""

# 1. 检查环境变量
echo "[1] JVM 环境变量检查:"
echo "    JAVA_TOOL_OPTIONS = ${JAVA_TOOL_OPTIONS:-(未设置)}"
echo "    JDK_JAVA_OPTIONS  = ${JDK_JAVA_OPTIONS:-(未设置)}"
echo "    JVM_OPTS          = ${JVM_OPTS:-(未设置)}"
echo ""

# 2. 检查 mods 目录
echo "[2] CraftingDead JAR 文件:"
ls -la /data/mods/crafting-dead*.jar 2>/dev/null || echo "    (无 crafting-dead JAR)"
echo ""

# 3. 检查 GunConfigurations.class
echo "[3] 分析 GunConfigurations.class 中的 disableSaving:"
for jar in /data/mods/crafting-dead*core*.jar; do
  if [ -f "$jar" ]; then
    echo "    检查: $(basename "$jar")"
    # 查找 GunConfigurations.class
    if unzip -l "$jar" 2>/dev/null | grep -q "GunConfigurations.class"; then
      echo "      ✅ 包含 GunConfigurations.class"
      # 提取并检查 disableSaving 字符串
      if unzip -p "$jar" "com/craftingdead/core/world/item/gun/GunConfigurations.class" 2>/dev/null | grep -qa "disableSaving"; then
        echo "      ✅ 包含 'disableSaving' 字符串"
      else
        echo "      ❌ 不包含 'disableSaving' 字符串"
        echo "         (可能使用了不同方法名，或已被补丁)"
      fi
      # 列出所有包含 "disable" 的字符串
      echo "      所有包含 'disable' 的字符串:"
      unzip -p "$jar" "com/craftingdead/core/world/item/gun/GunConfigurations.class" 2>/dev/null | strings | grep -i "disable" || echo "        (无)"
    else
      echo "      ❌ 不包含 GunConfigurations.class"
    fi
  fi
done
echo ""

# 4. 检查 codec JSON 文件
echo "[4] Codec JSON 文件检查:"
echo "    /data/world/data/minecraft/root/craftingdead:gun_configuration.json:"
ls -la "/data/world/data/minecraft/root/craftingdead:gun_configuration.json" 2>/dev/null || echo "      (不存在)"
echo ""

# 5. 检查 KubeJS 脚本
echo "[5] KubeJS 脚本检查:"
ls -la /data/kubejs/server_scripts/craft_registry_fix.js 2>/dev/null && echo "      ✅ 存在" || echo "      ❌ 不存在"
echo ""

# 6. 检查 entrypoint-wrapper.sh 是否存在
echo "[6] Entrypoint 脚本检查:"
ls -la /entrypoint-wrapper.sh 2>/dev/null && echo "      ✅ 存在" || echo "      ❌ 不存在"
echo ""

echo "============================================"
echo "诊断完成"
echo "============================================"

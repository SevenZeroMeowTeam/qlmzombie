#!/bin/bash
# ============================================================
# CraftingDead 注册表问题 - 独立修复脚本
# 不依赖 entrypoint-wrapper.sh，可直接在容器内运行
# ============================================================

set -e

AGENT_DIR="/mc-agent"
MODS_DIR="/data/mods"
LOG_FILE="${AGENT_DIR}/fix_manual.log"

mkdir -p "$AGENT_DIR"

log() {
    echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

log "============================================"
log "CraftingDead 独立修复脚本"
log "============================================"

# 步骤1：检查 Docker 环境
log "检查 Python 环境..."
if command -v python3 >/dev/null 2>&1; then
    PYTHON="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON="python"
else
    log "安装 Python..."
    apt-get update -qq && apt-get install -y -qq python3 >/dev/null 2>&1
    PYTHON="python3"
fi
log "Python: $($PYTHON --version)"

# 步骤2：查找所有 CraftingDead JAR
log ""
log "查找 CraftingDead JAR 文件..."
cd "$MODS_DIR"

# 使用 find 查找
ALL_JARS=$(find . -maxdepth 1 -name "crafting-dead-core*" -name "*.jar" ! -name "*.bak" -type f 2>/dev/null | sort)

if [ -z "$ALL_JARS" ]; then
    log "❌ 找不到 CraftingDead JAR"
    ls -la
    exit 1
fi

echo "$ALL_JARS" | while read -r jar_path; do
    jar_path=$(echo "$jar_path" | sed 's/^\.\///')
    log "  发现: $jar_path"
done

# 步骤3：处理 -all.jar（它包含 disableSaving 且未被补丁）
log ""
log "处理 -all.jar 文件..."

for jar_path in $ALL_JARS; do
    jar_name=$(basename "$jar_path")
    
    # 如果是 -all.jar，删除它（让 Forge 使用已补丁的非 -all 版本）
    if echo "$jar_name" | grep -q '\-all\.jar'; then
        log "  ⚠️  发现 -all.jar: $jar_name"
        log "  -all.jar 包含未补丁的 disableSaving，导致注册表缺失"
        log "  将其重命名为 .disabled，让 Forge 使用已补丁的非 -all 版本"
        
        mv "$jar_path" "${jar_path}.disabled"
        log "  ✅ 已重命名为: ${jar_name}.disabled"
    fi
done

# 步骤4：检查剩余的 JAR 并应用 Python 补丁
log ""
log "检查剩余 JAR 文件..."

REMAINING_JARS=$(find . -maxdepth 1 -name "crafting-dead-core*" -name "*.jar" ! -name "*.bak" ! -name "*.disabled" -type f 2>/dev/null | sort)

echo "$REMAINING_JARS" | while read -r jar_path; do
    jar_path=$(echo "$jar_path" | sed 's/^\.\///')
    jar_name=$(basename "$jar_path")
    
    log "  处理: $jar_name ($(du -h "$jar_path" | cut -f1))"
    
    # 检查是否已经补丁过
    if [ -f "${jar_path}.bak" ]; then
        log "    已有 .bak 备份，跳过（已补丁过）"
        continue
    fi
    
    # 应用 Python 补丁
    if [ -f "${AGENT_DIR}/patch_cd_jar.py" ]; then
        log "    运行 Python 补丁..."
        if $PYTHON "${AGENT_DIR}/patch_cd_jar.py" "$jar_path" 2>&1 | tee -a "$LOG_FILE"; then
            log "    ✅ 补丁成功"
        else
            log "    ❌ 补丁失败，尝试手动处理..."
        fi
    else
        log "    ⚠️  找不到 patch_cd_jar.py"
    fi
done

# 步骤5：验证补丁结果
log ""
log "验证补丁结果..."

FINAL_JARS=$(find . -maxdepth 1 -name "crafting-dead-core*" -name "*.jar" ! -name "*.disabled" -type f 2>/dev/null | sort)
POTAL_DISABLED=$(find . -maxdepth 1 -name "crafting-dead-core*" -name "*.disabled" -type f 2>/dev/null | sort)

log "活跃 JAR 列表:"
echo "$FINAL_JARS" | while read -r jp; do
    jp=$(echo "$jp" | sed 's/^\.\///')
    jn=$(basename "$jp")
    if [ -f "${jp}.bak" ]; then
        log "  ✅ $jn (已补丁，有 .bak)"
    else
        log "  ⚠️  $jn (未确认补丁状态)"
    fi
done

log "已禁用 JAR 列表:"
echo "$POTAL_DISABLED" | while read -r jp; do
    jp=$(echo "$jp" | sed 's/^\.\///')
    log "  🚫 $(basename $jp) - 已禁用"
done

# 步骤6：强制设置 JVM 参数
log ""
log "设置 JVM 参数..."
REQUIRED_JVM_OPTS="-Dfml.debugMissingRegistries=true -Dfml.resolveMissingRegistries=WARN -Dfml.ignoreMissingRegistries=true"

export JAVA_TOOL_OPTIONS="$REQUIRED_JVM_OPTS"
export JDK_JAVA_OPTIONS="$REQUIRED_JVM_OPTS"
export JVM_OPTS="$REQUIRED_JVM_OPTS"

log "  JAVA_TOOL_OPTIONS=$JAVA_TOOL_OPTIONS"
log "  JDK_JAVA_OPTIONS=$JDK_JAVA_OPTIONS"
log "  JVM_OPTS=$JVM_OPTS"

# 步骤7：清理 KubeJS 缓存
log ""
log "清理 KubeJS 缓存..."
rm -rf /data/kubejs/probe/ 2>/dev/null || true
rm -rf /data/kubejs/cache/ 2>/dev/null || true
log "  ✅ KubeJS 缓存已清理"

# 步骤8：检查 KubeJS 脚本
log ""
log "检查 KubeJS 修复脚本..."
KUBEJS_SCRIPT="/data/kubejs/server_scripts/craft_registry_fix.js"
if [ -f "$KUBEJS_SCRIPT" ]; then
    log "  ✅ $KUBEJS_SCRIPT 存在"
else
    log "  ⚠️  $KUBEJS_SCRIPT 不存在，KubeJS 将不执行注册表修复"
fi

# 步骤9：最终状态
log ""
log "============================================"
log "修复完成！"
log "============================================"
log ""
log "接下来请执行:"
log "  1. docker compose restart minecraft"
log "  2. 等待服务器启动完成"
log "  3. 查看日志: docker compose logs -f minecraft"
log "  4. 玩家尝试连接"
log ""
log "如果仍然失败，请运行:"
log "  docker compose exec minecraft bash $AGENT_DIR/diagnose_in_container.sh"
log ""
log "日志文件: $LOG_FILE"
log "============================================"

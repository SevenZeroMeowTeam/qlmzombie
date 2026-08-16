#!/bin/bash
# ============================================================
# 七零喵僵尸末日生存 - Minecraft 服务器入口包装脚本
# 功能：
#   - 将模板配置复制到 /data（避免权限问题）
#   - 依赖 JAR 自动释放（mc/libs + mcmod src/main/libs）
#   - 写入 EULA / RCON 配置
#   - 兼容 itzg/minecraft-server 镜像
# ============================================================
set -e

echo "[qlm] ========== entrypoint-wrapper 启动 =========="
echo "[qlm] 时间: $(date '+%Y-%m-%d %H:%M:%S %Z')"

# ---------- 1. 复制模板配置到 /data ----------
for f in server.properties eula.txt ops.json whitelist.json banned-players.json banned-ips.json; do
  if [ -f "/mc-templates/$f" ]; then
    if [ -f "/data/$f" ]; then
      echo "[qlm] 保留已有 /data/$f"
    else
      cp "/mc-templates/$f" "/data/$f" 2>/dev/null && echo "[qlm] 初始化 /data/$f"
    fi
  fi
done

# ---------- 2. 强制 EULA ----------
echo "eula=true" > /data/eula.txt
echo "[qlm] EULA 已写入 true"

# ---------- 3. 依赖 JAR 自动释放（防止 Missing registry / 服务器无法启动） ----------
echo "[qlm] 同步依赖 JAR..."
MC_MODS_DIR=/data/mods
mkdir -p "$MC_MODS_DIR"

# 3a. 来自 SeverAdmin/mc/libs（deploy.sh 已从 mcmod/src/main/libs 同步）
if [ -d "/mcmod-libs" ]; then
  find /mcmod-libs -maxdepth 1 -name '*.jar' 2>/dev/null | while read -r j; do
    cp -n "$j" "$MC_MODS_DIR/" 2>/dev/null && echo "[qlm]   libs -> $(basename "$j")"
  done
fi

# 3b. 来自 mcmod/src/main/libs 白名单单源（只读挂载）
if [ -d "/mcmod-libs-src" ]; then
  # 白名单：部署前由 deploy.sh 生成 libs-list.txt 过滤
  if [ -f "/mc-templates/libs-list.txt" ]; then
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      case "$line" in \#*) continue;; esac
      if [ -f "/mcmod-libs-src/$line" ]; then
        cp -n "/mcmod-libs-src/$line" "$MC_MODS_DIR/" 2>/dev/null && echo "[qlm]   src/main/libs -> $line"
      fi
    done < "/mc-templates/libs-list.txt"
  else
    # 无白名单时仅复制 qlmzombie 与 forge 核心，避免带入不需要的依赖
    for j in /mcmod-libs-src/*.jar; do
      base=$(basename "$j")
      case "$base" in
        qlmzombie-*.jar|forge-*.jar) cp -n "$j" "$MC_MODS_DIR/" 2>/dev/null && echo "[qlm]   src core -> $base" ;;
      esac
    done
  fi
fi

# ---------- 4. CraftingDead 补丁 agent（移除 disableSaving 调用，防止存档崩溃） ----------
if [ -d "/mc-agent" ] && [ -f "/mc-agent/patch_cd_jar.sh" ]; then
  echo "[qlm] 运行 CraftingDead 补丁..."
  bash /mc-agent/patch_cd_jar.sh || echo "[qlm] 补丁失败（忽略，继续启动）"
fi

echo "[qlm] ========== 启动 Minecraft 服务器 =========="
exec /start

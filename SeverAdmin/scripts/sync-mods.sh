#!/usr/bin/env bash
# ============================================================
# 七零喵僵尸末日生存 - 依赖模组同步脚本（Java 模式使用）
# 将 mcmod/src/main/libs 与 build/libs 同步到服务器 mods 目录
# ============================================================
set -euo pipefail

SEVERADMIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MCMOD_DIR="$(cd "${SEVERADMIN_DIR}/.." && pwd)"
LIBS_SOURCE_DIR="${MCMOD_DIR}/src/main/libs"
MC_DIR="${SEVERADMIN_DIR}/mc"
MODS_DIR="${MC_DIR}/mods"

mkdir -p "${MODS_DIR}" "${MC_DIR}/libs"

echo "[sync-mods] 同步依赖..."
while IFS= read -r line; do
  [ -z "${line}" ] && continue
  case "${line}" in \#*) continue;; esac
  if [ -f "${LIBS_SOURCE_DIR}/${line}" ]; then
    cp -f "${LIBS_SOURCE_DIR}/${line}" "${MC_DIR}/libs/" 2>/dev/null
    cp -f "${LIBS_SOURCE_DIR}/${line}" "${MODS_DIR}/" 2>/dev/null
  fi
done < "${MC_DIR}/libs-list.txt"

# 同步 qlmzombie 主模组（最新编译产物）
JAR=$(ls -t "${MCMOD_DIR}/build/libs"/qlmzombie-*.jar 2>/dev/null | grep -vE -- '-server|-sources|-javadoc' | head -1 || true)
if [ -n "${JAR}" ] && [ -f "${JAR}" ]; then
  cp -f "${JAR}" "${MODS_DIR}/"
  echo "[sync-mods] 已同步模组: $(basename "${JAR}")"
fi

echo "[sync-mods] 完成: $(ls "${MODS_DIR}"/*.jar 2>/dev/null | wc -l) 个 JAR"

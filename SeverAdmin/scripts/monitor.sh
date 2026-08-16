#!/usr/bin/env bash
# ============================================================
# 七零喵僵尸末日生存 - 监控脚本（Docker / Java 双模式）
# 用法: ./monitor.sh [docker|java|auto] [interval_seconds]
# ============================================================
set -uo pipefail

MODE="${1:-auto}"
INTERVAL="${2:-5}"
MC_CONTAINER="${MC_CONTAINER:-qlm-minecraft}"
MC_SERVICE="${MC_SERVICE:-qlm-minecraft}"

detect_mode() {
  if [ "${MODE}" = "docker" ] || { [ "${MODE}" = "auto" ] && command -v docker >/dev/null 2>&1 && docker ps >/dev/null 2>&1; }; then
    echo "docker"
  else
    echo "java"
  fi
}

monitor_once() {
  local m; m=$(detect_mode)
  clear
  echo "================ 七零喵僵尸末日生存 - 服务器监控 ================"
  echo "时间: $(date '+%Y-%m-%d %H:%M:%S')    模式: ${m}"
  echo "------------------------------------------------------------"
  if [ "${m}" = "docker" ]; then
    docker ps --filter "name=qlm-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    echo "------------------------------------------------------------"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" 2>/dev/null | grep -E "NAME|qlm-"
  else
    systemctl is-active "${MC_SERVICE}" 2>/dev/null | xargs echo "MC 服务:"
    systemctl is-active qlm-web 2>/dev/null | xargs echo "Web 服务:"
    echo "------------------------------------------------------------"
    ps aux | grep -E "java.*forge|node.*server.js" | grep -v grep | awk '{printf "  %s  %s%% CPU  %s%% MEM  %s\n", $11, $3, $4, $NF}'
  fi
  echo "------------------------------------------------------------"
}

echo "开始监控（每 ${INTERVAL} 秒刷新，Ctrl+C 退出）"
while true; do monitor_once; sleep "${INTERVAL}"; done

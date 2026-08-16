#!/usr/bin/env bash
# ============================================================
# 七零喵僵尸末日生存 - SeverAdmin 一键部署脚本
# 用法:
#   ./deploy.sh            # 自动检测部署方式
#   ./deploy.sh docker     # 强制 Docker 部署
#   ./deploy.sh java       # 强制 Java(systemd) 部署
#   ./deploy.sh status     # 查看部署状态
#   ./deploy.sh restart    # 静默重启（保存世界→公告→优雅重启）
#
# 域名: mc.sh197.dpdns.org
# ============================================================
set -euo pipefail

SEVERADMIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MCMOD_DIR="$(cd "${SEVERADMIN_DIR}/.." && pwd)"          # d:/mcmod
JAR_SOURCE_DIR="${MCMOD_DIR}/build/libs"                  # 编译产物
LIBS_SOURCE_DIR="${MCMOD_DIR}/src/main/libs"              # 依赖源
DOMAIN="${DOMAIN:-mc.sh197.dpdns.org}"

# ---------- 颜色 ----------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ---------- 工具函数 ----------
find_jar() {
  # 优先最新编译产物 qlmzombie-*.jar（排除 -server / -sources / -javadoc）
  local jar
  jar=$(ls -t "${JAR_SOURCE_DIR}"/qlmzombie-*.jar 2>/dev/null | grep -vE -- '-server|-sources|-javadoc' | head -1 || true)
  [ -n "${jar}" ] && echo "${jar}" && return 0
  # 其次 SeverAdmin/mc/mods 中已存在的
  jar=$(ls -t "${SEVERADMIN_DIR}/mc/mods"/qlmzombie-*.jar 2>/dev/null | head -1 || true)
  echo "${jar}"
}

sync_dependencies() {
  info "同步依赖 JAR（${LIBS_SOURCE_DIR} → ${SEVERADMIN_DIR}/mc/libs + mods）..."
  mkdir -p "${SEVERADMIN_DIR}/mc/libs" "${SEVERADMIN_DIR}/mc/mods"
  # 生成白名单
  if [ -f "${SEVERADMIN_DIR}/mc/libs-list.txt" ]; then
    cp "${SEVERADMIN_DIR}/mc/libs-list.txt" "${SEVERADMIN_DIR}/mc/libs-list.txt"
  else
    ls "${LIBS_SOURCE_DIR}"/*.jar 2>/dev/null | xargs -n1 basename | sed 's/^/libs-list.txt 生成: /' >/dev/null
    ls "${LIBS_SOURCE_DIR}"/*.jar 2>/dev/null | xargs -n1 basename > "${SEVERADMIN_DIR}/mc/libs-list.txt"
  fi
  # 复制依赖到 libs（entrypoint 会自动释放到 mods）
  while IFS= read -r line; do
    [ -z "${line}" ] && continue
    case "${line}" in \#*) continue;; esac
    if [ -f "${LIBS_SOURCE_DIR}/${line}" ]; then
      cp -f "${LIBS_SOURCE_DIR}/${line}" "${SEVERADMIN_DIR}/mc/libs/" 2>/dev/null
    fi
  done < "${SEVERADMIN_DIR}/mc/libs-list.txt"
  info "依赖同步完成: $(ls "${SEVERADMIN_DIR}/mc/libs"/*.jar 2>/dev/null | wc -l) 个 JAR"
}

fix_crlf() {
  # 避免 CRLF 导致 shell 脚本 / 配置文件解析问题
  find "${SEVERADMIN_DIR}" -name '*.sh' -o -name '*.conf' -o -name '*.properties' 2>/dev/null | \
    while read -r f; do sed -i 's/\r$//' "${f}" 2>/dev/null || true; done
}

port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/${1}") 2>/dev/null && exec 3>&- && return 0 || return 1; }
find_free_port() {
  local p="${1}"
  while port_in_use "${p}"; do p=$((p + 10)); done
  echo "${p}"
}

set_env_var() { # key value file
  local k="$1" v="$2" f="$3"
  if grep -q "^${k}=" "${f}" 2>/dev/null; then
    sed -i "s|^${k}=.*|${k}=${v}|" "${f}"
  else
    echo "${k}=${v}" >> "${f}"
  fi
}

# ---------- 检测 ----------
detect_mode() {
  if command -v docker >/dev/null 2>&1 && docker ps >/dev/null 2>&1; then
    echo "docker"
  elif command -v systemctl >/dev/null 2>&1; then
    echo "java"
  else
    echo "unknown"
  fi
}

# ---------- Docker 部署 ----------
deploy_docker() {
  info "========== Docker 模式部署 =========="
  [ -f "${SEVERADMIN_DIR}/.env" ] || { cp "${SEVERADMIN_DIR}/.env.example" "${SEVERADMIN_DIR}/.env"; warn "已从 .env.example 创建 .env，请修改密码后重新运行"; }
  fix_crlf
  sync_dependencies
  # 同步 qlmzombie jar 到 mods
  local jar; jar=$(find_jar)
  if [ -n "${jar}" ] && [ -f "${jar}" ]; then
    cp -f "${jar}" "${SEVERADMIN_DIR}/mc/mods/"
    info "已同步模组: $(basename "${jar}")"
  else
    warn "未找到 qlmzombie-*.jar，请先在 mcmod 执行: ./gradlew.bat build"
  fi
  # 检查端口
  local mc_port="${NGINX_MC_PORT:-25565}"
  if port_in_use "${mc_port}"; then
    local new_port; new_port=$(find_free_port "${mc_port}")
    warn "端口 ${mc_port} 被占用，改用 ${new_port}（修改 .env 的 NGINX_MC_PORT）"
    set_env_var "NGINX_MC_PORT" "${new_port}" "${SEVERADMIN_DIR}/.env"
  fi
  # 启动
  cd "${SEVERADMIN_DIR}"
  info "启动容器（首次拉取镜像可能需要几分钟）..."
  docker compose up -d --build
  info "========== 部署完成 =========="
  echo "  Minecraft: ${DOMAIN}:${NGINX_MC_PORT:-25565}"
  echo "  网站:      http://${DOMAIN}"
  echo "  后台:      http://${DOMAIN}/admin"
  echo "  Docker 状态: docker compose ps"
}

# ---------- Java 部署 ----------
deploy_java() {
  info "========== Java(systemd) 模式部署 =========="
  [ -f "${SEVERADMIN_DIR}/.env" ] || { cp "${SEVERADMIN_DIR}/.env.example" "${SEVERADMIN_DIR}/.env"; warn "请修改 .env 密码后重新运行"; }
  fix_crlf
  sync_dependencies
  # 同步 qlmzombie jar
  local jar; jar=$(find_jar)
  if [ -n "${jar}" ] && [ -f "${jar}" ]; then
    cp -f "${jar}" "${SEVERADMIN_DIR}/mc/mods/"
    info "已同步模组: $(basename "${jar}")"
  else
    warn "未找到 qlmzombie-*.jar"
  fi
  # 安装 systemd 服务
  if [ "$(id -u)" -eq 0 ] || command -v sudo >/dev/null 2>&1; then
    SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"
    ${SUDO} bash "${SEVERADMIN_DIR}/java-deploy/install.sh" "${SEVERADMIN_DIR}"
    info "========== 部署完成 =========="
    echo "  Minecraft: ${DOMAIN}:25565"
    echo "  后台:      http://${DOMAIN}/admin"
    echo "  状态:      systemctl status qlm-minecraft qlm-web"
  else
    error "Java 模式需要 root 权限安装 systemd 服务"
    exit 1
  fi
}

# ---------- 静默重启 ----------
restart_server() {
  info "静默重启：先保存世界并公告玩家..."
  local mode; mode=$(detect_mode)
  if [ "${mode}" = "docker" ]; then
    docker exec "${MC_CONTAINER:-qlm-minecraft}" rcon-cli save-all 2>/dev/null || true
    docker exec "${MC_CONTAINER:-qlm-minecraft}" rcon-cli "say [SeverAdmin] 服务器 10 秒后重启，请保存退出!" 2>/dev/null || true
    sleep 10
    docker restart "${MC_CONTAINER:-qlm-minecraft}"
  else
    rcon_cli() {
      # 若 rcon-cli 不可用则跳过
      command -v rcon-cli >/dev/null 2>&1 && rcon-cli "$@" || true
    }
    rcon_cli save-all
    rcon_cli "say [SeverAdmin] 服务器 10 秒后重启，请保存退出!"
    sleep 10
    sudo systemctl restart qlm-minecraft
  fi
  info "重启完成"
}

# ---------- 主流程 ----------
case "${1:-auto}" in
  docker)  deploy_docker ;;
  java)    deploy_java ;;
  status)
    mode=$(detect_mode)
    echo "部署模式: ${mode}"
    if [ "${mode}" = "docker" ]; then docker compose -f "${SEVERADMIN_DIR}/docker-compose.yml" ps 2>/dev/null || true
    else systemctl status qlm-minecraft --no-pager 2>/dev/null | head -20 || true; fi
    ;;
  restart) restart_server ;;
  sync)    sync_dependencies ;;
  *)
    mode=$(detect_mode)
    info "检测到部署模式: ${mode}"
    if [ "${mode}" = "docker" ]; then deploy_docker
    elif [ "${mode}" = "java" ]; then deploy_java
    else error "未检测到 Docker 或 systemd，请先安装 Docker（推荐）"; exit 1; fi
    ;;
esac

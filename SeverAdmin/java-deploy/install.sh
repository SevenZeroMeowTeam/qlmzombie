#!/usr/bin/env bash
# ============================================================
# 七零喵僵尸末日生存 - Java(systemd) 模式安装脚本
# 用法: sudo bash install.sh <SeverAdmin目录>
# 安装后:
#   systemctl start qlm-minecraft   # MC 服务器
#   systemctl start qlm-web         # Web 后台
# ============================================================
set -euo pipefail

SEVERADMIN_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
INSTALL_DIR="/opt/qlm"

if [ "$(id -u)" -ne 0 ]; then
  echo "需要 root 权限，请使用: sudo bash $0"
  exit 1
fi

echo "[install] 安装目录: ${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}/mc" "${INSTALL_DIR}/web" "${INSTALL_DIR}/web/downloads" "${INSTALL_DIR}/scripts"

# ---------- 同步文件 ----------
echo "[install] 同步 MC 服务器文件..."
cp -rf "${SEVERADMIN_DIR}/mc/." "${INSTALL_DIR}/mc/"

echo "[install] 同步 Web 后台..."
cp -rf "${SEVERADMIN_DIR}/web/." "${INSTALL_DIR}/web/"
cd "${INSTALL_DIR}/web"
if [ ! -d node_modules ]; then
  npm install --omit=dev --registry=https://registry.npmmirror.com || npm install --omit=dev
fi

echo "[install] 同步脚本..."
cp -f "${SEVERADMIN_DIR}/scripts/"*.sh "${INSTALL_DIR}/scripts/"

# ---------- 准备 forge-server.jar ----------
# 若 mc 目录无服务器 jar，提示从 mcmod 构建
if [ ! -f "${INSTALL_DIR}/mc/forge-server.jar" ]; then
  echo "[install] 未找到 forge-server.jar"
  echo "  请从 mcmod 项目构建 Forge 服务器:"
  echo "    cd ${SEVERADMIN_DIR}/.. && ./gradlew.bat prepareRuns"
  echo "  或将 Forge 1.20.1-47.4.22 服务端复制为:"
  echo "    ${INSTALL_DIR}/mc/forge-server.jar"
fi

# ---------- 安装 systemd 服务 ----------
echo "[install] 安装 systemd 服务..."
# 替换内存参数占位符
sed -i "s/-Xmx%I/-Xmx${MC_MAX_MEMORY:-2G}/" "${SEVERADMIN_DIR}/java-deploy/qlm-minecraft.service"
install -m 644 "${SEVERADMIN_DIR}/java-deploy/qlm-minecraft.service" /etc/systemd/system/qlm-minecraft.service
install -m 644 "${SEVERADMIN_DIR}/java-deploy/qlm-web.service" /etc/systemd/system/qlm-web.service

# 环境文件
if [ ! -f "${INSTALL_DIR}/.env" ]; then
  cp "${SEVERADMIN_DIR}/.env.example" "${INSTALL_DIR}/.env"
  echo "[install] 已创建 ${INSTALL_DIR}/.env，请修改密码后执行: systemctl restart qlm-web"
fi

systemctl daemon-reload

echo "[install] 启动服务..."
systemctl enable qlm-minecraft qlm-web 2>/dev/null || true
systemctl start qlm-minecraft 2>/dev/null || echo "[install] MC 服务器启动失败，请检查: journalctl -u qlm-minecraft"
systemctl start qlm-web 2>/dev/null || echo "[install] Web 后台启动失败，请检查: journalctl -u qlm-web"

echo "[install] ========== 安装完成 =========="
echo "  MC 服务器: systemctl status qlm-minecraft"
echo "  Web 后台:  systemctl status qlm-web"
echo "  网站:      http://${DOMAIN:-mc.sh197.dpdns.org}"
echo "  后台:      http://${DOMAIN:-mc.sh197.dpdns.org}/admin"

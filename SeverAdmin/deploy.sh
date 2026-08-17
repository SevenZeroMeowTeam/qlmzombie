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
# 校验 jar 是否为有效 zip（含 zip END 头）。构建中断可能留下截断/损坏的 jar
# （如 build40 主 jar 仅 114MB 且无 EOCD，部署后服务器启动直接崩 "zip END header not found"），
# 因此部署前必须校验，绝不能把损坏的 jar 发上服务器。
is_valid_jar() { # 文件路径
  local f="$1"
  [ -f "${f}" ] || return 1
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import sys,zipfile; zipfile.ZipFile(sys.argv[1]).close()" "${f}" >/dev/null 2>&1 && return 0 || return 1
  fi
  # 无 python3 时退化为尾部 magic 检查（zip EOCD 签名 PK\x05\x06 = 0x504b0506）
  local tail_hex
  tail_hex=$(tail -c 40 "${f}" 2>/dev/null | od -An -tx1 2>/dev/null | tr -d ' \n' || true)
  case "${tail_hex}" in *504b0506*) return 0;; esac
  return 1
}

# 查找可部署的 qlmzombie jar：只返回【有效】的 jar，按 主 jar → server 包 → mods 已有 顺序回退。
find_jar() {
  local jar last_bad=""
  # 优先最新编译产物 qlmzombie-*.jar（排除 -server / -sources / -javadoc，跳过损坏/截断的）
  for jar in $(ls -t "${JAR_SOURCE_DIR}"/qlmzombie-*.jar 2>/dev/null | grep -vE -- '-server|-sources|-javadoc'); do
    if is_valid_jar "${jar}"; then
      echo "${jar}"
      return 0
    fi
    last_bad="${jar}"
  done
  if [ -n "${last_bad}" ]; then
    warn "最新构建产物损坏/截断（无效 zip），已跳过: $(basename "${last_bad}")"
  fi
  # 其次最新【有效】server 专用包（DEDICATED_SERVER 发行版，同样内嵌 libs）
  for jar in $(ls -t "${JAR_SOURCE_DIR}"/qlmzombie-*-server.jar 2>/dev/null); do
    if is_valid_jar "${jar}"; then
      warn "回退使用 server 专用包: $(basename "${jar}")"
      echo "${jar}"
      return 0
    fi
  done
  # 最后 SeverAdmin/mc/mods 中已存在的有效 jar
  for jar in $(ls -t "${SEVERADMIN_DIR}/mc/mods"/qlmzombie-*.jar 2>/dev/null); do
    if is_valid_jar "${jar}"; then
      warn "回退使用 mods 目录中已有的有效 jar: $(basename "${jar}")"
      echo "${jar}"
      return 0
    fi
  done
  echo ""
}

# ---------- 同步 qlmzombie 主模组 jar 到 mods（含完整性校验 + 去重） ----------
# 部署前清理 mods 中残留的旧 qlmzombie jar（主 jar 与 server jar 并存会导致
# Forge 重复加载同一个 mod 崩溃）；find_jar 已保证只取有效 jar。
deploy_qlmzombie_jar() {
  local jar; jar=$(find_jar)
  if [ -z "${jar}" ] || [ ! -f "${jar}" ]; then
    warn "未找到有效的 qlmzombie-*.jar，请先在 mcmod 执行: ./gradlew.bat build"
    return 1
  fi
  if [ "$(dirname "${jar}")" = "${SEVERADMIN_DIR}/mc/mods" ]; then
    # 源已在 mods 内：只保留这一个，清理并存的 server/旧变体
    local old base; base=$(basename "${jar}")
    for old in "${SEVERADMIN_DIR}/mc/mods"/qlmzombie-*.jar; do
      [ -e "$old" ] || continue
      [ "$(basename "$old")" = "${base}" ] && continue
      rm -f "$old"
      info "清理 mods 中并存的旧 qlmzombie jar: $(basename "$old")"
    done
    info "模组已在 mods 目录，跳过复制: ${base}"
  else
    # 清理 mods 中历史残留的所有 qlmzombie jar（避免重复 mod 崩溃）
    local old
    for old in "${SEVERADMIN_DIR}/mc/mods"/qlmzombie-*.jar; do
      [ -e "$old" ] || continue
      rm -f "$old"
      info "清理 mods 中旧 qlmzombie jar: $(basename "$old")"
    done
    cp -f "${jar}" "${SEVERADMIN_DIR}/mc/mods/"
    info "已同步模组: $(basename "${jar}")"
  fi
  # 提取模组 jar 内嵌依赖（权威单源，保证 mandatory 依赖检查前全部就位）
  extract_embedded_libs "${jar}" "${SEVERADMIN_DIR}/mc/mods" || warn "内嵌依赖提取失败，容器内入口脚本将兜底"
  # 服务端移除纯客户端模组与乱码文件名（避免 ETF 崩溃 / CrashAssistant 重复检测）
  cleanup_client_only_mods "${SEVERADMIN_DIR}/mc/mods"
  cleanup_garbled_files "${SEVERADMIN_DIR}/mc/mods"
  # 确保关键模组 active（清理历史 .disabled 残留，如 Kleiders / ThirstWasTaken）
  ensure_required_mods_active "${SEVERADMIN_DIR}/mc/mods"
  # 预禁用冲突模组（ToughAsNails，避免 Thirst 兼容配方 NPE）
  disable_conflict_mods "${SEVERADMIN_DIR}/mc/mods"
  # 应用 KubeJS 补丁（修复 ConcurrentModificationException 启动失败）
  apply_kubejs_patch "${SEVERADMIN_DIR}/mc/mods"
  return 0
}

sync_dependencies() {
  info "同步依赖 JAR（${LIBS_SOURCE_DIR} → ${SEVERADMIN_DIR}/mc/libs + mods）..."
  mkdir -p "${SEVERADMIN_DIR}/mc/libs" "${SEVERADMIN_DIR}/mc/mods"
  # 白名单：存在则保留（人工维护）；不存在则从源码 libs 生成
  # 修复：原实现 `cp A A` 复制到自身会报 'same file' 错误
  if [ ! -f "${SEVERADMIN_DIR}/mc/libs-list.txt" ]; then
    ls "${LIBS_SOURCE_DIR}"/*.jar 2>/dev/null | xargs -n1 basename > "${SEVERADMIN_DIR}/mc/libs-list.txt" || true
    info "已生成依赖白名单: $(wc -l < "${SEVERADMIN_DIR}/mc/libs-list.txt") 条"
  fi
  # 复制依赖到 libs（自动跳过纯客户端模组，不破坏其他依赖；entrypoint 会自动释放到 mods）
  local synced=0 skipped=0
  while IFS= read -r line; do
    [ -z "${line}" ] && continue
    case "${line}" in \#*) continue;; esac
    if is_client_only_mod "${line}"; then
      warn "服务端跳过纯客户端模组: ${line}"
      skipped=$((skipped + 1))
      continue
    fi
    if [ -f "${LIBS_SOURCE_DIR}/${line}" ]; then
      cp -f "${LIBS_SOURCE_DIR}/${line}" "${SEVERADMIN_DIR}/mc/libs/" 2>/dev/null
      synced=$((synced + 1))
    fi
  done < "${SEVERADMIN_DIR}/mc/libs-list.txt"
  # 清理 mc/libs 中历史残留的纯客户端模组与乱码文件名
  cleanup_client_only_mods "${SEVERADMIN_DIR}/mc/libs"
  cleanup_garbled_files "${SEVERADMIN_DIR}/mc/libs"
  # 应用 KubeJS 补丁（修复 ConcurrentModificationException 启动失败）
  apply_kubejs_patch "${SEVERADMIN_DIR}/mc/libs"
  info "依赖同步完成: $(ls "${SEVERADMIN_DIR}/mc/libs"/*.jar 2>/dev/null | wc -l) 个 JAR（同步 ${synced}，跳过客户端 ${skipped}）"
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

# ---------- 从 .env 读取变量（避免 source 含空格值时失败） ----------
env_get() { # key default
  local k="$1" def="${2:-}" v="${2:-}"
  if [ -f "${SEVERADMIN_DIR}/.env" ]; then
    local line
    line=$(grep -E "^${k}=" "${SEVERADMIN_DIR}/.env" 2>/dev/null | tail -1 || true)
    [ -n "${line}" ] && v="${line#*=}"
  fi
  echo "${v}"
}

# ---------- 生成额外 MC 转发入口（多重转发 · 降低延迟） ----------
# 根据 .env 的 MC_EXTRA_PORTS 生成 nginx/stream-extra.conf：
#   格式: MC_EXTRA_PORTS="端口:后端地址,端口:后端地址"
#   例:   MC_EXTRA_PORTS="25566:203.0.113.10:25565,25567:198.51.100.20:25565"
generate_stream_extra() {
  local extra_file="${SEVERADMIN_DIR}/nginx/stream-extra.conf"
  local ports; ports=$(env_get MC_EXTRA_PORTS "")
  {
    echo "# ============================================================"
    echo "# 额外 MC 转发入口（多重转发 · 降低延迟）"
    echo "# 由 deploy.sh 根据 .env MC_EXTRA_PORTS 自动生成，请勿手改。"
    echo "# ============================================================"
    if [ -n "${ports}" ]; then
      local IFS=','
      local entry port backend
      for entry in ${ports}; do
        port="${entry%%:*}"
        backend="${entry#*:}"
        [ -z "${port}" ] && continue
        echo "    upstream mc_relay_${port} {"
        echo "        server ${backend};"
        echo "    }"
        echo "    server {"
        echo "        listen ${port};"
        echo "        proxy_pass mc_relay_${port};"
        echo "        proxy_connect_timeout 10s;"
        echo "        proxy_timeout 600s;"
        echo "        proxy_socket_keepalive on;"
        echo "        tcp_nodelay on;"
        echo "    }"
      done
      info "已生成额外 MC 转发入口: ${ports}"
    else
      echo "# 未配置 MC_EXTRA_PORTS，仅使用主入口。"
    fi
  } > "${extra_file}"
  info "stream-extra.conf 已更新（${extra_file}）"
}

# ---------- 从模组 jar 提取内嵌依赖到目标目录（权威单源） ----------
# 模组 jar 内嵌 libs/*.jar（118 个，kotlinforforge 走 jarjar 除外）。
# 曾因 libs-list 同步遗漏导致服务器 mods/ 缺失全部 [中文名] 前缀依赖，
# 报 "Missing or unsupported mandatory dependencies" 崩溃，故部署时直接从 jar 提取。
# 依序尝试 unzip → python3 → jar（宿主机至少有一种可用）。
extract_embedded_libs() { # src_jar dest_dir：解包模组 jar 内嵌依赖并过滤纯客户端模组
  local src="$1" dest="$2" n
  [ -f "${src}" ] || return 1
  mkdir -p "${dest}"
  if command -v unzip >/dev/null 2>&1; then
    if unzip -o -j "${src}" 'libs/*.jar' -d "${dest}/" >/dev/null 2>&1; then
      cleanup_client_only_mods "${dest}"   # 解包后立即过滤纯客户端模组
      n=$(ls "${dest}"/*.jar 2>/dev/null | wc -l)
      info "已从模组 jar 提取内嵌依赖: ${n} 个"
      return 0
    fi
  fi
  if command -v python3 >/dev/null 2>&1; then
    if python3 - "${src}" "${dest}" <<'PYEOF' >/dev/null 2>&1; then
import sys, os, zipfile, shutil
src, dest = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z:
    for name in z.namelist():
        if name.startswith('libs/') and name.endswith('.jar'):
            with z.open(name) as fi, open(os.path.join(dest, os.path.basename(name)), 'wb') as fo:
                shutil.copyfileobj(fi, fo)
PYEOF
      cleanup_client_only_mods "${dest}"   # 解包后立即过滤纯客户端模组
      n=$(ls "${dest}"/*.jar 2>/dev/null | wc -l)
      info "已用 python3 提取内嵌依赖: ${n} 个"
      return 0
    fi
  fi
  if command -v jar >/dev/null 2>&1; then
    local tmp; tmp=$(mktemp -d)
    if (cd "${tmp}" && jar xf "${src}" libs/) >/dev/null 2>&1; then
      cp -f "${tmp}"/libs/*.jar "${dest}/" 2>/dev/null
      cleanup_client_only_mods "${dest}"   # 解包后立即过滤纯客户端模组
      n=$(ls "${dest}"/*.jar 2>/dev/null | wc -l)
      info "已用 jar 提取内嵌依赖: ${n} 个"
      rm -rf "${tmp}"
      return 0
    fi
    rm -rf "${tmp}"
  fi
  return 1
}

# ---------- 纯客户端模组过滤（专用服务端移除） ----------
# ETF 的 ResourceLocation mixin 会在服务端加载客户端类 Screen 导致崩溃，
# 其余为无服务端功能的纯渲染/UI 模组。仅精确前缀匹配，绝不误删其他依赖/核心模组。
# 注意：kleiders_custom_renderer 不在过滤列表 —— 它注册网络 channel（客户端皮肤/模型同步），
# 服务器缺失会导致玩家连接报 "mismatched mod channel list"；服务端加载安全（空 mixin、无客户端类引用），
# 因此保留在服务端以匹配客户端 channel。
CLIENT_ONLY_MODS="entity_texture_features entity_model_features 3d-armor skinlayers3d imblocker sodiumdynamiclights sodiumoptionsapi ysm"

is_client_only_mod() { # 文件名
  local f="$1" lower p
  lower=$(echo "$f" | tr '[:upper:]' '[:lower:]')
  case "$lower" in \[*\]) lower="${lower#*] }";; esac
  for p in $CLIENT_ONLY_MODS; do
    case "$lower" in "$p"*) return 0;; esac
  done
  return 1
}

cleanup_client_only_mods() { # dir：解包/同步后清理目标目录中残留的纯客户端模组
  local dir="$1" f name
  [ -d "${dir}" ] || return 0
  for f in "${dir}"/*.jar; do
    [ -e "$f" ] || continue
    name=$(basename "$f")
    if is_client_only_mod "$name"; then
      rm -f "$f"
      warn "服务端移除纯客户端模组: $name"
    fi
  done
}

cleanup_garbled_files() { # dir：删除乱码/损坏文件名的 jar/disabled，避免 CrashAssistant 重复检测崩溃
  local dir="$1"
  [ -d "${dir}" ] || return 0
  if command -v python3 >/dev/null 2>&1; then
    python3 - "${dir}" <<'PYEOF' 2>/dev/null || true
import sys, os
d = sys.argv[1]
def is_mojibake(n):
    if any(0xDC80 <= ord(c) <= 0xDCFF for c in n):
        return True
    for ch in n:
        o = ord(ch)
        if (0x0370 <= o <= 0x05FF or 0x2500 <= o <= 0x257F or 0x3040 <= o <= 0x30FF or o == 0xFFFD):
            return True
    return '锟斤拷' in n
for name in list(os.listdir(d)):
    if not (name.endswith('.jar') or name.endswith('.disabled')):
        continue
    if is_mojibake(name):
        try:
            os.remove(os.path.join(d, name))
            print('[deploy] 删除乱码文件名: %r' % name)
        except OSError:
            pass
PYEOF
  else
    warn "无 python3，跳过乱码文件名清理"
  fi
}

# ---------- 确保关键模组 active（清理历史 .disabled 残留） ----------
# Kleiders Custom Renderer 注册网络 channel（客户端皮肤/模型同步），服务器端缺失会拒绝
# 对端连接（"mismatched mod channel list" / 服务器缺少 Kleiders）。旧版 ModDependencyHandler
# 或历史部署曾将其禁用为 .disabled，部署时主动恢复为 active，避免首次启动仍不加载。
# ThirstWasTaken 同理：客户端装有外置 JAR（modId=thirst，通道 thirst:main），服务器端必须
# 保留它以匹配客户端网络 channel（否则玩家报 "服务器缺少 Thirst was Taken"），
# 若历史部署曾禁用为 .disabled，部署时一并恢复。
ensure_required_mods_active() { # dir
  local dir="$1" f name
  [ -d "${dir}" ] || return 0
  for f in "${dir}"/kleiders_custom_renderer*.jar.disabled; do
    [ -e "$f" ] || continue
    name=$(basename "$f")
    rm -f "$f"
    info "恢复关键模组为 active（移除 .disabled）: ${name}"
  done
  for f in "${dir}"/*ThirstWasTaken*.jar.disabled; do
    [ -e "$f" ] || continue
    name=$(basename "$f")
    rm -f "$f"
    info "恢复 ThirstWasTaken 为 active（移除 .disabled）: ${name}"
  done
}

# ---------- 预禁用冲突模组（ToughAsNails） ----------
# ToughAsNails 与项目口渴系统冲突，必须让 Forge 在扫描 mods 前就看到 .disabled
# （运行时 ModDependencyHandler 禁用太晚——模组已被加载，且外部 ThirstWasTaken 的
#  compat/toughasnails 配方会因 null ItemStack 触发 NPE：
#   Zeta RecipeCrawlHandler "Failed to scan recipe thirst:compat/toughasnails/..." +
#   玩家登录配方同步 EncoderException "Cannot invoke ItemStack.isEmpty()... null"）。
# 预禁用后：toughasnails 不加载 → mod_loaded 条件失败 → 兼容配方被跳过 → 无 NPE。
# ModDependencyHandler.releaseJar 见 .disabled 存在（且无 active jar）时会保持禁用，不会重新释放。
disable_conflict_mods() { # dir
  local dir="$1" f name
  [ -d "${dir}" ] || return 0
  for f in "${dir}"/*ToughAsNails*.jar; do
    [ -e "$f" ] || continue
    name=$(basename "$f")
    if [ -e "${f}.disabled" ]; then
      # .disabled 已存在但 active jar 也在（同步/解包可能重复复制）：删除 active 保留禁用
      rm -f "$f"
      info "移除与 .disabled 并存的 ToughAsNails active jar（保留禁用）: ${name}"
      continue
    fi
    mv -f "$f" "${f}.disabled"
    info "预禁用冲突模组（ToughAsNails，避免 Thirst 兼容配方 NPE）: ${name}"
  done
}

# ---------- 部署后自检：关键模组在位 ----------
verify_mods() { # dir
  local dir="$1" k
  [ -d "${dir}" ] || { warn "mods 目录不存在: ${dir}"; return 0; }
  if ls "${dir}"/kleiders_custom_renderer*.jar >/dev/null 2>&1; then
    k=$(ls "${dir}"/kleiders_custom_renderer*.jar | head -1 | xargs basename)
    info "✓ Kleiders Custom Renderer 在位（channel 匹配）: ${k}"
  else
    warn "⚠ Kleiders Custom Renderer 不在 mods，玩家可能报 mismatched mod channel list"
  fi
  if ls "${dir}"/qlmzombie-*.jar >/dev/null 2>&1; then
    k=$(ls "${dir}"/qlmzombie-*.jar | grep -vE -- '-sources|-javadoc' | head -1 | xargs basename)
    info "✓ qlmzombie 主模组在位: ${k}"
  else
    warn "⚠ qlmzombie 主模组不在 mods"
  fi
  if ls "${dir}"/*ThirstWasTaken*.jar >/dev/null 2>&1; then
    k=$(ls "${dir}"/*ThirstWasTaken*.jar | head -1 | xargs basename)
    info "✓ ThirstWasTaken 在位（thirst:main 通道匹配）: ${k}"
  else
    warn "⚠ ThirstWasTaken 不在 mods，玩家可能报 mismatched mod channel list（服务器缺少 Thirst）"
  fi
}

# ---------- KubeJS 补丁（修复 ScriptManager ConcurrentModificationException 启动失败） ----------
# KubeJS 6.5 的 KubeJSPlugins.LIST 是静态 ArrayList，Forge 并行加载 mod 时另一个 mod 的
# 构造会并发 add 插件，导致 ScriptManager.load 遍历时抛 CME，服务器无法启动
# （"KubeJS (kubejs) has failed to load correctly / java.util.ConcurrentModificationException"）。
# 补丁脚本把 LIST 改为线程安全的 CopyOnWriteArrayList（迭代不抛 CME），已补丁时幂等跳过。
apply_kubejs_patch() { # dir：对目录中的 kubejs jar 应用补丁
  local dir="$1" k
  [ -d "${dir}" ] || return 0
  for k in "${dir}"/kubejs-forge-*.jar; do
    [ -e "$k" ] || continue
    if command -v python3 >/dev/null 2>&1 && [ -f "${SEVERADMIN_DIR}/mc/agent/patch_kubejs_jar.py" ]; then
      if python3 "${SEVERADMIN_DIR}/mc/agent/patch_kubejs_jar.py" "$k" >/dev/null 2>&1; then
        info "KubeJS 补丁已应用: $(basename "$k")"
      else
        warn "KubeJS 补丁失败: $(basename "$k")"
      fi
    else
      warn "无 python3 或补丁脚本，跳过 KubeJS 补丁"
    fi
    break
  done
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
  # 生成额外 MC 转发入口（多重转发 · 降低延迟）
  generate_stream_extra
  # 同步 qlmzombie jar 到 mods（完整性校验 + 去重，见 deploy_qlmzombie_jar）
  deploy_qlmzombie_jar || true
  # 检查主入口端口
  local mc_port; mc_port=$(env_get NGINX_MC_PORT 25565)
  if port_in_use "${mc_port}"; then
    local new_port; new_port=$(find_free_port "${mc_port}")
    warn "端口 ${mc_port} 被占用，改用 ${new_port}（修改 .env 的 NGINX_MC_PORT）"
    set_env_var "NGINX_MC_PORT" "${new_port}" "${SEVERADMIN_DIR}/.env"
  fi
  # 检查额外转发入口端口占用
  local extra_ports; extra_ports=$(env_get MC_EXTRA_PORTS "")
  if [ -n "${extra_ports}" ]; then
    local IFS=','
    local rp
    for rp in ${extra_ports}; do
      local p="${rp%%:*}"
      if port_in_use "${p}"; then
        warn "额外转发端口 ${p} 被占用，请修改 .env 的 MC_EXTRA_PORTS 或释放端口"
      fi
    done
  fi
  # 启动
  cd "${SEVERADMIN_DIR}"
  info "启动容器（首次拉取镜像可能需要几分钟）..."
  docker compose up -d --build
  # 部署后自检：关键模组在位（channel 匹配）
  verify_mods "${SEVERADMIN_DIR}/mc/mods"
  info "========== 部署完成 =========="
  # 域名不带端口（25565 为 MC 默认端口，玩家直连域名即可；非默认端口才显示端口）
  local mc_port; mc_port=$(env_get NGINX_MC_PORT 25565)
  if [ "${mc_port}" = "25565" ]; then
    echo "  Minecraft: ${DOMAIN}"
  else
    echo "  Minecraft: ${DOMAIN}:${mc_port}"
  fi
  if [ -n "${extra_ports}" ]; then
    echo "  额外入口（多重转发）: ${extra_ports}"
  fi
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
  # 同步 qlmzombie jar 到 mods（完整性校验 + 去重，见 deploy_qlmzombie_jar）
  deploy_qlmzombie_jar || true
  # 安装 systemd 服务
  if [ "$(id -u)" -eq 0 ] || command -v sudo >/dev/null 2>&1; then
    SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"
    ${SUDO} bash "${SEVERADMIN_DIR}/java-deploy/install.sh" "${SEVERADMIN_DIR}"
    info "========== 部署完成 =========="
    echo "  Minecraft: ${DOMAIN}"
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

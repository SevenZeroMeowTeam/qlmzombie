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

# 纯客户端模组前缀（专用服务端跳过：ETF mixin 会在服务端加载客户端类 Screen 导致崩溃，
# 其余为无服务端功能的纯渲染/UI 模组）。
# 注意：kleiders_custom_renderer 不在列表 —— 它注册网络 channel（客户端皮肤/模型同步），
# 服务器缺失会导致玩家连接报 "mismatched mod channel list"；服务端加载安全（空 mixin、无客户端类引用），
# 因此保留在服务端以匹配客户端 channel。
CLIENT_ONLY_MODS="entity_texture_features entity_model_features 3d-armor skinlayers3d imblocker sodiumdynamiclights sodiumoptionsapi ysm"

is_client_only() { # 文件名
  local f="$1" lower
  lower=$(echo "$f" | tr '[:upper:]' '[:lower:]')
  case "$lower" in \[*\]) lower="${lower#*] }";; esac
  local p
  for p in $CLIENT_ONLY_MODS; do
    case "$lower" in "$p"*) return 0;; esac
  done
  return 1
}

# 3a. 来自 SeverAdmin/mc/libs（deploy.sh 已从 mcmod/src/main/libs 同步）
if [ -d "/mcmod-libs" ]; then
  find /mcmod-libs -maxdepth 1 -name '*.jar' 2>/dev/null | while read -r j; do
    base=$(basename "$j")
    if is_client_only "$base"; then
      echo "[qlm]   服务端跳过纯客户端模组: $base"
    else
      cp -n "$j" "$MC_MODS_DIR/" 2>/dev/null && echo "[qlm]   libs -> $base"
    fi
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
        if is_client_only "$line"; then
          echo "[qlm]   服务端跳过纯客户端模组: $line"
        else
          cp -n "/mcmod-libs-src/$line" "$MC_MODS_DIR/" 2>/dev/null && echo "[qlm]   src/main/libs -> $line"
        fi
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

# 3c. 从 qlmzombie 模组 JAR 内嵌 libs/ 提取全部依赖（权威单源，不依赖 deploy 同步/挂载）
#     保证 mandatory 依赖检查（polyeng→ae2、kubejs→rhino、refinedpolymorph→refinedstorage 等）
#     在 mod 初始化前全部就位，避免 Missing or unsupported mandatory dependencies 崩溃。
#     优先 python3（zipfile 尊重 UTF-8 标志，中文文件名正确）；unzip 兜底
#     （部分镜像 unzip 不识别 UTF-8 标志，会把 [中文名] 解成乱码导致重复/损坏模组）。
# 3c 前置校验：主模组 jar 完整性 + 去重
# 构建中断可能留下截断/损坏的主 jar（无效 zip、无 zip END 头，如 build40 主 jar 仅 114MB），
# 直接启动会崩 "zip END header not found"。这里提前检测并给出明确提示；同时若 mods 中
# 并存主 jar 与 server 包，会导致 Forge 重复加载 qlmzombie mod，只保留最新一个。
QLM_JAR_COUNT=0
for mjar in "$MC_MODS_DIR"/qlmzombie-*.jar; do
  [ -f "$mjar" ] || continue
  QLM_JAR_COUNT=$((QLM_JAR_COUNT + 1))
done
if [ "$QLM_JAR_COUNT" -gt 1 ]; then
  # 保留最新的一个，删除其余（避免重复 mod 崩溃）
  latest=$(ls -t "$MC_MODS_DIR"/qlmzombie-*.jar | head -1)
  for mjar in "$MC_MODS_DIR"/qlmzombie-*.jar; do
    [ "$mjar" = "$latest" ] && continue
    echo "[qlm]   删除并存的旧 qlmzombie jar（保留 $latest）: $(basename "$mjar")"
    rm -f "$mjar"
  done
fi

for mjar in "$MC_MODS_DIR"/qlmzombie-*.jar; do
  [ -f "$mjar" ] || continue
  if command -v python3 >/dev/null 2>&1 && ! python3 -c "import sys,zipfile; zipfile.ZipFile(sys.argv[1]).close()" "$mjar" 2>/dev/null; then
    echo "[qlm] ⚠️  主模组 jar 损坏或截断（无效 zip）: $(basename "$mjar")"
    echo "[qlm] ⚠️  请重新构建并重新上传: cd /mcmod && ./gradlew.bat build && ./deploy.sh docker"
    echo "[qlm] ⚠️  服务器将因主模组损坏而无法启动（Forge: zip END header not found）"
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$mjar" "$MC_MODS_DIR" <<'PYEOF' || echo "[qlm]   python3 提取失败"
import sys, os, zipfile, shutil
src, dest = sys.argv[1], sys.argv[2]
skip = ('entity_texture_features','entity_model_features','3d-armor','skinlayers3d','imblocker','sodiumdynamiclights','sodiumoptionsapi','ysm')
def is_mojibake(n):
    # 无效 UTF-8 字节（os.listdir 以 surrogateescape 返回 \udc80-\udcff）
    if any(0xDC80 <= ord(c) <= 0xDCFF for c in n):
        return True
    for ch in n:
        o = ord(ch)
        # 中文文件名中不应出现的外语/符号区段（乱码标志）
        if (0x0370 <= o <= 0x05FF        # Greek/Cyrillic/Armenian/Hebrew
                or 0x2500 <= o <= 0x257F  # Box Drawing
                or 0x3040 <= o <= 0x30FF  # 日文假名
                or o == 0xFFFD):          # U+FFFD 替换符
            return True
    return '锟斤拷' in n
# 清理历史乱码/损坏文件名，避免 CrashAssistant 重复检测崩溃
for name in list(os.listdir(dest)):
    if not (name.endswith('.jar') or name.endswith('.disabled')):
        continue
    if is_mojibake(name):
        os.remove(os.path.join(dest, name))
        print('[qlm]   删除乱码文件名: %r' % name)
# 删除已存在的纯客户端模组（旧版 ModDependencyHandler 可能在运行期释放过，防止 ETF 崩溃/重复）
for name in list(os.listdir(dest)):
    if not (name.endswith('.jar') or name.endswith('.disabled')):
        continue
    low = name.lower()
    if low.startswith('['):
        low = low[low.find(']') + 1:].strip()
    if any(low.startswith(p) for p in skip):
        os.remove(os.path.join(dest, name))
        print('[qlm]   删除纯客户端模组: %s' % name)
with zipfile.ZipFile(src) as z:
    for name in z.namelist():
        if not (name.startswith('libs/') and name.endswith('.jar')):
            continue
        base = os.path.basename(name)
        low = base.lower()
        if low.startswith('['):
            low = low[low.find(']') + 1:].strip()
        if any(low.startswith(p) for p in skip):
            print('[qlm]   服务端跳过纯客户端模组: %s' % base)
            continue
        target = os.path.join(dest, base)
        if not os.path.exists(target):
            with z.open(name) as fi, open(target, 'wb') as fo:
                shutil.copyfileobj(fi, fo)
            print('[qlm]   jar内嵌 -> %s' % base)
PYEOF
  elif command -v unzip >/dev/null 2>&1; then
    unzip -o -j "$mjar" 'libs/*.jar' -d "$MC_MODS_DIR/" >/dev/null 2>&1 \
      && echo "[qlm]   已用 unzip 提取内嵌依赖" \
      || echo "[qlm]   unzip 提取失败"
  else
    echo "[qlm]   容器内无 python3/unzip，跳过（部署期 deploy.sh 已提取兜底）"
  fi
  break
done

# 3d. 确保关键模组 active（清理历史 .disabled 残留）
#     Kleiders Custom Renderer 注册网络 channel（客户端皮肤/模型同步），服务器缺失会拒绝
#     对端连接（"mismatched mod channel list"）；旧版 ModDependencyHandler 曾将其禁用为
#     .disabled，启动时主动恢复为 active，保证本次启动即生效。
for f in "$MC_MODS_DIR"/kleiders_custom_renderer*.jar.disabled; do
  [ -e "$f" ] || continue
  echo "[qlm]   恢复关键模组为 active（移除 .disabled）: $(basename "$f")"
  rm -f "$f"
done
#     ToughAsNails 预禁用：与口渴系统冲突，必须在 Forge 扫描前保持 .disabled
#     （运行时禁用太晚：模组已加载，外部 ThirstWasTaken 的 compat/toughasnails 配方
#      会因 null ItemStack 触发 NPE —— Zeta 配方扫描报错 + 玩家登录配方同步 EncoderException）。
#     ModDependencyHandler 见 .disabled 存在时会保持禁用，不会重新释放。
for f in "$MC_MODS_DIR"/*ToughAsNails*.jar; do
  [ -e "$f" ] || continue
  if [ -e "${f}.disabled" ]; then
    # 已存在 .disabled 但 active jar 也在（3a/3c 每次从 mc/libs 重新复制 active）：
    # 不一致状态——必须删除 active jar，否则 Forge 仍会加载它（登录配方 NPE 踢玩家）。
    echo "[qlm]   移除与 .disabled 并存的 ToughAsNails active jar: $(basename "$f")"
    rm -f "$f"
    continue
  fi
  echo "[qlm]   预禁用冲突模组（ToughAsNails）: $(basename "$f")"
  mv -f "$f" "${f}.disabled"
done

#     ThirstWasTaken 统一禁用：按当前策略本地/服务器都禁用口渴模组。
for f in "$MC_MODS_DIR"/*ThirstWasTaken*.jar; do
  [ -e "$f" ] || continue
  if [ -e "${f}.disabled" ]; then
    echo "[qlm]   移除与 .disabled 并存的 ThirstWasTaken active jar: $(basename "$f")"
    rm -f "$f"
    continue
  fi
  echo "[qlm]   预禁用口渴模组（ThirstWasTaken）: $(basename "$f")"
  mv -f "$f" "${f}.disabled"
done

# ---------- 4. CraftingDead 补丁 agent（移除 disableSaving 调用，防止存档崩溃） ----------
if [ -d "/mc-agent" ] && { [ -f "/mc-agent/patch_cd_jar.py" ] || [ -f "/mc-agent/patch_cd_jar.sh" ]; }; then
  echo "[qlm] 运行 CraftingDead 补丁..."
  # 定位 crafting-dead-core jar（含 com/craftingdead/core 的 GunConfigurations.class）
  CD_JAR=""
  for c in "$MC_MODS_DIR"/crafting-dead-core-*.jar; do
    [ -f "$c" ] && CD_JAR="$c" && break
  done
  if [ -n "$CD_JAR" ]; then
    # 优先 python 版（纯 stdlib，无需 javac；容器为 JRE 无 javac）
    if [ -f "/mc-agent/patch_cd_jar.py" ] && command -v python3 >/dev/null 2>&1; then
      python3 /mc-agent/patch_cd_jar.py "$CD_JAR" || echo "[qlm] 补丁失败（忽略，继续启动）"
    elif [ -f "/mc-agent/patch_cd_jar.sh" ]; then
      bash /mc-agent/patch_cd_jar.sh "$CD_JAR" || echo "[qlm] 补丁失败（忽略，继续启动）"
    else
      echo "[qlm] 无可用补丁脚本，跳过"
    fi
  else
    echo "[qlm] 未找到 crafting-dead-core jar，跳过补丁"
  fi
fi

# ---------- 5. KubeJS 补丁（修复 ScriptManager ConcurrentModificationException 启动失败） ----------
# KubeJS 6.5 的 KubeJSPlugins.LIST 是静态 ArrayList，Forge 并行加载 mod 时并发 add
# 导致 ScriptManager.load 遍历抛 CME（"KubeJS has failed to load correctly"），服务器无法启动。
# 补丁脚本把 LIST 改为 CopyOnWriteArrayList（线程安全、迭代不抛 CME），已补丁时幂等跳过。
if [ -f "/mc-agent/patch_kubejs_jar.py" ] && command -v python3 >/dev/null 2>&1; then
  KJS_JAR=""
  for k in "$MC_MODS_DIR"/kubejs-forge-*.jar; do
    [ -f "$k" ] && KJS_JAR="$k" && break
  done
  if [ -n "$KJS_JAR" ]; then
    echo "[qlm] 运行 KubeJS 补丁..."
    python3 /mc-agent/patch_kubejs_jar.py "$KJS_JAR" || echo "[qlm] KubeJS 补丁失败（忽略，继续启动）"
  else
    echo "[qlm] 未找到 kubejs jar，跳过补丁"
  fi
fi

echo "[qlm] ========== 启动 Minecraft 服务器 =========="
exec /start

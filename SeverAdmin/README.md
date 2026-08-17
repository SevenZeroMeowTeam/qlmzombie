# 🧟 七零喵僵尸末日生存 · SeverAdmin 服务器部署平台

> 域名：**mc.sh197.dpdns.org**（Minecraft 25565 / 网站 80,443）
> 版本：Minecraft Java 1.20.1 · Forge 47.4.22 · 模组 **qlmzombie-3.0.0.beta.build41**
> 同步日期：2026-08-17

服务器专用的一体化部署与管理平台，支持 **Docker** 或 **Java(systemd)** 两种部署方式，包含网站（服务器介绍、下载中心、后台管理）与 Minecraft 服务器的一键部署与监控。

## 📁 目录结构

```
SeverAdmin/
├── deploy.sh               # 一键部署脚本（docker|java|auto）
├── docker-compose.yml      # Docker 编排（MC + Web + Nginx + certbot）
├── .env.example            # 环境配置模板（复制为 .env）
├── mc/                     # Minecraft 服务器文件
│   ├── entrypoint-wrapper.sh   # 启动包装（依赖自动释放/EULA/补丁）
│   ├── server.properties       # 服务器配置（后台可改）
│   ├── libs-list.txt           # 依赖白名单（119 个模组）
│   ├── mods/ kubejs/ agent/ libs/
├── web/                    # Node.js Web 后台
│   ├── server.js           # 后台服务（认证/配置/OP/监控/静默重启/下载）
│   └── public/             # 前端页面（主页/下载中心/后台管理）
├── nginx/                  # Nginx 反向代理（域名统一入口 + SSL）
├── scripts/                # 辅助脚本（同步/监控）
├── java-deploy/            # Java(systemd) 模式部署
└── logs/
```

## 🚀 快速部署

### 方式一：Docker（推荐）

```bash
cp .env.example .env        # 修改密码
./deploy.sh docker          # 一键部署
# 首次 SSL 证书（可选）：
docker compose --profile ssl run --rm certbot
docker compose restart nginx
```

### 方式二：Java(systemd)

```bash
cp .env.example .env        # 修改密码
./deploy.sh java            # 需要 root/sudo
```

> **部署自检**：`deploy.sh` 部署后会自动检查关键模组是否在位——`kleiders_custom_renderer`（注册网络 channel，服务器缺失会导致玩家报 `mismatched mod channel list`，部署时已从纯客户端过滤中放行并清理历史 `.disabled` 残留）与 `qlmzombie` 主模组。缺失时会打印 ⚠ 警告。

## 🖥️ 后台功能

| 功能 | 说明 |
|------|------|
| 仪表盘 | Docker 容器 / Java 服务状态、CPU/内存、在线玩家 |
| 服务器配置 | 可视化编辑 `server.properties`，保存自动同步 |
| 游戏管理员 | OP / 白名单 / 封禁管理（写文件 + RCON 同步） |
| 控制台 | RCON 游戏内命令执行 |
| 下载中心 | 文件上传/下载/删除，**单文件上限 500MB** |
| 日志 | Docker logs / journalctl 实时查看 |
| 静默重启 | 先公告 + 保存世界 → 优雅重启，**面板不中断** |

## 🔄 静默重启原理

1. 后台通过 RCON 广播 `[SeverAdmin] 服务器将在 N 秒后重启`
2. 执行 `save-all` 保存世界
3. 等待 N 秒（玩家正常退出，不被强踢）
4. 后台（独立进程/容器）调用 `docker restart qlm-minecraft` 或 `systemctl restart qlm-minecraft`
5. 玩家重新加入即恢复 —— 后台面板全程在线

## 🛡️ 兼容性保障（防止服务器无法启动 / 玩家无法加入）

- **依赖自动释放**：`entrypoint-wrapper.sh` 从 `mc/libs`（= `mcmod/src/main/libs` 白名单）自动复制全部 119 个依赖模组到 `mods/`
- **EULA 强制写入**：自动 `eula=true`
- **注册表修复**：`-Dfml.ignoreMissingRegistries=true` 等 JVM 参数修复 CraftingDead Missing registry
- **CraftingDead 补丁**：`mc/agent/patch_cd_jar.sh` 移除 `disableSaving()` 调用
- **端口自动检测**：25565 被占用时自动 +10 查找空闲端口
- **后台同步**：网站修改配置 → 写入挂载卷 / 服务目录 → RCON 即时生效

## ⚙️ 环境变量（.env）

| 变量 | 说明 | 默认 |
|------|------|------|
| `DOMAIN` | 站点域名 | `mc.sh197.dpdns.org` |
| `RCON_PASSWORD` | RCON 密码（必改） | - |
| `JWT_SECRET` | 后台 JWT 密钥（必改） | - |
| `ADMIN_USER` / `ADMIN_PASS` | 后台登录账号（必改） | admin / - |
| `ADMIN_TOKEN` | 备用 API Token | - |
| `MAX_UPLOAD_MB` | 下载中心单文件上限 | 500 |
| `NGINX_MC_PORT` | MC 入口端口 | 25565 |
| `MC_EXTRA_PORTS` | 多重转发额外入口（见下方章节） | 空 |
| `MC_RELAY_PORT1..3` | 额外入口宿主发布端口 | 25566/25567/25568 |
| `MC_MAX_MEMORY` | 内存上限 | 2G |

## 🌐 多重转发（多入口降低延迟）

除主入口（`NGINX_MC_PORT=25565`）外，可配置多个**额外 MC 转发入口**，每个入口
可指向不同后端/中转节点，玩家按地区选择最近入口，配合 `tcp_nodelay`（禁用 Nagle）、
`proxy_socket_keepalive`、`keepalive` 连接池复用等调优降低延迟。

**配置方式**：编辑 `.env`，设置 `MC_EXTRA_PORTS`，然后重新运行 `./deploy.sh docker`。

```bash
# 格式: MC_EXTRA_PORTS="端口:后端地址,端口:后端地址"
# 例 1: 本机多入口分流（三个入口都到本机 MC）
MC_EXTRA_PORTS="25566:qlm-minecraft:25565,25567:qlm-minecraft:25565"
# 例 2: 指向外置中转节点（不同地区玩家连最近节点）
MC_EXTRA_PORTS="25566:203.0.113.10:25565,25567:198.51.100.20:25565"
```

- 入口端口为容器内监听端口，宿主默认同号（可用 `MC_RELAY_PORT1..3` 覆盖宿主端口）。
- `deploy.sh` 会根据 `MC_EXTRA_PORTS` 自动生成 `nginx/stream-extra.conf` 并写入
  `upstream`/`server` 块（含低延迟参数），随后 `docker compose up` 生效。
- 未配置时仅使用主入口，额外端口（25566-25568）虽有发布但无监听，连接会被拒绝。
- 需要外置中转节点时，请自行准备（如 FRP / UDP2RAW / 云主机转发），本项目提供转发端。

## 🔧 常用命令

```bash
./deploy.sh status      # 查看状态
./deploy.sh restart     # 静默重启服务器
./deploy.sh sync        # 重新同步依赖模组
./scripts/monitor.sh    # 实时监控（docker|java 双模式）
```

> 依赖源：`../src/main/libs`（mcmod 项目）→ 构建产物 `../build/libs/qlmzombie-*.jar`

## 🔄 与模组同步状态（build41）

| 项 | 状态 |
|:---|:-----|
| 模组 JAR | `qlmzombie-3.0.0.beta.build41.jar` ✅ |
| 依赖白名单 | `libs-list.txt` 119 条目 = `src/main/libs` 119 JAR ✅ |
| KubeJS 脚本 | 已同步 6 个核心脚本（空投/血月/幸运月/丰收月/合成/战利品）+ 部署特制 craft_registry_fix ✅ |
| FTB 任务 | 30 个任务（含 12 个「枪械大师」TaCZ 任务链），奖励枪械+弹药+配件 ✅ |
| 火药掉落 | 击杀敌对生物概率掉落（苦力怕封禁后的火药来源，默认 12%+抢夺加成，封顶 50%）✅ |
| 掉落物控制 | 敌对生物 60% 掉落概率过滤 + 每分钟清理陈旧掉落物 ✅ |
| 废弃建筑 | 8 类生成器周期扫描自动生成（已修复区块被永久跳过的问题）✅ |
| 性能监控 | 模组每秒采样 TPS/MSPT/人数/内存 → 网站 TPS/延迟实时曲线 ✅ |
| 特殊僵尸 | 18 种（巨人500/军阀180/木桶150...） |
| 特殊骷髅 | 9 种（狙击55/铁甲60/迅捷18...） |
| 动态难度 | 5 阶段（0-25和平 → 100+锁定困难） |
| 品质系统 | 10 级（劣质→神话）+ Apotheosis 词缀 + 神话套装 |
| 成就 | 22 项（技能点奖励）+ 初始 5 技能点 |
| 生物/维度 | 下界末地封禁 + 11 种生物封禁 |

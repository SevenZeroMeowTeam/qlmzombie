# 七零喵僵尸末日生存mod (QLM Zombie Apocalypse)

**制作团队：七零喵团队 (SevenZeroMeowTeam)**
**当前版本：`2.10.0.rewrite.beta.build.32.0`**

基于 Minecraft Forge 1.20.1 的末日生存 mod。

---

## 环境要求

- JDK 17
- Minecraft 1.20.1
- Forge 47.4.10+

## 构建方法

```bash
./gradlew build
```

输出 JAR：`build/libs/qlmzombie-2.10.0.rewrite.beta.build.32.0.jar`

---

## 核心玩法

### 动态难度系统

根据天数自动切换 5 个难度阶段，每个阶段影响僵尸进化概率和 AI 行为：

| 阶段 | 天数范围 | 难度 | 进化概率 |
|------|----------|------|----------|
| 安全日 | 1-25 | PEACEFUL | 0% |
| 简单 | 26-50 | EASY | 15% |
| 普通 | 51-100 | NORMAL | 30% |
| 困难（锁定） | 101-150 | HARD | 45% |
| 极限（锁定） | 151+ | HARD | 60% |

### 月相系统

- **血月**：安全日后每 14 天一次，刷怪量大增，禁止睡觉，触发尸潮
- **幸运之月**：7% 概率，玩家获得 Luck II
- **丰收之月**：7% 概率，作物生长翻倍

### 僵尸进化

进化僵尸名字显示红色，随阶段获得更多 buff（速度/伤害/抗性/再生）和额外生命值。

### 尸潮系统

血月期间触发 5 波尸潮，第 5 波后生成尸潮领主 Boss（三阶段战斗）。

### AI 智能优化

- NORMAL+：僵尸破门、破坏挡路方块
- EXTREME：自动搭方块爬墙追击
- 特殊僵尸：自爆/木桶/TNT/药水/召唤师/骷髅必中箭/村庄守卫者
- 动态难度增强：僵尸/骷髅随天数增加速度/护甲/伤害/生命值

### 击杀随机奖励

击杀任何生物（怪物/动物/生物，排除玩家）1% 概率获得永久属性加成（死亡不丢失）：

| 属性 | 概率 | 每次加成 | 上限 |
|------|------|----------|------|
| 最大生命值 | 50% | +1 颗心 | +1024 |
| 攻击力 | 50% | +1 伤害 | +1024 |

### 口渴模式

为末日生存增加真实感，玩家需要饮水维持生存：

| 机制 | 说明 |
|------|------|
| **口渴值** | 玩家口渴值 0~100，每 4 秒自动增加 1 点 |
| **脱水效果** | 口渴值 > 80 时获得缓慢 + 疲劳负面效果 |
| **饮用限制** | 原版水瓶不能直接饮用（会提示需要加热净化） |
| **纯净水** | 新物品，将水瓶放入熔炉加热可获得 |
| **恢复口渴** | 饮用一瓶纯净水可减少 25 点口渴值 |

### 源码替代玩法（遵循开源准则）

以下开源 mod 的玩法已由 qlmzombie 原创代码替代，不再通过 libs 释放 JAR：

| 功能 | 替代模组 | 实现方式 |
|------|----------|----------|
| 饱食度满仍可吃 | AlwaysEat | `PlayerInteractEvent.RightClickItem` |
| 骷髅攻击间隔减半 + 感知提升 | SkeletonAIFix | 反射修改 `RangedAttackGoal` |
| 熔炉/烟熏炉/高炉配方缓存 | FastFurnace | 签名哈希缓存 |
| 配方系统缓存 | FastSuite | 输入签名 → 配方映射 |
| 经验球 8 邻域合并 | Clumps | `EntityJoinLevelEvent` 合并 `ExperienceOrb.value` |
| 全实体 AI Goal 节流 | AI-Improvements | `WrappedGoal` 包装 + 每 2-3 tick 执行 |
| 全键无冲 | NonConflictKeys | 反射 `KeyMapping` 设 `NO_CONFLICT` |
| 动物额外掉肉 / 亡灵额外腐肉 | DropTheMeat | Global Loot Modifier（原 JAR 保留，含纹理） |

> 含纹理/模型/语言资源的 mod 一律保留原 JAR 释放，不做源码替代。

---

## 游戏系统

### 时间系统

| 项目 | 原版 | 本 mod |
|------|------|--------|
| 每天 tick 数 | 24,000 | **57,600** |
| 一天现实时长 | ~20 分钟 | **~48 分钟** |

### 计分板 HUD

屏幕左侧每秒刷新：天数、时间（12/24 小时制）、当前阶段、月相状态。

### 血量 UI

经验条样式血量条替代原版心形阵列，动态变色（红→橙→警告闪烁），支持金苹果吸收段。

### 初始物资

首次登录获得：全套无限耐久铁盔甲（随机附魔）、铁工具+弓、64 箭、48 附魔金苹果、64 面包、最大生命值 200 点。

### 武器品质系统

合成武器/工具/盔甲随机生成 7 档品质（破损→神话），品质越高伤害越高。神话级可破坏基岩、无视游戏规则限制。

### 连锁挖矿/砍树

镐子挖矿石、铲子挖泥土、斧头砍树 → 一键连锁破坏同类方块。支持所有 mod 工具和 mod 矿石/树木。

### AI 玩家系统

- 主世界随机生成 NPC 玩家，可驯服
- 聊天指令系统（Player2 MCP API）：自然语言理解，14 种任务类型
- AI 建造：聊天指令 `build` / `house` 建造小屋
- AI 制作：自动解析配方寻工作台合成
- AI 自由活动：未驯服时漫步 + 好奇靠近玩家

### 建筑宝箱注入

原版建筑宝箱随机注入 TaCZ 枪械、Spartan 武器、QLM 自制物资。废弃商店结构含奖励箱，运行时动态扫描 23 个 mod 命名空间注册物品。

### 成就系统

生存天数、血月幸存、僵尸猎手、阶段生存、尸潮征服者等挑战成就（隐藏式，逐步解锁）。

### 封禁系统

女巫/蜘蛛/末影人无法生成；下界/末地无法进入。

---

## 源码替代 mod 管理

`ModDependencyHandler` 自动管理 112 个依赖 mod JAR：

- **有开源仓库 + 0 资源**：源码替代，JAR 不释放（`FEATURE_REPLACED_KEYWORDS` 过滤）
- **有纹理/模型/语言**：保留原 JAR 释放（资源保护原则）
- **无开源仓库**：通过 libs 文件夹释放到 mods
- **启动时自动清理**：删除已被源码替代的旧版残留 JAR
- 自动去重、冲突检测、白名单管理

---

## 命令系统

| 命令 | 权限 | 说明 |
|------|------|------|
| `/qlm info` | 所有人 | 查看当前状态 |
| `/qlm day <N>` | OP | 设置游戏天数 |
| `/qlm phase` | 所有人 | 查看当前阶段 |
| `/qlm phases` | 所有人 | 查看所有阶段 |
| `/qlm mods` | 所有人 | 查看 mod 列表/冲突 |
| `/qlm download` | OP | 重新释放内部 mod |
| `/qlm aiplayer spawn [名字] [皮肤URL]` | OP | 生成 AI 玩家 |
| `/qlm aiplayer list` | 所有人 | 列出 AI 玩家 |
| `/qlm aiplayer tp <名字>` | OP | 传送到 AI 玩家 |
| `/qlm aiplayer tame <玩家>` | OP | 强制驯服 |
| `/qlm aiplayer kill` | OP | 移除 AI 玩家 |

---

## 配置说明

配置文件：`<MC实例>/config/qlmzombie-common.toml`

| 节点 | 说明 |
|------|------|
| `[difficulty]` | 各阶段天数范围、难度锁定 |
| `[moon]` | 血月间隔、幸运月/丰收月概率 |
| `[zombie_evolution]` | 进化概率、额外生命、装甲僵尸 |
| `[ai_optimization]` | AI 总开关、破门/搭方块/自爆等 |
| `[player2_mcp]` | Player2 API 开关/地址/密钥 |
| `[ai_player_spawn]` | AI 玩家生成概率/数量/半径 |
| `[music]` | 音乐开关/音量/间隔 |
| `[building_loot]` | 宝箱注入开关/概率 |
| `[health_ui]` | 血条开关/位置/尺寸 |
| `[chain_mining]` | 连锁挖矿开关/上限 |

---

## 音乐系统

| 场景 | 文件 |
|------|------|
| 登录 | epic_main_theme.ogg |
| 血月升起 | blood_moon_rising.ogg |
| 血月战斗 | blood_moon_battle.ogg |
| 探索 | adventure_overture.ogg |
| Boss 三阶段 | boss_phase_1/2/3.ogg |
| 尸潮氛围 | horde_ambient.ogg |

---

## 项目结构

```
src/main/java/com/qlm/zombie/
├── QLMZombieMod.java              # @Mod 主类
├── config/QLMConfig.java          # Forge 配置
├── ai/                            # AI 优化 + Player2 API + AI 物品注册表
├── dayphase/                      # 难度阶段管理
├── dependency/                    # mod 自动释放/去重/冲突检测
├── feature/                       # 源码替代玩法（AlwaysEat/Clumps/AIImprovements 等）
├── horde/                         # 尸潮波次管理
├── item/                          # 武器品质 + 神话级物品
├── loot/                          # 建筑宝箱注入
├── mining/                        # 连锁挖矿
├── moon/                          # 月相系统
├── music/                         # 音乐系统
├── player/                        # 初始物资/血量UI/AI玩家/聊天指令
├── restriction/                   # 封禁系统
├── scoreboard/                    # HUD 计分板
└── zombie/                        # 僵尸进化
```

---

## Changelog

### v2.10.0.rewrite.beta.build.32.0 — 2026-08-05

**新增：LittleSkin 随机形象皮肤系统 — AI 玩家不再是默认史蒂夫！**

从 [https://littleskin.cn/skinlib](https://littleskin.cn/skinlib) 抓取热门皮肤为每个自然生成 / 命令生成的 AI 玩家随机分配形象：Alex 细胳膊 / Steve 宽胳膊自动适配。

- **LittleSkinClient** (`com/qlm/zombie/player/LittleSkinClient.java`)：
  - 在线抓取：`/skinlib?filter=skin&sort=likes|time&page={随机 1~500}` → 正则 `/skinlib/show/{tid}` 提取 TID
  - 图片 URL：`https://littleskin.cn/preview/{tid}`
  - 内置 40+ 高人气 FALLBACK_TIDS（彩虹人/苦力怕娘/Gawr Gura/蔡徐坤/胡桃/miku/佐助/美西螈/幼猫/菜鸟…）
  - 在线队列缓存 + LRU 最近 200 条去重，避免重复皮肤
  - 传统加载 API 备用：`https://littleskin.cn/skin/{名字}.png`
- **AIPlayerSkinManager** (`com/qlm/zombie/player/AIPlayerSkinManager.java`)：
  - 生成时毫秒级先设兜底，然后异步 4s 超时拉在线皮肤覆盖
  - 失败 50% 概率切换 `/skin/{AI名字}.png`，进一步增加多样性
  - TID 末位奇偶启发式选 slim/Alex，客户端 `isSlimSkin()` 二次精确覆盖
  - 所有写入 `level.getServer().execute()` 切回主线程
- **修复 FakePlayerEntityRenderer HTTP 皮肤 Bug**：`fetchSkinFromURLAsync` 下载后既没注册纹理也没写 SKIN_CACHE，导致 URL 皮肤永远是 Steve 默认。现在主线程 `loadSkinFromBytes()` 注册纹理 + 双 key 缓存（URL + base64），增加 Accept 头、最小文件校验、preview/skin 路径豁免。
- 接入点：`AIPlayerSpawnHandler.trySpawnAIPlayer` 自然生成 / `QLMAIPlayerCommands.spawnAIPlayer` 未指定 skinUrl 时均会调用。

**修改文件：**
- 新增：`LittleSkinClient.java`, `AIPlayerSkinManager.java`
- 修改：`FakePlayerEntityRenderer.java` (fix URL skin cache), `AIPlayerSpawnHandler.java`, `QLMAIPlayerCommands.java`, `QLMZombieMod.java`, `gradle.properties`

---

### v2.10.0.rewrite.beta.build.31.0 — 2026-08-05

**新增：Node.js + mineflayer 外部 AI 机器人系统（多层架构）**

在 `node-bot/` 目录下新增独立的 Node.js AI 机器人项目，基于 `mineflayer` + `mineflayer-pathfinder` 实现，与游戏内 Java 版 AI 玩家互补，提供更强大的自主行为能力。

- **五层架构**：
  - 感知层 `Sensor.js`：方块/实体/自身状态扫描，20 tick 节流，避免每帧扫描
  - 记忆层 `Memory.js`：缓存 homePos/taskQueue/hostileMobs/attacker，`data/memory.json` 持久化
  - 决策层：三种可切换大脑
    - `FSMBrain.js` — 有限状态机（IDLE/MINE/FIGHT/FLEE/GOTO/COLLECT）
    - `BTBrain.js` — 行为树（选择/序列/条件/动作节点）
    - `GoapBrain.js` + `GoapPlanner.js` — GOAP 目标导向规划（A* 搜索动作链）
  - 行为层 `Actions.js` + `ActionLock.js`：封装 goToPos/mineBlockAt/fightMob/build，动作锁保证串行执行
  - 执行层 `Navigator.js`：mineflayer-pathfinder A* 寻路，禁踩岩浆/火/仙人掌
- **任务系统** `TaskSystem.js`：串行队列，预设任务链 `wooden_pickaxe`（挖木头→合成木板→工作台→木镐）、`cobblestone <n>`
- **背包管理** `InventoryManager.js`：自动切换最佳工具、耐久 <5% 换新、丢弃垃圾物品
- **11 个聊天指令**：`!help` / `!status` / `!mine` / `!goto` / `!follow` / `!stop` / `!sethome` / `!home` / `!brain` / `!task` / `!inventory`
- **避坑设计**：
  - 感知节流（20 tick 扫描一次，不是每帧）
  - 动作锁（挖方块/攻击有冷却，Promise 串行）
  - 寻路 Movements 自定义（爬梯/跳坑/避岩浆）
  - 仅本地/私人服务器使用（公共服务器反作弊会 ban）

**修复：** `FSMBrain.js` 的 `require('../utils/logger')` 路径错误（fsm 子目录需上溯两级），改为 `require('../../utils/logger')`。

**验证：** 15 个 JS 文件语法全部通过，14 个模块加载成功，3 个大脑类用 mock 依赖实例化成功，`QLMAIBot` 构造器工作正常。

**修改文件：**
- `node-bot/` — 新增完整 Node.js AI 机器人项目（15 个 JS 文件 + config.json + package.json + README.md）
- `node-bot/src/brain/fsm/FSMBrain.js` — 修复 require 路径
- `QLMZombieMod.java` — 版本号 build.31.0 + 游戏公告新增 4 条外部 AI 机器人介绍
- `gradle.properties` — 版本号 build.31.0

---

### v2.10.0.rewrite.beta.build.30.0 — 2026-08-04

**修复：JPMS 模块冲突崩溃（彻底解决 org.w3c.dom.html 冲突）**

- **问题根源**：`jython-standalone-2.7.3.jar` 同时存在于两个位置：
  1. `implementation` 依赖 → 类被提取到 JAR 根目录（`org/python/**`），已正确过滤 `org/w3c/dom/**`
  2. `src/libs/` 原始 JAR → 被复制到输出 JAR 的 `libs/[Python] jython-standalone-2.7.3.jar`，**这个嵌套 JAR 内部包含 `org.w3c.dom.html`**
- **冲突机制**：Forge 的 JarJar 系统在处理 qlmzombie JAR 时，检测到嵌套 JAR 中的 `org.w3c.dom.html` 包，与 JDK 内置 `jdk.xml.dom` 模块冲突
- **错误信息**：`ResolutionException: Modules qlmzombie and jdk.xml.dom export package org.w3c.dom.html to module refinedstorage`
- **修复方案**：
  - 在 `build.gradle` 的 `jar` 任务中新增排除规则，禁止 Python 引擎原始 JAR 被复制进输出 JAR 的 `libs/` 文件夹
  - 排除关键字：`*jython*`、`*graal*`、`*polyglot*`、`*[Python]*`
  - Jython/GraalPy 类仍通过 `implementation` 依赖提取，保持功能完整
- **关于 module-info.java**：尝试创建 JPMS 模块描述符以显式声明不导出冲突包，但 Forge 1.20.1 的模块系统不支持标准 JPMS `requires transitive` 声明，编译失败后移除

**修改文件：**
- `build.gradle` — 新增 Python 引擎 JAR 排除规则
- `gradle.properties` — 版本号 build.30.0

---

### v2.10.0.rewrite.beta.build.29.0 — 2026-08-04

**崩溃修复：JPMS 模块冲突 + ThirstCanteen 版本不兼容**

- **修复 `org.w3c.dom.html` 模块冲突崩溃**：
  - 原因：`jython-standalone` 和 `graalvm.polyglot` 作为 `implementation` 依赖时，其 `org.w3c.dom.html` 包被打包进 qlmzombie JAR，与 JDK 内置 `jdk.xml.dom` 模块冲突
  - 错误：`ResolutionException: Modules qlmzombie and jdk.xml.dom export package org.w3c.dom.html to module refinedstorage`
  - 修复：从 `build.gradle` 移除所有 Python 引擎的 `implementation` 依赖，Jython JAR 改为通过 `src/libs` 释放到 mods 文件夹
- **移除 ThirstCanteen 3.6**：
  - 原因：ThirstWasTaken 1.4.0 包名已从 `cn.milus.thirst` 改为 `dev.ghen.thirst`，ThirstCanteen 3.6 仍依赖旧包名，导致 `ClassNotFoundException`
  - 修复：从 `knownInternalJars` 移除 ThirstCanteen，删除 `src/libs` 和 mods 文件夹中的 JAR
- **三引擎状态更新**：

| 引擎 | 状态 | 配置方式 |
|------|------|----------|
| **Jython** | ✅ 可用 | `src/libs` 释放到 mods 文件夹 |
| **Jep** | ✅ 可用 | `pip install jep` |
| **GraalPy** | 可选 | 用户手动将 JAR 放入 mods 文件夹 |

**修改文件：**
- `build.gradle` — 移除 jython-standalone 和 graalvm.polyglot 的 implementation 依赖
- `ModDependencyHandler.java` — 移除 ThirstCanteen，新增 Jython JAR 到 knownInternalJars
- `QLMZombieMod.java` — 版本号 build.29.0 + 游戏公告更新
- `gradle.properties` — 版本号 build.29.0

**删除文件：**
- `src/libs/[口渴-水壶] ThirstCanteen-1.20.1-3.6.jar`

---

### v2.10.0.rewrite.beta.build.28.0 — 2026-08-05

**GraalPy 集成：三引擎全部开箱即用**

- **GraalPy 依赖集成**：`build.gradle` 直接添加 `org.graalvm.polyglot:polyglot:23.1.0` + `org.graalvm.polyglot:python:23.1.0`
- 三引擎状态更新：

| 引擎 | Python 版本 | 之前状态 | 现在状态 |
|------|------------|----------|----------|
| **GraalPy** | 3.x | 需手动添加 JAR | ✅ Maven 依赖自动打包 |
| **Jython** | 2.7 | ✅ 已集成 | ✅ 保持 |
| **Jep** | 3.13 (CPython) | ✅ pip install | ✅ 保持 |

- 引擎优先级：GraalPy (Python 3.x) → Jython (Python 2.7) → Jep (CPython 3.x)
- 无需任何手动配置，三个引擎开箱即用

**修改文件：**
- `build.gradle` — 新增 GraalPy Maven 依赖
- `QLMZombieMod.java` — 版本号 build.28.0 + 游戏公告
- `gradle.properties` — 版本号 build.28.0

---

### v2.10.0.rewrite.beta.build.27.0 — 2026-08-05

**qlm API 扩充：方块操作、实体生成、Forge 事件桥接**

- **方块操作 API**：
  - `qlm.getBlock(x, y, z)` — 获取方块 ID
  - `qlm.setBlock(blockId, x, y, z)` / `qlm.placeBlock(...)` — 放置方块
  - `qlm.breakBlock(x, y, z)` — 破坏方块（产生掉落物）
  - `qlm.getBlockRange(x1,y1,z1, x2,y2,z2)` — 批量获取区域方块
- **实体操作 API**：
  - `qlm.spawnEntity(typeId, x, y, z)` — 生成实体，返回 UUID
  - `qlm.spawnEntityBatch(typeId, x, y, z, count)` — 批量生成
  - `qlm.getNearbyEntities(x, y, z, radius)` — 获取附近实体列表（UUID/类型/坐标/名称）
  - `qlm.removeEntity(uuid)` — 移除实体
  - `qlm.getLevel(dimensionId)` — 获取指定维度（主世界/地狱/末地）
- **Forge 事件桥接（PythonEventBridge）**：
  - `qlm.onBlockBreak(callback)` — 监听方块破坏，回调数据：`{x, y, z, blockId, playerUuid}`
  - `qlm.onEntityDeath(callback)` — 监听实体死亡，回调数据：`{entityType, entityUuid, sourceName, x, y, z}`
  - 实际注册 Forge `BlockEvent.BreakEvent` 和 `LivingDeathEvent`，自动分发到 Python 回调
- **示例脚本更新**：破坏钻石矿广播、击杀僵尸 10% 概率增援、Python 建造避难所函数

**新增文件：**
- `PythonEventBridge.java` — Forge 事件 → Python 回调桥接器

**修改文件：**
- `PythonAPI.java` — 新增方块操作 5 个方法 + 实体操作 5 个方法 + 事件监听 2 个方法
- `PythonScriptEngine.java` — 脚本执行后绑定 EventBridge
- `welcome.py` — 更新示例（方块/实体/事件/建筑）
- `QLMZombieMod.java` — 版本号 build.27.0 + 游戏公告

---

### v2.10.0.rewrite.beta.build.26.0 — 2026-08-05

**Python 脚本引擎 + 性能优化**

- **三引擎 Python 脚本支持**：Java 负责模组加载，Python 负责游戏逻辑脚本
  - **Jython**（Python 2.7）：纯 Java 实现，已打包进 JAR，开箱即用
  - **GraalPy**（Python 3.x）：需用户添加 GraalVM JAR 到 classpath，运行时自动检测
  - **Jep**（CPython 3.x）：需系统安装 Python + jep，运行时自动检测
  - 引擎优先级：GraalPy → Jython → Jep，自动选择第一个可用的引擎
- **脚本自动加载**：将 `.py` 文件放入 `scripts/python/` 目录，游戏启动时自动执行
- **qlm API 桥接层**：Python 脚本通过 `qlm` 对象调用 Java/Forge 功能
  - `qlm.getServer()` / `qlm.getPlayer(uuid)` — 服务器/玩家访问
  - `qlm.sendMessage(uuid, msg)` / `qlm.broadcast(msg)` — 聊天消息
  - `qlm.giveItem(uuid, itemId, count)` — 给予物品
  - `qlm.onEvent(name, callback)` — 注册事件回调
  - `qlm.getGameDay()` / `qlm.getPlayerCount()` — 游戏状态查询
  - `qlm.log(msg)` / `qlm.warn(msg)` / `qlm.error(msg)` — 日志输出
- **性能优化**：
  - 口渴 debuff 检查从每 tick → 每 40 tick（2秒），减少 97.5% 开销
  - 成就检查从每 tick → 每 100 tick（5秒），减少 99% 遍历开销
  - 口渴 HUD 渲染缓存文本宽度，仅在口渴值变化时重计算
- **已有性能引擎**：Embeddium（渲染）+ FerriteCore（内存）+ ModernFix（通用）+ Starlight（光照）+ FastWorkbench（合成）

**新增文件：**
- `PythonScriptEngine.java` — 统一三引擎入口，反射检测+脚本加载
- `PythonAPI.java` — Java API 桥接层，暴露给 Python 调用
- `scripts/python/welcome.py` — 示例脚本（事件回调/新手礼包/天数检测）

**修改文件：**
- `build.gradle` — 添加 Maven Central + Jython 依赖 + JAR 打包配置
- `QLMZombieMod.java` — 版本号 build.26.0 + Python 引擎初始化 + 游戏公告
- `ThirstFeature.java` — debuff 检查节流（每 40 tick）
- `AchievementTracker.java` — 成就检查节流（每 100 tick）
- `ThirstBarOverlay.java` — HUD 渲染缓存优化

---

### v2.10.0.rewrite.beta.build.25.0 — 2026-08-05

**口渴 debuff 修复：仅口渴时才上 debuff**

- **修复加入游戏即被施加 debuff**：ThirstWasTaken mod 在玩家加入时会施加挖掘疲劳/缓慢，现改为仅在玩家真正口渴时保留这些效果
- **智能 debuff 清除**：每 tick 检测玩家口渴值
  - 口渴值 > 6（不渴）：自动清除 ThirstWasTaken 施加的挖掘疲劳/缓慢
  - 口渴值 ≤ 6（严重缺水）：保留 debuff，让玩家感受到口渴惩罚
- **安全过滤**：仅清除短时长（≤200 tick）+ 低放大器（≤1）的效果，不误删信标/远古守卫者/药水等合法来源
- **登录即清除**：玩家登录时立即清除 debuff（此时口渴值为满值 20）

**修改文件：**
- `ThirstFeature.java` — 新增 `removeThirstDebuffs()` 方法，按口渴值阈值智能清除 debuff
- `QLMZombieMod.java` — 版本号 build.25.0 + 游戏公告更新

---

### v2.10.0.rewrite.beta.build.24.0 — 2026-08-05

**口渴 HUD 优化 + 自动释放开源口渴 mod**

- **移除脱水负面效果**：移除了口渴过高时的「缓慢」+「挖掘疲劳」药水效果，惩罚仅保留口渴归零扣血
- **口渴 HUD 位置调整**：口渴条从物品栏上方移至**饱食度/血量图标行上方**（`screenHeight - 50`），不遮挡原版 UI
- **像素风水滴图标**：口渴条左侧新增 6×8 像素水滴图标（原版 Minecraft 像素风格，`gui.fill` 绘制）
  - 含高光/暗边 3D 质感
  - 口渴值低时水滴图标和条体同步变红闪烁
- **保留开源口渴 mod**：从 `FEATURE_REPLACED_KEYWORDS` 移除 `thirstmod`/`thirstcanteen` 关键词
- **自动释放开源口渴 mod JAR**：将 `[口渴] ThirstWasTaken-1.20.1-1.4.0.jar` 和 `[口渴-水壶] ThirstCanteen-1.20.1-3.6.jar` 添加到 `knownInternalJars` 列表，游戏启动时自动从 `src/libs` 释放到 `mods` 目录

**修改文件：**
- `ThirstFeature.java` — 移除脱水药水效果（缓慢/疲劳）
- `ThirstBarOverlay.java` — 口渴条移至饱食度上方 + 像素水滴图标
- `ModDependencyHandler.java` — 移除 thirst 关键词 + 添加 ThirstWasTaken/ThirstCanteen 到自动释放列表
- `QLMZombieMod.java` — 版本号 build.24.0 + 游戏公告更新

---

### v2.10.0.rewrite.beta.build.23.0 — 2026-08-05

**口渴模式重写 + 经验条 HUD + 保留并自动释放开源口渴 mod**

基于 `Thirst Was Taken`（GitHub #119）与 `ThirstCanteen`（GitHub #118）开源仓库架构重写口渴系统：

- **Exhaustion 衰减模型**：采用原版 `FoodData` 的设计模式，玩家跑动/跳跃/挖掘等活动积累 `exhaustion`（消耗值），当消耗值累积到 4.0 时自动扣除 1 点口渴值（0~20），使口渴衰减更符合游戏沉浸感
- **移除脱水负面效果**：移除了之前口渴过高时的「缓慢」+「挖掘疲劳」药水效果，惩罚机制仅保留口渴归零扣血
- **口渴归零扣血**：口渴值降至 0 时，类似原版饥饿扣血机制，每 2 秒扣 1.0 生命值，迫使玩家饮水求生
- **雨天自动补水**：玩家在雨天暴露于天空下（`canSeeSky`）时每 6 秒回 1 点口渴值
- **纯净水饮用**：饮用纯净水恢复 8 点口渴值（4 颗心），同时消耗 3.0 exhaustion 防止滥用
- **保留开源口渴 mod JAR**：从 `FEATURE_REPLACED_KEYWORDS` 移除 `thirstmod`/`thirstcanteen` 等关键词，Thirst Was Taken / ThirstCanteen 的 JAR 不再被过滤
- **自动释放开源口渴 mod**：将 `[口渴] ThirstWasTaken-1.20.1-1.4.0.jar` 和 `[口渴-水壶] ThirstCanteen-1.20.1-3.6.jar` 添加到 `knownInternalJars` 列表，游戏启动时自动从 `src/libs` 释放到 `mods` 目录，保留其纹理和机制
- **经验条样式口渴 HUD**：新增 `ThirstBarOverlay.java`（CLIENT-only），参考 `HealthBarOverlay` 实现
  - 位于饱食度/血量图标行上方（`screenHeight - 50`）
  - 蓝色口渴条（宽 182px，与原版经验条同宽）
  - 左侧绘制 6×8 像素水滴图标（原版 Minecraft 像素风格，`gui.fill` 绘制，含高光/暗边 3D 质感）
  - 口渴值低时水滴图标和条体同步变红闪烁
  - 显示 `当前/最大` 数字标签
  - 动态变色：>50% 亮蓝 → >25% 中蓝 → <25% 红色闪烁警告
  - 纯代码绘制，无需纹理资源；创造/旁观模式自动隐藏

**修改文件：**
- `ThirstFeature.java` — exhaustion 衰减模型，移除脱水药水效果，雨天补水，扣血机制
- `PurifiedWaterItem.java` — 简化实现，饮用动画 + 使用时长
- `ModDependencyHandler.java` — 移除 thirst 关键词（保留开源 JAR）+ 添加 ThirstWasTaken/ThirstCanteen 到 `knownInternalJars` 自动释放列表
- `QLMZombieMod.java` — 版本号 + 游戏公告

**新增文件：**
- `ThirstBarOverlay.java` — 经验条样式口渴 HUD 渲染（饱食度上方 + 像素水滴图标）
- `purified_water_bottle.png` — 16×16 蓝色瓶装水纹理
- `purified_water_smelting.json` — 熔炉烧制纯净水配方

**参考仓库：**
- GitHub #118 ThirstCanteen: https://github.com/mlus-asuka/ThirstCanteen/tree/1.20.1
- GitHub #119 Thirst-Mod: https://github.com/ghen-git/Thirst-Mod/tree/1.20.1

---

### v2.10.0.rewrite.beta.build.22.0 — 2026-08-05

**新增：口渴模式（Thirst Mode）**

末日生存核心机制，让玩家需要饮水维持生存：

- **口渴值系统**：玩家口渴值 0~100，每 4 秒自动增加 1 点（通过 `TickEvent.ServerTickEvent` 遍历所有在线玩家）
- **脱水负面效果**：口渴值 ≥ 80 时自动获得缓慢（Slowness）+ 疲劳（Mining Fatigue）效果
- **拦截原版水瓶**：`LivingEntityUseItemEvent.Start` 事件检测水瓶（`PotionUtils.getPotion == Potions.WATER`），取消饮用并提示"生水中有细菌，需要熔炉加热净化后才能饮用！"
- **新物品「纯净水」**：注册 `purified_water_bottle`，使用 `DRINK` 动画，1.6 秒饮用时长，单堆叠 16
- **熔炉配方**：水瓶 → 纯净水（200 tick，0.1 经验），通过 JSON recipe 实现
- **恢复口渴值**：`LivingEntityUseItemEvent.Finish` 检测纯净水饮用完成，减少 25 点口渴值
- **持久化存储**：口渴值通过 `Player.getPersistentData()` NBT 存储，死亡/重生/登录均保留
- **新文件**：`ThirstFeature.java` / `PurifiedWaterItem.java` / `purified_water_smelting.json`

---

### v2.10.0.rewrite.beta.build.21.0 — 2026-08-04

**调整：击杀奖励平衡**

- 触发概率从 15% 调整为 **1%**（降低刷屏频率，增加稀有感）
- 生命上限保持 +1024
- 攻击上限保持 +1024

---

### v2.10.0.rewrite.beta.build.20.0 — 2026-08-04

**扩展：击杀奖励范围大幅放宽**

- 触发范围从「仅敌对怪物（Monster/Enemy）」扩展为「**任何生物**（排除玩家）」
- 包括：动物（牛/羊/鸡等）、被动生物（村民/铁傀儡等）、敌对生物（僵尸/骷髅等）、中立生物（末影人/蜘蛛等）
- 生命上限从 +20（10 颗心）提升至 **+1024**
- 攻击上限从 +10 提升至 **+1024**
- 代码修改：移除 `Monster`/`Enemy` 类型检查，增加 `target instanceof Player` 排除
- 修复消息显示整数除法 bug（`(int) HEALTH_PER_KILL / 2` → `(int) (HEALTH_PER_KILL / 2)`）

---

### v2.10.0.rewrite.beta.build.19.0 — 2026-08-04

**新增：击杀怪物随机奖励**

击杀敌对怪物时有机率获得永久属性加成（死亡不丢失）：

- **触发条件**：玩家击杀 `Monster` 或 `Enemy` 实体
- **触发概率**：15% / 次
- **奖励机制**：50% 概率 +2 最大生命值（1 颗心），50% 概率 +1 攻击力
- **上限**：生命 +20（10 颗心），攻击 +10
- **属性修改**：通过 `AttributeModifier`（UUID 固定）直接修改 `MAX_HEALTH` / `ATTACK_DAMAGE` 属性
- **持久化**：`Player.getPersistentData()` NBT 存储；`PlayerEvent.Clone` 死亡复制；`PlayerRespawnEvent` + `PlayerLoggedInEvent` 重新应用修饰符
- **UI 提示**：聊天栏显示红色 ❤ / 金色 ⚔ + 累计总量

---

### v2.10.0.rewrite.beta.build.18.0 — 2026-08-04

**修复：反射字段名 + 旧 JAR 自动清理**

- **反射字段修复**：所有 feature 中的反射字段名从 **SRG 混淆名**（`f_25349_`、`f_20785_`、`f_90897_` 等）改为 **Mojang 官方映射名**（`availableGoals`、`value`、`keyConflictContext` 等），修复生产环境（reobfJAR）全部 feature 失效的严重 bug
  - 涉及文件：`SkeletonAIFixFeature.java` / `ClumpsFeature.java` / `AIImprovementsFeature.java` / `NonConflictKeysFeature.java`
- **旧 JAR 自动清理**：`ModDependencyHandler.cleanupStaleReplacedJars()` 启动时扫描 mods 目录，自动删除匹配 `FEATURE_REPLACED_KEYWORDS` 的旧版残留 JAR，避免原 mod 与 qlmzombie 代码双重加载
- 首次启动会提示"共清理 N 个旧版残留 JAR（需重启）"

---

### v2.10.0.rewrite.beta.build.17.0 — 2026-08-04

**第二批源码替代整合**

新增 3 个原创代码实现的玩法功能（均已审计 JAR 含 0 资源 0 纹理 0 语言）：

| 功能 | 对应开源 mod | 实现要点 |
|------|------------|----------|
| 经验球合并 | Clumps | `EntityJoinLevelEvent(ExperienceOrb)` 8×8×8 AABB 邻域合并 `ExperienceOrb.value`，其余 discard |
| AI Goal 节流 | AI-Improvements | `WrappedGoal` 包装 + 每 2~4 tick 执行，覆盖 Zombie/Creeper/Animal/Villager 等 |
| 全键无冲 | NonConflictKeys | CLIENT-only：反射 `KeyMapping.keyConflictContext` → `NO_CONFLICT`，`keyModifier` → `NONE` |

**资源审计结论**：
- 80 个含纹理/模型/语言的 mod → 保留原 JAR 释放（资源保护原则）
- 11 个核心前置库（Architectury/Rhino/KotlinForForge 等）→ 永不替代（其他 mod 强依赖）
- 16 个无开源仓库 mod → 继续 src/libs 释放

---

### v2.10.0.rewrite.beta.build.16.0 — 2026-08-04

**首批源码替代整合 + 项目文档化**

- **新建「测试」文件夹**：整理 117 个 mod 的整合计划（有/无开源仓库清单、玩法整合计划）
- **首批 4 个原创代码玩法**：AlwaysEat / SkeletonAIFix / FastFurnace / FastSuite
- **释放逻辑改造**：`ModDependencyHandler.FEATURE_REPLACED_KEYWORDS` 列表控制，源码替代的 mod 不再从 libs 释放到 mods
- **资源保护原则确立**：含 `textures / models / lang / blockstates / sounds / loot_tables` 任一资源的 mod 永不源码替代

---

### v2.10.0.rewrite.beta.build.15.0 及更早

- 初始版本：动态难度系统、月相系统、僵尸进化、尸潮系统、AI 优化、武器品质、连锁挖矿、AI 玩家、建筑宝箱注入、成就系统、封禁系统、音乐系统等核心玩法

---

---

## MIT License

Copyright (c) 七零喵团队 SevenZeroMeowTeam

# 七零喵僵尸末日生存mod (QLM Zombie Apocalypse)
**制作团队：七零喵团队 (SevenZeroMeowTeam)**

一个基于 Minecraft Forge 1.20.1 的末日生存 mod，按天数推进难度、定期触发血月、随机触发幸运月/丰收月、僵尸按阶段随机进化、自动发放初始物资、成就系统、尸潮系统、**AI 智能优化（僵尸破方块/搭方块/自爆/木桶、骷髅必中破甲箭、村庄守卫者、村民恐慌）**，以及**左侧实时 HUD 计分板**（天数 / 游戏内时间 / 当前阶段 / 月相）。

## 玩法规则

### 动态难度系统

**核心机制**：游戏根据天数自动监控并调整难度，不会创建游戏时就默认困难模式。第1天始终为和平模式（安全日）。

| 阶段 | 天数范围 | 难度 | 僵尸进化概率 | 描述 |
|------|---------|------|-------------|------|
| 安全日 | 1-25 | Peaceful | 0% | 安全时期，无敌对生物 |
| 简单 | 26-50 | Easy | 15% | 僵尸开始出现，低概率进化 |
| 普通 | 51-100 | Normal | 30% | 难度增加，僵尸进化概率提升 |
| 困难(锁定) | 101-150 | Hard (锁定) | 45% | 难度锁定，僵尸高概率进化 |
| 极限(锁定) | 151+ | Hard (锁定) | 60% | 极限难度，装甲僵尸/骷髅出现 |

> 注：天数范围可通过配置文件 `qlmzombie-common.toml` 调整。

### 月相系统
- **血月**：安全日期间（1-25天）不触发；安全日过后每 14 天一次（第 28、42、56… 天夜晚），怪物刷怪量大幅增加，禁止睡觉，触发尸潮
- **幸运之月**：随机 7% 概率触发，所有在线玩家获得 Luck II buff 直到天亮
- **丰收之月**：随机 7% 概率触发，作物随机刻强度翻倍

### 尸潮系统

血月期间触发5波尸潮，每波难度递增：

| 波数 | 僵尸数量 | 精英概率 | 骷髅数量 | Boss数量 |
|------|---------|---------|---------|---------|
| 第1波 | 20只 | 0% | 0 | 0 |
| 第2波 | 35只 | 15% | 0 | 0 |
| 第3波 | 50只 | 30% | 15只 | 0 |
| 第4波 | 70只 | 45% | 20只 | 0 |
| 第5波 | 100只 | 60% | 30只 | 1只（尸潮领主） |

**特殊僵尸：**
- **精英僵尸**：速度I、力量I、生命恢复I，60点血量
- **尸潮领主**：速度II、力量III、生命恢复II、防火，200点血量，15点攻击力

**奖励**（成功抵挡5波尸潮）：
- 钻石x5、绿宝石x10、金锭x32、附魔金苹果x16、下界合金锭x2、经验瓶x64
- 钻石剑（锋利V、耐久III）、弓（力量V、无限）、箭矢x128
- **面包x64**
- **TaCZ 满配 AKM**（锋利V、耐久III、抢夺III、火焰附加II、穿透V）
- **TaCZ 创造弹药箱**（无限弹药）
- **车万女仆 - 替身地藏**
- 生命恢复II（30秒）、吸收III（30秒）
- **随机mod物品**（2-4件）：僵尸核心、感染精华、生存套件、应急口粮、医疗补给、强化零件、生化样本、战术弹药

> 注：TaCZ AKM、创造弹药箱、替身地藏需要对应 mod 已安装，未安装时会提示但不会导致崩溃。

### 僵尸进化系统

进化僵尸会获得额外 buff 和生命值：
- **初期**：速度 I
- **中期**：2 种随机 buff
- **后期**：3 种随机 buff + 额外生命
- **极限**：4 种随机 buff + 大量额外生命

### AI 智能优化系统

针对僵尸、骷髅、村民的 AI 行为增强，所有参数可通过配置文件调整。

**1. 僵尸智能行为**

| 行为 | 触发阶段 | 说明 |
|------|---------|------|
| 破门 | NORMAL+ | 僵尸会主动破坏挡路的木门 |
| 破坏方块 | NORMAL+ | 僵尸会破坏挡路的普通方块(木/石/泥土等，硬度<5) |
| 自动搭方块 | EXTREME | 极限阶段僵尸会放置方块搭桥/爬墙追击玩家 |
| 追踪范围倍率 | 全阶段 | 默认 1.6x，可调整至 1.0~3.0x |

**2. 特殊僵尸（HARD+ 阶段概率生成）**

| 类型 | 触发概率 | 行为 |
|------|---------|------|
| 💥 自爆僵尸 | 默认 12% | 靠近玩家后倒计时 40 tick 爆炸，爆炸半径随阶段提升 |
| 🛢️ 木桶僵尸 | 默认 8% | 被击杀后随机生成 2~4 只小僵尸形成围攻 |

**3. 骷髅 AI 增强**

- **主动索敌**：80格半径内搜索最近玩家
- **必中破甲箭**：有概率射出必中箭 (默认 10%，随难度提升，极限阶段 20%)，命中造成 180% 伤害并附加 虚弱II(80秒)+缓慢I(60秒)

**4. 村庄守卫者**

| 特性 | 值 |
|------|---|
| 生成概率 | 1% |
| 血量 | 40 HP |
| 基础攻击 | 5伤害 + 武器附魔 |
| 索敌半径 | 24格 |
| 装备 | 铁护甲(保护II) + 附魔铁剑/石剑/铁斧 |
| mod武器概率 | 1%：下界合金剑(锋利III + 火焰附加II + 耐久III)，名称"✦ 村庄守护者之剑 ✦" |

守卫者会主动攻击附近的僵尸、骷髅、爬行者、蜘蛛、尸壳、溺尸、zombified猪灵、女巫、劫掠者，战斗期间速度+20%。

**5. 村民恐慌**

- 警戒半径 24格，侦测到怪物后移动速度倍率提升 1.6x
- 范围和倍率均可配置

### 封禁系统

**封禁怪物**：女巫、蜘蛛、洞穴蜘蛛、末影人（无法生成）

**封禁维度**：下界、末地（玩家无法通过任何方式进入）

### 初始物资发放

玩家首次登录时自动获得：
- **盔甲**：全套无限耐久铁盔甲（带随机保护附魔）
- **武器**：铁剑、铁斧、铁镐、铁铲、弓（带随机附魔，最多5种效果）
- **消耗品**：64支箭、48个附魔金苹果、64个面包
- **生命值**：200 点最大生命值

### 计分板（HUD）系统

游戏内左侧（SIDEBAR）会显示彩色计分板，**每 1 秒刷新一次**，展示以下信息：

| 条目 | 内容 |
|------|------|
| 天 数 | `第 X 天`（基于世界时间 `dayTime/24000`） |
| 游戏时间 | 12小时制 AM/PM + 24小时制 + 时间段标签（清晨/白天/黄昏/夜晚/黎明） |
| 当前阶段 | 如"困难期 [锁定]"，自动随难度切换 |
| 月 相 | 普通 / 血月 / 幸运之月 / 丰收之月 |

> 计分板由 mod 自动创建 objective `qlm_survival`，无需任何设置。
>
> 玩家**首次登录**或**服务端 tick 到 20**时自动刷新；玩家首次登录会向其推送全量状态信息。
>
> 使用 HEARTS 渲染类型 + score=0，避免在条目右侧显示数字列表；条目顺序由 entryName 字母升序控制。

### 成就系统

**隐藏机制**：所有挑战成就默认隐藏，只有完成前置挑战后才会显示新的挑战内容。

**成就列表**：
- **生存天数**：7天、14天、30天、60天、100天、150天、365天
- **血月幸存者**：存活1次、3次、10次血月
- **僵尸猎手**：击杀10只、50只、100只、500只、1000只僵尸，击杀进化僵尸
- **阶段生存**：度过不同难度阶段
- **尸潮浪潮**：在血月中成功抵挡5波尸潮攻击（非进入游戏自动获得）
- **尸潮征服者**：成功击退完整的5波尸潮（非进入游戏自动获得）

**解锁机制**：完成一个挑战后，系统会自动解锁相关的下一个挑战，并发送提示消息通知玩家。

**注意**：尸潮浪潮和尸潮征服者成就必须在血月期间成功抵挡5波尸潮后才能获得，不会在进入游戏时自动发放。

## 环境要求

- JDK 17
- Minecraft 1.20.1 + Forge 47.4.10 及以上

## 安装步骤

**两步安装** — 启动时自动从内部 libs 释放依赖mod，无需用户干预！

1. 下载 `build/libs/qlmzombie-1.0.0.jar`
2. 复制到 `<Minecraft实例>/mods/`
3. **启动游戏**：mod 会自动扫描内部 `libs/` 目录，将所有依赖mod释放到 `mods/` 目录
4. **重复mod检测**：扫描 `mods/` 目录中所有 jar，自动检测并删除重复的 mod（同一 mod 多个版本/文件名，保留一个）
5. **冲突检测**：自动检测常见冲突mod（如 JEI vs REI、WTHIT vs Jade）并禁用冲突mod
6. **查看进度**：玩家登录时自动显示释放、重复处理、冲突状态，也可用 `/qlm info` 查看当前状态
7. **重启游戏**：释放完成后会提示重启，Forge 加载所有mod，完整功能生效

> 核心功能（僵尸进化、尸潮、难度渐进、封禁系统等）不受影响，启动即正常运行。
>
> **智能检测**：下次启动时 mod 会自动检测所有依赖是否已成功安装，确认全部就绪后不再显示"需重启"提示。
>
> **手动释放**：可随时使用 `/qlm download` 命令手动重新释放所有内部mod。

**包含的依赖**（从内部 libs 自动释放）：
- Enhanced Celestials (1.20.1-5.0.3.2)
- CorgiLib (1.20.1-4.0.3.4)
- Data Anchor (1.20.1-1.0.0.20)
- Cloth Config (11.1.136)
- KubeJS (2001.6.5-build.26)
- Rhino (2001.2.3-build.10)
- Architectury (9.2.14)
- KubeJS Additions (4.3.4)
- LootJS (1.20.1-2.13.1)
- Tacz (1.20.1-1.1.8)
- TaczJS (1.4.2+mc1.20.1)

**可选mod支持**（打包在内部 libs，启动时自动释放）：
- JEI (物品管理器)
- WTHIT (鼠标悬停信息)
- Iron Chests (铁质箱子)
- Storage Drawers (存储抽屉)
- Create (创造模组)
- Mekanism (通用机械)
- Applied Energistics 2 (应用能源2)
- Thermal (热力系列)
- Immersive Engineering (沉浸工程)
- PneumaticCraft (气压工艺)
- Blood Magic (血魔法)
- Botania (植物魔法)
- Forestry Community Edition (林业社区版)
- EnderIO (末影接口)
- Refined Storage (精致存储)
- FLIB (函数库)
- Simple Storage Network (简单存储网络)
- Curios API (饰品栏)
- Tetra (模块化工具)
- Artifacts (神器)
- Quark (夸克)
- Environmental (环境模组)
- **TaCZ (现代战争枪械)** — 尸潮奖励：满配AKM + 创造弹药箱
- **Touhou Little Maid (车万女仆)** — 尸潮奖励：替身地藏

**自动重复mod检测与删除**：
- 通过文件名前缀识别重复的 mod（例如 `jei-1.20.1-forge-15.20.0.133.jar` 和 `jei-1.20.1-forge-15.20.0.133.jar` 视为重复）
- 优先保留与内部 libs 中匹配的版本，否则保留文件名最长（通常更新、更完整）的版本
- 多余副本直接删除（而非重命名为 `.disabled`），避免 Forge 误加载
- 如删除失败会降级为重命名为 `.jar.disabled`
- **必要mod白名单保护**：FTB 团队/任务/区块、Architectury、Cloth Config、Bookshelf **不会被误删**，即使出现在重复组中也会被全部保留

**自动冲突检测与禁用**：
- 物品管理器冲突：JEI ↔ REI（**默认优先保留 REI**，如偏好 JEI 请见下文调整）
- 悬停信息冲突：WTHIT ↔ Jade（优先保留 WTHIT）
- 存储系统冲突：Applied Energistics 2 ↔ Refined Storage（检测其他同类）
- 加载器不兼容：自动禁用 Fabric 版本的 mod
- 冲突的 mod 会被自动重命名为 `.jar.disabled`，如需重新启用请删除 `.disabled` 后缀
- **必要mod白名单保护**：FTB 系列、Architectury、Cloth Config、Bookshelf **不会被自动禁用**

**如何调整物品管理器偏好（JEI ↔ REI）**：

编辑 `src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java`，在 `detectAndResolveConflicts` 方法中找到物品管理器冲突的判断逻辑：

```java
// 当前设置：优先保留 REI
if (group.groupName().contains("物品管理器") && (m.toLowerCase().contains("rei-") || m.toLowerCase().contains("roughlyenough"))) {
    keepName = m; // 优先保留 REI
}

// 如需改为优先保留 JEI，改为：
if (group.groupName().contains("物品管理器") && m.toLowerCase().contains("jei-")) {
    keepName = m; // 改为优先保留 JEI
}
```

修改后重新编译：`./gradlew build`，将新的 jar 文件放入 `mods/` 目录即可。

## Boss 三阶段系统

尸潮第 5 波会出现 **尸潮领主**（Boss），具有三个动态阶段：

| 阶段 | 血量阈值 | 属性变化 | 效果 |
|------|---------|---------|------|
| **阶段 1** | 100% → 67% | 速度+2, 伤害+3, 200HP, 15伤害 | 初始基础属性 |
| **阶段 2** | 66% → 34% | 速度+3, 伤害+4, 20伤害 | 攻击强化，聊天栏提示 |
| **阶段 3 (狂暴)** | ≤ 33% | 速度+4, 伤害+5, 25伤害, 伤害抗性+1, 再生 | 名字变为"尸潮领主 [狂暴]" |

- Boss 根据实时血量自动切换阶段
- 切换阶段时会向附近 50 格内的玩家发送聊天提示
- 配合音乐系统，各阶段有独立的背景音乐

## 音乐系统说明

`assets/qlmzombie/` 下包含以下与音乐相关的资源：

```
assets/qlmzombie/
├── music/                      # 音乐文件目录（放入 OGG 文件）
│   ├── README.txt              # 放置说明
│   ├── epic_main_theme.ogg     # ← 史诗开场主题（进入游戏时播放）
│   ├── blood_moon_rising.ogg   # ← 紧张局势（血月升起时播放）
│   ├── blood_moon_battle.ogg   # ← 战歌（血月战斗时播放）
│   ├── adventure_overture.ogg  # ← 冒险序曲（日常探索时播放）
│   ├── boss_phase_1.ogg        # ← Boss 第一阶段
│   ├── boss_phase_2.ogg        # ← Boss 第二阶段
│   ├── boss_phase_3.ogg        # ← Boss 第三阶段
│   └── horde_ambient.ogg       # ← 尸潮氛围音
└── sounds.json                 # Forge 音效事件定义（已配置）

**已配置的 SoundEvent**（无需修改）：
- `qlmzombie:epic_main_theme` — 玩家登录时播放的史诗开场主题
- `qlmzombie:blood_moon_rising` — 血月升起时播放的紧张局势音乐
- `qlmzombie:blood_moon_battle` — 血月期间循环播放的战斗音乐
- `qlmzombie:adventure_overture` — 非血月时循环播放的冒险序曲
- `qlmzombie:boss_phase_1` — Boss 第一阶段音乐
- `qlmzombie:boss_phase_2` — Boss 第二阶段音乐（攻击强化）
- `qlmzombie:boss_phase_3` — Boss 第三阶段音乐（狂暴）
- `qlmzombie:horde_ambient` — 尸潮氛围音

**音乐格式要求**：
- OGG (Vorbis) 格式
- 采样率 44100 Hz，立体声或单声道
- 建议时长：Boss / 场景 2-5 分钟循环播放
- 没有音乐文件时，Boss 三阶段和场景功能仍正常运行，只是不会播放音乐

**自定义音乐**：
1. 将你自己的 OGG 音乐文件按上表命名放入 `assets/qlmzombie/music/`
2. 重新编译：`./gradlew build`
3. 新 jar 中的音乐将在对应场景自动播放
4. 也可以通过资源包替换这些音乐文件

## 构建方法

```bash
./gradlew build
```

产物：`build/libs/qlmzombie-1.0.0.jar`

## 配置

游戏首次启动后会在 `<实例>/config/qlmzombie-common.toml` 生成配置：

```toml
[difficulty]
    # true = 第 101 天起难度锁定 Hard
    enableDifficultyLock = true
    # 安全日截止天数 (1-25)
    peacefulDays = 25
    # 简单截止天数 (26-50)
    normalDays = 50
    # 普通截止天数 (51-100)
    hardDays = 100
    # 困难截止天数 (101-150)
    extremeDays = 150

[moon]
    # 血月间隔天数
    bloodMoonInterval = 7
    # 幸运月触发概率（每晚）
    luckyMoonChance = 0.20
    # 丰收月触发概率（每晚）
    harvestMoonChance = 0.15

[zombie_evolution]
    # EASY阶段僵尸进化概率
    evolveChanceEasy = 0.15
    # NORMAL阶段僵尸进化概率
    evolveChanceNormal = 0.30
    # HARD阶段僵尸进化概率
    evolveChanceHard = 0.45
    # EXTREME阶段僵尸进化概率
    evolveChanceExtreme = 0.60
    # 进化僵尸额外生命值（半心 = 1）
    evolveBonusHealth = 20
    # EXTREME阶段生成装甲僵尸/骷髅的概率
    armoredZombieChance = 0.25

[ai_optimization]
    # true = 开启AI优化模块(僵尸/骷髅/村民)
    enableAiOptimization = true
    # 僵尸是否可以破门(NORMAL阶段+)
    zombieBreakDoors = true
    # 僵尸/骷髅追踪范围倍率
    zombieFollowRangeMultiplier = 1.6
    # 僵尸破坏方块的间隔tick
    zombieBreakInterval = 40
    # 僵尸搭建方块的间隔tick
    zombiePlaceInterval = 30
    # HARD+阶段僵尸变为自爆僵尸的概率
    suicideZombieChance = 0.12
    # HARD+阶段僵尸变为木桶僵尸概率
    barrelZombieChance = 0.08
    # 骷髅射出必中破甲箭概率
    skeletonPerfectShotChance = 0.10
    # 僵尸/骷髅主动搜索半径(格)
    aggressiveTargetingRadius = 80
    # 村民对怪物的警戒半径(格)
    villagerFleeRadius = 24
    # 村民恐慌移动速度倍率
    villagerPanicBoost = 1.6
    # 村民成为村庄守卫者的概率
    villagerGuardianChance = 0.01
    # 村庄守卫者携带mod武器的概率
    villagerGuardianModWeaponChance = 0.01
```

## 项目结构

```
七零喵僵尸末日生存mod/
├── build.gradle                    # Gradle 构建配置
├── gradle.properties               # mod_id=qlmzombie, version=1.0.0
├── settings.gradle
├── libs/                           # 依赖 mod JAR（会打包进最终JAR）
├── src/main/java/com/qlm/zombie/
│   ├── QLMZombieMod.java           # @Mod 主类
│   ├── config/QLMConfig.java       # ForgeConfigSpec 配置
│   ├── ai/                         # AI 智能优化
│   │   └── AIOptimizationHandler.java # 僵尸破方块/搭方块、自爆/木桶、骷髅必中、村庄守卫者、村民恐慌
│   ├── dayphase/                   # DayPhase 枚举 + DayPhaseManager
│   │   ├── DayPhase.java           # 难度阶段定义
│   │   ├── DayPhaseManager.java    # 难度管理
│   │   └── DifficultyLockState.java # 难度锁定状态
│   ├── dependency/                 # 依赖处理
│   │   └── ModDependencyHandler.java # 自动释放依赖mod、重复检测、冲突检测
│   ├── horde/                      # 尸潮系统
│   │   ├── HordeWave.java          # 尸潮波次配置
│   │   └── HordeManager.java       # 尸潮管理逻辑
│   ├── moon/MoonHelper.java        # 月相系统
│   ├── music/                      # 音乐系统
│   │   ├── QLMSounds.java          # SoundEvent 注册
│   │   └── BossMusicManager.java   # Boss 三阶段 + 场景音乐调度
│   ├── player/                     # 玩家系统
│   │   └── PlayerInitHandler.java  # 初始物资发放
│   ├── restriction/                # 限制系统
│   │   └── MobRestrictionHandler.java # 怪物/维度封禁
│   ├── scoreboard/               # 计分板
│   │   └── ScoreboardHandler.java  # 左侧 HUD 计分板（天数/游戏时间/阶段/月相，无数字占位符）
│   └── zombie/                     # 僵尸系统
│       └── ZombieEvolutionHandler.java # 僵尸进化
├── src/main/resources/
│   ├── META-INF/mods.toml          # mod 元数据
│   ├── pack.mcmeta
│   ├── assets/qlmzombie/lang/      # en_us/zh_cn 翻译
│   ├── data/qlmzombie/advancements/ # 成就数据
│   └── data/enhancedcelestials/enhancedcelestials/lunar/event/
│       └── lucky_moon.json         # 自定义幸运月月相
└── kubejs/                         # KubeJS 脚本（已整合进mod）
```

## 特性亮点

1. **零配置启动**：单个JAR文件，启动时自动从内部 `libs/` 释放所有依赖mod，无需用户手动安装
2. **重复mod检测与删除**：扫描 `mods/` 目录中的所有 jar，通过文件名前缀识别重复 mod，仅保留一个最新/最完整版本
3. **自动冲突检测与禁用**：启动时检测常见冲突mod（JEI/REI、WTHIT/Jade等），自动禁用冲突方并重命名为 `.jar.disabled`
4. **实时状态反馈**：玩家登录时自动显示释放、重复删除、冲突检测结果，可用 `/qlm info` 随时查看
5. **智能重启检测**：重启后自动验证依赖安装状态，确认就绪后不再显示"需重启"提示
6. **安全降级**：依赖未加载时月相功能自动降级，不影响核心玩法
7. **动态难度**：根据游戏天数自动调整难度，无需手动设置
8. **丰富内容**：血月、幸运月、丰收月、僵尸进化、成就系统、尸潮系统
9. **Boss 三阶段战斗系统**：尸潮领主具有三个动态阶段，按血量自动切换，附带阶段性音乐和属性增强
10. **完整音乐系统**：包含 `assets/qlmzombie/music/` 文件夹，支持史诗开场主题、血月升起紧张局势、战歌、冒险序曲及Boss各阶段音乐（OGG格式）
11. **AI 智能优化**：僵尸破门/破方块/搭方块追击、HARD+阶段自爆/木桶僵尸、骷髅必中破甲箭、村庄守卫者（1%概率村民变身）、村民恐慌
12. **初始物资**：合理的新手保护，帮助玩家度过前期
13. **高度可配置**：几乎所有参数都可通过配置文件调整
14. **封禁系统**：封禁危险怪物和维度，增加生存挑战
15. **完整命令系统**：/qlm 系列命令涵盖状态查询、mod管理、天数设置等
16. **左侧 HUD 计分板**：游戏内实时显示天数、游戏内时间（12/24小时制）、当前阶段及月相状态，每秒刷新一次，无需任何配置

## 验证清单

- [x] `./gradlew build` 成功生成 `build/libs/qlmzombie-1.0.0.jar`
- [x] 首次启动时自动从内部 `libs/` 释放依赖 mod 到 `mods/` 目录
- [x] 首次启动时检测重复 mod 并自动删除多余副本（仅保留一个版本）
- [x] 首次启动时检测冲突 mod 并自动禁用（重命名为 `.jar.disabled`）
- [x] 首次启动时向玩家发送重启提醒（日志 + 聊天消息）
- [x] 重启后所有依赖 mod 被 Forge 正常加载
- [x] 重启后自动检测依赖安装状态，确认就绪后隐藏"需重启"提示
- [x] 依赖标记文件写入 `config/qlmzombie_deps_installed.txt`，重启后验证
- [x] 首次启动（未重启）时核心功能正常运行，月相功能安全降级
- [x] 第 1 天难度为 Peaceful（安全日）
- [x] 第 26 天难度自动切换为 Easy
- [x] 第 51 天难度自动切换为 Normal
- [x] 第 101 天难度锁定为 Hard
- [x] 第 151 天进入极限阶段（装甲僵尸/骷髅）
- [x] 安全日期间不触发血月
- [x] 第 28 天夜晚触发血月（安全日后首次）
- [x] 偶发幸运月时玩家获得 Luck II buff
- [x] 偶发丰收月时作物加速生长
- [x] 玩家首次登录获得初始物资
- [x] 进化僵尸出现并显示红字名称
- [x] 成就系统正常显示
- [x] 血月期间触发5波尸潮
- [x] 封禁怪物（女巫、蜘蛛、洞穴蜘蛛、末影人）无法生成
- [x] 封禁维度（下界、末地）无法进入
- [x] 血量低于10%自动获得生命恢复II(60秒) + 5分钟冷却
- [x] `/qlm download` 一键重新释放所有内部 mod
- [x] `/qlm info` 显示实时状态和依赖/冲突状态
- [x] `/qlm mods` 显示所有内部 mod 列表和冲突检测结果
- [x] 启动时 `initializeFromLibs()` 自动扫描内部 libs 并释放
- [x] `detectAndResolveConflicts()` 检测 JEI/REI、WTHIT/Jade 等常见冲突
- [x] 冲突 mod 自动重命名为 `.jar.disabled`，Forge 不加载冲突 mod
- [x] needsRestart 标记在有新释放/禁用时触发，玩家登录时提示重启
- [x] 释放和冲突检测在主线程同步完成，不阻塞后续初始化
- [x] **左侧 HUD 计分板**：自动创建 objective `qlm_survival`，无需任何设置
- [x] **计分板·天数**：显示 `第 X 天`，每 20 tick 刷新一次
- [x] **计分板·游戏时间**：同时显示 12 小时制(AM/PM) 和 24 小时制，并标注时间段（清晨/白天/黄昏/夜晚/黎明）
- [x] **计分板·阶段**：自动根据当前难度阶段更新（如"困难期 [锁定]"）
- [x] **计分板·月相**：自动根据当前月相（普通/血月/幸运之月/丰收之月）更新显示
- [x] **计分板·刷新**：玩家登录时立即刷新，服务端每 1 秒更新一次，以 entryName 字母升序控制条目顺序

## 命令系统

| 命令 | 权限 | 说明 |
|------|------|------|
| `/qlm info` | 所有人 | 查看当前天数、阶段、难度、月相、配置、**依赖释放状态**、**重复删除结果**、**冲突状态**、是否需重启 |
| `/qlm day` | 所有人 | 查看当前天数 |
| `/qlm day <天数>` | OP | 设置游戏天数（自动切换难度） |
| `/qlm phase` | 所有人 | 查看当前阶段详情 |
| `/qlm phases` | 所有人 | 查看所有5个阶段一览 |
| `/qlm difficulty` | 所有人 | 查看当前难度和锁定状态 |
| `/qlm mods` | 所有人 | 查看内部mod列表、**重复删除结果**、**冲突组及已禁用mod** |
| `/qlm download` | OP | 重新释放所有内部 mod（同步释放，完成后提示重启） |

> 示例：`/qlm info` 显示完整状态面板，包含天数、阶段、难度、月相、配置参数、**依赖释放状态、重复mod删除结果和冲突检测结果**。
> 释放/禁用/删除mod后需**重启游戏**才能生效。
> 已禁用的 mod 文件名带有 `.jar.disabled` 后缀，如需启用请手动删除后缀。

## 使用的 mod 及开源地址

以下列出本项目 `libs/` 目录中包含的所有 mod 及其开源仓库链接（如有）。感谢所有作者的开源贡献！

| Mod 名称（英文） | 中文名称 | 开源地址 |
| :--- | :--- | :--- |
| 3D Armor | 3D 盔甲 | https://github.com/Pabilo8/3d_armor |
| AI Improvements | AI 改进 | https://github.com/michaelkedy/AI-Improvements |
| Advanced Skills Re-forge | 高级技能重铸 | *无公开开源仓库* |
| Applied Energistics 2 | 应用能源 2 | https://github.com/AppliedEnergistics/Applied-Energistics-2 |
| Architectury | 架构前置 | https://github.com/architectury/architectury-api |
| Artifacts | 神器 | https://github.com/ochotonida/artifacts |
| Bad Packets | 数据包前置 | https://github.com/bailey-huff/badpackets |
| Balm | 药膏前置 | https://github.com/ModdingLegacy/Balm |
| Better Combat | 更好的战斗 | https://github.com/daedelus-dev/bettercombat |
| Blood Magic | 血魔法 | https://github.com/WayofTime/BloodMagic |
| Blueprint | 蓝图前置 | https://github.com/team-abnormals/blueprint |
| Bookshelf | 书架前置 | https://github.com/Darkhax-Minecraft/Bookshelf |
| Botania | 植物魔法 | https://github.com/VazkiiMods/Botania |
| BucketLib | 桶前置 | https://github.com/Chikage0o0/BucketLib |
| Cloth Config | 布料配置 | https://github.com/shedaniel/cloth-config |
| Clumps | 经验机制改革 | https://github.com/jaredlll08/clumps |
| CoFH Core | CoFH 核心 | https://github.com/CoFH/CoFHCore |
| Collective | 通用集合 | https://github.com/ricksouth/serilum-mc-mods |
| Create | 机械动力 | https://github.com/Creators-of-Create/Create |
| Creative Core | 创意核心 | https://github.com/CreativeMD/CreativeCore |
| Crash Assistant | 崩溃助手 | https://github.com/serilum-mc-mods |
| Crash Exploit Fixer | 崩溃漏洞修复 | https://github.com/embeddedt/crashexploitfixer |
| CorgiLib | 柯基前置 | https://github.com/CorgiTaco/CorgiLib |
| Curios API | 饰品栏 | https://github.com/TheIllusiveC4/Curios |
| Data Anchor | 数据锚 | https://github.com/CorgiTaco/DataAnchor |
| Ender IO | 末影接口 | https://github.com/Team-EnderIO/EnderIO |
| Embeddium | 钠 (Forge) | https://github.com/embeddedt/embeddium |
| Enchantment Descriptions | 附魔描述 | https://github.com/Darkhax-Minecraft/EnchantmentDescriptions |
| Enhanced AI | 增强 AI | https://github.com/AmereBaggett/enhancedai |
| Enhanced Celestials | 增强天体 | https://github.com/CorgiTaco/Enhanced-Celestials |
| Entity Model Features | 实体模型特性 | https://github.com/traben0/Entity_Model_Features |
| Entity Texture Features | 实体纹理特性 | https://github.com/traben0/Entity_Texture_Features |
| Environmental | 环境模组 | https://github.com/team-abnormals/environmental |
| Farmer's Delight | 农夫乐事 | https://github.com/vectorwing/FarmersDelight |
| Fast Boot | 快速启动 | https://github.com/IMS212/fastboot |
| Fast Furnace | 熔炉性能优化 | https://github.com/Shadows-of-Fire/FastFurnace |
| Fast Suite | 配方性能优化 | https://github.com/Shadows-of-Fire/FastSuite |
| Fast Workbench | 工作台性能优化 | https://github.com/Shadows-of-Fire/FastWorkbench |
| Fastload-Reforged | 快速加载重铸 | https://github.com/PingIsHero/Fastload-Reforged |
| FerriteCore | 铁氧体磁芯 | https://github.com/malte0811/FerriteCore |
| FLIB | 函数库 | https://github.com/Raptorcopter201/flib |
| Footwork | 步法 | https://github.com/AmereBaggett/footwork |
| Forestry Community Edition | 林业社区版 | https://github.com/ForestryMC/ForestryMC |
| Forge Config API Port | 配置 API 移植 | https://github.com/ACGaming/ForgeConfigAPIPort |
| Forge Config Screens | 配置界面 | https://github.com/ACGaming/ForgeConfigScreens |
| FTB Chunks | FTB 区块 | https://github.com/FTBTeam/FTB-Chunks |
| FTB Quests | FTB 任务 | https://github.com/FTBTeam/FTB-Quests |
| FTB Teams | FTB 团队 | https://github.com/FTBTeam/FTB-Teams |
| GeckoLib | 壁虎动画库 | https://github.com/bernie-g/geckolib |
| Guide Me | 引导手册 | https://github.com/MC-U-Team/Guide-Me |
| Immersive Engineering | 沉浸工程 | https://github.com/BluSunrize/ImmersiveEngineering |
| Infectious | 感染模组 | *无公开开源仓库* |
| InsaneLib | 疯狂库 | https://github.com/Insane96/InsaneLib |
| Iron Chests | 铁质箱子 | https://github.com/progwml6/ironchest |
| Item Physic | 物品物理掉落 | https://github.com/CreativeMD/ItemPhysic |
| Jade | 玉 (HUD) | https://github.com/Snownee/Jade |
| JEI | 物品管理器 | https://github.com/mezz/JustEnoughItems |
| JourneyMap | 旅行地图 | https://github.com/teamjm/journeymap |
| Kotlin for Forge | Forge Kotlin 支持 | https://github.com/thedarkcolour/KotlinForForge |
| KubeJS | KubeJS 脚本 | https://github.com/KubeJS-Mods/KubeJS |
| KubeJS Additions | KubeJS 扩展 | https://github.com/Prunoideae/KubeJS-Additions |
| LootJS | LootJS 脚本 | https://github.com/Prunoideae/LootJS |
| Maid Useful Task | 女仆实用任务 | https://github.com/TartaricAcid/TouhouLittleMaid |
| Mekanism | 通用机械 | https://github.com/mekanism/Mekanism |
| Modern Fix | 现代化修复 | https://github.com/embeddedt/modernfix |
| Moonlight | 月光前置 | https://github.com/MehVahdJukaar/Moonlight |
| mrqx's Slashblade Core | 拔刀剑核心 | *无公开开源仓库* |
| mutil | 工具库 | https://github.com/mutlticore/mutil |
| Non Conflict Keys | 全键无冲 | *无公开开源仓库* |
| Oculus | 光影核心 | https://github.com/Asek3/Oculus |
| Patchouli | 帕秋莉手册 | https://github.com/VazkiiMods/Patchouli |
| Placebo | 安慰剂前置 | https://github.com/Shadows-of-Fire/Placebo |
| Player Animation Lib | 玩家动画库 | https://github.com/TRobGit/player-animation-lib |
| Player Revive | 玩家救援 | https://github.com/CreativeMD/PlayerRevive |
| PneumaticCraft: Repressurized | 气压工艺 | https://github.com/TeamPneumatic/pnc-repressurized |
| Puzzles Lib | 谜语前置 | https://github.com/Fuzss/puzzleslib |
| Quark | 夸克 | https://github.com/VazkiiMods/Quark |
| Quark Oddities | 夸克-奇思妙想 | https://github.com/VazkiiMods/Quark |
| Radium | 镭 (优化) | https://github.com/Asek3/Radium |
| Refined Storage | 精致存储 | https://github.com/refinedmods/refinedstorage |
| Rhino | Rhino 引擎 (KubeJS) | https://github.com/KubeJS-Mods/KubeJS |
| Roughly Enough Items (REI) | REI 物品管理器 | https://github.com/shedaniel/RoughlyEnoughItems |
| Skin Layers 3D | 3D 皮肤层 | https://github.com/tr7zw/Skin-Layers-3D |
| Simple Core Lib | 简单前置 | https://github.com/AlcatrazEscapee/SimpleCoreLib |
| Simple Ores | 简单矿石 | https://github.com/AlcatrazEscapee/SimpleOres2 |
| Simple Storage Network | 简单存储网络 | https://github.com/lothrazar/SimpleStorageNetwork |
| Skeleton AI Fix | 骷髅 AI 修复 | *无公开开源仓库* |
| Slashblade: Resharpened | 拔刀剑：重锋 | https://github.com/577fkj/SlashBladeResharped |
| Sodium Dynamic Lights | 钠：动态光源 | https://github.com/ashis-tamang/SodiumDynamicLights |
| Sodium Options API | 钠：选项 API | https://github.com/FlashyReese/sodium-extra |
| Sophisticated Backpacks | 精妙背包 | https://github.com/AlphaMode/sophisticated-backpacks |
| Sophisticated Core | 精妙核心 | https://github.com/AlphaMode/sophisticated-core |
| Spartan Shields | 斯巴达之盾 | https://github.com/OblivionScape/SpartanShields |
| Spartan Simple Ores | 斯巴达简单矿石 | https://github.com/OblivionScape/SpartanSimpleOres |
| Spartan Toolkit | 斯巴达工具包 | https://github.com/OblivionScape/SpartanToolkit |
| Spartan Weaponry | 斯巴达的武器 | https://github.com/OblivionScape/SpartanWeaponry |
| Starlight | 星光 | https://github.com/PaperMC/Starlight |
| Storage Drawers | 存储抽屉 | https://github.com/jaquadro/StorageDrawers |
| Superb Warfare | 卓越前线 | *无公开开源仓库* |
| TaCZ | 现代战争枪械 | https://github.com/MCModderAnchor/TACZ |
| TaCZ JS | TaCZ KubeJS 扩展 | https://github.com/MCModderAnchor/TACZJS |
| Tetra | 模块化工具 | https://github.com/mickelus/tetra |
| Thermal Foundation | 热力系列 | https://github.com/CoFH/ThermalFoundation |
| Touhou Little Maid | 车万女仆 | https://github.com/TartaricAcid/TouhouLittleMaid |
| Touhou Maid Affection | 车万女仆附属：爱恋 | https://github.com/TartaricAcid/TouhouLittleMaid |
| Travelers Titles | 旅人标题 | https://github.com/Insane96/TravelersTitles |
| True Power | 真正的力量 | *无公开开源仓库* |
| True Power of Maid | 车万女仆：真正的力量 | *无公开开源仓库* |
| WTHIT | 鼠标悬停信息 | https://github.com/badasintended/wthit |
| YUNG's API | YUNG 前置 | https://github.com/yungnickyoung/YUNGs-Api |
| Yes Steve Model (YSM) | 是，史蒂夫模型 | https://github.com/kosmx/browser-editor |
| Zeta | Zeta 前置 | https://github.com/VazkiiMods/Zeta |
| Zombie Island | 僵尸岛 | https://github.com/steaf13/Zombie-Island |
| **qlmzombie** | **七零喵僵尸末日生存 (本项目)** | *(用户自行填入 GitHub 仓库地址)* |

> **说明**：标记为 "无公开开源仓库" 的 mod 可能是私有/闭源项目，或通过 CurseForge/Modrinth 分发但未公开源代码。

---

## 许可证

MIT License
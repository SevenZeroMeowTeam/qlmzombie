# 七零喵僵尸末日生存 Mod (QLM Zombie)

> **Minecraft Forge 1.20.1 · Kotlin + Java + KubeJS 重构版**
>
> 基于开源模组准则整合的末日生存模组 —— 让每一个夜晚都充满紧张与刺激

![Version](https://img.shields.io/badge/版本-3.0.0.beta.build27-blue)
![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge](https://img.shields.io/badge/Forge-47.4.22-orange)
![License](https://img.shields.io/badge/许可证-MIT-yellow)
![Build](https://img.shields.io/badge/构建-BUILD27%20SUCCESSFUL-brightgreen)

---

## 📦 版本信息

| 项目 | 值 |
|------|----|
| **Mod ID** | `qlmzombie` |
| **Mod 名称** | 七零喵僵尸末日生存mod |
| **版本号格式** | `主版本.次版本.修订版本.beta.build构建号` |
| **当前版本** | `3.0.0.beta.build26` |
| **发布 JAR 文件名** | `qlmzombie-3.0.0.beta.build26.jar` |
| **Minecraft 版本** | 1.20.1 |
| **Forge 版本** | 47.4.22 |
| **映射** | Official 1.20.1 |
| **Group ID** | `com.qlm.zombie` |
| **作者** | SevenZeroMeow Team |
| **许可证** | MIT |

---

## 🎯 项目简介

QLM Zombie 是一个深度整合的 Minecraft 末日生存模组，基于 **Kotlin + Java + KubeJS** 三语言架构重写开发。模组融合了 100+ 开源模组的精华功能，构建了一个完整的僵尸末日生存体验：

- 🌙 **动态昼夜阶段系统**：从和平到地狱，8 个难度阶段随天数递进
- 🧟 **僵尸进化系统**：僵尸属性随阶段动态增强，每个夜晚越来越危险
- 💧 **口渴生存系统**：除饥饿值外新增口渴值管理，脱水会导致虚弱与死亡
- 🦠 **感染机制**：被僵尸攻击有概率感染，叠加中毒、虚弱、反胃效果
- 🤖 **AI 玩家伴侣系统**：召唤可驯服的 AI 同伴，支持建造、挖矿、跟随等任务
- 🏗️ **随机建筑生成**：废弃商店、9 层高楼、海底遗迹、随机小屋自然生成
- 🔫 **Crafting Dead 装备体系**：武器、弹药、护甲、医疗用品完整复刻
- 📦 **战利品注入**：全球战利品修改器 + KubeJS 脚本双重注入机制
- 🔧 **自动依赖释放**：JAR 内嵌 100+ 开源模组自动释放到 mods 目录，采用精确白名单+自动恢复误禁用机制，零误伤

---

## 🏗️ 技术架构

### 三语言栈设计

```
qlmzombie/
├── Kotlin (主要业务逻辑)
│   ├── 注册系统 (Items / Entities / Blocks / Effects / Sounds)
│   ├── 核心玩法 (昼夜阶段 / 口渴系统 / 僵尸进化 / 感染系统)
│   ├── AI 系统 (任务引擎 / 伴侣管理 / 聊天命令)
│   ├── 建筑生成 (4 种结构生成器)
│   └── 客户端渲染 (UI 覆盖层 / 注册事件)
│
├── Java (底层工具)
│   ├── ModDependencyHandler (JAR 内嵌模组自动释放)
│   └── Mixin (FluidBucketWrapper 兼容性修复)
│
└── KubeJS (数据驱动)
    ├── qlmzombie_scripts.js (合成配方 / Tag 修改 / 登录提示)
    └── qlmzombie_loot.js (战利品表注入 / 生物掉落 / 方块掉落)
```

### 构建系统

- **构建工具**：Gradle 8.14.5 + Kotlin DSL (`build.gradle.kts`)
- **插件**：ForgeGradle 6.0+ · Kotlin JVM 1.9.22 · Maven Publish
- **编译选项**：
  - JVM Target: 17
  - `-Xjvm-default=all` (Kotlin 默认接口方法)
  - `-Xskip-metadata-version-check` (兼容不同 Kotlin 元数据版本)
  - Java: `-parameters` (保留参数名) + `-Xlint:deprecation`

---

## 📁 项目结构

```
D:\mcmod\
├── build.gradle.kts                     # Gradle 构建脚本 (Kotlin DSL)
├── settings.gradle                      # Gradle 项目设置
├── gradle.properties                    # 版本/模组元数据
├── gradlew / gradlew.bat                # Gradle 包装器
├── README.md                            # 本文件
│
└── src/main/
    ├── java/
    │   └── com/qlm/zombie/
    │       └── dependency/
    │           └── ModDependencyHandler.java  # 自动释放依赖模组 + 冲突检测
    │
    ├── kotlin/com/qlm/zombie/
    │   ├── QLMZombieMod.kt              # 模组主类 @Mod 入口
    │   ├── client/
    │   │   └── QLMClientMod.kt          # 客户端专用事件注册
    │   │
    │   ├── config/
    │   │   └── QLMConfig.kt             # 模组配置 (口渴/感染/阶段等开关)
    │   │
    │   ├── dayphase/
    │   │   ├── DayPhase.kt              # 8 个阶段枚举定义
    │   │   └── DayPhaseManager.kt       # 阶段切换 + 难度计算逻辑
    │   │
    │   ├── feature/
    │   │   ├── ThirstFeature.kt         # 口渴系统核心 (数据存储 + 衰减)
    │   │   ├── AlwaysEatFeature.kt      # 随时可进食 (无视饱食度)
    │   │   ├── DropTheMeatFeature.kt    # 生物额外掉落肉类
    │   │   ├── FastCraftingFeature.kt   # 快速合成优化
    │   │   └── NonConflictKeysFeature.kt # 无冲突按键绑定
    │   │
    │   ├── zombie/
    │   │   └── ZombieEvolutionHandler.kt # 僵尸属性动态加成 (生命/伤害/速度)
    │   │
    │   ├── player/
    │   │   ├── ThirstBarOverlay.kt      # HUD 口渴条渲染
    │   │   ├── PlayerInitHandler.kt     # 玩家登录/克隆/重生初始化
    │   │   ├── InfectionHandler.kt      # 感染系统 (层数/效果/衰减)
    │   │   └── AIPlayerChatHandler.kt   # AI 伴侣聊天命令系统
    │   │
    │   ├── ai/
    │   │   ├── Player2APIService.kt     # Player2 NPC API 封装
    │   │   ├── companion/
    │   │   │   └── CompanionManager.kt  # 伴侣单例管理 (生成/驯服/传送)
    │   │   └── task/
    │   │       ├── Task.kt              # 任务抽象基类 (状态机)
    │   │       ├── TaskRunner.kt        # 任务调度执行器 (优先级队列)
    │   │       ├── TaskCatalogue.kt     # 任务注册目录 (命令映射)
    │   │       ├── BuildTask.kt         # 建造任务
    │   │       ├── MineTask.kt          # 挖矿任务
    │   │       └── FollowTask.kt        # 跟随任务
    │   │
    │   ├── item/
    │   │   ├── QLMItems.kt              # 核心物品注册表 (DeferredRegister)
    │   │   ├── QLMTabs.kt               # 创造模式物品栏标签
    │   │   ├── AntidoteItem.kt          # 解毒剂 (治疗感染)
    │   │   ├── PurifiedWaterItem.kt     # 净化水瓶 (恢复口渴值)
    │   │   ├── PlankAxeItem.kt          # 木板斧 (快速砍树工具)
    │   │   └── PlankCollectorItem.kt    # 木板收集器
    │   │
    │   ├── entity/
    │   │   ├── QLMEntities.kt           # 实体类型注册表
    │   │   ├── FakePlayerEntity.kt      # AI 伴侣实体 (FakePlayer)
    │   │   └── GiantZombieEntity.kt     # 巨型僵尸 Boss 实体
    │   │
    │   ├── block/
    │   │   └── QLMBlocks.kt             # 方块注册表
    │   │
    │   ├── effect/
    │   │   └── QLMEffects.kt            # 药水效果注册表
    │   │
    │   ├── music/
    │   │   └── QLMSounds.kt             # 音效事件注册表
    │   │
    │   ├── loot/
    │   │   └── QLMGlobalLootModifiers.kt # Forge 全球战利品修改器
    │   │
    │   ├── structure/
    │   │   ├── RandomBuildingGenerator.kt      # 5x5 随机小屋 (5% 概率)
    │   │   ├── AbandonedShopGenerator.kt       # 废弃商店 (4% 概率, 医疗/弹药/食物)
    │   │   ├── HighriseBuildingGenerator.kt    # 9 层高楼 (2% 概率, 2-6 层宝箱)
    │   │   └── OceanRuinGenerator.kt           # 海底遗迹 (8% 概率, 海洋生物群系)
    │   │
    │   └── craftingdead/                 # Crafting Dead 装备系统复刻
    │       ├── item/
    │       │   ├── CDItems.kt           # CD 物品 (武器/弹药/护甲/医疗)
    │       │   ├── CDArmorMaterial.kt   # CD 护甲材料 (钛合金/陶瓷/凯夫拉)
    │       │   └── gun/
    │       │       └── AbstractGunItem.kt # 枪械抽象基类
    │       ├── block/
    │       │   └── CDBlocks.kt          # CD 方块 (工作台/弹药箱)
    │       ├── entity/
    │       │   └── CDEntities.kt        # CD 实体 (投掷物/AI)
    │       ├── effect/
    │       │   └── CDEffects.kt         # CD 药水效果 (流血/骨折)
    │       └── tab/
    │           └── CDCreativeTabs.kt    # CD 专属创造标签页
    │
    ├── kubejs/                          # KubeJS 数据驱动脚本
    │   ├── qlmzombie_scripts.js         # 配方/Tag/登录消息
    │   └── qlmzombie_loot.js            # 战利品表/生物掉落/方块掉落
    │
    └── libs/                            # 内嵌 100+ 开源模组 JAR
        ├── kotlinforforge-4.12.0-all.jar
        ├── kubejs-forge-2001.6.5-build.26.jar
        ├── cloth-config-11.1.136-forge.jar
        ├── PuzzlesLib-v8.1.33-1.20.1-Forge.jar
        ├── create-1.20.1-6.0.8.jar
        ├── Botania-1.20.1-454-FORGE.jar
        ├── Mekanism-1.20.1-10.4.16.80.jar
        ├── EnderIO-1.20.1-6.2.18-beta-all.jar
        ├── ImmersiveEngineering-1.20.1-10.2.0-183.jar
        ├── refinedstorage-1.12.4.jar
        ├── embeddium-0.3.31+mc1.20.1.jar
        ├── modernfix-forge-5.27.58+mc1.20.1.jar
        ├── [卓越前线] superbwarfare-0.8.9-final.jar
        ├── [拔刀剑：重锋] SlashBladeResharped-1.20.1-1.9.65.jar
        ├── [斯巴达的武器] SpartanWeaponry-3.2.1-all.jar
        ├── [农夫乐事] FarmersDelight-1.20.1-1.3.2.jar
        ├── [意志坚定] ToughAsNails-forge-9.2.0.171.jar
        ├── [口渴] ThirstWasTaken-1.20.1-1.4.0.jar  (⚠️ 默认自动禁用：与内置口渴系统冲突)
        ├── Better Combat / Epic Fight / Create / Tetra / Forestry ...
        └── ... (共 100+ JAR，详见下方开源模组清单)
```

---

## 🎮 核心功能详解

### 1. 动态昼夜阶段系统 (Day Phase)

游戏根据世界天数自动切换 8 个难度阶段：

| 阶段 | 名称 | 天数范围 | 难度乘数 | 描述 |
|------|------|---------|---------|------|
| 0 | PEACE (和平) | Day 1-3 | 1.0x | 新手保护期，僵尸无加成 |
| 1 | EASY (简单) | Day 4-7 | 1.2x | 轻微属性增强 |
| 2 | NORMAL (普通) | Day 8-14 | 1.5x | 僵尸开始变强 |
| 3 | HARD (困难) | Day 15-21 | 2.0x | 生命/伤害翻倍，速度加成 |
| 4 | NIGHTMARE (噩梦) | Day 22-30 | 2.5x | 白天也会生成僵尸 |
| 5 | HELL (地狱) | Day 31-45 | 3.0x | 地狱级难度 |
| 6 | APOCALYPSE (启示录) | Day 46-60 | 4.0x | 启示录模式 |
| 7 | DOOMSDAY (末日) | Day 61+ | 5.0x | 终极挑战，生存就是胜利 |

### 2. 僵尸进化系统 (Zombie Evolution)

与阶段系统联动，每次僵尸生成时自动应用属性修饰符：

- **生命值**：基础值 × (阶段乘数 - 1) = 额外永久生命加成
- **攻击伤害**：基础值 × (阶段乘数 - 1) = 额外永久伤害加成
- **移动速度**：仅当乘数 ≥ 2.0x 时生效 (`0.02 × (乘数 - 1)`)
- **额外护甲**：所有僵尸注册时默认附带 2.0 护甲值

### 3. 口渴生存系统 (Thirst)

```
MAX_THIRST = 20 (与饥饿值一致)
```

- 玩家登录时口渴值重置为 20
- 死亡重生时设置为 14 (70%)
- 每 30 秒游戏刻自动衰减 1 点口渴值
- 口渴值 ≤ 0 时：每 5 秒造成 1 点伤害 + 持续虚弱 II 效果
- **恢复方式**：
  - 净化水瓶 (`purified_water_bottle`)：恢复 10 点
  - 水瓶 + 煤炭合成
  - KubeJS 钓鱼宝藏也可获取

### 4. 感染系统 (Infection)

| 属性 | 值 |
|------|----|
| 基础感染概率 | 15% |
| 每层叠加概率 | +3% |
| 最大层数 | 10 层 |
| 感染持续时间 | 3 分钟 |
| 自然衰减间隔 | 5 分钟 (未被攻击后) |

**效果叠加**：
- 1-4 层：中毒 (amplifier = 层数-1)
- 5-9 层：+ 虚弱效果
- 10 层：+ 反胃 (混乱) 效果

**治疗方式**：解毒剂物品 (`antidote`) 立即清除全部感染。

### 5. AI 玩家伴侣系统 (AI Companion)

#### 聊天命令系统

在游戏聊天框输入 `ai <命令>` 控制伴侣：

| 命令 | 别名 | 用法 | 说明 |
|------|------|------|------|
| `spawn` | 生成/召唤 | `ai spawn [名字] [数量]` | 生成 AI 伴侣 |
| `tame` | 驯服 | `ai tame <名字> [增加度]` | 增加伴侣亲和度 (默认+10) |
| `kill` | 杀死/移除 | `ai kill [名字]` | 移除伴侣 (留空移除全部) |
| `tp` | 传送 | `ai tp <名字>` | 将伴侣传送到身边 |
| `status` | 状态 | `ai status [名字]` | 查看伴侣/任务状态 |
| `list` | 列表 | `ai list` | 列出所有伴侣 |
| `stop` | 停下 | `ai stop [名字]` | 停止伴侣当前任务队列 |
| `help` | 帮助 | `ai help` | 显示帮助信息 |

#### 内置任务类型

| 任务命令 | 功能 | 说明 |
|---------|------|------|
| `follow` | 跟随 | 伴侣跟随玩家移动 |
| `mine` | 挖矿 | 在指定位置挖掘方块 |
| `build` | 建造 | 在指定位置放置方块 |

#### 系统特性

- **伴侣驯服**：亲和度 0-100%，达到阈值自动标记「已驯服」
- **任务调度**：基于优先级队列的任务执行器，高优先级任务先执行
- **独立 AI**：每个伴侣独立管理实体、状态、任务队列
- **跨维度兼容**：支持克隆 (死亡/末地返回) 时保留口渴等数据

### 6. 四种随机建筑生成器

| 建筑 | 生成概率 | 最小间隔 | 尺寸 | 特色 |
|------|---------|---------|------|------|
| 随机小屋 | 5%/区块 | 5 区块 | 5×5×3 | 生存初始物资，1 个宝箱 |
| 废弃商店 | 4%/区块 | 6 区块 | 9×7×4 | 4 类战利品 (医疗/弹药/食物/杂物)，5 个宝箱 |
| 9 层高楼 | 2%/区块 | 8 区块 | 13×9×36 | 每层 2-5 个宝箱，仅陆地生成，含楼梯系统 |
| 海底遗迹 | 8%/区块 | 7 区块 | 10×10 平面 | 珊瑚/海草装饰，海洋生物群系专用 |

所有生成器均具备：
- `ConcurrentHashMap` 线程安全区块记忆 (防止重复生成)
- 最小间距检查 (防止建筑密集)
- 高度图地面检测 (`Heightmap.Types.WORLD_SURFACE`)
- try-catch 异常保护 (单个建筑失败不崩溃游戏)

### 7. Crafting Dead 装备系统

完整复刻 Crafting Dead 末日装备体系：

#### 物品分类
- **医疗用品**：绷带、急救包、肾上腺素、止痛药、止血带、生理盐水、夹板、手术剪刀
- **弹药**：步枪弹、手枪弹、霰弹、狙击弹、(QLM追加) 战术弹药
- **护甲**：
  - 防弹头盔 (Ballistic Helmet)
  - 插板胸甲 (Plate Carrier)
  - 战术背心 (Tactical Vest)
  - 材料：钛合金 / 陶瓷复合 / 凯夫拉纤维
- **武器系统**：基于 `AbstractGunItem` 抽象基类的模块化枪械

### 8. 全局战利品注入系统

双轨并行注入机制，确保 QLM 物品出现在世界各处：

#### 轨道 1：Forge IGlobalLootModifier (编译期)
`QLMChestLootModifier` — 基于 `IGlobalLootModifier.apply()` API，对所有容器战利品表按概率注入：

| 物品 | 注入概率 |
|------|---------|
| 僵尸核心 | 10% |
| 感染精华 | 15% |
| 医疗用品 | 10% |
| 强化部件 | 6% |
| 生物危害样本 | 2% |
| 解毒剂 | 5% |
| ... | ... |

#### 轨道 2：KubeJS loot_tables 事件 (运行期)
`qlmzombie_loot.js` 精确修改 15+ 个具体战利品表：
- 地牢、废弃矿井、下界要塞、堡垒遗迹、要塞
- 3 种村庄房屋、丛林神庙 (2 处)、末地城、海底遗迹、雪屋
- 掠夺者前哨、远古城市、钓鱼宝藏/垃圾、猪灵交易

生物掉落覆盖：僵尸类(4种)、牛、猪、羊、鸡、兔、狐狸、爬行者、骷髅(2种)、蜘蛛(2种)、末影人、凋灵骷髅。

---

## 🧩 自动依赖释放系统

### 工作原理（build25 重写版）

`ModDependencyHandler.java` 是模组启动时运行的核心基础设施，采用 **精确白名单 + 保守禁用 + 自动恢复** 三层设计：

1. **白名单源**：mod JAR 内 `libs/` 目录下所有 JAR（由 `build.gradle.kts` 从 `src/main/libs/` 打包而来）。构建时 `generateLibsManifest` 任务会生成权威清单 `libs/manifest.txt`，每个 JAR 文件名一行。
2. **阶段 1：释放**：白名单 JAR 逐个写入 `mods/<文件名>.jar`；已存在且大小一致则跳过，不一致则覆盖。
3. **阶段 2：自动恢复误禁用**：扫描 mods 目录中所有 `.disabled` 文件 → 若文件名在白名单中且不在 `DEFAULT_DISABLED_PREFIXES` 列表中 → 自动恢复为 `.jar`（取消禁用），保证项目依赖不被外部脚本误删。
4. **阶段 3：保守禁用**：**仅**对精确前缀匹配的"已知问题模组"（`toughasnails`、`thirstwastaken` 等，与内置口渴系统冲突）自动禁用，不再模糊扫描整个 mods 目录。
5. **阶段 4：去重**：检测重复 JAR，白名单中的 JAR 优先保留，仅删除非白名单的重复版本。
6. **追踪保护**：`qlmzombie_disabled_tracker.txt` 记录用户手动启用的模组，下次启动不会再自动禁用。

### 核心改动 vs 旧版（build24 及之前）

| 维度 | 旧版（build24-） | 新版（build25+） |
|------|------------------|------------------|
| 冲突匹配 | 35+ 模糊关键字 `contains` 扫描整个 mods 目录 | 仅 `DEFAULT_DISABLED_PREFIXES`（4项）精确前缀匹配 |
| 保留策略 | `KEEP_ALWAYS_KEYWORDS` 模糊关键字（无法匹配连字符如 `crafting-dead`） | 嵌入 JAR 文件名 = 精确白名单，100% 保留 |
| 误禁用恢复 | 无 | `restoreMistakenlyDisabled()` 自动恢复白名单中的 `.disabled` |
| 重复删除 | 无白名单保护，排序后随机保留 | 白名单优先保留，非白名单重复项才删 |
| 清单验证 | 无 | `libs/manifest.txt` 构建时生成，可校验 JAR 文件名权威清单 |

**已知问题模组**（启动时自动禁用，除非用户手动启用过）：

| 前缀 | 模组 | 原因 |
|------|------|------|
| `toughasnails` | Tough As Nails (意志坚定) | 与内置口渴系统冲突，会导致双重扣水 + 创造模式翻页崩溃 |
| `thirstwastaken` | Thirst Was Taken | 同上，口渴功能完全由 `ThirstFeature` 接管 |
| `thirstmod` / `thirstcanteen` | 其它口渴模组 | 避免重复口渴系统 |

### 释放输出 JAR 统计

成功构建后，`qlmzombie-3.0.0.beta.build27.jar` 内部嵌入 `src/main/libs/` 全部 100+ JAR，并附带 `libs/manifest.txt` 清单。

---

## 📜 开源模组清单 (已验证 GitHub 仓库)

以下模组均存在公开开源仓库，已按 QLM 开源整合准则纳入：

| # | 模组 | GitHub 仓库 | 许可证 |
|---|------|------------|--------|
| 1 | Kotlin for Forge | thedarkcolour/KotlinForForge | LGPL |
| 2 | KubeJS | KubeJS-Mods/KubeJS | LGPL |
| 3 | Rhino (JS 引擎) | KubeJS-Mods/Rhino | LGPL |
| 4 | LootJS | AlmostReliable/lootjs | MIT |
| 5 | Cloth Config | shedaniel/ClothConfig | LGPL |
| 6 | Puzzles Lib | Fuzss/puzzleslib | MIT |
| 7 | Architectury | architectury/architectury | LGPL |
| 8 | Curios API | TheIllusiveC4/Curios | LGPL |
| 9 | Create | Creators-of-Create/Create | MIT |
| 10 | Botania | VazkiiMods/Botania | Botania License |
| 11 | Quark | VazkiiMods/Quark | CC BY-NC-SA 3.0 |
| 12 | Patchouli | VazkiiMods/Patchouli | MIT |
| 13 | Zeta | VazkiiMods/Zeta | - |
| 14 | Mekanism | mekanism/Mekanism | MIT |
| 15 | Ender IO | Team-EnderIO/EnderIO | EULA (开源) |
| 16 | Immersive Engineering | BluSunrize/ImmersiveEngineering | Apache-2.0 |
| 17 | Refined Storage | refinedmods/refinedstorage | MIT |
| 18 | Applied Energistics 2 | AppliedEnergistics/Applied-Energistics-2 | LGPL |
| 19 | Thermal Foundation | CoFH/ThermalFoundation | CoFH License |
| 20 | CoFH Core | CoFH/CoFHCore | CoFH License |
| 21 | Blood Magic | WayofTime/BloodMagic | MIT |
| 22 | Forestry MC | thedarkcolour/ForestryMC | - |
| 23 | PneumaticCraft | TeamPneumatic/pnc-repressurized | - |
| 24 | Better Combat | ZsoltMolnarrr/BetterCombat | GPL-3.0 |
| 25 | tetra | 17cupsofcoffee/tetra | MIT |
| 26 | Farmer's Delight | vectorwing/FarmersDelight | MIT |
| 27 | Tough As Nails | Glitchfiend/ToughAsNails | MIT |
| 28 | Infectious (Contagion) | MC-Mods-Pete/Contagion | MIT |
| 29 | Spartan Weaponry | ObliviousSpartan/SpartanWeaponry | MIT |
| 30 | Spartan Shields | ObliviousSpartan/SpartanShields | MIT |
| 31 | Spartan Toolkit | KreloX/SpartanToolkit | - |
| 32 | SlashBlade Resharped | 0999312/SlashBlade_Resharped | GPL-3.0 |
| 33 | mrqx's Slashblade Core | mrqx0195/mrqx-s-Slashblade-Core | - |
| 34 | Superb Warfare | Mercurows/SuperbWarfare | MIT |
| 35 | Timeless and Classics Zero | MCModderAnchor/TACZ | GPL-3.0 |
| 36 | TaCZ JS | gizmo-ds/taczjs-mod | - |
| 37 | Touhou Little Maid | TartaricAcid/TouhouLittleMaid | - |
| 38 | Touhou Maid: Affection | yabo083/maid-affection | - |
| 39 | Maid Useful Task | zxy19/maid_useful_task | - |
| 40 | FTB Quests | FTBTeam/FTB-Quests | ARR (开源) |
| 41 | FTB Teams | FTBTeam/FTB-Teams | ARR (开源) |
| 42 | FTB Chunks | FTBTeam/FTB-Chunks | ARR (开源) |
| 43 | FTB Library | FTBTeam/FTB-Library | ARR (开源) |
| 44 | JourneyMap | TeamJM/journeymap | - |
| 45 | Roughly Enough Items | shedaniel/RoughlyEnoughItems | MIT |
| 46 | WTHIT | badasintended/wthit | WTFPL |
| 47 | Bad Packets | badasintended/badpackets | WTFPL |
| 48 | Advanced Skills | iMoonDay/AdvancedSkills | GPL-3.0 |
| 49 | Bookshelf | Darkhax-Minecraft/Bookshelf | LGPL |
| 50 | Enchantment Descriptions | Darkhax-Minecraft/Enchantment-Descriptions | LGPL |
| 51 | Corgi Lib | CorgiTaco/CorgiLib | - |
| 52 | Enhanced Celestials | CorgiTaco/Enhanced-Celestials | - |
| 53 | Data Anchor | CorgiTaco/Data-Anchor | - |
| 54 | YUNG's API | YUNG-GANG/YUNGs-API | LGPL |
| 55 | Traveler's Titles | YUNG-GANG/Travelers-Titles | LGPL |
| 56 | Creative Core | CreativeMD/CreativeCore | LGPL |
| 57 | ItemPhysic | CreativeMD/ItemPhysic | LGPL |
| 58 | PlayerRevive | CreativeMD/PlayerRevive | LGPL |
| 59 | ItemPhysic Guns | lavafrai/itemphysicguns | - |
| 60 | Storage Drawers | jaquadro/StorageDrawers | MIT |
| 61 | Simple Storage Network | Lothrazar/Storage-Network | MIT |
| 62 | GlitchCore | Glitchfiend/GlitchCore | All Rights Reserved |
| 63 | Environmental | team-abnormals/environmental | All Rights Reserved |
| 64 | Blueprint | team-abnormals/blueprint | All Rights Reserved |
| 65 | Enhanced AI | Insane96/EnhancedAI | MIT |
| 66 | InsaneLib | Insane96/InsaneLib | MIT |
| 67 | Starlight (Engine) | PaperMC/Starlight | GPL-3.0 |
| 68 | FerriteCore | malte0811/FerriteCore | MIT |
| 69 | Embeddium | FiniteReality/embeddium | LGPL |
| 70 | ModernFix | embeddedt/ModernFix | LGPL |
| 71 | Sodium Dynamic Lights | txnimc/SodiumDynamicLights | LGPL |
| 72 | Sodium Options API | txnimc/SodiumOptionsAPI | LGPL |
| 73 | Entity Model Features | Traben-0/Entity_Model_Features | LGPL |
| 74 | Entity Texture Features | Traben-0/Entity_Texture_Features | LGPL |
| 75 | Player Animator | KosmX/minecraftPlayerAnimator | MIT |
| 76 | GeckoLib 4 | bernie-g/geckolib | MIT |
| 77 | 3D Skin Layers | tr7zw/3d-skin-layers | MIT |
| 78 | IMBlocker (输入法修复) | reserveword/IMBlocker | - |
| 79 | Moonlight Lib | MehVahdJukaar/Moonlight | MIT |
| 80 | Balm | TwelveIterations/Balm | LGPL |
| 81 | Collective | Serilum/Collective | MIT |
| 82 | Artifacts | ochotonida/artifacts | MIT |
| 83 | Sophisticated Core | P3pp3rF1y/SophisticatedCore | MIT |
| 84 | Sophisticated Backpacks | P3pp3rF1y/SophisticatedBackpacks | MIT |
| 85 | Iron Chests | ThatGravyBoat/Ironchests | - |
| 86 | mutil | mickelus/mutil | MIT |
| 87 | footwork | Jackiecrazy/footwork | - |
| 88 | True POWER | mrqx0195/true-power | GPL-3.0 |
| 89 | Placebo | Shadows-of-Fire/Placebo | MIT |
| 90 | FastBoot | GUN2RAS/FastBoot | - |
| 91 | Player2NPC | shakey2/Player2NPC | - |
| 92 | PlayerEngine | shakey2/PlayerEngine | - |
| 93 | Player2 - AI Players | SevenZeroMeowTeam/player2-code | - |
| 94 | Zombie Survival Kit | Scarasol/Zombie-Survival-Kit | - |
| 95 | Uncrafting Table | Pitan76/uncraftingtable | MIT |
| 96 | KubeJS Additions | Hunter19823/kubejsadditions | - |
| 97 | Drop the Meat | Moralle/DropTheMeat | MIT |
| 98 | Fast Workbench | Shadows-of-Fire/FastWorkbench | MIT |
| 99 | Thirst was Taken | ghen-git/Thirst-Mod | MIT |
| 100 | SevenZeroMeow/qlmzombie | SevenZeroMeowTeam/qlmzombie | MIT |

> ⚖️ **开源合规说明**：所有内嵌模组均保留其原始 JAR、纹理、语言文件、许可证信息。
> QLM Zombie 仅通过 `ModDependencyHandler` 做运行时动态释放，不修改任何第三方 JAR 内部字节码。
> 冲突处理采用 `.disabled` 文件后缀方式（Forge 会自动忽略 `.disabled` 结尾的文件），而非修改或删除第三方内容。

---

## 🚀 构建指南

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17 (Temurin / Zulu / Oracle 均可) |
| Gradle | 8.14.5 (自动下载通过 wrapper) |
| 内存 | ≥ 3GB (`-Xmx3G` 默认配置) |
| 磁盘 | ≥ 10GB (Forge 依赖 + 本地 libs) |

### ⚠ 依赖文件下载（重要）

由于 `src/main/libs/` 目录下的 110 个依赖 jar 文件总计约 **407MB**，超过 GitHub 文件大小限制，**未纳入版本控制**。Clone 仓库后需要手动下载依赖才能编译。

#### 第一步：查看依赖列表

完整的依赖文件名列表在 [scripts/libs-list.txt](file:///D:/mcmod/scripts/libs-list.txt) 中，共 110 个 jar 文件。

#### 第二步：下载依赖

从以下网站搜索并下载对应版本的 jar 文件：

| 下载源 | 网址 | 说明 |
|:------:|:-----|:-----|
| CurseForge | https://www.curseforge.com/minecraft/mc-mods | 最全的 MC 模组仓库 |
| Modrinth | https://modrinth.com/mods | 新兴模组仓库，下载速度快 |
| GitHub | 各模组的 GitHub Releases | 部分模组的官方发布页 |

将下载的所有 jar 文件放入 `src/main/libs/` 目录。

#### 第三步：验证依赖完整性

```powershell
# Windows (PowerShell)
.\scripts\check-libs.ps1

# Linux / macOS / Git Bash
./scripts/check-libs.sh
```

脚本会检查 `src/main/libs/` 目录中是否包含所有 110 个必需依赖，并列出缺失的文件。

```
✅ 输出示例（依赖完整）:
========================================
  依赖检查 - 七零喵僵尸末日生存 Mod
========================================
需要依赖文件总数: 110
[OK] 所有 110 个依赖文件均已存在 (110/110)
可以正常编译: .\gradlew.bat build

❌ 输出示例（缺少依赖）:
[MISSING] 缺少 3 个依赖文件 (107/110)
缺少的文件:
  - create-1.20.1-6.0.8.jar
  - Mekanism-1.20.1-10.4.16.80.jar
  - Botania-1.20.1-454-FORGE.jar
```

### 构建命令

```powershell
# Windows (PowerShell / CMD)
cd D:\mcmod

# 1. 编译 Kotlin + Java 源码 (用于开发验证)
.\gradlew.bat compileKotlin compileJava

# 2. 完整构建模组 JAR (输出到 build/libs/)
.\gradlew.bat build

# 3. 跳过测试的快速构建
.\gradlew.bat build -x test

# 4. 清理并重新构建
.\gradlew.bat clean build

# 5. 启动本地 Minecraft 开发客户端
.\gradlew.bat runClient

# 6. 启动本地 Minecraft 开发服务端
.\gradlew.bat runServer

# 7. 发布到本地 Maven (配合 maven-publish 插件)
.\gradlew.bat publishToMavenLocal
```

### 输出产物

成功执行 `build` 后：

```
D:\mcmod\build\libs\
├── qlmzombie-3.0.0.beta.build26.jar          # 主发行版 (含 classes + 资源 + 内嵌 libs/ + libs/manifest.txt)
└── qlmzombie-3.0.0.beta.build26-sources.jar  # 源码包 (可选，用于调试)
```

---

## 📥 安装方式

### 方法 A：玩家正常使用 (推荐)

1. 下载并安装 **Minecraft Forge 47.4.22** (MC 1.20.1)
2. 将 `qlmzombie-3.0.0.beta.build27.jar` 放入 `.minecraft/mods/` 目录
3. **启动游戏一次，然后关闭**
   - QLM Zombie 会在第一次启动时自动释放 100+ 内部模组到 `mods/` 目录
   - 若检测到有依赖被外部脚本误禁用为 `.disabled`，会自动恢复，并提示重启
   - 冲突的口渴模组 (如 ThirstWasTaken / ToughAsNails) 会被重命名为 `.disabled`
4. 再次启动游戏即可游玩

### 方法 B：开发者安装 (从源码构建)

```powershell
git clone <仓库>
cd qlmzombie
copy -r C:\Users\Administrator\Desktop\qlmzombie-main\src\libs src\main\libs\
.\gradlew.bat build
copy build\libs\qlmzombie-*.jar %APPDATA%\.minecraft\mods\
```

### 方法 C：自定义禁用 / 启用模组

若希望启用 QLM 默认禁用的口渴模组：

1. 启动游戏一次，生成追踪文件
2. 打开 `.minecraft/qlmzombie_disabled_tracker.txt`
3. 删除对应行或整行注释 (`#` 前缀)
4. 将 `mods/thirst*.jar.disabled` 重命名回 `.jar`
5. 重新启动即可（QLM 不会再次禁用已从追踪列表移除的模组）

---

## ⚙️ 配置文件

模组配置文件位于 `.minecraft/config/qlmzombie.toml` (通过 Cloth Config 提供)：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enableThirst` | Boolean | true | 是否启用心渴系统 |
| `enableInfection` | Boolean | true | 是否启用感染系统 |
| `enableDayPhase` | Boolean | true | 是否启用昼夜阶段系统 |
| `enableZombieEvolution` | Boolean | true | 是否启用僵尸进化 |
| `thirstDecayRate` | Integer | 1 | 口渴值每 N tick 衰减 1 点 |
| `baseInfectionChance` | Float | 0.15 | 基础感染概率 |
| `structureSpawnMultiplier` | Float | 1.0 | 建筑生成概率倍率 (0 = 禁用) |

---

## 🆘 常见问题 (FAQ)

### Q1：启动后提示缺少 Kotlin/KubeJS？
A1：这是**开发环境**特有警告。正常发布 JAR 会通过 `ModDependencyHandler` 自动释放这些库。请确认 `src/main/libs/kotlinforforge-*.jar` 和 `kubejs-forge-*.jar` 文件存在。

### Q2：游戏内看不到口渴条？
A2：请确认：
- 非调试/创造模式
- `config/qlmzombie.toml` 中 `enableThirst = true`
- 客户端事件正确注册 (检查日志中 `[QLM Client]` 行)

### Q3：AI 伴侣召唤后不动？
A3：请确认 `player2-forge-1.20.1-1.4.0.jar` 已加载，且使用 `ai follow` 命令激活跟随任务，或查看 `ai status` 排查。

### Q4：两个口渴系统同时生效了 (双重扣水)？
A4：检查 `mods/` 目录中是否同时存在 ThirstWasTaken JAR。QLM 默认会将其重命名为 `.disabled`。若没自动禁用，请手动删除或加 `.disabled` 后缀。

### Q5：如何升级到新版 beta.buildN？
A5：替换 `mods/` 中的旧版 JAR 即可。若要强制重新释放所有内嵌模组，请删除 `mods/qlmzombie_disabled_tracker.txt`。

---

## 🐛 提交 Bug

请在 [GitHub Issues](https://github.com/SevenZeroMeowTeam/qlmzombie/issues) 提交 Bug，并附带：

1. **版本号**：`3.0.0.beta.build26` (精确到 build)
2. **崩溃日志**：`crash-reports/` 下最新文件
3. **最新日志**：`logs/latest.log`
4. **mods 列表截图**或 `mods/` 目录文件列表
5. **复现步骤**：从新存档开始可稳定复现的最简步骤

---

## 🤝 贡献指南

QLM Zombie 完全开源 (MIT License)，欢迎贡献代码：

1. Fork 仓库
2. 创建 feature 分支：`git checkout -b feature/new-skill`
3. 提交变更：`git commit -am "feat: add xxx"`
4. Push 到分支：`git push origin feature/new-skill`
5. 发起 Pull Request

**代码规范**：
- Kotlin 文件：使用 4 空格缩进，避免 `!!` 非空断言，优先 `as?` 安全转换
- Java 文件：遵循 Google Java Style，`@NotNull` / `@Nullable` 显式标注
- KubeJS：JS 脚本使用 ES6+ 语法，`const` 优先于 `let`
- 所有注册类必须使用 `DeferredRegister`，禁止直接 `Registry.register()` 调用

### 🤖 GitHub Actions CI

每次推送代码或提交 PR 时，GitHub Actions 会自动触发以下检查：

| 检查项 | 说明 |
|:------:|:-----|
| 变更通知 | 记录提交信息、作者、变更文件数、分支 |
| 版本号一致性 | 检查 `gradle.properties`、`QLMZombieMod.kt`、`README.md` 三处版本号是否一致 |
| 文件结构 | 验证关键文件（构建脚本、模组入口、配置文件）是否存在 |
| 依赖列表 | 检查 `scripts/libs-list.txt` 是否完整 |
| 大文件检查 | 确保没有超过 50MB 的文件或 jar 文件被提交 |

CI 配置文件：[.github/workflows/ci.yml](file:///D:/mcmod/.github/workflows/ci.yml)

### 🔄 自动推送

本地配置了 `post-commit` Git Hook，每次 `git commit` 后会自动推送到 GitHub：

```sh
# 提交后自动推送（无需手动 git push）
git commit -m "feat: 新功能描述"
# → [auto-push] 正在推送 dev 分支到 GitHub...
# → [auto-push] 推送成功 ✓
```

---

## 📄 许可证

```
MIT License

Copyright (c) 2025 SevenZeroMeow Team (七零喵团队)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 📋 更新说明

### 3.0.0.beta.build27 (2026-08-14)

#### 重写：依赖自动释放 v2（支持 `[中文名]` 前缀 + 重复文件清理 + 精准禁用）

**问题根因**（build26 以下，多个用户反馈口渴冲突失效、依赖误释放）：

`ModDependencyHandler.java` 和 `build.gradle.kts` 的文件匹配均使用文件名**从头匹配**逻辑，未处理用户自定义的 `[中文名]` 前缀（如 `[意志坚定] ToughAsNails-forge-...jar`），导致：
1. `[意志坚定] ToughAsNails-forge-1.20.1-9.2.0.171.jar` 不匹配 `startsWith("toughasnails")` → 口渴模组**不会被自动禁用**，与内置 ThirstFeature 双重口渴冲突。
2. `refinedstorage-1.12.4.jar` 与 `[精致存储] refinedstorage-1.12.4.jar` 视为两个不同模组 → 重复加载报错。
3. `libs/` 目录中误混入 `qlmzombie-3.0.0.beta.build26.jar`（编译产物，424 MB）→ 打包时 JAR 体积膨胀，若带 `[中文名]` 前缀亦无法被 `shouldSkipEmbeddedJar` 排除。

**修复方案（三端同步）**：

| 层级 | 改动 | 文件 | 说明 |
|:-----|:-----|:-----|:-----|
| 运行时匹配 | 新增 `stripBracketPrefix()` 剥离 `[中文名]` | [ModDependencyHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java#L288-L302) | 先剥离方括号前缀（含去空格小写），再做前缀匹配和去版本 |
| 禁用判定 | `isDefaultDisabled()` 改用前缀剥离后比较 | 同上 [#L549-L557](file:///D:/mcmod/src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java#L549-L557) | 带中文前缀的 ToughAsNails / ThirstWasTaken 现在可正确命中 `DEFAULT_DISABLED_PREFIXES` |
| 重复检测 | `stripVersion()` 先剥离前缀再去版本号 | 同上 [#L559-L569](file:///D:/mcmod/src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java#L559-L569) | `[精致存储] refinedstorage-…` 和裸 `refinedstorage-…` 返回同一 baseName → 正确识别为重复 |
| 排除判定 | `shouldSkipEmbeddedJar()` 先剥离前缀匹配 qlmzombie/serveradmin 等 | 同上 [#L274-L286](file:///D:/mcmod/src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java#L274-L286) | 防止 `[某名] qlmzombie-xxx.jar` 被误打包嵌入 |
| 构建清单 | `generateLibsManifest` 任务同步添加 `stripBracketPrefix` | [build.gradle.kts](file:///D:/mcmod/build.gradle.kts#L146-L175) | manifest 白名单生成阶段即过滤带前缀的排除项 |
| 打包排除 | `jar` 任务排除通配加 `*] qlmzombie*` 等前缀变体 | 同上 [#L217-L235](file:///D:/mcmod/build.gradle.kts#L217-L235) | 方括号无法被 `qlmzombie*.jar` glob 覆盖，需追加模式 |

#### 清理：`src/main/libs` 目录去重 + 剔除编译产物

| 删除项 | 原因 | 保留项 |
|:-------|:-----|:-------|
| `qlmzombie-3.0.0.beta.build26.jar` (424 MB) | 编译产物非依赖，且 `JarInJar` 递归嵌入会超 1GB | — |
| `refinedstorage-1.12.4.jar`（裸名版，3223 KB） | 与 `[精致存储] refinedstorage-1.12.4.jar` 重复 | `[精致存储] refinedstorage-1.12.4.jar` |
| `crafting-dead-core-1.20.1-1.9.0.homebaked.jar`（非 all 版，6587 KB） | `-all.jar` 已包含全部内嵌依赖，裸版为残包 | `crafting-dead-core-1.20.1-1.9.0.homebaked-all.jar` |

清理后：**119 个 JAR，总大小 448.3 MB**（之前 121 个、907 MB）。

#### 配套：`MoonHelper.java` 升级到 EnhancedCelestials 5.x API

（随本次 build 一并提交）
[MoonHelper.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/moon/MoonHelper.java) 从 EC 4.x `CelestialHolder` 静态字段直接读取，迁移到 EC 5.x `DataAnchor<TrackedDataKey>` 反射机制，适配 5.0.x+ 版本月相读取。

#### 版本号同步

| 改动 | 文件 |
|:-----|:-----|
| `3.0.0.beta.build26` → `build27` | [gradle.properties](file:///D:/mcmod/gradle.properties)、[QLMZombieMod.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/QLMZombieMod.kt)、[README.md](file:///D:/mcmod/README.md)、`scripts/libs-list.txt` |

### 3.0.0.beta.build26 (2026-08-14)

#### 修复：服务端 ExceptionInInitializerError 崩溃

**问题根因**：专用服务端启动时 `qlmzombie` mod 加载失败，报 `java.lang.ExceptionInInitializerError: null`。

通过 `javap` 分析编译后的 class 文件，定位到 [NonConflictKeysFeature.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/feature/NonConflictKeysFeature.kt)：

```kotlin
// 修复前：object 初始化块中直接创建 KeyMapping（客户端类）
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object NonConflictKeysFeature {
    private val keyMappings = listOf(
        KeyMapping("key.qlmzombie.open_qlm_menu", GLFW.GLFW_KEY_R, "category.qlmzombie.main"),
        // ...
    )
}
```

崩溃链：
1. `@Mod.EventBusSubscriber` 缺少 `Dist.CLIENT` → Forge 在服务端加载该类
2. Kotlin `object` 类加载时初始化 `keyMappings` → 创建 `KeyMapping` 实例
3. `KeyMapping` = `net.minecraft.client.KeyMapping`（客户端独有）→ 服务端不存在
4. `NoClassDefFoundError` → `ExceptionInInitializerError` → mod 加载失败

**修复方案（双重保险）**：

| 改动 | 文件 | 说明 |
|:-----|:-----|:-----|
| 添加 `Dist.CLIENT` | [NonConflictKeysFeature.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/feature/NonConflictKeysFeature.kt#L11-L15) | `@Mod.EventBusSubscriber` 添加 `value = [Dist.CLIENT]`，Forge 不在服务端加载此类 |
| 延迟初始化 | 同上 [#L22-L45](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/feature/NonConflictKeysFeature.kt#L22-L45) | `keyMappings` 改为 `by lazy`，即使类被意外加载也不会触发 `KeyMapping` 创建 |

**排查方法**：使用 `javap -c -p` 扫描编译输出的非 client 包 class 文件，查找引用 `net/minecraft/client/` 的类。

#### 版本号同步

| 改动 | 文件 |
|:-----|:-----|
| `3.0.0.beta.build25` → `build26` | [gradle.properties](file:///D:/mcmod/gradle.properties)、[QLMZombieMod.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/QLMZombieMod.kt)、[README.md](file:///D:/mcmod/README.md) |

### 3.0.0.beta.build25 (2026-08-14)

#### 重写：ModDependencyHandler 自动依赖释放系统（精确白名单 + 自动恢复）

**问题根因**（build24 日志多次出现 `kotlinforforge/kubejs/cloth 缺失` 循环报错）：

旧版 `ModDependencyHandler.java` 使用 35+ 模糊关键字 `contains` 扫描整个 mods 目录并禁用冲突项，但存在两处致命缺陷：
1. `KEEP_ALWAYS_KEYWORDS` 中写了 `craftingdead`，而实际文件是 `crafting-dead-xxx.jar`（带连字符），`contains("craftingdead")` 永远匹配不到，导致 `KEEP_ALWAYS` 保护失效，`crafting-dead-core`、`crafting-dead-decoration` 等被误禁用。
2. 没有"误禁用恢复"机制，外部脚本若把 `kotlinforforge-4.12.0-all.jar.disabled` 放到 mods 目录，旧版不会恢复。

**修复方案（四层设计）**：

| 改动 | 文件 | 说明 |
|:-----|:-----|:-----|
| 新增精确白名单 | [ModDependencyHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java) | 白名单源 = mod JAR 内 `libs/` 目录全部文件名，永不删除或禁用 |
| 新增自动恢复 | `restoreMistakenlyDisabled()` | 扫描 `.disabled` 文件，若在白名单中且非已知问题模组 → 自动恢复为 `.jar` |
| 移除模糊扫描 | 删除 `CONFLICT_KEYWORDS` / `KEEP_ALWAYS_KEYWORDS` / `scanAndHandleConflicts()` | 不再用 35+ 关键字误伤依赖 |
| 保守禁用 | `DEFAULT_DISABLED_PREFIXES`（4项精确前缀） | 仅禁用 `toughasnails / thirstwastaken / thirstmod / thirstcanteen` |
| 去重保护 | `detectAndRemoveDuplicates()` | 白名单优先保留，仅删除非白名单重复项 |
| 构建清单 | [build.gradle.kts](file:///D:/mcmod/build.gradle.kts) | 新增 `generateLibsManifest` 任务，构建时生成 `libs/manifest.txt` 并打包进 JAR |

#### 更新：版本号 + 游戏公告 + README

| 改动 | 文件 | 说明 |
|:-----|:-----|:-----|
| 版本号 build25 | [gradle.properties](file:///D:/mcmod/gradle.properties)、[QLMZombieMod.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/QLMZombieMod.kt)、[README.md](file:///D:/mcmod/README.md) | 3 处同步到 `3.0.0.beta.build25` |
| 登录提示优化 | `QLMZombieMod.onPlayerLogin()` | `needsRestart` 消息新增"误禁用 N 个，已自动恢复"分支 |
| 公告精简 | 同上 | 将旧版逐 build 修复公告替换为功能描述 + 依赖白名单说明 + `/qlm mods`/`download` 命令提示 |

### 3.0.0.beta.build24 (2026-08-14)

#### 修复：口渴模组兼容性 + 创造模式翻页崩溃

**问题 1：创造模式物品栏翻页到第 5 页崩溃**

**根因**：`ToughAsNails`（意志坚定）模组在客户端加载后，其创造模式标签页翻页时触发崩溃。`ModDependencyHandler` 之前只禁用了 `ThirstWasTaken`（文件名含 "thirst" 关键字），但 `ToughAsNails` 文件名不含 "thirst"，未被自动禁用。

**修复**：将 `toughasnails`、`tough_as_nails`、`tough-as-nails` 加入 `DEFAULT_DISABLED_KEYWORDS` 和 `CONFLICT_KEYWORDS`，`ModDependencyHandler` 启动时自动禁用 ToughAsNails（重命名为 `.disabled`），口渴系统完全由 mod 内置 `ThirstFeature` 接管。

| 文件 | 修复内容 |
|:-----|:---------|
| [ModDependencyHandler.java#L27-L39](file:///D:/mcmod/src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java#L27-L39) | `DEFAULT_DISABLED_KEYWORDS` + `CONFLICT_KEYWORDS` 新增 ToughAsNails 关键字 |

**问题 2：净化水瓶恢复口渴值失效**

**根因**：`PurifiedWaterItem.kt` 使用反射调用 `com.qlm.zombie.craftingdead.feature.ThirstFeature`，但实际类路径为 `com.qlm.zombie.feature.ThirstFeature`，`ClassNotFoundException` 导致口渴值无法恢复（`runCatching` 吞掉了异常）。

**修复**：移除反射，改为直接调用 `ThirstFeature.restoreThirst()`。

| 文件 | 修复内容 |
|:-----|:---------|
| [PurifiedWaterItem.kt#L3-L12](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/item/PurifiedWaterItem.kt#L3-L12) | 添加 `ThirstFeature` import，移除反射 |
| [PurifiedWaterItem.kt#L34-L38](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/item/PurifiedWaterItem.kt#L34-L38) | 直接调用 `ThirstFeature.restoreThirst(livingEntity, 8)` |

### 3.0.0.beta.build23 (2026-08-13)

#### 修复：KubeJS 脚本全面修复（第二轮）

**问题**：根据服务器日志，修复 KubeJS 6 兼容性剩余 4 个错误。

**修复内容**：

| 问题 | 文件 | 修复方案 |
|:-----|:-----|:---------|
| `BlockEvents.randomTick` 不存在 | [harvest_moon_growth.js](file:///D:/mcmod/src/main/kubejs/harvest_moon_growth.js) | 改用 `LevelEvents.tick`，每 200 tick 遍历玩家附近 8 格催熟作物 |
| `PlayerEvents.logged_in` 不存在 | [qlmzombie_scripts.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_scripts.js) | KubeJS 6 驼峰命名：`logged_in` → `loggedIn` |
| `LootJS.addTableModifier` 不存在 | [qlmzombie_loot.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_loot.js) | 改用 1.20.1 版本 API `addLootTypeModifier` |
| `craftingdead:bullet` 物品不存在 | [qlmzombie_scripts.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_scripts.js) | 移除该合成配方 |
| `sleeping_bag` 合成配方 result 为空 | [qlmzombie_scripts.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_scripts.js) | 添加 `Ingredient.of().itemIds.length` 物品存在检查 |

#### 修复：语言文件缺失

| 文件 | 修复内容 |
|:-----|:---------|
| [zh_cn.json](file:///D:/mcmod/src/main/resources/assets/qlmzombie/lang/zh_cn.json) | 添加 `block.qlmzombie.sleeping_bag` 条目（BlockItem 翻译键来自 Block） |
| [en_us.json](file:///D:/mcmod/src/main/resources/assets/qlmzombie/lang/en_us.json) | 添加 `item` 和 `block` 两个 sleeping_bag 条目 |

### 3.0.0.beta.build22 (2026-08-13)

#### 修复：KubeJS 脚本全面修复（第一轮）

**问题**：服务器日志显示 KubeJS 脚本加载失败，7 个脚本仅 2 个成功（2/7）。

**修复内容**：

| 问题 | 修复方案 |
|:-----|:---------|
| `const ServerLevel` 重复声明（4 个脚本） | 用 IIFE（立即执行函数表达式）包裹，使 const 成为局部变量 |
| `const THROTTLE_TICKS` 重复声明（2 个脚本） | 同上 |
| `onEvent()` 已废弃（2 个脚本，9 处调用） | 迁移至 KubeJS 6 新 API |

**API 迁移映射**：

| 旧 API (KubeJS 5) | 新 API (KubeJS 6) |
|:------------------|:------------------|
| `onEvent('recipes', ...)` | `ServerEvents.recipes(...)` |
| `onEvent('tags', ...)` | `ServerEvents.tags('item', ...)` |
| `onEvent('player.logged_in', ...)` | `PlayerEvents.loggedIn(...)` |
| `onEvent('entity.death', ...)` | `EntityEvents.death(...)` |
| `onEvent('block.break', ...)` | `BlockEvents.broken(...)` |
| `onEvent('loot_tables', ...)` | `LootJS.modifiers(...)` + `addLootTypeModifier` |

**修改文件**：
- [airdrop_scheduler.js](file:///D:/mcmod/src/main/kubejs/airdrop_scheduler.js) — IIFE 包裹
- [harvest_moon_growth.js](file:///D:/mcmod/src/main/kubejs/harvest_moon_growth.js) — IIFE 包裹
- [lucky_moon_buff.js](file:///D:/mcmod/src/main/kubejs/lucky_moon_buff.js) — IIFE 包裹
- [moon_scheduler.js](file:///D:/mcmod/src/main/kubejs/moon_scheduler.js) — IIFE 包裹
- [qlmzombie_loot.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_loot.js) — onEvent → LootJS/EntityEvents/BlockEvents
- [qlmzombie_scripts.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_scripts.js) — onEvent → ServerEvents/PlayerEvents

### 3.0.0.beta.build21 (2026-08-13)

#### 新功能：睡袋系统

**实现**：新增睡袋方块（`qlmzombie:sleeping_bag`），3 个羊毛横排合成。

**核心特性**：
- **不重置出生点**：使用 `LivingEntity.startSleeping()` 而非 `Player.startSleepInBed()`，睡觉不会改变玩家出生点
- **夜晚随时放随时睡**：夜晚右键放置后再次右键即可入睡
- **白天自动收起**：通过 `randomTick` 检测白天自动破坏并掉落物品，也可白天右键手动收起
- **可捡起来**：破坏方块后掉落为睡袋物品，可重复使用
- **无碰撞箱**：高度 2 像素，类似地毯，不阻挡移动

**修改文件**：
| 文件 | 说明 |
|:-----|:-----|
| [SleepingBagBlock.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/block/SleepingBagBlock.java) | 睡袋方块核心逻辑 |
| [QLMBlocks.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/block/QLMBlocks.kt) | 方块注册 |
| [QLMItems.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/item/QLMItems.kt) | 移除原 Item 注册（改由 BlockItem 管理） |
| [qlmzombie_scripts.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_scripts.js) | KubeJS 合成配方 |
| [zh_cn.json](file:///D:/mcmod/src/main/resources/assets/qlmzombie/lang/zh_cn.json) | 中文翻译 |
| [blockstates/sleeping_bag.json](file:///D:/mcmod/src/main/resources/assets/qlmzombie/blockstates/sleeping_bag.json) | 方块状态 |
| [models/block/sleeping_bag.json](file:///D:/mcmod/src/main/resources/assets/qlmzombie/models/block/sleeping_bag.json) | 方块模型 |
| [models/item/sleeping_bag.json](file:///D:/mcmod/src/main/resources/assets/qlmzombie/models/item/sleeping_bag.json) | 物品模型 |
| [textures/block/sleeping_bag.png](file:///D:/mcmod/src/main/resources/assets/qlmzombie/textures/block/sleeping_bag.png) | 方块贴图 |
| [loot_tables/blocks/sleeping_bag.json](file:///D:/mcmod/src/main/resources/data/qlmzombie/loot_tables/blocks/sleeping_bag.json) | 掉落表 |

### 3.0.0.beta.build20 (2026-08-13)

#### 修复：反射字段名映射问题

**问题**：游戏启动时出现 5 条反射警告，导致骷髅AI修复、经验球合并、AI节流等功能失效。

**根本原因**：`SkeletonAIFixFeature`、`ClumpsFeature`、`AIImprovementsFeature` 使用 `getDeclaredField("mojangName")` 查找字段，但 Forge 1.20.1 运行时 Minecraft 类字段使用 SRG 名称（如 `f_257247_`），导致 `NoSuchFieldException`。

**修复**：新增 [ReflectionHelper.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/util/ReflectionHelper.java) 工具类，采用三级降级策略查找字段：
1. 尝试 Mojang mappings 名称（开发环境）
2. 尝试 SRG 名称（生产环境）
3. 按**字段类型**遍历匹配（终极降级，不依赖名称）

| 修复文件 | 反射字段 | 降级方式 |
|:---------|:---------|:---------|
| SkeletonAIFixFeature | `RangedAttackGoal.attackIntervalMax` | Mojang→SRG→跳过 |
| SkeletonAIFixFeature | `GoalSelector.availableGoals` | Mojang→SRG→Set类型匹配 |
| ClumpsFeature | `ExperienceOrb.value` | Mojang→SRG→int类型排除法 |
| AIImprovementsFeature | `GoalSelector.availableGoals` | Mojang→SRG→Set类型匹配 |
| AIImprovementsFeature | `WrappedGoal.goal` | Mojang→SRG→Goal类型匹配 |

### 3.0.0.beta.build19 (2026-08-13)

#### 修复：配置文件加载崩溃

**问题**：游戏启动时模组加载失败，报错 `ExceptionInInitializerError`，导致整个客户端进入"broken mod state"。

**根本原因**：`QLMConfig.kt` 中通过 `builder.comment()` 添加的分组标题注释（如"【AI 优化】"、"【LLM】"等）未被任何 `define()` 方法消耗。ForgeConfigSpec 在 `builder.build()` 时验证根上下文是否有未消耗注释，抛出 `IllegalStateException: Non-empty comment when empty expected`。

**修复**：重写 [QLMConfig.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/config/QLMConfig.kt)，移除所有独立的 `builder.comment()` 调用，只保留与 `define()` 链式调用的 `.comment()`（这些会被 `define()` 自动消耗）。

**影响**：TOML 配置文件中不再有分组标题注释，但每个配置项仍有完整的中文说明。62 项配置功能完全保留。

### 3.0.0.beta.build18 (2026-08-13)

#### 新功能：隐藏初始装备附魔显示

**变更**：初始装备的 tooltip 中不再显示附魔列表和"不可破坏"标签，使物品信息更简洁。

**实现**：通过设置 NBT `HideFlags` 位掩码（`1 | 4 = 5`）：
- bit 0 (1)：隐藏附魔列表
- bit 2 (4)：隐藏 Unbreakable 标签

修改文件：[StarterKitHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/StarterKitHandler.java)

### 3.0.0.beta.build17 (2026-08-13)

#### 修复：移除旧版红色经验条血量显示

**问题**：旧版 `HealthBarOverlay.java` 在屏幕底部绘制红色经验条样式的血量条（0xFF_FF3030 亮红/橙红/深红渐变），与新版绿色血量条（`HealthBarOverlayHandler.java`）同时显示，造成 UI 冲突。

**修复**：删除旧版 `HealthBarOverlay.java`，仅保留 [HealthBarOverlayHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/client/HealthBarOverlayHandler.java) 的绿色血量条 + 护甲/饱食度文字 UI。

#### 新功能：初始装备全部无限耐久

**变更**：所有初始装备（武器/工具/弓/盔甲）现在拥有 **无限耐久（Unbreakable）**，不会损耗耐久度。

| 装备类型 | 物品 | 耐久 |
|:--------:|:-----|:----:|
| 武器 | 铁剑（攻击 999） | ∞ 无限 |
| 工具 | 铁斧（攻击 55）/ 铁镐（攻击 44）/ 铁锹 / 铁锄 | ∞ 无限 |
| 远程 | 弓（满附魔） | ∞ 无限 |
| 盔甲 | 铁头盔 / 铁胸甲 / 铁护腿 / 铁靴子 | ∞ 无限 |

**实现方式**：通过 `ItemStack.getOrCreateTag().putBoolean("Unbreakable", true)` 设置 NBT 标签，使物品耐久度锁定不衰减。

修改文件：[StarterKitHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/StarterKitHandler.java)

#### 新增：配置文件中文介绍

[QLMConfig.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/config/QLMConfig.kt) 为所有配置项添加了详细的中文注释说明，游戏启动时自动生成带中文介绍的 `config/qlmzombie-common.toml` 配置文件。

覆盖 8 大配置分组共 62 项配置，每项包含：功能说明、参数含义、默认值、取值范围。

### 3.0.0.beta.build16 (2026-08-13)

> ⚠ **材质说明**：目前所有自定义实体（Boss、特殊僵尸、特殊骷髅等）均使用原版 Minecraft 材质，未制作自定义材质/模型。请见谅，后续版本会逐步添加。

#### 修复：血量UI重复显示

**问题**：原版红心形血量、灰色护甲、鸡腿饥饿条与自定义绿色血量条同时显示。
**修复**：使用 `RenderGuiOverlayEvent.Pre` 事件隐藏原版 `PLAYER_HEALTH`、`ARMOR_LEVEL`、`FOOD_LEVEL` 三个 Overlay，仅保留自定义绿色血量条、护甲数值和饱食度文字。

**UI 层级（从上到下）**：
| 位置 | 内容 |
|:----:|:-----|
| 上层 | 血量条（绿/黄/红渐变） + ❤ 数值 |
| 中层 | 🛡 护甲值（左） / ☕ 饱食度（右） |
| 下层 | 原版蓝色经验条 |

修改文件：[HealthBarOverlayHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/client/HealthBarOverlayHandler.java)

#### 重写：计分板实时同步系统

**之前**：旧版计分板只有 4 条信息（天数/时间/阶段/月相），数据不准确。
**现在**：重写为按玩家个性化更新，每秒（20 tick）自动同步 8 项数据：

| 序号 | 字段 | 图标 | 说明 |
|:----:|:----:|:----:|:-----|
| 1 | 天数 | ☀ | 实时游戏天数 |
| 2 | 安全日 | ☘ | 剩余安全期（前25天） |
| 3 | 游戏内时间 | ⌚ | 12小时制 + 时段（清晨/白天/黄昏/夜晚/黎明） |
| 4 | 月相 | ☾ | 血月☠/幸运🍀/丰收🌾 + 8种真实月相emoji |
| 5 | 难度阶段 | ⚔ | 和平/简单/普通/困难/极限 + 锁定状态 |
| 6 | 生命上限 | ❤ | 自动读取真实 MAX_HEALTH 属性，显示总数值+击杀永久加成 |
| 7 | 攻击上限 | ⚔ | 自动读取真实 ATTACK_DAMAGE 属性，显示总数值+击杀永久加成 |

关键代码：[ScoreboardHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/scoreboard/ScoreboardHandler.java)

#### 修复：成就系统仅统计敌对生物

**问题**：之前击杀中立/被动生物（鸡、牛、羊、村民、铁傀儡）也计入成就击杀数。
**修复**：在 [AchievementManager.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/achievement/AchievementManager.java) 的 `onLivingDeath` 事件中增加 `Monster` 接口判定，只有击杀实现 `Monster` 接口的敌对生物才统计成就进度。

#### 新增：中文翻译扩充（zh_cn.json）

从 108 条扩展到 **288 条翻译**，覆盖：
- 🏆 成就系统标题+描述（30+）
- 🏷 Tooltip（品质/属性/范围挖掘/无限耐久/虚空免疫）
- 🧬 10 项成就名 + 技能点奖励提示
- ✨ 技能点总览 / 获得 / 可用 / 已用
- 🔦 自动扫描 5 种强度标签
- 🧟 16 种特殊僵尸名
- ☠ 7 种特殊骷髅名
- 📢 战斗提示 20+（破甲箭/TNT自爆/召唤/进化）
- 📊 计分板 7 项标签

翻译文件：[zh_cn.json](file:///D:/mcmod/src/main/resources/assets/qlmzombie/lang/zh_cn.json)

#### 新功能：投手僵尸

| 属性 | 说明 |
|:----:|:------|
| 血量 | 35 |
| 攻击 | 4 |
| 特殊能力 | 向玩家**丢点燃的TNT**（4秒爆炸），冷却9秒，距离5-13格 |
| 粒子 | 火焰粒子追踪 |
| 警告 | 玩家收到 "⚠ 投手僵尸丢出了点燃的TNT！快躲开！" 提示 |

#### 新功能：自爆僵尸

| 属性 | 说明 |
|:----:|:------|
| 血量 | 20（极脆） |
| 攻击 | 0（不自爆不死） |
| 速度 | 极快（0.35基础移速） |
| 特殊能力 | 10格内加速冲向玩家，**5格内自爆**（范围3破坏地形） |
| 粒子 | 靠近时闪烁红色 LAVA 粒子 |

#### 新功能：弓箭手僵尸

| 属性 | 说明 |
|:----:|:------|
| 血量 | 40 |
| 攻击 | 8 |
| 特殊能力 | 2秒冷却射箭，距离4-12格，精准度较高 |
| 装备 | 手持弓 |

关键代码：[SpecialZombieHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/SpecialZombieHandler.java)

### 3.0.0.beta.build15 (2026-08-12)

> ⚠ **材质说明**：目前所有自定义实体（Boss、特殊僵尸、特殊骷髅等）均使用原版 Minecraft 材质，未制作自定义材质/模型。请见谅，后续版本会逐步添加。

#### 新功能：近战骷髅（3种）

| 类型 | 血量 | 攻击 | 护甲 | 攻击方式 | 特殊效果 |
|:----:|:----:|:----:|:----:|:--------:|:--------:|
| 🗡 骷髅剑士 | 50 | 14 | 6 | 铁剑近战+盾牌 | 35%概率流血（瞬间伤害+缓慢） |
| ⚔ 骷髅狂战士 | 45 | 18 | 4 | 铁斧近战 | 30%概率破甲重击（15伤害+击飞） |
| 🛡 骷髅守卫 | 70 | 8 | 14 | 石剑近战+盾牌+铁甲 | 40%概率反伤+抗性提升 |

**特殊说明**：近战骷髅不射箭，用近战武器攻击，每个都有独特的战斗特效

#### 新功能：远程僵尸（3种）

| 类型 | 血量 | 攻击 | 投射物 | 攻击效果 |
|:----:|:----:|:----:|:------:|:--------:|
| 🪨 投掷僵尸 | 35 | 5 | 雪球（模拟石块） | 伤害8+击飞，3秒冷却 |
| ☘ 吐息僵尸 | 30 | 4 | 鸡蛋（模拟毒液） | 伤害6+中毒II 5秒+缓慢，3秒冷却 |
| 💥 爆破僵尸 | 40 | 6 | 火焰弹（模拟炸弹） | 伤害12+火焰3秒+爆炸范围1.5，6秒冷却 |

**特殊说明**：远程僵尸不靠近玩家，在3-12格范围内远程攻击

#### 新功能：骷髅箭矢效果调整

普通骷髅不再有特殊效果。只有特殊骷髅才有概率触发：

| 类型 | 触发概率 | 效果 |
|:--------:|:--------:|:-----|
| ☠ 凋零骷髅射手 | 30% | 凋零II 10秒 |
| ☘ 剧毒骷髅射手 | 40% | 剧毒II 10秒 |
| 💥 爆破骷髅射手 | 25% | 爆炸范围2 |
| ⚙ 铁甲骷髅射手 | 30% | 破甲箭20-40伤害+瞬间伤害+缓慢II 10秒 |

关键代码：[SpecialSkeletonHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/SpecialSkeletonHandler.java) / [SpecialZombieHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/SpecialZombieHandler.java)

### 3.0.0.beta.build14 (2026-08-12)

> ⚠ **材质说明**：目前所有自定义实体（Boss、特殊僵尸、特殊骷髅等）均使用原版 Minecraft 材质，未制作自定义材质/模型。请见谅，后续版本会逐步添加。

#### 新功能：7种特殊僵尸（攻击行为各不相同）

| 类型 | 血量 | 攻击 | 特殊能力 | 攻击行为 |
|:----:|:----:|:----:|----------|:--------:|
| 🔴 巨人僵尸 | 200 | 15 | 每5秒范围震地+缓慢II | 近距离AOE攻击 |
| 🟡 木桶僵尸 | 150 | 10 | 半血/死亡丢出小鬼僵尸 | 半血后召唤 |
| 🟣 召唤僵尸 | 80 | 8 | 每5秒30%召唤1-2只僵尸 | 远程召唤辅助 |
| 🔥 烈焰僵尸 | 40 | 12 | 攻击点燃目标5秒，永久火焰外观 | 火焰攻击 |
| ☘ 剧毒僵尸 | 35 | 8 | 攻击附带中毒II 5秒 | 毒素攻击 |
| ⚙ 铁甲僵尸 | 60 | 12 | 护甲16，攻击附带击飞 | 坦克型正面硬刚 |
| ☁ 跳跃僵尸 | 30 | 6 | 3-8格距离跳跃扑向玩家 | 机动性高 |

**生成概率**：随天数递增（Day25+ 1.5x，Day50+ 2x，Day100+ 3x，基础2%）

关键代码：[SpecialZombieHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/SpecialZombieHandler.java)

#### 新功能：僵尸双手持物品

**25%概率**手持物品（主手+40%概率副手），主手70%原版武器/工具，副手可持火把/盾牌/金苹果等

关键代码：[ZombieItemHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/ZombieItemHandler.java)

#### 新功能：4种特殊骷髅

| 类型 | 血量 | 攻击 | 特殊能力 |
|:----:|:----:|:----:|----------|
| ☠ 凋零骷髅射手 | 40 | 12 | 箭矢附带凋零II 10秒 |
| ☘ 剧毒骷髅射手 | 35 | 10 | 箭矢附带剧毒II 10秒 |
| 💥 爆破骷髅射手 | 45 | 15 | 箭矢爆炸（范围2） |
| ⚙ 铁甲骷髅射手 | 60 | 10 | 护甲12，穿甲箭+15伤害 |

**所有骷髅统一**：20%破甲箭（无视护甲20-40伤害 + 瞬间伤害I + 缓慢II 10秒）

关键代码：[SpecialSkeletonHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/SpecialSkeletonHandler.java)

#### 新功能：AI玩家背包GUI

- 使用 `/qlm backpack` 打开AI背包（3行27格）
- 可像真实玩家背包一样**放入/取出物品**
- 数据自动持久化保存在NBT中
- 支持跨会话保存

关键代码：[AIPlayerBackpack.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/player/AIPlayerBackpack.java)

#### 新功能：村民守卫增强

- 村民守卫**不会逃跑**，主动攻击附近敌对生物
- 受伤时**召唤附近25格铁傀儡**协助反击
- 铁傀儡获得 `[守卫]` 标记
- 守卫和铁傀儡协同作战

关键代码：[VillagerGuardHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/villager/VillagerGuardHandler.java)

#### 其他新增命令

- `/qlm backpack` - 打开AI玩家背包
- `/qlm skill` - 查看技能点信息
- `/qlm achievement` - 查看成就列表

### 3.0.0.beta.build13 (2026-08-12)

> ⚠ **材质说明**：目前所有自定义实体（Boss、特殊僵尸等）均使用原版 Minecraft 材质，未制作自定义材质/模型。请见谅，后续版本会逐步添加。

#### 新功能：Boss死亡掉落宝箱

**小Boss宝箱**：3-5个原版稀有物品（钻石、绿宝石、金苹果、末影珍珠、黑曜石、经验瓶等）+ 1-2个其他模组随机物品

**大Boss宝箱**：3-6个高级物品（钻石块、绿宝石块、下界合金锭、下界之星、附魔金苹果等）+ 2-3个其他模组随机物品 + 附魔钻石剑（锋利IV+火焰附加II）+ 附魔钻石胸甲（保护IV）

**自动注册**：扫描所有已加载模组物品，自动纳入宝箱掉落池

关键代码：[BossDropHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/boss/BossDropHandler.java)

#### 新功能：僵尸方块破坏与搭建

**破坏方块**：僵尸追踪玩家时，自动破坏前方阻挡的方块（泥土、石头、木板、门、栅栏等30+种方块），非基岩/黑曜石/液体
**搭建追击**：僵尸在坑中时会自动在脚下放置泥土方块搭建追击玩家
**粒子特效**：破坏/放置均有 CRIT 粒子特效

关键代码：[ZombieBlockHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/ZombieBlockHandler.java)

#### 新功能：僵尸手持物品

**25%概率**手持物品，70%原版物品（铁剑/斧/镐、石剑、面包、腐肉等），30%其他模组随机物品
**80%死亡掉落**，不影响僵尸攻击行为

关键代码：[ZombieItemHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/ZombieItemHandler.java)

#### 新功能：僵尸/骷髅昼夜行为

| 行为 | 白天 | 夜晚 |
|:----:|:----:|:----:|
| 🧟 僵尸 | 速度-0.15，不主动攻击，不燃烧 | 速度+0.10，主动攻击 |
| ☠ 骷髅 | 停止行动，不主动攻击，不燃烧 | 速度+0.10，主动攻击 |
| 反击 | 被攻击后10秒内反击 | 正常攻击 |

**只有被玩家攻击后才会反击**，白天游荡缓慢，晚上行动加速

关键代码：[MobBehaviorHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/MobBehaviorHandler.java)

#### 新功能：特殊僵尸系统

**3种特殊僵尸**，随天数增加提高生成概率：

| 类型 | 血量 | 攻击 | 特殊能力 | 生成条件 |
|:----:|:----:|:----:|----------|:--------:|
| 🟣 召唤僵尸 | 80 | 10 | 每5秒30%召唤1-2只普通僵尸 | Day 25+ 3% / Day 50+ 5% / Day 100+ 8% |
| 🟡 木桶僵尸 | 150 | 10 | 半血丢出1-2只小鬼僵尸，死亡额外丢出2只 | Day 25+ 3% / Day 50+ 5% / Day 100+ 8% |
| 🔴 巨人僵尸 | 200 | 15 | 每5秒范围震地（伤害8+缓慢II），护甲8 | Day 25+ 2% / Day 50+ 4% / Day 100+ 6% |

关键代码：[SpecialZombieHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/SpecialZombieHandler.java)

### 3.0.0.beta.build12 (2026-08-12)

> ⚠ **材质说明**：目前所有自定义实体（Boss、村民守卫等）均使用原版 Minecraft 材质，未制作自定义材质/模型。请见谅，后续版本会逐步添加。

#### 新功能：Boss技能粒子特效

Boss释放技能时使用游戏内粒子系统制作视觉特效，无需额外材质包：

**小Boss「震地」特效：**
- 🧨 大爆炸粒子（EXPLOSION_EMITTER）中心爆发
- 💨 30个烟尘粒子环形扩散
- 🔥 15个火焰粒子模拟地面裂纹
- 💥 20个石块飞溅（CRIT粒子）
- 🔊 爆炸音效

**大Boss特效（阶段升级）：**
- 🧨 核心爆炸
- **阶段1**：40个烟尘粒子
- **阶段2**：60个火焰+烟尘粒子
- **阶段3**：80个火焰+熔岩+灵魂火焰粒子（狂暴感）
- 🔊 爆炸音效 + 僵尸破门音效

**Boss死亡特效：**
- 小Boss：20个烟尘粒子 + 爆炸
- 大Boss：50个火焰+熔岩+灵魂火焰粒子 + 大爆炸

关键代码：[BossSkillHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/BossSkillHandler.java)

#### 新功能：增强AI系统

使用事件驱动方式强化生物AI：

**🧟 僵尸增强：**
- **夜间加速**：夜晚移动速度+0.08，白天恢复
- **召唤同伴**：被玩家攻击时20%概率召唤2-3只僵尸同伴
- **破门增强**：更高概率破坏木门

**☠ 骷髅增强：**
- **近战逃跑**：玩家靠近3格内时自动逃跑拉开距离
- **缓慢箭**：15%概率附加缓慢 II 效果（5秒）
- **精准射击**：提高射击精度

**👨 村民增强：**
- **遇敌逃跑**：检测到附近有敌对生物时自动逃跑
- **受伤呼救**：被攻击时通知附近20格内的铁傀儡前来救援

**🤖 铁傀儡增强：**
- **主动索敌**：自动扫描20格内敌对生物并攻击
- **伤害加成**：+10额外伤害
- **击飞效果**：攻击时击飞敌人，额外5点伤害
- **攻击苦力怕**：原版铁傀儡不主动攻击苦力怕，现已修复

关键代码：[EnhancedAIHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/ai/EnhancedAIHandler.java)

### 3.0.0.beta.build11 (2026-08-12)

#### 新功能：成就系统

**10项成就**，完成任务解锁，不可作弊获取：

| 成就 | 名称 | 条件 | 奖励 |
|------|------|------|------|
| 🔥 | 初出茅庐 | 击杀第一个敌对生物 | 1技能点 |
| ⚔️ | 僵尸猎人 | 累计击杀100只僵尸 | 2技能点 |
| 🏆 | 僵尸大师 | 累计击杀500只僵尸 | 3技能点 |
| ☀️ | 生存者 | 存活25天 | 2技能点 |
| ⭐ | 老手 | 存活100天 | 3技能点 |
| 💀 | Boss杀手 | 击杀Boss | 5技能点 |
| 🛡️ | 僵尸潮幸存者 | 成功存活一次僵尸潮 | 5技能点 |
| ⛏️ | 下界合金猎手 | 获得下界合金锭 | 5技能点 |
| ✨ | 神话工匠 | 获得神话品质装备 | 5技能点 |
| 🧬 | 进化见证者 | 见证10只僵尸进化 | 2技能点 |

成就解锁时全服广播通知。

关键代码：[AchievementManager.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/achievement/AchievementManager.java)

#### 新功能：技能点系统

- **初始5技能点**，首次登录自动获得
- 通过完成成就获得更多技能点
- 使用 `/qlm skill` 查看技能点信息
- 总获得点数、已花费点数、可用点数一目了然

关键代码：[SkillPointHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/skill/SkillPointHandler.java)

#### 新功能：新手保护

- **前25天**（Day 0-24）不生成任何敌对生物
- 保护新手玩家安心度过前期
- 仅限主世界，下界/末地已被封禁
- 经过25天后恢复正常生成

关键代码：[NewbieProtectionHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/restriction/NewbieProtectionHandler.java)

#### 新功能：村民守卫

- 村民有 **5%** 概率刷新为**村民守卫**
- 守卫属性：**100血量** / **25攻击力** / 8护甲 / 32格追踪范围
- 守卫会主动攻击附近敌对生物（通过原版AI）
- 守卫有红色名称 `村民守卫`，持久化不消失

关键代码：[VillagerGuardHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/villager/VillagerGuardHandler.java)

#### 新功能：自动扫描系统

- **每2秒**扫描玩家附近**20格**内的敌对生物
- 根据僵尸强度显示不同颜色的**发光效果**：
  - 绿色：普通僵尸（低血量/低伤害）
  - 黄色：中等强度
  - 橙色：精英（高血量/高伤害）
  - 红色：进化僵尸（NBT标记）
  - 紫色：Boss级
- 高强度僵尸自动添加标记名称（[精英]、[进化]、[Boss]）
- 附近超过5个敌对生物时发出警告

关键代码：[ZombieScanner.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/scanner/ZombieScanner.java)

### 3.0.0.beta.build10 (2026-08-12)

#### 新功能：血量过低自动再生（5%阈值）

**修改**：玩家血量低于 **5%**（原10%）时自动触发**生命恢复 III**（60秒，冷却5分钟），快速恢复血量。

关键代码：[PlayerHealthHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/player/PlayerHealthHandler.java)

#### 新功能：血月调度系统

- **前25天**（Day 0-24）：安全期，不生成血月
- **第25天起**：每 **14天** 自动触发一次血月
- 只在夜晚触发，同一晚只触发一次
- 使用 EnhancedCelestials API 强制设置血月事件

关键代码：[BloodMoonScheduler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/moon/BloodMoonScheduler.java)

#### 新功能：僵尸进化系统

**进化概率随天数提升：**
| 天数 | 进化概率 | 血量加成 | 伤害加成 |
|------|---------|---------|---------|
| 0-24 | 0% | 无 | 无 |
| 25-49 | 10% | 1.5x | +50% |
| 50-99 | 25% | 2.0x | +100% |
| 100-149 | 50% | 3.0x | +150% |
| 150+ | 75% | 5.0x | +200% |

进化后的僵尸带有 `[进化]` 标签和特殊颜色，血量更厚、伤害更高、速度更快。

关键代码：[ZombieEvolutionManager.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/ZombieEvolutionManager.java)

#### 新功能：僵尸潮系统（每28天）

**触发条件**：每28天夜晚（Day 28, 56, 84...）自动触发

**5波僵尸潮机制：**
| 波次 | 敌人 | 数量 | 说明 |
|------|------|------|------|
| 第1波 | 普通僵尸 | 10-15 | 基础僵尸 |
| 第2波 | 僵尸 + 骷髅 | 14-21 | 混合远程 |
| 第3波 | 混合 + **小Boss x1** | 16-23 | 第3阶段召唤小Boss |
| 第4波 | **小Boss** + 支援 | 11-21 | 小Boss 500血 |
| 第5波 | **大Boss** + 精英 | 21-41 | 大Boss 10000血 |

**Boss技能：**
- **小Boss**（500血）：范围震地（伤害15+击退+缓慢5秒），30%概率触发
- **大Boss**（10000血，3阶段）：
  - **阶段1**：初始状态，50伤害，20护甲
  - **阶段2**（血量≤66%）：速度提升，伤害70，召唤1个小Boss，恢复10%血量
  - **阶段3**（血量≤33%）：狂暴模式，速度0.5，伤害100，护甲30，召唤2个小Boss，恢复15%血量

**Boss血条**：屏幕顶部显示红色Boss血条，显示当前波次进度

关键代码：
- [ZombieHordeHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/ZombieHordeHandler.java)
- [BossSkillHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/zombie/BossSkillHandler.java)

### 3.0.0.beta.build9 (2026-08-12)

#### 新功能：维度封禁 & 敌对生物限制

**维度封禁**：玩家无法通过任何方法（传送门、指令、传送等）进入下界和末地。

- 监听 `EntityTravelToDimensionEvent` 在传送门打开前取消
- 监听 `PlayerChangedDimensionEvent` 在维度改变后强制拉回主世界
- 监听 `EntityJoinLevelEvent` 在下界/末地维度中阻止非玩家实体生成

**敌对生物限制**：末影人、苦力怕、蜘蛛、洞穴蜘蛛、女巫无法在游戏中生成。

关键代码：[MobRestrictionHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/restriction/MobRestrictionHandler.java)

#### 新功能：下界合金锭敌对生物掉落

击杀敌对生物（排除玩家、村民、铁傀儡）有 **1.5%** 概率掉落下界合金锭，替代被禁用的下界维度获取方式。

关键代码：[NetheriteDropsHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/drop/NetheriteDropsHandler.java)

#### 新功能：骷髅破甲箭

骷髅和流浪者有 **20%** 概率发射破甲箭，命中玩家时造成 **20~40** 点（10~20心）无视护甲的魔法伤害，并移除原箭矢。

关键代码：[SkeletonArmorPiercingHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/projectile/SkeletonArmorPiercingHandler.java)

#### 新功能：玩家初始血量 200

玩家初始生命值上限提升至 **200**（基础20 + 180），登录/重生时自动设置。同时添加血量过低自愈机制：血量低于10%时自动施加生命恢复 II（60秒，冷却5分钟）。

关键代码：[PlayerHealthHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/player/PlayerHealthHandler.java)

#### 新功能：自定义血量覆盖层

在经验条和饱食度上方显示类似经验条样式的自定义血量条，包含：
- ❤ 血量条（绿色/黄色/红色渐变，居中显示数值）
- ☕ 饥饿值（显示在血量条右侧）
- 🛡 护甲值（显示在血量条左侧）
- 使用MC可识别的Unicode符号

关键代码：[HealthBarOverlayHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/client/HealthBarOverlayHandler.java)

### 3.0.0.beta.build8 (2026-08-12)

#### 神话装备合成配方重构（按用户图示）

将 11 个神话装备（剑/斧/镐/锹/锄/弓/头盔/胸甲/护腿/靴子）的合成配方统一重写为以下 3×3 对称结构：

```
┌──────────┬──────────┬──────────┐
│ 下界合金锭 │   钻石    │ 下界合金锭 │   (N D N)
├──────────┼──────────┼──────────┤
│   钻石    │ 对应装备   │   钻石    │   (D X D)
├──────────┼──────────┼──────────┤
│ 下界合金锭 │   钻石    │ 下界合金锭 │   (N D N)
└──────────┴──────────┴──────────┘
```

**耗材（每个神话装备）：**
- 下界合金锭 × 4（四角）
- 钻石 × 4（上下左右四边）
- 对应下界合金装备 × 1（中心）
- 合计：下界合金锭4 + 钻石4 + 中心装备 = **单件神话成本约 8 钻石 + 4 下界合金 + 1 下界合金装备**

全部 11 种神话装备使用同一对称模板，仅中心 `X` 替换为对应装备。
产物 NBT 带 `qlm_mythic_forced=true`，由 `MythicCraftHandler` 拦截并强制赋予神话品质（ID=9，攻击力无限 / 无耐久 / 破坏基岩 / 全套盔甲虚空免伤）。

关键代码：[qlmzombie_scripts.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_scripts.js#L215-L316)

### 3.0.0.beta.build7 (2026-08-12)

#### 命令系统注册（`/qlm` 命令）

全新完善的 Brigadier 命令体系，**玩家命令**无需 OP（权限 0 即可用），**管理员命令**需 OP（权限 2+）。

**玩家命令（所有人可用）：**

| 命令 | 功能 |
|------|------|
| `/qlm` | 显示帮助菜单（同 `/qlm help`） |
| `/qlm help` | 显示完整命令帮助（玩家版/管理员版自动切换） |
| `/qlm stats` | 查看永久击杀属性：总击杀数 / ❤ 永久生命上限 / ⚔ 永久攻击上限 |
| `/qlm quality` | 查看**手持物品**的品质详情：品质等级、攻击倍率、攻击/生命/护甲/随机伤害加成、神话级特殊属性 |
| `/qlm moon` | 查看当前月相状态（☠ 血月 / ★ 幸运之月 / ✿ 丰收之月 / 原版 8 相）及合成加成百分比 |

**管理员命令（OP 权限 2+）：**

| 命令 | 功能 |
|------|------|
| `/qlm day` / `/qlm day <N>` | 查看/设置当前游戏天数 |
| `/qlm phase` | 查看当前难度阶段（和平/简单/普通/困难/极限/锁定） |
| `/qlm difficulty` | 查看当前游戏难度 + 阶段锁定状态 |
| `/qlm info` | 完整状态面板（天数/阶段/难度/月相/依赖释放/冲突/重复） |
| `/qlm phases` | 所有难度阶段一览表（含天数范围与难度乘数） |
| `/qlm mods` | 内部 Mod 扫描列表（已安装✔ / 待释放○）+ 冲突检测 |
| `/qlm download` | 重新释放所有内嵌依赖 Mod + 冲突检测解决 |
| `/qlm starter <玩家名>` | 重置指定玩家初始装备标记（下次登录自动重发） |
| `/qlm moon force <blood\|lucky\|harvest>` | 强制触发血月/幸运之月/丰收之月 |
| `/qlm aiplayer ...` | AI 玩家：spawn / skin / tame / list / tp / kill |
| `/qlm mcp` | Player2 MCP 服务器集成信息（连接配置 JSON） |

关键代码：[QLMCommands.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/command/QLMCommands.java)

### 3.0.0.beta.build6 (2026-08-12)

#### 新功能：侧边栏计分板

在玩家屏幕右侧显示实时游戏信息，使用 MC 可识别的 Unicode 符号标记：

```
✦ 七零喵末日 ✦       (标题，金色)
☀ 天数: 42            (当前游戏天数，黄色)
☘ 安全日: 剩 14 天    (PEACE 阶段剩余天数，绿色/红色)
⌚ 时间: 06:30 夜晚   (游戏内时间 + 白天/黑夜)
☾ 月相: ☠ 血月        (当前月相状态，颜色随月相变化)

⚔ 阶段: 普通          (当前难度阶段)
☠ 击杀: 87 ❤+12.5 ⚔+5.3  (永久击杀属性统计)
```

**特性：**
- 每 1 秒（20 tick）更新一次
- 月相支持：☠ 血月、★ 幸运之月、✿ 丰收之月、原版 8 相月
- 安全日：PEACE 阶段（0-24 天）显示剩余天数，超过后显示"已结束"
- 击杀统计：显示总击杀数 + 永久生命/攻击加成
- 使用 Team prefix + 不可见 fake player 实现纯文本行（无数字干扰）

关键代码：[ScoreboardHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/ScoreboardHandler.java)

#### 常规合成品质确认

确认所有常规材料合成都可以获得特殊属性，概率不同：

| 材料 | 材料加成 | 高品质概率 |
|------|---------|-----------|
| 木材/石头 | +0 | 最低 |
| 铁/金/红石/烈焰棒 | +1 | 较低 |
| 钻石 | +2 | 中等 |
| 下界合金 | +3 | 较高 |

**无需下界合金剑作为前置**——任何木剑、石剑、铁剑等常规合成即可获得品质属性。
**神话系列除外**——神话装备仍需通过特殊合成配方（龙蛋+下界之星+不死图腾）获得。

关键代码：
- [ItemAttributeHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/ItemAttributeHandler.java) `onItemCrafted` — 所有可品质物品合成时 roll 品质
- [EquipmentQuality.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/EquipmentQuality.java) `computeMaterialBias` — 材料加成计算

#### 游戏公告更新

登录公告新增计分板提示，共 10 条公告覆盖全部功能。

### 3.0.0.beta.build5 (2026-08-12)

#### 重大升级：10 级品质系统（新增"精良"等级）

在原 9 级品质基础上新增第 3 级"精良"（FINE，深绿色），完整顺序如下：

| ID | 品质 | 颜色 | 权重 | 随机伤害区间 | 镐子默认范围 | 特殊属性 |
|----|------|------|------|------------|-------------|---------|
| 0 | 劣质 | 灰 | 25 | 0.5 ~ 2.0 | 1×1 | — |
| 1 | 一般 | 白 | 22 | 1.0 ~ 5.0 | 1×1 | — |
| 2 | **精良**（新） | 深绿 | 18 | 1.5 ~ 7.0 | 1×1 | — |
| 3 | 普通 | 绿 | 15 | 2.0 ~ 10.0 | 1×1 | — |
| 4 | 高级 | 青 | 10 | 3.0 ~ 18.0 | 1×1 | — |
| 5 | 稀有 | 亮紫 | 7 | 6.0 ~ 30.0 | 3×3 | 可破坏黑曜石/哭泣黑曜石 |
| 6 | 神器 | 金 | 4 | 10.0 ~ 60.0 | 5×5 | — |
| 7 | 史诗 | 红 | 2.5 | 20.0 ~ 150.0 | 7×7 | — |
| 8 | 传说 | 深红 | 1.2 | 50.0 ~ 500.0 | 9×9 | — |
| 9 | **神话** | 深紫 | 0.3 | ∞ | 11×11 | **攻击力无限 + 无耐久 + 破坏基岩 + 虚空免伤** |

关键代码：[EquipmentQuality.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/EquipmentQuality.java)

#### 镐子能力全面开放（所有品质均有概率获得）

旧版：稀有+ 自动拥有黑曜石破坏、神话自动拥有基岩/11×11。
新版：**所有品质**（含劣质）都有概率获得任意能力，仅概率不同：

| 能力 | 劣质 | 精良 | 普通 | 高级 | 稀有 | 神器 | 史诗 | 传说 | 神话 |
|------|------|------|------|------|------|------|------|------|------|
| 黑曜石破坏 | 5% | 15% | 20% | 25% | 30%* | 35%* | 40%* | 45%* | 100% |
| 基岩破坏 | 0.1% | 0.5% | 1.3% | 1.7% | 2.1% | 2.5% | 2.9% | 3.3% | 100% |
| 范围挖掘 | 5% | 11% | 23% | 29% | 35% | 41% | 47% | 53% | 100% |

\* 标记的品质会直接拥有该能力（不再 roll）。

- 范围挖掘保留互斥机制（取最大），最大可达 11×11
- 神话品质直接拥有全部能力
- **所有破坏方块均有掉落物**（`destroyBlock(pos, true, player)` 第二参数 dropBlock=true）

关键代码：[PickaxeAbility.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/PickaxeAbility.java#L105-L194)

#### 神话级虚空免伤：必须全套盔甲

旧版：任意一件神话盔甲即可触发虚空免伤。
新版：**必须 4 件全部为神话品质**（头盔+胸甲+护腿+靴子），脱一件都不行。

- 在 `QualityEquipmentHandler.onLivingHurt` 中通过 `hasFullMythicArmor(victim)` 检查
- 触发时取消 `FELL_OUT_OF_WORLD` 伤害并传送到 Y+64 位置
- 通知玩家"神话庇护：虚空之力已被全套神话盔甲抵御！"

关键代码：[QualityEquipmentHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/QualityEquipmentHandler.java#L178-L194)

#### 新功能：玩家初始装备

首次登录的玩家自动获得完整初始装备：

**武器 / 工具（铁质统一）**
- 铁剑（攻击力 **999**）
- 铁斧（攻击力 **55**）
- 铁镐（攻击力 **44**）
- 铁锹、铁锄
- 每件随机 **5 种附魔**

**远程武器**
- 弓（全部 7 种附魔：力量 V、冲击 II、火矢 I、无限 I、耐久 III、经验修补 I、消失诅咒 I）
- 64 支箭

**盔甲（铁质全套，正确穿戴）**
- 铁头盔 → 头部槽位
- 铁胸甲 → 胸部槽位
- 铁护腿 → 腿部槽位
- 铁靴子 → 脚部槽位
- 每件随机 5 种附魔

**消耗品**
- 64 个附魔金苹果
- 64 个面包

仅首次登录发放，通过 `qlm_starter_kit_received` NBT 持久化标记。

关键代码：[StarterKitHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/StarterKitHandler.java)

#### 新功能：神话装备合成配方

通过 KubeJS 添加 11 个神话装备合成配方，产物 NBT 带 `qlm_mythic_forced=true` 标记，
由 `MythicCraftHandler` 在 `ItemCraftedEvent` 中拦截并强制赋予神话品质：

| 装备 | 配方（3×3） | 中心材料 |
|------|------------|---------|
| 神话剑 | D N D / N S N / D T D | 下界合金剑 + 龙蛋×4 + 下界合金锭×4 + 不死图腾×2 |
| 神话斧 | D N D / N A S / D T D | 下界合金斧 + 龙蛋×4 + 下界合金锭×4 + 不死图腾×2 + 下界之星 |
| 神话镐 | D N D / N P S / D T D | 下界合金镐 + 龙蛋×4 + 下界合金锭×4 + 不死图腾×2 + 下界之星 |
| 神话锹 | D N D / N A S / 空 T 空 | 下界合金锹 + 同上 |
| 神话锄 | D N D / N A S / 空 T 空 | 下界合金锄 + 同上 |
| 神话弓 | D N D / N B S / D T D | 弓 + 同上 |
| 神话头盔/胸甲/护腿/靴子 | 同上结构 | 对应下界合金盔甲件 + 同上 |

由于材料成本极高（龙蛋、下界之星、不死图腾），神话装备自然成为服务器顶级目标。

关键代码：
- Java：[MythicCraftHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/MythicCraftHandler.java)
- KubeJS 配方：[qlmzombie_scripts.js](file:///D:/mcmod/src/main/kubejs/qlmzombie_scripts.js#L215-L327)

#### 铁质工具伤害重写

通过 `ItemAttributeModifierEvent` 添加额外 `ATTACK_DAMAGE` 修饰符：

| 物品 | 原始伤害 | 加成 | 最终伤害 |
|------|---------|------|---------|
| 铁剑 | 7 | +992 | **999** |
| 铁斧 | 9 | +46 | **55** |
| 铁镐 | 4 | +40 | **44** |

关键代码：[ItemAttributeHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/ItemAttributeHandler.java#L160-L174)

#### 游戏公告系统重写

玩家登录时显示完整公告（共 9 条）：

```
[七零喵僵尸末日]  v3.0.0.beta.build5  - 末日求生·品质时代
[公告] 新增 10 级装备品质系统：劣质→一般→精良→普通→高级→稀有→神器→史诗→传说→神话
[公告] 神话级武器攻击力/耐久无限；全套神话盔甲可无视虚空伤害、可破坏基岩
[公告] 镐子能力扩展：3×3 / 5×5 / 7×7 / 9×9 / 11×11 范围挖掘；可破坏黑曜石/哭泣黑曜石/基岩
[公告] 击杀敌对生物（除玩家/村民/铁傀儡）获得永久随机生命上限和攻击力上限
[公告] 新增玩家初始装备：铁质全套装 + 5 附魔 + 弓满附魔 + 64 附魔金苹果 + 64 面包
[公告] 铁剑伤害 999 / 铁斧伤害 55 / 铁镐伤害 44 / 神话装备可通过合成获得（极高成本）
[公告] 合成时材料+月相影响品质；所有品质镐子均有概率获得特殊能力
[七零喵] 输入 /qlm help 查看命令列表，/qlm stats 查看永久属性
```

关键代码：[QLMZombieMod.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/QLMZombieMod.kt#L129-L151)

#### Bug 修复 & 清理

- 删除无用的旧 `WeaponQuality.java`（已被统一 `EquipmentQuality` 取代）
- 修复 `PickaxeAbilityHandler` 中遗留的 `getId() >= 4` 硬编码，改为 `canBreakObsidianByQuality()` / `canBreakBedrockByQuality()` 谓词方法
- 修复 `StarterKitHandler` 中 `player.inventory.setChanged()` 私有访问错误，改用 `player.getInventory().setChanged()`
- 修复 `StarterKitHandler` 中 `Enchantments.PROTECTION` 等 1.20.1 映射不存在的字段问题，统一改用 `ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation)` 解析
- 修复铁镐 UUID 与铁斧冲突问题，新增 `IRON_PICKAXE_DAMAGE_UUID`

### 3.0.0.beta.build4 (2026-08-12)

#### 核心新功能：统一品质系统（武器 / 工具 / 盔甲）

**9 级品质枚举**（劣质 → 神话），替换掉原先混乱的双系统（EquipmentQuality + WeaponQuality）：

| 等级 | 品质 | 颜色 | 权重 | 随机伤害区间 | 镐子范围挖掘 | 特殊属性 |
|------|------|------|------|------------|-------------|---------|
| 0 | 劣质 | 灰 | 25 | 0.5 ~ 2.0 | 1×1 | — |
| 1 | 一般 | 白 | 25 | 1.0 ~ 5.0 | 1×1 | — |
| 2 | 普通 | 绿 | 20 | 2.0 ~ 10.0 | 1×1 | — |
| 3 | 高级 | 青 | 12 | 3.0 ~ 18.0 | 1×1 | — |
| 4 | 稀有 | 亮紫 | 8 | 6.0 ~ 30.0 | 3×3 | 可破坏黑曜石/哭泣黑曜石 |
| 5 | 神器 | 金 | 5 | 10.0 ~ 60.0 | 5×5 | — |
| 6 | 史诗 | 红 | 3 | 20.0 ~ 150.0 | 7×7 | — |
| 7 | 传说 | 深红 | 1.5 | 50.0 ~ 500.0 | 9×9 | — |
| 8 | **神话** | 深紫 | 0.5 | ∞ | 11×11 | **攻击力无限 + 无耐久 + 破坏基岩 + 虚空免伤** |

关键代码：[EquipmentQuality.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/EquipmentQuality.java)

#### 合成时品质加权（材料 + 月相）

- **材料加成**：下界合金锭 → +3 档、钻石 → +2 档、铁/金/红石/烈焰棒 → +1 档（映射为 shiftBonus，提升高品质权重）
- **月相加成**：幸运之月 +40% 高品质加权、血月 +25%、丰收之月 +10%
- 合成武器时根据品质区间 **随机伤害**（神话级使用指数分布，攻击力理论无限、中位数 ≥ 11k）
- 神话武器 AttributeModifier +99999 攻击、设置 `Unbreakable` NBT

关键代码：[ItemAttributeHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/ItemAttributeHandler.java) `onItemCrafted`

#### 镐子能力扩展（原 3 级 → 5 级范围）

新增 `PickaxeAbility.BEDROCK_BREAKER`、`RANGE_7X7`、`RANGE_9X9`、`RANGE_11X11`；与品质自动联动：

- 稀有及以上直接拥有 可破坏黑曜石/哭泣黑曜石
- 神话直接拥有 可破坏基岩
- 范围挖掘等级由 `EquipmentQuality.NBT_MINE_RANGE` 统一存储，`PickaxeAbilityHandler.onBlockBreak` 使用该字段
- 低品质镐子也可按 50%+ 概率抽取额外范围能力（品质越高范围越大、概率越大）

关键代码：
- [PickaxeAbility.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/PickaxeAbility.java)
- [PickaxeAbilityHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/PickaxeAbilityHandler.java)

#### 击杀敌对生物 → 永久随机生命上限 / 攻击力

新增 `PermanentKillStats`：
- **排除**：玩家、村民、铁傀儡（与玩家友好的生物）
- **允许**：所有实现 `Enemy` 接口的敌对生物（僵尸、骷髅、爬行者、尸壳、流浪者、掠夺者、唤魔者、BOSS 等 + 按名字正则兜底）
- 80% 基础触发概率；持有/穿着神话级装备 → **100% 触发且加成翻倍**
- `❤ 生命 +0.5~2.0` 随机，`⚔ 攻击 +0.2~1.5` 随机
- 持久化到玩家 `PersistentData`（`qlm_kill_stats_v1` → `health_total` / `attack_total` / `kill_count`）
- 登录/重生/换维度/每秒心跳重算 transient modifier

关键代码：[PermanentKillStats.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/PermanentKillStats.java)

#### 神话级特殊能力整合

- **虚空免伤**：穿任意神话盔甲 → 取消 `FELL_OUT_OF_WORLD` 伤害并传送到 Y+64 位置
- **破坏基岩**：神话/品质 NBT 含 `qlm_break_bedrock=true` 的镐子 → 左键基岩时直接破坏
- **不可破坏方块通用处理**：持任意神话装备 → 末地传送门框架/命令方块等 `destroySpeed < 0` 方块均可手动破坏并掉落资源
- **领地 / 出生点保护无视**：`BreakEvent` 被其他 mod 取消时，神话装备强制撤销取消（`priority = LOWEST`）

关键代码：
- [QualityEquipmentHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/QualityEquipmentHandler.java)
- [MythicItemHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/MythicItemHandler.java)

#### 统一 Tooltip 展示

合成、掉落、随机品质装备的物品悬浮提示统一展示：
- `✦ 品质: 【品质名称】`
- `⚔ 随机伤害 +X.X`（神话显示 `⚔ 攻击力 ∞ 无限`）
- `❤ 生命上限 +X`、`🛡 护甲 +X`
- `⛏ 范围挖掘 N×N`
- `✦ 可破坏黑曜石 / 基岩`
- `✦ 耐久无限制 / 虚空免伤`
- PickaxeAbility 能力详情（可破坏黑曜石、可破坏基岩、3×3~11×11）

#### 随机装备掉落重写

击杀敌对生物 30% 概率掉落品质装备：从随机 roll 时的 9 级品质中抽取，属性/能力/随机伤害一次写入，镐子直接走 `PickaxeAbility.rollAbilities`。

关键代码：[RandomEquipmentDropHandler.java](file:///D:/mcmod/src/main/java/com/qlm/zombie/item/RandomEquipmentDropHandler.java)

#### Bug 修复 & 兼容性

- 统一移除了原 `WeaponQuality` 7 级破碎系统，全部切换到 9 级 `EquipmentQuality`，避免同一物品两个系统相互覆盖
- [CDItems.kt](file:///D:/mcmod/src/main/kotlin/com/qlm/zombie/craftingdead/item/CDItems.kt) 新增 `@JvmStatic isQualityItem(Item)`，让 Crafting Dead 武器/医疗/近战/护甲/弹药也进入品质系统覆盖
- `EquipmentQuality.extractCraftInputs` 从合成事件反射输入物品材料；失败则回退到 `itemTierBias`（从维修材料推断 tier）

### 3.0.0.beta.build3 (2026-08-12)

#### 新增功能

整合源项目（qlmzombie-main）的完整 Java 源码和资源文件到 Kotlin 重构版中，实现 Java + Kotlin 双语言混合架构。

**新增 Java 源文件（156 个）：**

- **AI 算法模块** (`ai/algorithm/`)：行为树（BehaviorTree、BTNode、BTSelector、BTSequence 等）、有限状态机（FiniteStateMachine、FSMState、FSMTransition）、Q-学习（QLearningAgent、QTable、RewardFunction）、模糊逻辑（FuzzyVariable、FuzzySet）、A*寻路（AStarPathfinder）、效用系统（UtilitySystem、Consideration）
- **CloudAI 模块** (`cloudai/`)：WebSocket 客户端、AI 实体管理、环境扫描、攻击辅助、命令缓存
- **SDK 模块** (`sdk/`)：模组开发 SDK，包含 UI 组件（SDKButton、SDKScreen、SDKTextField 等）、事件系统（SDKEventBus、SDKEvent）、注册表（CustomBlock、CustomItem、CustomEntity）、任务调度器、命令构建器、粒子/音效/渲染 API
- **Python 脚本引擎** (`script/`)：PythonAPI、PythonScriptEngine、PythonEventBridge
- **Crafting Dead 武器系统**：枪械（AmmoItem、AmmoType、IGun、各种附件）、医疗物品（Bandage、FirstAidKit、Painkillers、Splint 等）、近战武器（Crowbar、CombatKnife、BowieKnife）、护甲（BallisticHelmet、CombatBoots、PlateCarrier、TacticalVest）、手榴弹（FragmentGrenade、FlashbangGrenade、MolotovCocktail）
- **Crafting Dead 实体**：CivilianZombie、ScientistZombie、SoldierZombie、ThrownGrenadeEntity
- **Crafting Dead 方块**：AmmoCrateBlock、MedicalSupplyCrateBlock、SupplyCrateBlockEntity
- **玩家系统**：AIPlayerSpawnHandler、AIPlayerSkinManager、LittleSkinClient、AISelectionHandler、PlayerHealthHandler、PlayerProtectionHandler、HealthBarOverlay
- **游戏机制**：HordeManager（尸潮系统）、MoonHelper（月相系统）、ChainMiningHandler（连锁挖矿）、ScoreboardHandler（计分板）、MobRestrictionHandler（怪物限制）
- **物品系统**：EquipmentQuality、WeaponQuality、MythicItemHandler、PickaxeAbility、QualityEquipmentHandler、RandomEquipmentDropHandler
- **指令系统**：QLMCommands、QLMAIPlayerCommands
- **成就系统**：AchievementTracker、AdvancementManager
- **特性模块**：KillBonusFeature、ClumpsFeature、SkeletonAIFixFeature、FastFurnaceFeature、AIImprovementsFeature
- **其他**：BossMusicManager、FakePlayerMenu、DifficultyLockState、ForgeEventBridge

**新增资源文件（214 个）：**

- 战利品表（`data/qlmzombie/loot_tables/chests/`）：abandoned_shop、ocean_ruin、random_building、other_mod_building
- 战利品修饰符（`data/qlmzombie/loot_modifiers/`）：drop_the_meat、building_weapon_inject、building_minecraft_gear、abandoned_shop_loot 等
- 成就（`data/qlmzombie/advancements/`）：zombie_kills、survival_days、blood_moon、phase_survival、ai_player、combat、building 等
- FTB 任务（`data/ftbquests/quests/`）：15 个任务文件
- DropTheMeat 资源（`assets/dropthemeat/`）：50+ 种肉类物品的纹理、模型、语言文件
- 配方（`data/qlmzombie/recipes/`）：purified_water_smelting、plank_collector、plank_axe
- 月相配置（`data/enhancedcelestials/`）：lucky_moon 事件
- MCP 配置（`mcp/player2_mcp_config.json`）
- 武器检测配置（`assets/qlmzombie/data/weapon_detection.json`）
- 实体皮肤（`assets/qlmzombie/textures/entity/cloudai/default_skin.png`）

#### Bug 修复

1. **修复 Kotlin/Java 互操作性问题**
   - 为 `QLMZombieMod.LOGGER` 和 `needsRestart` 添加 `@JvmField` 注解，使 Java 代码可以直接访问
   - 为 `CDEffects`、`CDEntities`、`QLMEntities`、`QLMItems`、`CDItems`、`CDBlocks` 的所有 RegistryObject 字段添加 `@JvmField`
   - 为 `DayPhaseManager.getCurrentPhase()` 添加 `@JvmStatic`
   - 为 `Player2APIService` 添加 `@JvmStatic isPlayer2Available()` 伴生对象方法
   - 为 `CDItems` 添加 18 个 `CD_` 前缀别名（如 `CD_AMMO_556X45`、`CD_BANDAGE` 等）
   - 为 `CDEffects` 添加 `BROKEN_BONE`→`FRACTURE`、`PAIN_SUPPRESSION`→`PAINKILLER`、`ADRENALINE_RUSH`→`ADRENALINE` 别名

2. **修复重复类定义**
   - 从 `CDBlocks.kt` 移除与 Java 重复的 `MedicalSupplyCrateBlock`、`AmmoCrateBlock` 类定义
   - 从 `CDEntities.kt` 移除与 Java 重复的 `ThrownGrenadeEntity` 类定义
   - 删除与 Kotlin 版本重复的 `DropTheMeatLootModifier.java`
   - 将 `FakePlayerEntity` 从 Kotlin（继承 `LivingEntity`）替换为 Java 版（继承 `PathfinderMob`，有完整 AI 功能）

3. **修复 DayPhase 枚举兼容性**
   - 在 `DayPhase.kt` 添加 `isLocked()` 方法
   - 在 `AIOptimizationHandler.java` 的 switch 语句中添加 `case LOCKED` 分支
   - 修复 `QLMCommands.java` 中 DayPhase 属性访问方式

4. **扩展配置项**
   - 在 `QLMConfig.kt` 中添加 28 个缺失的配置字段（DayPhase 阈值、Player2 MCP、ChainMining、AI Player Spawn、Health Bar 等）

5. **适配 ThrownGrenadeEntity API 变更**
   - 更新 `CDItems.kt` 中手榴弹物品的使用逻辑，适配 Java 版构造器和 `GrenadeType` 枚举

#### KubeJS 脚本

- 4 个 KubeJS 脚本（`moon_scheduler.js`、`lucky_moon_buff.js`、`harvest_moon_growth.js`、`airdrop_scheduler.js`）经对比与源项目完全一致，无需更新
- 2 个目标项目独有脚本（`qlmzombie_loot.js`、`qlmzombie_scripts.js`）保持不变

### 3.0.0.beta.build2 (2026-08-12)

#### Bug 修复

1. **修复 Kotlin `object` 单例导致 mod 实例化失败**
   - 问题：`modLoader = "javafml"` 时，Forge 的 `FMLModContainer` 通过反射调用构造器实例化 mod 类，但 Kotlin `object` 的构造器是 `private`（单例模式），导致 `IllegalAccessException` 崩溃
   - 修复：将 [mods.toml](src/main/resources/META-INF/mods.toml) 中 `modLoader` 从 `"javafml"` 改为 `"kotlinforforge"`，KotlinForForge 语言提供商了解 Kotlin `object` 的单例语义，无需反射调用构造器

2. **修复 kotlinforforge 语言提供商版本范围不匹配**
   - 问题：`modLoader` 改为 `kotlinforforge` 后，`loaderVersion` 字段不再指 Forge 版本，而是指 kotlinforforge 版本。原值 `[47,)` 要求 kotlinforforge ≥ 47，但实际嵌入版本是 4.12.0
   - 修复：将 [gradle.properties](gradle.properties) 中 `loader_version_range` 从 `[47,)` 改为 `[4,)`

3. **修复 kotlin-stdlib 打包导致的 Java 模块系统冲突**
   - 问题：将 kotlin-stdlib class 文件直接打进 jar 根目录后，`qlmzombie` 和 `kotlinforforge` 两个模块同时导出 `kotlinx.coroutines.sync` 包，触发 `ResolutionException: Modules qlmzombie and thedarkcolour.kotlinforforge export package kotlinx.coroutines.sync` 崩溃
   - 修复：移除 [build.gradle.kts](build.gradle.kts) 中 `from(zipTree(kotlinForForgeJar))` 的 kotlin class 提取逻辑，改为仅以完整 JAR 形式内嵌到 `libs/` 目录，由 Forge 的 `JarInJarDependencyLocator` 在 mod 扫描阶段自动发现

4. **修复首次安装时依赖检查失败导致崩溃**
   - 问题：`mods.toml` 中 `kotlinforforge`、`kubejs`、`cloth-config` 标记为 `mandatory = true`，但 `ModDependencyHandler` 在 `@Mod` 构造函数中才执行（Forge 依赖检查之后），首次安装时这些 jar 尚未被释放到 `mods/` 目录
   - 修复：将 `kubejs` 和 `cloth-config` 改为 `mandatory = false`（代码中不直接引用它们的类）；`kotlinforforge` 保持 `mandatory = true`（JarInJar 机制在依赖检查前发现它）；同时在 [QLMZombieMod.kt](src/main/kotlin/com/qlm/zombie/QLMZombieMod.kt) 中添加 `checkOptionalDependencies()` 方法，首次启动时在聊天框提示玩家重启

5. **统一 MOD_VERSION 与 gradle.properties 版本号**
   - 问题：`QLMZombieMod.kt` 中 `MOD_VERSION = "3.0.0.kotlin.build.1.0"` 与 `gradle.properties` 中 `mod_version=3.0.0.beta.build1` 不一致
   - 修复：统一使用 `gradle.properties` 中的版本号

### 3.0.0.beta.build1 (2026-08-12)

#### Bug 修复

1. **修复 mods.toml ordering 值导致游戏启动崩溃**
   - 问题：`kotlinforforge` 和 `kubejs` 依赖的 `ordering` 字段使用了 `"BOTH"`，该值在 Forge 1.20.1 的 `IModInfo.Ordering` 枚举中不存在（仅有 `NONE`/`BEFORE`/`AFTER`），导致游戏启动时直接抛出 `IllegalArgumentException` 崩溃
   - 修复：将 `ordering` 从 `"BOTH"` 改为 `"AFTER"`（[mods.toml](src/main/resources/META-INF/mods.toml)）

2. **修复模组名称和描述中文乱码**
   - 问题：`gradle.properties` 中的中文 `mod_name` 和 `mod_description` 被 Java Properties 按 ISO-8859-1 读取（规范强制），导致 `processResources` 展开后 `mods.toml` 中出现 `ä¸é¶åµå°¸æ«æ¥çamod` 之类的乱码
   - 修复：在 [build.gradle.kts](build.gradle.kts) 中直接硬编码中文字符串，绕过 Properties 编码限制；同时添加 `filteringCharset = "UTF-8"` 确保资源过滤使用正确编码

3. **修复 Player2API 服务端口与 Minecraft 服务器冲突**
   - 问题：`Player2APIService` 默认绑定端口 25565，与 Minecraft 服务器默认端口完全相同，导致启动本地服务器时端口冲突
   - 修复：默认端口从 25565 改为 18921，并添加 `apiPort` 配置项到 [QLMConfig](src/main/kotlin/com/qlm/zombie/config/QLMConfig.kt)，玩家可在 `config/qlmzombie.toml` 中自定义

4. **修复 Player2API 服务在服务端错误启动**
   - 问题：`commonSetup` 事件在客户端和服务端均触发，导致 API 服务在服务端也尝试启动
   - 修复：在 [QLMZombieMod.kt](src/main/kotlin/com/qlm/zombie/QLMZombieMod.kt) 中添加 `FMLEnvironment.dist == Dist.CLIENT` 判断，确保仅客户端启动 API 服务

5. **添加运行时 UTF-8 编码支持**
   - 在 Minecraft runs 配置中注入 `file.encoding`、`sun.stdout.encoding`、`sun.stderr.encoding` 为 UTF-8，修复日志中中文时间戳和文本显示乱码

#### 已知问题（非本 Mod 导致）

- **多人服务器连接被拒绝**：当客户端安装了 `ThirstWasTaken`、`ToughAsNails` 但服务端未安装时，这些模组注册的网络通道版本不匹配会导致连接被终止。`qlmzombie` 本身不注册任何网络通道，不会导致此问题。解决方法：在服务端安装对应模组，或从客户端移除

---

## 🙏 致谢

特别感谢以下团队 / 个人的开源工作，让 QLM Zombie 得以存在：

- **Minecraft Forge 团队** — 底层模组加载器
- **thedarkcolour** — Kotlin for Forge 项目
- **KubeJS 团队 (LatvianModder / MaxNeedsSnacks)** — 强大的数据驱动脚本引擎
- **VazkiiMods** — Botania / Quark / Patchouli / Zeta 生态
- **Glitchfiend** — ToughAsNails 坚定意志生存
- **TartaricAcid / 车万女仆团队** — 东方女仆 AI 系统先驱
- **CreativeMD** — ItemPhysic / PlayerRevive / CreativeCore
- **Shadows-of-Fire** — Placebo / FastWorkbench
- **Fuzss** — PuzzlesLib / Forge Config 生态
- **Serilum** — Collective 系列模组框架
- **所有 100+ 开源模组作者** — 每一颗星星都是末日的萤火
- **七零喵社区玩家** — 你们的反馈让 mod 越来越好

---

> 🌅 **愿你在末日的每一个黎明，都能看到第二天的太阳。**
>
> — SevenZeroMeow Team · 七零喵僵尸末日生存 Mod
>
> 版本：`3.0.0.beta.build27` · 构建日期：2026-08-14 · Minecraft 1.20.1

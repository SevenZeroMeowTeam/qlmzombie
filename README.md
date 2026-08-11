# 七零喵僵尸末日生存mod (QLM Zombie Apocalypse)

**制作团队：七零喵团队 (SevenZeroMeowTeam)**
**当前版本：`2.10.0.rewrite.beta.build.47.0`**

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

输出 JAR：`build/libs/qlmzombie-2.10.0.rewrite.beta.build.47.0.jar`

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
| 工作台配方性能优化 | FastWorkbench | 与 FastSuite/FastFurnace 同系列签名哈希缓存机制 |

> 含纹理/模型/语言资源的 mod 一律保留原 JAR 释放，不做源码替代。

---

## Crafting Dead 末日生存装备系统（v2.10.0.b36 新增）

基于 Crafting Dead（NEXUSNODE 开源架构，参考 `crafting-dead-upstream/`）原创实现的装备系统，包含医疗、枪械、附件、近战、投掷物、防具、僵尸变种、补给箱方块等完整末日装备生态：

### 4 个创意标签页

| 标签 | 说明 | 图标 |
|------|------|------|
| CD 医疗 | 医疗物品与急救设备 | 急救包 |
| CD 战斗 | 枪械/近战/弹药/手雷/附件 | AK47 |
| CD 装备 | 防具/服装/战术装备 | 防弹衣 |
| CD 方块 | 医疗补给箱/弹药箱 | 医疗补给箱 |

### 医疗系统（8 物品 + 5 自定义效果）

| 物品 | 用途 |
|------|------|
| 绷带 | 恢复生命值，止血 |
| 急救包 | 大幅恢复生命值，移除流血效果 |
| 肾上腺素 | 瞬时回复 + 速度/力量，移除疼痛与骨折 |
| 止痛药 | 抑制疼痛（止痛效果） |
| 止血带 | 停止流血（移除 BLEEDING） |
| 生理盐水袋 | 恢复生命值 + 水分 |
| 夹板 | 移除骨折（BROKEN_BONE） |
| 手术剪刀 | 医疗工具，右键快速使用绷带 |

**CDEffects** 5 种自定义效果：BLEEDING 流血、BROKEN_BONE 骨折、ADRENALINE_RUSH 肾上腺素、PAIN_SUPPRESSION 止痛、INFECTION_SEVERE 重度感染。

### 枪械系统（8 枪 + 7 弹 + 13 附件）

| 类别 | 内容 |
|------|------|
| **枪械** | AK47 / M4A1 / MP5 / M1014（霰弹）/ Desert Eagle / Glock17 / Barrett M82（.50 反器材）/ AWM（.338 狙击） |
| **弹药** | 5.56×45 / 7.62×39 / 9×19 / .45 ACP / 12 号霰弹 / .50 BMG / .338 Lapua |
| **瞄准镜** | 红点 / EOTECH 全息 / ACOG 4× / 8× 狙击镜 |
| **握把** | 垂直握把 / 转角握把 / 两脚架（反器材专用） |
| **枪管** | 消音器 / 补偿器 / 加长枪管（+伤害+射程） |
| **弹匣** | 标准 / 扩容（+50%）/ 弹鼓（+150%） |

所有物品注册名前缀 `cd_`（如 `cd_ak47`, `cd_ballistic_helmet`）。

### 近战武器

| 物品 | 效果 |
|------|------|
| 战斗刀 | 15% 概率造成流血 |
| 博伊刀 | 25% 流血 + 10% 骨折 |
| 撬棍 | 高伤害慢攻速，右键 10% 概率直接破坏方块 |

### 投掷物系统

| 物品 | 效果 |
|------|------|
| 破片手雷 | 4F 范围爆炸 |
| 闪光弹 | 半径 15 格失明 5 秒 + 缓慢 3 秒 |
| 燃烧弹（莫洛托夫） | 2.5F 爆炸 + 5×5 区域随机点火 |

### 防具系统（自定义材料 CDArmorMaterial）

| 物品 | 防御 | 稀有度 |
|------|------|--------|
| 防弹头盔 | 头 3 | UNCOMMON |
| 防弹衣（插板） | 胸 8 | RARE |
| 战术背心 | 胸 6 | UNCOMMON |
| 作战靴 | 靴 3 | UNCOMMON |

### 僵尸变种实体

| 实体 | 血量 | 攻击 | 特殊 |
|------|------|------|------|
| 军人僵尸 cd_soldier_zombie | 35 | 6 + 甲 5 | 铁剑/斧装备 + 每分钟 8 格内玩家流血光环 |
| 科学家僵尸 cd_scientist_zombie | 25 | 4 | 受伤 30% 毒反伤 + 白衣外套 + 死后云雾粒子 |
| 平民僵尸 cd_civilian_zombie | 20 | 3 | 弱化版，经验奖励 3 |

### 方块系统

| 方块 | 行为 |
|------|------|
| 医疗补给箱 cd_medical_supply_crate | 右键随机获得医疗物品（60%绷带/25%急救包/15%其他），60% 保留可重复开箱 |
| 弹药箱 cd_ammo_crate | 右键获得 1-3 种随机弹药 × 8-32 发，60% 保留 |

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

### 镐子随机能力系统（v2.10.0.b37 新增）

合成镐子时有概率获得特殊能力，可叠加多个，Tooltip 显示 ✦ 标记：

| 能力 | 概率 | 颜色 | 效果 |
|------|------|------|------|
| 黑曜石破坏者 | 15% | 紫色 | 左键黑曜石/哭泣黑曜石直接破坏+掉落物，任何品质镐子均可，消耗 2 耐久 |
| 3×3 范围挖掘 | 10% | 青色 | 破坏方块时以玩家面向平面为中心，3×3 同种方块连锁破坏 |
| 5×5 范围挖掘 | 5% | 金色 | 同上 5×5 范围（5×5 优先于 3×3），消耗耐久 |

- NBT bitmask 存储（`qlm_pickaxe_abilities.flags`），一个 int 存所有能力 flag
- 范围挖掘根据 `getDirection()` + `getXRot()` 自动判断平面（水平/垂直）
- 防递归 ThreadLocal，仅破坏同种方块

### 9 层高楼建筑系统（v2.10.0.b38 新增）

世界生成时 20% 概率生成 9 层高楼（其余 80% 为小屋/瞭望塔/废墟）：

**建筑规格**：13×9 外部尺寸，36 格高（9 层 × 4 格/层）

**每层布局**（5 个房间 + 十字走廊）：

| 房间 | 位置 | 尺寸 | 说明 |
|------|------|------|------|
| 房间 0 | 左前 | 3×3 | 奖励箱位置 0 |
| 房间 1 | 中前 | 3×3 | 奖励箱位置 1 |
| 房间 2 | 右前 | 3×3 | 奖励箱位置 2 |
| 房间 3 | 左后 | 5×3 | 奖励箱位置 3 |
| 房间 4 | 右后 | 5×3 | 奖励箱位置 4 |

- 走廊交叉 `x=4, z=4`，楼梯连接各层
- 每层 1 个奖励箱，5 个房间位置按楼层循环
- 随机裂纹石砖装饰 + 窗户 + 火把照明

**其他模组物品注入**（15% 概率）：

每个奖励箱 15% 概率使用 `other_mod_building` loot 表，通过 loot 修改器动态扫描 29 个模组命名空间：

| 类别 | 扫描的模组 |
|------|-----------|
| 武器/弹药 | TaCZ（tacz）— 手枪/步枪/霰弹枪/狙击枪/弹药 |
| 近战/防具 | SpartanWeaponry/SpartanShields — 长剑/刀/矛/斧/盾 |
| 科技 | Create/Mekanism/Botania/BloodMagic/IE/Thermal/Quark |
| 存储 | IronChest/StorageDrawers/RefinedStorage/AE2 |
| 其他 | Environmental/FarmersDelight/Forestry/PneumaticCraft/EnderIO/CofHCore/Patchouli/KubeJS/Oculus/Embeddium 等 |

**建筑不重复**：使用 `ConcurrentHashMap<Long>` 记录已生成区块坐标，已生成的区块不会再生成新建筑。

**保底武器**：所有建筑奖励箱保底一把武器 + 弹药（独立 pool，rolls=1 必出）：
- Pool 1（武器）：TaCZ 手枪/步枪/霰弹枪/狙击枪 + SpartanWeaponry 近战 + MC 弓弩
- Pool 2（弹药）：TaCZ 9mm/.45 ACP/5.56/7.62/12gauge + MC 箭

**高楼改进**：
- 螺旋楼梯：石砖阶梯旋转上升，不挡走廊通行
- 外墙门洞：底层四面中央开门（2 格宽 × 2 格高）
- 陆地检测：检查建筑区域 5 点（四角+中心）是否为陆地，非陆地取消生成

### 海底废墟系统（v2.10.0.b39 新增）

海洋区域 8% 概率在海底生成废墟结构：

**建筑规格**：7×7 × 5 格高，海泡菜/海灵灯笼装饰

**奖励箱**：2 个箱子，保底其他模组物品/武器：
- Pool 1（武器）：TaCZ 全系列枪械 + SpartanWeaponry 近战 + MC 三叉戟/弓弩
- Pool 2（弹药）：TaCZ 全口径弹药 + MC 箭
- Pool 3（额外物品）：金/铁/钻石/绿宝石/海晶碎片/鹦鹉螺壳/海洋之心/附魔书（含水下亲和分析）

### 击杀掉落随机品质装备系统（v2.10.0.b40 新增）

击杀除玩家、村民、铁傀儡外的所有生物，30% 概率掉落随机品质装备（武器/工具/盔甲）。

**10 级品质**：

| 品质 | 颜色 | 攻击力 | 生命上限 | 护甲 | 概率 |
|------|------|--------|----------|------|------|
| 劣质 | 灰色 | ×0.5 | 0 | 0 | 20% |
| 一般 | 白色 | ×1.0 +2 | 0 | 1 | 20% |
| 普通 | 绿色 | ×1.5 +4 | 1 | 2 | 15% |
| 精良 | 蓝色 | ×2.0 +7 | 2 | 3 | 12% |
| 高级 | 青色 | ×3.0 +12 | 3 | 5 | 10% |
| 稀有 | 粉色 | ×5.0 +20 | 5 | 8 | 8% |
| 神器 | 金色 | ×10 +50 | 10 | 15 | 6% |
| 传说 | 红色 | ×25 +200 | 20 | 25 | 4% |
| 史诗 | 暗红 | ×100 +2000 | 50 | 50 | 3% |
| 神话 | 暗紫 | 99999 | 100 | 100 | 2% |

**神话品质特殊属性**：
- 攻击力 99999（一刀秒杀）
- 无耐久消耗（Unbreakable）
- 可破坏基岩（左键基岩直接破坏+掉落）
- 虚空不掉生命值（穿戴任意神话品质盔甲时虚空伤害无效）

**盔甲生命上限机制**：
- 穿上品质盔甲 → 增加生命上限（不扣当前生命）
- 脱下品质盔甲 → 减少生命上限（如果当前生命超过新上限则减到新上限）

**镐子能力改进**：
- 合成镐子时仅随机赋予一个能力（不再叠加）
- 黑曜石破坏者 5% / 3×3 范围 3% / 5×5 范围 1%
- 品质越高的镐子（从击杀掉落获得）能力概率越高

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

`ModDependencyHandler` 自动管理 115 个依赖 mod JAR：

- **有开源仓库 + 0 资源**：源码替代，JAR 不释放（`FEATURE_REPLACED_KEYWORDS` 过滤）
- **有纹理/模型/语言**：保留原 JAR 释放（资源保护原则）
- **无开源仓库**：通过 libs 文件夹释放到 mods
- **启动时自动清理**：删除已被源码替代的旧版残留 JAR
- 自动去重、冲突检测、白名单管理

### 开源依赖鸣谢

本项目 `src/libs` 内嵌释放 115 个模组 JAR，其中 **107 个**拥有公开开源仓库。以下按字母序列出全部已核实的 GitHub 源码仓库，鸣谢原作者：

| 模组 | GitHub 仓库 |
|------|-----------|
| Advanced Skills: Remastered | https://github.com/iMoonDay/AdvancedSkills |
| Applied Energistics 2 | https://github.com/AppliedEnergistics/Applied-Energistics-2 |
| Architectury | https://github.com/architectury/architectury |
| Artifacts | https://github.com/ochotonida/artifacts |
| Bad Packets | https://github.com/badasintended/badpackets |
| Balm | https://github.com/TwelveIterations/Balm |
| Better Combat | https://github.com/ZsoltMolnarrr/BetterCombat |
| Blood Magic | https://github.com/WayofTime/BloodMagic |
| Blueprint | https://github.com/team-abnormals/blueprint |
| Bookshelf | https://github.com/Darkhax-Minecraft/Bookshelf |
| Botania | https://github.com/VazkiiMods/Botania |
| Cloth Config API | https://github.com/shedaniel/ClothConfig |
| CoFH Core | https://github.com/CoFH/CoFHCore |
| Collective | https://github.com/Serilum/Collective |
| CorgiLib | https://github.com/CorgiTaco/CorgiLib |
| Create | https://github.com/Creators-of-Create/Create |
| CreativeCore | https://github.com/CreativeMD/CreativeCore |
| CrashExploitFixer | https://github.com/DrexHD/CrashExploitFixer |
| Crash Assistant | https://github.com/KostromDan/Crash-Assistant |
| Curios API | https://github.com/TheIllusiveC4/Curios |
| Data Anchor | https://github.com/CorgiTaco/Data-Anchor |
| Drop the Meat | https://github.com/Moralle/DropTheMeat |
| Embeddium | https://github.com/FiniteReality/embeddium |
| Enhanced AI | https://github.com/Insane96/EnhancedAI |
| Enhanced Celestials | https://github.com/CorgiTaco/Enhanced-Celestials |
| Enchantment Descriptions | https://github.com/Darkhax-Minecraft/Enchantment-Descriptions |
| Environmental | https://github.com/team-abnormals/environmental |
| Ender IO | https://github.com/Team-EnderIO/EnderIO |
| Entity Model Features | https://github.com/Traben-0/Entity_Model_Features |
| Entity Texture Features | https://github.com/Traben-0/Entity_Texture_Features |
| FastBoot | https://github.com/GUN2RAS/FastBoot |
| Fast Workbench | https://github.com/Shadows-of-Fire/FastWorkbench |
| Ferrite Core | https://github.com/malte0811/FerriteCore |
| Footwork | https://github.com/Jackiecrazy/footwork |
| Forge Config API Port | https://github.com/Fuzss/forgeconfigapiport |
| Forge Config Screens | https://github.com/Fuzss/forgeconfigscreens |
| Forestry (Community Ed.) | https://github.com/thedarkcolour/ForestryMC |
| FTB Chunks | https://github.com/FTBTeam/FTB-Chunks |
| FTB Library | https://github.com/FTBTeam/FTB-Library |
| FTB Quests | https://github.com/FTBTeam/FTB-Quests |
| FTB Teams | https://github.com/FTBTeam/FTB-Teams |
| GeckoLib 4 | https://github.com/bernie-g/geckolib |
| GlitchCore | https://github.com/Glitchfiend/GlitchCore |
| GuideME | https://github.com/AppliedEnergistics/GuideME |
| IMBlocker | https://github.com/reserveword/IMBlocker |
| Immersive Engineering | https://github.com/BluSunrize/ImmersiveEngineering |
| Infectious (Contagion) | https://github.com/MC-Mods-Pete/Contagion |
| InsaneLib | https://github.com/Insane96/InsaneLib |
| Iron Chests | https://github.com/ThatGravyBoat/Ironchests |
| ItemPhysic | https://github.com/CreativeMD/ItemPhysic |
| itemphysicguns | https://github.com/lavafrai/itemphysicguns |
| Journeymap | https://github.com/TeamJM/journeymap |
| Jython (standalone) | https://github.com/jython/jython |
| Kleiders Custom Renderer | https://github.com/kleiders3010/KleidersCustomRenderer |
| Kotlin for Forge | https://github.com/thedarkcolour/KotlinForForge |
| KubeJS | https://github.com/KubeJS-Mods/KubeJS |
| KubeJS Additions | https://github.com/Hunter19823/kubejsadditions |
| LootJS | https://github.com/AlmostReliable/lootjs |
| maid useful task | https://github.com/zxy19/maid_useful_task |
| Mekanism | https://github.com/mekanism/Mekanism |
| ModernFix | https://github.com/embeddedt/ModernFix |
| Moonlight Library | https://github.com/MehVahdJukaar/Moonlight |
| mrqx's Slashblade Core | https://github.com/mrqx0195/mrqx-s-Slashblade-Core |
| mutil | https://github.com/mickelus/mutil |
| Oculus | https://github.com/Asek3/Oculus |
| Patchouli | https://github.com/VazkiiMods/Patchouli |
| Placebo | https://github.com/Shadows-of-Fire/Placebo |
| Player Animator | https://github.com/KosmX/minecraftPlayerAnimator |
| Player2 NPC | https://github.com/shakey2/Player2NPC |
| PlayerEngine | https://github.com/shakey2/PlayerEngine |
| PlayerRevive | https://github.com/CreativeMD/PlayerRevive |
| PneumaticCraft: Repressurized | https://github.com/TeamPneumatic/pnc-repressurized |
| Puzzles Lib | https://github.com/Fuzss/puzzleslib |
| Quark | https://github.com/VazkiiMods/Quark |
| Quark Oddities | https://github.com/VazkiiMods/Quark (附属) |
| Refined Storage | https://github.com/refinedmods/refinedstorage |
| REI (Roughly Enough Items) | https://github.com/shedaniel/RoughlyEnoughItems |
| Rhino | https://github.com/KubeJS-Mods/Rhino |
| 3d-Skin-Layers | https://github.com/tr7zw/3d-skin-layers |
| Simple Storage Network | https://github.com/Lothrazar/Storage-Network |
| Slash Blade: Resharped | https://github.com/0999312/SlashBlade_Resharped |
| Sodium Dynamic Lights | https://github.com/txnimc/SodiumDynamicLights |
| Sodium Options API | https://github.com/txnimc/SodiumOptionsAPI |
| Spartan Shields | https://github.com/ObliviousSpartan/SpartanShields |
| Spartan Weaponry | https://github.com/ObliviousSpartan/SpartanWeaponry |
| Spartan Weaponry Toolkit | https://github.com/KreloX/SpartanToolkit |
| Starlight | https://github.com/PaperMC/Starlight |
| Storage Drawers | https://github.com/jaquadro/StorageDrawers |
| Superb Warfare | https://github.com/Mercurows/SuperbWarfare |
| TACZ (Timeless & Classics Guns) | https://github.com/MCModderAnchor/TACZ |
| TaCZ JS | https://github.com/gizmo-ds/taczjs-mod |
| TAN+ | https://github.com/plus-rkwitt/TAN |
| tetra | https://github.com/17cupsofcoffee/tetra |
| Thermal Foundation | https://github.com/CoFH/ThermalFoundation |
| Thirst was Taken | https://github.com/ghen-git/Thirst-Mod |
| Tough As Nails | https://github.com/Glitchfiend/ToughAsNails |
| Touhou Little Maid | https://github.com/TartaricAcid/TouhouLittleMaid |
| Touhou Maid: Affection | https://github.com/yabo083/maid-affection |
| Traveler's Titles | https://github.com/YUNG-GANG/Travelers-Titles |
| True POWER | https://github.com/mrqx0195/true-power |
| Uncrafting Table | https://github.com/Pitan76/uncraftingtable |
| wthit | https://github.com/badasintended/wthit |
| YUNG's API | https://github.com/YUNG-GANG/YUNGs-API |
| Zeta | https://github.com/VazkiiMods/Zeta |
| Zombie Survival Kit | https://github.com/Scarasol/Zombie-Survival-Kit |

**无开源仓库（闭源/未验证，共 8 个）**：3D Armor、Fastload-Reforged、Zombie Island、Sona Survival 101、Yes Steve Model (ysm)、Dyairdrop、flib、Zombie Apocalypse Core (zac)。

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
├── ai/                            # AI 优化 + Player2 API + AI 物品注册表 + LLM 桥接
├── craftingdead/                  # Crafting Dead 装备系统（原创实现，参考开源架构）
│   ├── item/CDItems.java          # 42 个物品注册（医疗/枪械/弹药/附件/近战/手雷/防具）
│   ├── effect/CDEffects.java      # 5 种自定义状态（流血/骨折/肾上腺素/止痛/重度感染）
│   ├── entity/CDEntities.java     # 手雷实体 + 3 种僵尸变种
│   ├── block/CDBlocks.java        # 医疗补给箱/弹药箱 + BlockEntity
│   └── tab/CDCreativeTabs.java    # 4 个创意标签页
├── dayphase/                      # 难度阶段管理
├── dependency/                    # mod 自动释放/去重/冲突检测
├── feature/                       # 源码替代玩法（AlwaysEat/Clumps/AIImprovements 等）
├── horde/                         # 尸潮波次管理
├── item/                          # 武器品质 + 神话级物品
├── loot/                          # 建筑宝箱注入
├── mining/                        # 连锁挖矿
├── moon/                          # 月相系统
├── music/                         # 音乐系统
├── player/                        # 初始物资/血量UI/AI玩家/聊天指令/皮肤系统
├── restriction/                   # 封禁系统
├── script/                        # Python 三引擎脚本 + qlm API 桥接
├── scoreboard/                    # HUD 计分板
├── structure/                     # 随机建筑/废弃商店/9层高楼生成
└── zombie/                        # 僵尸进化
```
## Changelog

### v2.10.0.rewrite.beta.build.47.0 — 2026-08-12

**基于 117 开源仓库全面审查 + 核心库白名单 + GitHub 推送准备！**

- **核心库白名单 KEEP_ALWAYS_KEYWORDS 扩充至 30 个关键字**（原有 6 个 → 新增 24 个）：
  - **Kotlin / JS 语言运行时**：kotlinforforge、rhino、kubejs
  - **大型综合库**：moonlight（MehVahdJukaar 全系列）、bookshelf（Darkhax 全系列）、puzzleslib（Fuzss 全系列）、placebo（Shadows 全系列）、corgilib（CorgiTaco 全系列）、yungsapi（YUNG 结构全系列）、balm（Twelve Iterations 全系列）、blueprint（Team Abnormals 全系列）、zeta（Vazkii 全系列）、glitchcore（Glitchfiend 全系列）、cofh_core（CoFH/Thermal 全系列）
  - **单功能工具库**：geckolib（实体动画）、curios（饰品系统）、badpackets（网络传输）、mutil（Mickelus，tetra 等）、creativecore（CreativeMD）、insanelib（Insane96）
  - 效果：`detectAndResolveConflicts()` 中重复 mod 删除、冲突组禁用都不会再误删这些核心依赖
- **knownInternalJars 同步 src/libs 115+ JAR**：
  - 补齐 **`lostcitytacz.jar`**（Lost Cities 结构 × TaCZ 枪械小联动 mod，70KB 纯逻辑）
  - 修正 **`[旅行地图]`** 文件名：原 `journeymap-forge-1.20.1-5.10.3-forge.jar` → 改为匹配实际磁盘文件 `journeymap-1.20.1-5.10.3-forge.jar`
  - **`[Python] jython-standalone-2.7.3.jar`（45.1MB）不通过 libs/ 内嵌**：由 build.gradle 中 `implementation 'org.python:jython-standalone:2.7.3'` 直接解压 class 并过滤冲突包（org.w3c.dom.* / netscape.* / org.antlr.* / com.ibm.icu.*），避免 build.30 时期发现的 JPMS 模块冲突（`org.w3c.dom.html` 与 JDK `jdk.xml.dom` 模块冲突）
- **`.gitignore` 扩充（防大文件误传）**：
  - 新增构建目录：`out/`、`exports/`、`dist/`、`eclipse/`、`projects/`（FG 用户缓存）
  - 新增服务端目录：`world/`、`local/`、`server.properties`、`ops.json`、`whitelist.json`、`banned-*.json`
  - 新增二进制：`*.db`、`*.sqlite`、`*.db-journal`、`*.pak`、`*.pk3`、`*.pk4`
  - 保持 `src/libs/` 与 `*.jar` 忽略（JAR 不直接进 Git，避免单仓库超过 1GB）
- **`.gitattributes` 重写（行尾规范化 + Git LFS 大文件追踪）**：
  - 文本行尾：所有代码文件（*.java/gradle/md/toml/json/properties/py/sh）统一 `text eol=lf`；脚本（*.bat/cmd/ps1）保留 `crlf`
  - Git LFS 追踪：`*.png/jpg/jpeg/webp/psd/ogg/wav/flac/mp3/obj/glb/gltf/fbx/stl/ttf/otf/woff/woff2/jar/zip/exe/dll/so/dylib/mca/nbt/db/sqlite/pak/pk3/pk4` 全部 `binary filter=lfs diff=lfs merge=lfs -text`（安装 Git LFS 后生效，未安装时自动降级为普通二进制标记，无副作用）
- **开源清单审计（115 JAR × 117 条目对应）**：
  - 基于 `有开源仓库的模组清单.md` 与 `src/libs/*.jar` 文件大小排序，确认 60+ 个 mod（<1MB 纯逻辑/库）适合源码替代（但 qlmzombie 当前已实现 7 个源码替代：Clumps/FastFurnace/FastSuite/AI-Improvements/NonConflictKeys/DropTheMeat/FastWorkbench，其余保留原 JAR 以纹理优先）
  - 纹理/模型 mod（ysm 60MB、tacz 54MB、create 18MB 等 40+ 大型 mod）一律保留 JAR，不做源码替代（README 明确声明："含纹理/模型/语言资源的 mod 一律保留原 JAR 释放"）
- **版本号升级**：`2.10.0.rewrite.beta.build.46.0` → `2.10.0.rewrite.beta.build.47.0`
- **游戏公告双处更新**：QLMZombieMod.commonSetup() 追加 build.47 一行说明；mods.toml description 首行追加 b47 版本摘要

**修改文件：**
- `src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java` — KEEP_ALWAYS 扩充 / knownInternalJars 补齐 lostcitytacz、移除 jython(通过 Gradle implementation 替代) / 旅行地图文件名修正
- `.gitignore` — 构建/服务端/二进制忽略规则扩充
- `.gitattributes` — 行尾规范化 + Git LFS 大文件追踪配置
- `gradle.properties` — 版本号 build.47.0
- `src/main/java/com/qlm/zombie/QLMZombieMod.java` — MOD_VERSION + 游戏公告
- `src/main/resources/META-INF/mods.toml` — description 首行
- `README.md` — 版本号 + changelog
- `build.gradle` — 原排除逻辑不变(已正确排除 jython/graal/polyglot/[Python])

---

### v2.10.0.rewrite.beta.build.46.0 — 2026-08-12

**卡世界生成修复 + 奖励箱掉宝完善！**

- **Oculus 光影 vs Embeddium Mixin 严重冲突修复**：
  - `ModDependencyHandler` 新增 `BANNED_ALWAYS_KEYWORDS` 永久封禁列表（Oculus 光影无论客户端/服务端都不允许加载）
  - 新增 `cleanupBannedAlwaysJars()` 在启动时扫描 mods 目录删除/禁用 Oculus JAR（以前只在服务端跳过释放，客户端仍然加载引发 Taint）
  - 日志中原 MixinTaintDetector "Oculus modified embeddium internal class" 不再出现
- **zombiekit 空投箱 loot 表解析失败修复**：
  - 通过资源包优先级覆盖方式，在 `data/zombiekit/loot_tables/chests/` 创建两个同名 JSON
  - 原版不存在的 `zombiekit:flare_gun` → 替换为 `minecraft:firework_rocket`（信号弹功能等价）
  - 原版不存在的 `zombiekit:skiing_helmet` → 替换为 `minecraft:iron_helmet`（护甲功能等价）
  - 并添加 qlmzombie 战术物资（医疗/品质/弹药）让空投箱奖励更贴合玩法
- **网络版本检查全部禁用，消除启动时长瓶颈**：
  - 在 `ModDependencyHandler` 静态初始化块（类加载即执行）设置 11 个系统属性：
    - Forge：`forge.noverify=true` / `forge.updateChecker=false` / `forge.disableVersionCheck=true`
    - Placebo：`disablePatreonFeatures=true` / `patreon.wings=false` / `patreon.trails=false`
    - CorgiLib：`disableAnnouncements=true`（公告 SSL 握手失败）
    - Immersive Engineering：`contributors=false`（贡献者 HTTP 超时）
    - Moonlight：`hub=false`（Hub Fetcher URL 超时）
    - Java 全局：`defaultConnectTimeout=5000` / `defaultReadTimeout=5000`（从无限缩短到 5 秒）
  - 预计游戏启动时长从 87 秒降至 30 秒以内
- **TaCZ 奖励箱掉落完善 + 工作台同宝箱仅刷新 1 次**：
  - `building_tacz_spartan.json` 新增 `tacz:gun_bench` 稀有条目（权重 1，min/max 固定 1）
  - `QLMGlobalLootModifiers.BuildingWeaponLootModifier` 新增：
    - `isExcludedItem()` 排除 `gun_bench` / `weapon_bench` 关键字，防止动态扫描重复添加工作台
    - `doApply()` 工作台物品去重：同一宝箱内工作台类物品在每次 roll 后检查，已存在即跳过
    - 新增辅助方法 `isWorkbenchItem()`：识别 TaCZ 枪械工作台等物品
- **自动释放模组规则完善**：
  - `EXCLUDE_PATTERNS` 新增 `-fabric` / `fabric-`：任何 Fabric 版 JAR 都不会被释放（src/libs 中的 ForgeConfigAPIPort Fabric 版即被剔除）
  - `FEATURE_REPLACED_KEYWORDS` 新增 dropthemeat（源码已有替代）、fastworkbench（工作台性能优化）、fastbench
  - `CLIENT_SIDE_KEYWORDS` 新增 journeymap（旅行地图纯客户端）、itemphysic（物品物理掉落纯客户端）、crashassistant
  - 前缀提取与冲突检测逻辑不变
- **版本号升级**：`2.10.0.rewrite.beta.build.45.0` → `2.10.0.rewrite.beta.build.46.0`
- **更新游戏公告**：`QLMZombieMod.commonSetup()` 与 `mods.toml` description 均追加 b46 更新说明

**修改文件：**
- `src/main/java/com/qlm/zombie/dependency/ModDependencyHandler.java` — 永久封禁 / 网络检查 / 源码替代 / 客户端 mod 列表
- `src/main/java/com/qlm/zombie/loot/QLMGlobalLootModifiers.java` — 工作台去重逻辑 + isExcludedItem 排除 + isWorkbenchItem 判定
- `src/main/resources/data/zombiekit/loot_tables/chests/weaponairdrop2.json` — 新建（资源覆盖）
- `src/main/resources/data/zombiekit/loot_tables/chests/weaponairdrop3.json` — 新建（资源覆盖）
- `src/main/resources/data/qlmzombie/loot_modifiers/building_tacz_spartan.json` — 新增 tacz:gun_bench
- `src/main/resources/META-INF/mods.toml` — description 追加 b46
- `src/main/java/com/qlm/zombie/QLMZombieMod.java` — 版本号 build.46.0 + 游戏公告
- `gradle.properties` — 版本号 build.46.0
- `README.md` — 版本号 + 源码替代表 + changelog

---

### v2.10.0.rewrite.beta.build.44.0 — 2026-08-12

**日志与脚本兼容性修复！**

- **KubeJS 脚本迁移至 KubeJS 6 语法**：
  - `highrise_loot.js`：将 `java('net.minecraft.core.registries.BuiltInRegistries')` 改为 `Java.loadClass(...)`
  - 修正 `itemRegistry.iterator()` 直接引用为 `itemRegistry.iterator()` 方法调用
  - 添加异常捕获，注册表遍历失败时输出 `[loot]` 错误日志并优雅降级
  - 模组物品数量为 0 时提前返回，避免空数组遍历报错
- **外部模组日志分析**：
  - `thirst:add_loot_table` 为 ThirstWasTaken 模组内部问题（缺少 ToughAsNails/BrewinAndChewin/Jade 依赖），非本模组 bug
  - `zombiekit:flare_gun` 为 Zombie Island 模组内部物品引用错误，非本模组 bug
  - `scroll` / `spellbook` Curios slot 错误为 Touhou Little Maid 等附属模组 slot 未注册导致
  - Mixin `minVersion` 警告（crashexploitfixer/playerengine/player2npc）不影响运行
- **建筑 POI 数据不一致（POI data mismatch）**：由 serveradmin 高楼/海底废墟在已有区块上重复生成导致，非 qlmzombie 代码 bug
- **确认 qlmzombie 所有 loot_table/loot_modifiers JSON 语法正确**：`scanWeight/scanMinCount/scanMaxCount` 字段齐备，`enchant_randomly` 采用字符串列表（1.20.1 要求）

**修改文件：**
- `src/main/resources/META-INF/mods.toml` — description 追加 b44
- `src/main/java/com/qlm/zombie/QLMZombieMod.java` — 版本号 build.44.0 + 游戏公告
- `gradle.properties` — 版本号 build.44.0
- `README.md` — 版本号 + changelog
- `kubejs/server_scripts/highrise_loot.js` — 迁移 `java()` → `Java.loadClass()`

---

### v2.10.0.rewrite.beta.build.43.0 — 2026-08-11

**品质装备系统修复 + 攻击加成优化！**

- **修复盔甲生命上限/护甲加成不生效**：
  - 旧方案：`setBaseValue()` 直接修改属性 → 被 Forge 属性重计算覆盖
  - 新方案：使用 `AttributeInstance.addTransientModifier()` 持久化修饰符
  - 固定 UUID 修饰符：`QUALITY_HEALTH_MODIFIER` + `QUALITY_ARMOR_MODIFIER`
  - 装备变化时重新遍历所有盔甲槽，累计品质加成
- **合并伤害事件处理器**：将 `onLivingHurt` 中两个处理逻辑（虚空保护 + 攻击加成）合并为单一处理器
  - 神话武器：攻击力直接设为 99999（无视计算）
  - 普通品质：`newAmount = (float) (event.getAmount() * attackMultiplier + bonusAttack)`
- **玩家登录/重生自动重算品质属性**：监听 `PlayerLoggedInEvent` + `PlayerRespawnEvent`
- **修复 Java 17 语法兼容性**：将 pattern matching `instanceof X x` 改为 `instanceof X` + 强制转换
- **修复 EquipmentSlot 引用**：`EQUIPMENT_SLOT` / `MAIN_HAND` → `EQUIPMENT_SLOT` / `MAINHAND`

**修改文件：**
- `item/QualityEquipmentHandler.java` — 核心修复
- `QLMZombieMod.java` — 版本号 build.43.0 + 游戏公告
- `gradle.properties` — 版本号 build.43.0
- `mods.toml` — description 追加 b43

---

### v2.10.0.rewrite.beta.build.42.0 — 2026-08-11

**品质装备属性系统编译修复！**

- 修复 `Attribute.setBaseValue()` 被 Forge 重计算覆盖问题
- 改用 `AttributeInstance` + `AttributeModifier` 动态应用加成
- 修复 UUID 格式错误
- 添加玩家登录/重生事件处理
- 清理未使用的 import

**修改文件：**
- `item/QualityEquipmentHandler.java` — AttributeInstance + 玩家事件
- `QLMZombieMod.java` — 版本号 build.42.0
- `gradle.properties` — 版本号 build.42.0
- `mods.toml` — description 追加 b42

---

### v2.10.0.rewrite.beta.build.41.0 — 2026-08-10

**建筑浮空修复 + 楼梯通行改进！**

- **建筑防浮空**：
  - **高楼**：扫描 13×9 覆盖区域所有列的 `Heightmap.WORLD_SURFACE`，取最低点作为建筑 Y 坐标
  - **普通建筑**：小屋/瞭望塔/废墟生成前扫描覆盖区域，向下填充 5 格空气/水/岩浆为圆石地基
  - **高楼地基**：向下填充 5 格空隙/水/岩浆为石砖，确保不浮空
- **高楼梯子通行修复**：
  - 旧方案：4 块螺旋阶梯，不连续无法走上去
  - 新方案：2×2 梯子井（`x+5,z+4` 面东 + `x+6,z+3` 面西），从地板到天花板连续梯子
  - 天花板在梯子井位置（`x+5~6, z+3~4`）开 2×2 孔洞，玩家可自由上下
  - 水平分隔墙在梯子井位置留空，不阻挡通行

**修改文件：**
- `structure/HighriseBuildingGenerator.java` — `findMinGroundHeight()` + `flattenFoundation()` + 梯子井
- `structure/RandomBuildingGenerator.java` — `flattenBuildingFoundation()` 普通建筑地基
- `QLMZombieMod.java` — 版本号 build.41.0 + 游戏公告
- `gradle.properties` — 版本号 build.41.0
- `mods.toml` — description 追加 b41

---

### v2.10.0.rewrite.beta.build.40.0 — 2026-08-10

**击杀掉落随机品质装备：10 级品质，神话攻击力 99999，可破坏基岩！**

- **击杀掉落**：击杀除玩家/村民/铁傀儡外所有生物，30% 概率掉落随机品质装备
  - 随机类型：武器（剑/弓弩/三叉戟/斧）、工具（镐/斧/铲/锄/钓竿/剪刀）、盔甲（铁/钻石/下界合金/锁链/金/皮/海龟）
  - 随机品质：劣质(20%)/一般(20%)/普通(15%)/精良(12%)/高级(10%)/稀有(8%)/神器(6%)/传说(4%)/史诗(3%)/神话(2%)
- **神话品质特殊属性**：
  - 攻击力 99999（一刀秒杀）
  - 无耐久消耗（Unbreakable）
  - 可破坏基岩（左键基岩直接破坏+掉落）
  - 虚空不掉生命值（穿戴任意神话品质盔甲时虚空伤害无效）
- **盔甲生命上限机制**：
  - 穿上品质盔甲 → 增加生命上限（不扣当前生命）
  - 脱下品质盔甲 → 减少生命上限（如果当前生命超过新上限则减到新上限）
- **镐子能力改进**：
  - 合成镐子时仅随机赋予一个能力（不再叠加）
  - 黑曜石破坏者 5% / 3×3 范围 3% / 5×5 范围 1%
  - 品质越高的镐子（从击杀掉落获得）能力概率越高
- **Tooltip 显示**：品质行 + 攻击力加成 + 生命上限加成 + 护甲加成 + 神话特殊属性标记

**新增文件：**
- `item/EquipmentQuality.java` — 10 级品质枚举（NBT 存储 + 随机 roll）
- `item/RandomEquipmentDropHandler.java` — 击杀掉落处理器 + Tooltip 事件
- `item/QualityEquipmentHandler.java` — 基岩破坏 + 盔甲生命上限 + 虚空保护 + 攻击力加成

**修改文件：**
- `item/PickaxeAbility.java` — 黑曜石破坏 15%→5%，仅随机一个能力
- `QLMZombieMod.java` — 版本号 build.40.0 + 游戏公告
- `gradle.properties` — 版本号 build.40.0
- `mods.toml` — description 追加 b40
- `lang/zh_cn.json` — 10 级品质翻译键
- `lang/en_us.json` — 10 级品质翻译键

---

### v2.10.0.rewrite.beta.build.39.0 — 2026-08-10

**建筑大改进 + 海底废墟系统：保底武器、螺旋楼梯、门洞、陆地检测、海底废墟！**

- **保底武器**：所有建筑奖励箱（小屋/瞭望塔/废墟/高楼）保底一把武器 + 弹药
  - 独立 pool rolls=1 必出：TaCZ 手枪/步枪/霰弹枪/狙击枪 + SpartanWeaponry 近战 + MC 弓弩
  - 独立 pool rolls=1 必出：TaCZ 9mm/.45 ACP/5.56/7.62/12gauge + MC 箭
- **高楼改进**：
  - 螺旋楼梯：石砖阶梯旋转上升，不挡走廊通行，天花板开洞防撞头
  - 外墙门洞：底层四面中央开门（2 格宽 × 2 格高），可进出
  - 陆地检测：检查建筑区域 5 点（四角+中心）是否为陆地，非陆地取消生成
- **海底废墟系统**：海洋区域 8% 概率生成海底废墟
  - 7×7 × 5 格高，残破石砖墙 + 海泡菜/海灵灯笼装饰
  - 2 个奖励箱，保底其他模组物品/武器（含三叉戟/海洋之心/鹦鹉螺壳）
  - 海底废墟 loot 表含水下亲和分析附魔书

**新增文件：**
- `structure/OceanRuinGenerator.java` — 海底废墟生成器
- `data/qlmzombie/loot_tables/chests/ocean_ruin.json` — 海底废墟 loot 表（3 pools）

**修改文件：**
- `structure/HighriseBuildingGenerator.java` — 螺旋楼梯 + 门洞 + 陆地检测
- `structure/RandomBuildingGenerator.java` — 海底废墟 8% 生成概率
- `loot_tables/chests/random_building.json` — 新增保底武器 pool + 弹药 pool
- `loot_tables/chests/other_mod_building.json` — 新增保底武器 pool + 弹药 pool
- `QLMZombieMod.java` — 版本号 build.39.0 + 游戏公告
- `gradle.properties` — 版本号 build.39.0
- `mods.toml` — description 追加 b39

---

### v2.10.0.rewrite.beta.build.38.0 — 2026-08-10

**9 层高楼建筑系统：13×9 高楼 + 5 房间/层 + 每层奖励箱 + 15% 其他模组物品注入！**

新增 `HighriseBuildingGenerator`，世界生成时 20% 概率生成 9 层高楼（其余 80% 为小屋/瞭望塔/废墟）：

- **建筑规格**：13×9 外部尺寸，36 格高（9 层 × 4 格/层）
- **每层布局**：3 前排房间(3×3) + 2 后排房间(5×3) + 十字走廊 + 楼梯
- **奖励箱**：每层 1 个，5 个房间位置按楼层循环放置
- **建筑不重复**：`ConcurrentHashMap<Long>` 记录已生成区块坐标
- **15% 其他模组物品注入**：每个奖励箱 15% 概率使用 `other_mod_building` loot 表
  - 扫描 29 个模组命名空间：tacz/spartanweaponry/spartanshields/superbwarfare/slashblade/tetra/artifacts/environmental/farmersdelight/create/mekanism/botania/bloodmagic/immersiveengineering/forestry/thermal/quark/pneumaticcraft/enderio/ironchest/storagedrawers/refinedstorage/ae2/cofh_core/patchouli/kubejs/rhino/oculus/embeddium
  - 通过 `QLMGlobalLootModifiers.BuildingWeaponLootModifier` 动态注入武器/弹药/附件/防具/材料
  - `injectChance=0.15` + `scanWeight=5` + `scanMinCount=1` + `scanMaxCount=3`

**新增文件：**
- `structure/HighriseBuildingGenerator.java` — 9 层高楼生成器（地板/墙壁/窗户/楼梯/奖励箱）
- `data/qlmzombie/loot_tables/chests/other_mod_building.json` — 其他模组 loot 表
- `data/qlmzombie/loot_modifiers/other_mod_building_loot.json` — loot 修改器（80+ 物品条目 + 29 命名空间扫描）

**修改文件：**
- `structure/RandomBuildingGenerator.java` — 20% 高楼生成概率 + 区块坐标去重
- `data/qlmzombie/loot_modifiers/global_loot_modifiers.json` — 注册 `other_mod_building_loot`
- `QLMZombieMod.java` — 版本号 build.38.0 + 游戏公告
- `gradle.properties` — 版本号 build.38.0

---

### v2.10.0.rewrite.beta.build.37.0 — 2026-08-09

**镐子随机能力系统：合成镐子有概率获得黑曜石破坏/3×3/5×5 范围挖掘能力！**

合成镐子时独立 roll 每个能力（可叠加），NBT bitmask 存储，Tooltip 显示 ✦ 标记：

- **黑曜石破坏者（15%）**：左键黑曜石/哭泣黑曜石直接破坏 + 掉落物，任何品质镐子（木/石/铁/钻石/下界合金）均可使用，消耗 2 耐久
  - 监听 `PlayerInteractEvent.LeftClickBlock`，手动 `destroyBlock(pos, true, player)` 产生掉落 + cancel 事件
- **3×3 范围挖掘（10%）**：破坏方块时以玩家面向平面为中心，3×3 同种方块连锁破坏
  - 监听 `BlockEvent.BreakEvent`，根据 `getDirection()` + `getXRot()` 自动判断平面（水平/垂直）
  - 仅破坏同种方块，消耗耐久，防递归 ThreadLocal
- **5×5 范围挖掘（5%）**：同上 5×5 范围（5×5 优先于 3×3）
- **NBT 存储**：`qlm_pickaxe_abilities.flags` bitmask（bit 0=黑曜石, bit 1=3x3, bit 2=5x5）
- **语言文件**：zh_cn.json / en_us.json 添加 3 个能力翻译键

**新增文件：**
- `item/PickaxeAbility.java` — 镐子能力枚举（NBT 读写 + 随机 roll）
- `item/PickaxeAbilityHandler.java` — 事件处理器（黑曜石破坏 + 范围挖掘）

**修改文件：**
- `item/ItemAttributeHandler.java` — 合成时调用 `PickaxeAbility.rollAbilities()` + Tooltip 显示能力
- `assets/qlmzombie/lang/zh_cn.json` — 添加 3 个能力翻译键
- `assets/qlmzombie/lang/en_us.json` — 英文翻译
- `QLMZombieMod.java` — 版本号 build.37.0 + 游戏公告
- `gradle.properties` — 版本号 build.37.0

---

### v2.10.0.rewrite.beta.build.36.0 — 2026-08-09

**Crafting Dead 末日装备系统完整移植：8 枪 + 7 弹 + 13 附件 + 3 近战 + 3 投掷物 + 4 防具 + 3 僵尸变种 + 2 补给箱方块，共 42+ 新物品全接入 Forge 1.20.1 DeferredRegister！**

新增 `com.qlm.zombie.craftingdead` 子包，完全原创 Forge 1.20.1 代码实现（参考 Crafting Dead 开源架构，上游源码保留在 `crafting-dead-upstream/` 目录，含完整 LICENSE/NOTICE/HEADER 署名）：

- **注册中心 5 大 DeferredRegister**：
  - `CDEffects.MOB_EFFECTS` — 5 种自定义效果：BLEEDING 流血 / BROKEN_BONE 骨折 / ADRENALINE_RUSH 肾上腺素 / PAIN_SUPPRESSION 止痛 / INFECTION_SEVERE 重度感染
  - `CDItems.ITEMS` — 42 个 `RegistryObject<Item>`，全部 `cd_` 前缀（`cd_bandage`, `cd_ak47`, `cd_ballistic_helmet` 等）
  - `CDEntities.ENTITY_TYPES` — 通用手雷实体（Fragment/Flashbang/Molotov 三种子）+ 军人/科学家/平民 3 种僵尸变种
  - `CDBlocks.BLOCKS` + `BLOCK_ENTITIES` + `BLOCK_ITEM_REGISTER` — 医疗补给箱方块 + 弹药箱方块 + SupplyCrateBlockEntity（预留 GUI 扩展）
  - `CDCreativeTabs.TABS` — 4 个创意标签页（CD医疗/CD战斗/CD装备/CD方块），图标与 displayItems 完全配置
- **医疗系统（8 物品）**：绷带、急救包、肾上腺素针、止痛药、止血带、生理盐水袋、夹板、手术剪刀 — 各自右键互动 + 效果处理 + Tooltip
- **枪械系统（8 枪 + 7 弹 + 13 附件）**：
  - 接口/基类：IGun（6 种 SlotType）+ AbstractGunItem（抽象基类）+ IAttachmentItem
  - 枪械预设：AK47 / M4A1 / MP5 / M1014（霰弹）/ Desert Eagle / Glock17 / Barrett M82（.50 反器材）/ AWM（.338 狙击）
  - 弹药：7 种 AmmoType（5.56×45 / 7.62×39 / 9×19 / .45 ACP / 12号霰弹 / .50 BMG / .338 Lapua）
  - 附件：瞄准镜 4 种（红点/全息/ACOG/8x）、握把/两脚架 3 种、枪管 3 种（消音/补偿/加长）、弹匣 3 种（标准/扩容/弹鼓）
- **近战武器（3）**：战斗刀（15%流血）、博伊刀（25%流血+10%骨折）、撬棍（10%概率直接破坏方块）
- **投掷物系统（3 物品 + 1 通用实体）**：破片手雷（4F爆炸）/ 闪光弹（15格失明+缓慢）/ 燃烧弹（2.5F爆炸+5×5点火）
- **防具系统（4 护甲 + 自定义材料）**：CDArmorMaterial（耐久倍率20 / 头3胸8腿6靴3 / 韧性1.0）+ 防弹头盔 / 防弹衣 / 战术背心 / 作战靴
- **僵尸变种实体（3）**：
  - SoldierZombie — 血量35，攻击6，甲5，装备铁剑/斧，每分钟对 8 格内玩家施加流血光环
  - ScientistZombie — 血量25，白衣外套，受伤 30% 反毒，死后云雾粒子
  - CivilianZombie — 弱化基础版，血量20，攻击3，经验奖励3
- **方块系统（2 方块 + BlockEntity）**：
  - MedicalSupplyCrateBlock — 右键随机医疗物品（60%绷带/25%急救包/15%其他），60%概率保留刷新点
  - AmmoCrateBlock — 右键 1-3 种随机弹药 × 8-32 发，60%概率保留刷新点
- **QLMZombieMod 主类接入**：所有 DeferredRegister 调用 `.register(modEventBus)` + 三种僵尸变种 `EntityAttributeCreationEvent` 注册属性
- **上游源码保留（Fork+署名，非商业用途）**：`crafting-dead-upstream/` 目录含 1.18.x 原始源码（89 个 Java 文件 + 344 个资源）+ LICENSE.txt + HEADER.txt + NOTICE 署名声明
- **编译验证**：`./gradlew build` 通过，`compileJava` 无错误，JAR 重新打包成功

**新增文件（60+）：**
- `craftingdead/item/gun/` — IGun / AbstractGunItem / AmmoType / AmmoItem / IAttachmentItem + 4 种附件类
- `craftingdead/item/medical/` — 8 个医疗物品类
- `craftingdead/item/melee/` — CombatKnifeItem / BowieKnifeItem / CrowbarItem
- `craftingdead/item/grenade/` — FragmentGrenadeItem / FlashbangGrenadeItem / MolotovCocktailItem
- `craftingdead/item/armor/` — CDArmorMaterial + 4 个护甲类
- `craftingdead/entity/zombie/` — SoldierZombie / ScientistZombie / CivilianZombie
- `craftingdead/block/` — MedicalSupplyCrateBlock / AmmoCrateBlock / SupplyCrateBlockEntity
- `crafting-dead-upstream/` — 上游参考源码（含 LICENSE/NOTICE/HEADER 署名）

**修改文件：**
- `QLMZombieMod.java` — 版本号 build.36.0 + 游戏公告 + changelog
- `README.md` — 版本号 + 新增「Crafting Dead 末日生存装备系统」大章节（创意标签/医疗/枪械/近战/投掷物/防具/僵尸/方块）+ 项目结构更新
- `gradle.properties` — 版本号 build.36.0

---

### v2.10.0.rewrite.beta.build.35.0 — 2026-08-06

**严重崩溃修复：FakePlayer 饥饿掉血触发 Footwork/Mekanism capability NPE — 服务端不再崩溃！**

FakePlayer（AI 玩家）在饱食度归零后触发饥饿掉血，`hurt()` → `actuallyHurt()` → `ForgeHooks.onLivingHurt()` → Footwork `EntityHandler.pain()` 检查物品 capability → Mekanism `ItemCapabilityWrapper.getCapability()` 因 `capability` 为 null 抛出 `NullPointerException`，导致服务端 `Ticking entity` 崩溃。

- **根因**：`java.lang.NullPointerException: Cannot invoke "net.minecraftforge.common.capabilities.Capability.isRegistered()" because "capability" is null`
  - `mekanism.common.capabilities.ItemCapabilityWrapper.getCapability(ItemCapabilityWrapper.java:43)`
  - `jackiecrazy.footwork.handler.EntityHandler.pain(EntityHandler.java:119)` — Footwork 在 LivingHurtEvent 中对 FakePlayer 持有的物品调用 `getCapability()` 检查
  - Mekanism 的 `ItemCapabilityWrapper` 未注册对应 capability → NPE
  - 崩溃链：`FakePlayerEntity.aiStep():898` → `hurt():998` → `LivingEntity.actuallyHurt` → `ForgeHooks.onLivingHurt` → Footwork → Mekanism NPE
- **2 次崩溃报告确认同一根因**：`crash-2026-08-06_13.18.18`、`crash-2026-08-06_13.31.41`
- **修复方案**：
  - **(1) FakePlayer 不再饥饿掉血**：`aiStep()` 中 `foodLevel <= 0` 时不再调用 `this.hurt(starve, 1.0F)`，改为 `this.setFoodLevel(20)` 自动恢复饱食度，从源头切断触发 hurt 链路的可能
  - **(2) hurt 方法添加 try-catch 安全网**：`super.hurt(source, amount)` 包裹 try-catch 捕获 `NullPointerException` 返回 false，防止任何伤害源（怪物攻击/掉落/火焰等）触发 Footwork/Mekanism capability 检查时崩溃

**修改文件：**
- `src/main/java/com/qlm/zombie/entity/FakePlayerEntity.java` — 饥饿掉血改为恢复饱食度 + hurt 方法 try-catch 安全网
- `QLMZombieMod.java` — 版本号 build.35.0 + 游戏公告 + changelog
- `gradle.properties` — 版本号 build.35.0

---

### v2.10.0.rewrite.beta.build.34.0 — 2026-08-05

**严重崩溃修复：服务器区块加载死锁 — AI Bot 不再因服务器无响应而超时断开！**

AI Bot 连接 Forge 服务器后约 30 秒超时断开，根本原因是服务器在加载区块时发生死锁被 Watchdog 强制关闭。

- **根因**：`RandomBuildingGenerator.onChunkLoad()` 和 `AbandonedShopGenerator.onChunkLoad()` 在 `ChunkEvent.Load` 事件中调用 `level.getHeight()`，该方法内部调用 `level.getChunk()` 阻塞等待区块加载。但此时区块正在加载中（`protoChunkToFullChunk`），事件处理在区块注册到缓存之前触发 → 死锁 → 服务器 tick 超时 60 秒 → Watchdog 强制关闭 → AI Bot 收不到 keepalive 超时断开
- **3 次崩溃报告确认同一根因**：`crash-2026-08-05_18.56.44`（AbandonedShopGenerator:54）、`19.03.22`（RandomBuildingGenerator:47）、`19.08.42`（RandomBuildingGenerator:47）
- **修复方案**：
  - `level.getHeight(WORLD_SURFACE_WG)` → `chunk.getHeight(WORLD_SURFACE)`：使用事件中的区块对象自身的 heightmap，不经过 `getChunk()`
  - `level.getBlockState()` → `chunk.getBlockState()`：同理，直接访问区块数据
  - 建筑生成延迟到下一 tick：`level.getServer().execute(() -> generateBuilding(...))`，此时区块已完全注册到缓存，`setBlock()` 不会再死锁
  - 跳过 ProtoChunk：`if (!(event.getChunk() instanceof LevelChunk chunk)) return;`

- **Forge 握手修复**：ModData 不再发送 Acknowledgement（之前导致服务器日志 "Recieved unexpected index 0 in client reply" 警告），改为 `understood=true, data=null` 空响应
- **日志路径修复**：`config.json` 的 `file` 从 `logs/bot.log` 改为 `bot.log`，修复嵌套 `logs/logs/` 目录

**修改文件：**
- `src/main/java/com/qlm/zombie/structure/RandomBuildingGenerator.java` — 区块高度图 + 延迟 tick 生成
- `src/main/java/com/qlm/zombie/structure/AbandonedShopGenerator.java` — 同上死锁修复
- `node-bot/src/forge/ForgeHandshake.js` — ModData 空响应替代 Ack
- `node-bot/config.json` — 日志路径修复
- `QLMZombieMod.java` — 版本号 build.34.0 + 游戏公告
- `gradle.properties` — 版本号 build.34.0

---

### v2.10.0.rewrite.beta.build.33.0 — 2026-08-05

**新增：LLM 大模型接入（Node.js 外部 AI + Mod 内部 AI）+ AI 自动搭建 + 原地打转修复**

AI 玩家现在可以理解自然语言指令，自动搭建方块收集高处物品，不再原地打转。

- **Node.js 外部 AI (mineflayer)**：
  - `LLMBridge.js`：Ollama/OpenAI 兼容 API，自然语言 → 任务 JSON 数组
  - `!ai <自然语言>` 聊天指令，LLM 规划任务链交给 TaskSystem 串行执行
  - `buildPillarUp()`：自动搭建方块柱向上爬，收集高处物品/挖掘高处方块
  - `findBuildBlock()`：从背包按优先级查找可搭建方块
  - `collectItem()` / `mineBlockAt()`：集成高处自动搭建
  - 修复 `Navigator.js` 变量引用和事件清理
  - 修复 `FSMBrain.js` 寻路失败死循环，新增 `unstick()` 脱困机制（跳跃 + 侧向移动）

- **Mod 内部 AI (Forge Java)**：
  - `LLMBridge.java`：异步调用 LLM API，注入 AI 上下文（坐标/背包/血量），CompletableFuture 不阻塞游戏线程
  - 系统提示词支持 17 种任务类型，自动过滤非法类型
  - `TaskRunner` 新增任务链队列 `startTaskChain()`，多步任务串行执行
  - `AIPlayerChatHandler` 三级降级：Player2 API → 本地关键词解析 → LLM 翻译
  - `QLMConfig` 新增 `llm` 配置段（enableLlm/llmApiUrl/llmModel/llmTemperature/llmTimeout）

- **LLM 配置**（config.json / qlmzombie-common.toml）：
  ```json
  "llm": {
    "enabled": true,
    "provider": "ollama",
    "apiUrl": "http://localhost:11434/v1/chat/completions",
    "model": "qwen2.5-coder:1.5b",
    "temperature": 0.3,
    "timeout": 30000
  }
  ```

- **使用示例**：
  ```
  !ai 帮我建一座房子          → 规划: mine→craft→craft→build
  !ai 挖10个铁矿石           → 规划: mine iron_ore × 10
  对驯服AI说: 帮我收集5个橡木  → LLM 翻译 → 任务链执行
  ```

**新增文件：**
- `node-bot/src/llm/LLMBridge.js` — Node.js LLM 客户端
- `src/main/java/com/qlm/zombie/ai/LLMBridge.java` — Java LLM 客户端

**修改文件：**
- `node-bot/src/action/Actions.js` — buildPillarUp / findBuildBlock / collectItem / mineBlockAt
- `node-bot/src/brain/fsm/FSMBrain.js` — unstick() 脱困 + 寻路失败修复
- `node-bot/src/executor/Navigator.js` — 变量引用 + 事件清理修复
- `node-bot/src/index.js` — !ai 指令 + LLMBridge 初始化
- `node-bot/src/utils/config.js` — llm 配置默认值
- `node-bot/config.json` — llm 配置段
- `src/main/java/com/qlm/zombie/ai/task/TaskRunner.java` — 任务链队列 startTaskChain
- `src/main/java/com/qlm/zombie/config/QLMConfig.java` — llm 配置段
- `src/main/java/com/qlm/zombie/player/AIPlayerChatHandler.java` — LLM 翻译路径
- `QLMZombieMod.java` — 版本号 build.33.0 + 游戏公告
- `gradle.properties` — 版本号 build.33.0

---

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

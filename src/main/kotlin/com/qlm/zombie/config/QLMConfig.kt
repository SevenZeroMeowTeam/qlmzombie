package com.qlm.zombie.config

import net.minecraftforge.common.ForgeConfigSpec

object QLMConfig {
    private val builder = ForgeConfigSpec.Builder()

    // ==================== 基础设置 ====================
    private val enableThirstConfig = builder
        .comment("是否启用口渴系统（Thirst-Mod 整合）", "  true  - 开启口渴值管理，脱水会受伤甚至死亡", "  false - 关闭口渴值管理", "  默认: false")
        .define("enableThirst", false)

    private val enableDropTheMeatConfig = builder
        .comment("是否启用生物额外掉落肉类", "  true  - 击杀生物会额外掉落肉类物品", "  false - 仅保留原版掉落", "  默认: true")
        .define("enableDropTheMeat", true)

    private val dayPhaseLengthConfig = builder
        .comment("昼夜阶段长度（单位: tick，20 tick = 1 秒）", "  控制一个完整昼夜周期的时长", "  默认: 24000 (原版 20 分钟)", "  范围: 1 ~ " + Int.MAX_VALUE)
        .defineInRange("dayPhaseLength", 24000, 1, Int.MAX_VALUE)

    private val hordeIntervalConfig = builder
        .comment("僵尸潮触发间隔（单位: 游戏天数）", "  每隔多少天触发一次僵尸潮事件", "  默认: 14", "  范围: 1 ~ 365")
        .defineInRange("hordeInterval", 14, 1, 365)

    private val buildingSpawnChanceConfig = builder
        .comment("废弃建筑生成概率", "  控制随机建筑（废弃商店/高楼/海底遗迹/小屋）的生成密度", "  默认: 0.05 (5%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("buildingSpawnChance", 0.05, 0.0, 1.0)

    private val lootQualityChanceConfig = builder
        .comment("战利品品质加成概率", "  控制战利品箱中高品质物品的出现概率", "  默认: 0.3 (30%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("lootQualityChance", 0.3, 0.0, 1.0)

    private val apiPortConfig = builder
        .comment("Player2 MCP API 服务端口", "  客户端用于 AI 玩家通信的 HTTP 端口", "  默认: 18921", "  范围: 1024 ~ 65535")
        .defineInRange("apiPort", 18921, 1024, 65535)

    // ==================== AI 优化 ====================
    init { builder.push("ai") }

    @JvmField val ENABLE_AI_OPTIMIZATION = builder
        .comment("是否启用 AI 优化系统", "  true  - 启用增强 AI（更聪明的寻路/目标选择）", "  false - 使用原版 AI", "  默认: true")
        .define("enableAiOptimization", true)

    @JvmField val ENHANCED_MOB_AI = builder
        .comment("是否启用增强型怪物 AI", "  true  - 怪物具备更复杂的攻击/躲避行为", "  false - 原版怪物 AI", "  默认: true")
        .define("enhancedMobAi", true)

    @JvmField val ZOMBIE_FOLLOW_RANGE_MULTIPLIER = builder
        .comment("僵尸追踪范围倍率", "  控制僵尸发现玩家的距离", "  默认: 1.0 (原版)", "  范围: 0.1 ~ 10.0")
        .defineInRange("zombieFollowRangeMultiplier", 1.0, 0.1, 10.0)

    @JvmField val ZOMBIE_SPEED_MULTIPLIER = builder
        .comment("僵尸移动速度倍率", "  控制僵尸的基础移动速度", "  默认: 1.0 (原版)", "  范围: 0.1 ~ 10.0")
        .defineInRange("zombieSpeedMultiplier", 1.0, 0.1, 10.0)

    @JvmField val ZOMBIE_BREAK_DOORS = builder
        .comment("僵尸是否可以破坏木门", "  默认: true")
        .define("zombieBreakDoors", true)

    @JvmField val ZOMBIE_BREAK_BLOCKS = builder
        .comment("僵尸是否可以破坏方块（追击玩家时）", "  默认: true")
        .define("zombieBreakBlocks", true)

    @JvmField val ZOMBIE_BREAK_INTERVAL = builder
        .comment("僵尸破坏方块的间隔（单位: tick）", "  数值越小破坏越快", "  默认: 100 (5 秒)", "  范围: 1 ~ 10000")
        .defineInRange("zombieBreakInterval", 100, 1, 10000)

    @JvmField val ZOMBIE_PLACE_BLOCKS = builder
        .comment("僵尸是否可以搭建方块（跨越障碍）", "  默认: true")
        .define("zombiePlaceBlocks", true)

    @JvmField val ZOMBIE_PLACE_INTERVAL = builder
        .comment("僵尸搭建方块的间隔（单位: tick）", "  数值越小搭建越快", "  默认: 200 (10 秒)", "  范围: 1 ~ 10000")
        .defineInRange("zombiePlaceInterval", 200, 1, 10000)

    @JvmField val AGGRESSIVE_TARGETING = builder
        .comment("是否启用激进目标锁定", "  true  - 僵尸主动追踪远处玩家", "  false - 仅原版视线锁定", "  默认: true")
        .define("aggressiveTargeting", true)

    @JvmField val AGGRESSIVE_TARGETING_RADIUS = builder
        .comment("激进目标锁定的半径（单位: 格）", "  默认: 32", "  范围: 1 ~ 128")
        .defineInRange("aggressiveTargetingRadius", 32, 1, 128)

    @JvmField val MOB_AI_SPEED_BONUS_PER_PHASE = builder
        .comment("每个难度阶段怪物的速度加成", "  阶段越高怪物越快", "  默认: 0.1 (每阶段 +10%)", "  范围: 0.0 ~ 10.0")
        .defineInRange("mobAiSpeedBonusPerPhase", 0.1, 0.0, 10.0)

    @JvmField val MOB_AI_ARMOR_PER_PHASE = builder
        .comment("每个难度阶段怪物的护甲加成", "  默认: 2.0 (每阶段 +2 护甲)", "  范围: 0.0 ~ 100.0")
        .defineInRange("mobAiArmorPerPhase", 2.0, 0.0, 100.0)

    @JvmField val MOB_AI_KNOCKBACK_RESISTANCE = builder
        .comment("怪物击退抗性", "  0.0 = 完全可被击退，1.0 = 完全免疫击退", "  默认: 0.1", "  范围: 0.0 ~ 1.0")
        .defineInRange("mobAiKnockbackResistance", 0.1, 0.0, 1.0)

    // ---------------- 特殊僵尸 ----------------
    @JvmField val SUICIDE_ZOMBIE_CHANCE = builder
        .comment("自爆僵尸生成概率", "  靠近玩家自爆，破坏地形", "  默认: 0.05 (5%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("suicideZombieChance", 0.05, 0.0, 1.0)

    @JvmField val BARREL_ZOMBIE_CHANCE = builder
        .comment("木桶僵尸生成概率", "  死亡时掉落额外战利品", "  默认: 0.05 (5%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("barrelZombieChance", 0.05, 0.0, 1.0)

    @JvmField val TNT_ZOMBIE_CHANCE = builder
        .comment("投手僵尸生成概率（投掷点燃的 TNT）", "  远程投掷 TNT 攻击玩家", "  默认: 0.03 (3%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("tntZombieChance", 0.03, 0.0, 1.0)

    @JvmField val POTION_ZOMBIE_CHANCE = builder
        .comment("剧毒/吐息僵尸生成概率", "  喷洒药水/剧毒攻击", "  默认: 0.05 (5%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("potionZombieChance", 0.05, 0.0, 1.0)

    @JvmField val SUMMONER_ZOMBIE_CHANCE = builder
        .comment("召唤师僵尸生成概率", "  召唤小僵尸协助战斗", "  默认: 0.02 (2%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("summonerZombieChance", 0.02, 0.0, 1.0)

    @JvmField val SUMMONER_ZOMBIE_MAX_SUMMONS = builder
        .comment("召唤师僵尸最多可召唤的小僵尸数量", "  默认: 3", "  范围: 1 ~ 20")
        .defineInRange("summonerZombieMaxSummons", 3, 1, 20)

    @JvmField val SUMMONER_ZOMBIE_SPAWN_INTERVAL = builder
        .comment("召唤师僵尸的召唤间隔（单位: tick）", "  默认: 200 (10 秒)", "  范围: 1 ~ 10000")
        .defineInRange("summonerZombieSpawnInterval", 200, 1, 10000)

    @JvmField val ZOMBIE_MAX_POPULATION = builder
        .comment("主世界僵尸数量上限（人口控制）", "  超过上限会按离玩家由远到近自动移除超限僵尸", "  太高（如 500+）会导致服务器 tick 卡顿（Can't keep up），", "  太低会失去末日氛围", "  默认: 400", "  范围: 50 ~ 5000")
        .defineInRange("zombieMaxPopulation", 400, 50, 5000)

    // ---------------- 骷髅 ----------------
    @JvmField val SKELETON_PERFECT_SHOT_CHANCE = builder
        .comment("骷髅完美射击概率", "  完美射击具有更高命中率", "  默认: 0.1 (10%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("skeletonPerfectShotChance", 0.1, 0.0, 1.0)

    @JvmField val SKELETON_STRAFE = builder
        .comment("骷髅是否启用横移闪避", "  true  - 攻击时左右移动躲避", "  默认: true")
        .define("skeletonStrafe", true)

    @JvmField val SKELETON_ACCURACY_BOOST = builder
        .comment("骷髅射击精准度加成", "  数值越大命中率越高", "  默认: 0.5", "  范围: 0.0 ~ 10.0")
        .defineInRange("skeletonAccuracyBoost", 0.5, 0.0, 10.0)

    @JvmField val SKELETON_MOD_WEAPON_CHANCE = builder
        .comment("骷髅使用模组武器的概率", "  骷髅可能装备 Crafting Dead 武器", "  默认: 0.1 (10%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("skeletonModWeaponChance", 0.1, 0.0, 1.0)

    @JvmField val SKELETON_INFINITE_ARROWS = builder
        .comment("骷髅是否拥有无限箭矢", "  true  - 骷髅射箭不消耗箭矢", "  默认: true")
        .define("skeletonInfiniteArrows", true)

    // ---------------- 村民 ----------------
    @JvmField val VILLAGER_FLEE_RADIUS = builder
        .comment("村民逃离敌对生物的半径（单位: 格）", "  默认: 8", "  范围: 1 ~ 64")
        .defineInRange("villagerFleeRadius", 8, 1, 64)

    @JvmField val VILLAGER_PANIC_BOOST = builder
        .comment("村民恐慌时的速度加成", "  被攻击时临时加速", "  默认: 0.5", "  范围: 0.0 ~ 10.0")
        .defineInRange("villagerPanicBoost", 0.5, 0.0, 10.0)

    @JvmField val VILLAGER_GUARDIAN_CHANCE = builder
        .comment("村民变守卫的概率", "  守卫村民会主动攻击敌对生物", "  默认: 0.1 (10%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("villagerGuardianChance", 0.1, 0.0, 1.0)

    @JvmField val VILLAGER_GUARDIAN_MOD_WEAPON_CHANCE = builder
        .comment("守卫村民使用模组武器的概率", "  默认: 0.2 (20%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("villagerGuardianModWeaponChance", 0.2, 0.0, 1.0)

    // ---------------- AI 算法 ----------------
    @JvmField val AI_ALGORITHM_ENABLED = builder
        .comment("是否启用 AI 算法系统", "  true  - 怪物会学习并适应玩家行为", "  默认: true")
        .define("algorithmEnabled", true)

    @JvmField val AI_DECISION_INTERVAL = builder
        .comment("AI 决策间隔（单位: tick）", "  数值越小决策越频繁", "  默认: 20 (1 秒)", "  范围: 1 ~ 200")
        .defineInRange("decisionInterval", 20, 1, 200)

    @JvmField val AI_QL_LEARNING_RATE = builder
        .comment("Q-Learning 学习率", "  控制怪物学习新行为的速度", "  默认: 0.1", "  范围: 0.0 ~ 1.0")
        .defineInRange("qlLearningRate", 0.1, 0.0, 1.0)

    @JvmField val AI_QL_DISCOUNT_FACTOR = builder
        .comment("Q-Learning 折扣因子", "  控制怪物对未来奖励的重视程度", "  默认: 0.9", "  范围: 0.0 ~ 1.0")
        .defineInRange("qlDiscountFactor", 0.9, 0.0, 1.0)

    @JvmField val AI_QL_EXPLORATION_RATE = builder
        .comment("Q-Learning 探索率", "  控制怪物尝试新行为的概率", "  默认: 0.1 (10%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("qlExplorationRate", 0.1, 0.0, 1.0)

    @JvmField val AI_ALGORITHM_DEFAULT_MODE = builder
        .comment("AI 算法默认模式", "  可选: AUTO / MANUAL / DISABLED", "  默认: AUTO")
        .define("algorithmDefaultMode", "AUTO")

    init { builder.pop() }

    // ==================== LLM 大语言模型 ====================
    init { builder.push("llm") }

    @JvmField val ENABLE_LLM = builder
        .comment("是否启用 LLM 集成", "  true  - AI 玩家使用大语言模型生成对话", "  false - 使用预设回复", "  默认: false")
        .define("enableLlm", false)

    @JvmField val LLM_API_URL = builder
        .comment("LLM API 接口地址", "  支持 OpenAI 兼容接口（如 Ollama / vLLM / LM Studio）", "  默认: http://localhost:11434/v1/chat/completions (Ollama)")
        .define("apiUrl", "http://localhost:11434/v1/chat/completions")

    @JvmField val LLM_API_KEY = builder
        .comment("LLM API 密钥", "  本地部署可留空", "  默认: (空)")
        .define("apiKey", "")

    @JvmField val LLM_MODEL = builder
        .comment("使用的模型名称", "  示例: qwen2.5:7b / gpt-4o-mini / llama3.1:8b", "  默认: qwen2.5:7b")
        .define("model", "qwen2.5:7b")

    @JvmField val LLM_TEMPERATURE = builder
        .comment("生成温度（创造性）", "  0.0 = 严谨确定，2.0 = 极具创意", "  默认: 0.7", "  范围: 0.0 ~ 2.0")
        .defineInRange("temperature", 0.7, 0.0, 2.0)

    @JvmField val LLM_TIMEOUT = builder
        .comment("请求超时时间（单位: 毫秒）", "  默认: 30000 (30 秒)", "  范围: 1000 ~ 120000")
        .defineInRange("timeout", 30000, 1000, 120000)

    init { builder.pop() }

    // ==================== 昼夜阶段阈值 ====================
    init { builder.push("dayPhase") }

    @JvmField val PEACEFUL_DAYS = builder
        .comment("和平阶段持续天数（前 N 天不生成敌对生物）", "  默认: 24", "  范围: 0 ~ 365")
        .defineInRange("peacefulDays", 24, 0, 365)

    @JvmField val NORMAL_DAYS = builder
        .comment("简单→普通阶段的切换天数", "  默认: 49", "  范围: 0 ~ 365")
        .defineInRange("normalDays", 49, 0, 365)

    @JvmField val HARD_DAYS = builder
        .comment("普通→困难阶段的切换天数", "  默认: 99", "  范围: 0 ~ 365")
        .defineInRange("hardDays", 99, 0, 365)

    @JvmField val EXTREME_DAYS = builder
        .comment("困难→极限阶段的切换天数", "  默认: 149", "  范围: 0 ~ 365")
        .defineInRange("extremeDays", 149, 0, 365)

    init { builder.pop() }

    // ==================== Player2 MCP ====================
    init { builder.push("player2") }

    @JvmField val ENABLE_PLAYER2_MCP = builder
        .comment("是否启用 Player2 MCP 集成", "  允许外部 AI 通过 MCP 协议控制游戏", "  默认: false")
        .define("enableMcp", false)

    @JvmField val PLAYER2_MCP_URL = builder
        .comment("MCP 服务端点 URL", "  默认: http://localhost:18921/mcp")
        .define("mcpUrl", "http://localhost:18921/mcp")

    @JvmField val PLAYER2_MCP_API_KEY = builder
        .comment("MCP API 密钥（可选）", "  默认: (空)")
        .define("mcpApiKey", "")

    @JvmField val PLAYER2_MCP_TIMEOUT = builder
        .comment("MCP 请求超时（单位: 毫秒）", "  默认: 30000 (30 秒)", "  范围: 1000 ~ 120000")
        .defineInRange("mcpTimeout", 30000, 1000, 120000)

    init { builder.pop() }

    // ==================== 连锁挖掘 ====================
    init { builder.push("mining") }

    @JvmField val ENABLE_CHAIN_MINING = builder
        .comment("是否启用连锁挖矿", "  true  - 挖矿时连锁破坏相同方块", "  默认: true")
        .define("enableChainMining", true)

    @JvmField val ENABLE_TREE_CHOP = builder
        .comment("是否启用一键砍树", "  true  - 砍伐原木时自动破坏整棵树", "  默认: true")
        .define("enableTreeChop", true)

    @JvmField val CHAIN_PLANKS_ENABLED = builder
        .comment("连锁是否包含木板", "  默认: true")
        .define("chainPlanksEnabled", true)

    @JvmField val CHAIN_FENCES_ENABLED = builder
        .comment("连锁是否包含栅栏", "  默认: true")
        .define("chainFencesEnabled", true)

    @JvmField val TREE_CHOP_MAX_BLOCKS = builder
        .comment("砍树最大破坏方块数", "  默认: 256", "  范围: 1 ~ 4096")
        .defineInRange("treeChopMaxBlocks", 256, 1, 4096)

    @JvmField val TREE_CHOP_INCLUDE_LEAVES = builder
        .comment("砍树时是否一并破坏树叶", "  默认: true")
        .define("treeChopIncludeLeaves", true)

    @JvmField val CHAIN_MINING_MAX_BLOCKS = builder
        .comment("连锁挖矿最大破坏方块数", "  默认: 64", "  范围: 1 ~ 4096")
        .defineInRange("chainMiningMaxBlocks", 64, 1, 4096)

    @JvmField val CHAIN_MINING_RADIUS = builder
        .comment("连锁挖矿搜索半径（单位: 格）", "  默认: 8", "  范围: 1 ~ 64")
        .defineInRange("chainMiningRadius", 8, 1, 64)

    @JvmField val CHAIN_MINING_PICKAXE_ENABLED = builder
        .comment("镐子是否启用连锁挖矿", "  默认: true")
        .define("chainMiningPickaxeEnabled", true)

    @JvmField val CHAIN_MINING_SHOVEL_ENABLED = builder
        .comment("铲子是否启用连锁挖矿", "  默认: true")
        .define("chainMiningShovelEnabled", true)

    init { builder.pop() }

    // ==================== AI 玩家生成 ====================
    init { builder.push("aiPlayer") }

    @JvmField val ENABLE_AI_PLAYER_SPAWN = builder
        .comment("是否启用 AI 玩家自动生成", "  默认: true")
        .define("enableSpawn", true)

    @JvmField val AI_PLAYER_SPAWN_INTERVAL = builder
        .comment("AI 玩家生成检查间隔（单位: tick）", "  默认: 1200 (60 秒)", "  范围: 20 ~ 72000")
        .defineInRange("spawnInterval", 1200, 20, 72000)

    @JvmField val AI_PLAYER_MAX_COUNT = builder
        .comment("AI 玩家最大数量", "  默认: 5", "  范围: 0 ~ 50")
        .defineInRange("maxCount", 5, 0, 50)

    @JvmField val AI_PLAYER_SPAWN_CHANCE = builder
        .comment("AI 玩家生成概率", "  默认: 0.3 (30%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("spawnChance", 0.3, 0.0, 1.0)

    @JvmField val AI_PLAYER_SPAWN_RADIUS = builder
        .comment("AI 玩家生成半径（单位: 格）", "  默认: 48", "  范围: 8 ~ 256")
        .defineInRange("spawnRadius", 48, 8, 256)

    init { builder.pop() }

    // ==================== 掉落物控制 ====================
    init { builder.push("drops") }

    @JvmField val HOSTILE_DROP_CHANCE = builder
        .comment("敌对生物掉落物保留概率", "  击杀敌对生物时，每个掉落物按该概率保留（减少地面物品堆积导致的卡顿）", "  1.0 = 全部保留（原版）", "  默认: 0.6 (60%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("hostileDropChance", 0.6, 0.0, 1.0)

    @JvmField val HOSTILE_GUNPOWDER_CHANCE = builder
        .comment("击杀敌对生物掉落火药概率", "  苦力怕已被封禁，火药只能靠击杀敌对生物获取（用于 TaCZ 弹药/火药相关合成）", "  概率受控，不会满地图都是", "  默认: 0.12 (12%)", "  范围: 0.0 ~ 0.5")
        .defineInRange("hostileGunpowderChance", 0.12, 0.0, 0.5)

    @JvmField val HOSTILE_GUNPOWDER_LOOTING_BONUS = builder
        .comment("每级抢夺附魔增加的火药掉落概率", "  默认: 0.03 (每级+3%，最多不超过 50%)", "  范围: 0.0 ~ 0.2")
        .defineInRange("hostileGunpowderLootingBonus", 0.03, 0.0, 0.2)

    @JvmField val DROP_CLEANUP_ENABLED = builder
        .comment("是否启用掉落物定期清理", "  true  - 每隔一段时间自动清理地面上的部分陈旧掉落物", "  默认: true")
        .define("dropCleanupEnabled", true)

    @JvmField val DROP_CLEANUP_INTERVAL = builder
        .comment("掉落物清理间隔（单位: tick，20 tick = 1 秒）", "  默认: 1200 (1 分钟)", "  范围: 20 ~ 72000")
        .defineInRange("dropCleanupInterval", 1200, 20, 72000)

    @JvmField val DROP_CLEANUP_MIN_AGE = builder
        .comment("掉落物最小存在时间（单位: tick，小于该时间的掉落物不会被清理）", "  默认: 600 (30 秒)", "  范围: 0 ~ 72000")
        .defineInRange("dropCleanupMinAge", 600, 0, 72000)

    @JvmField val DROP_CLEANUP_CHANCE = builder
        .comment("每次清理时单个陈旧掉落物被清理的概率", "  默认: 0.5 (50%)", "  范围: 0.0 ~ 1.0")
        .defineInRange("dropCleanupChance", 0.5, 0.0, 1.0)

    init { builder.pop() }

    // ==================== 血量条 UI ====================
    init { builder.push("ui") }

    @JvmField val HIDE_VANILLA_HEALTH = builder
        .comment("是否隐藏原版心形血量条", "  true  - 隐藏原版红心，使用自定义绿色血量条", "  false - 同时显示原版心形和自定义血量条", "  默认: false")
        .define("hideVanillaHealth", false)

    @JvmField val ENABLE_HEALTH_BAR = builder
        .comment("是否启用自定义血量条", "  默认: true")
        .define("enableHealthBar", true)

    @JvmField val HEALTH_BAR_WIDTH = builder
        .comment("血量条宽度（单位: 像素）", "  默认: 200", "  范围: 50 ~ 800")
        .defineInRange("healthBarWidth", 200, 50, 800)

    @JvmField val HEALTH_BAR_HEIGHT = builder
        .comment("血量条高度（单位: 像素）", "  默认: 10", "  范围: 2 ~ 50")
        .defineInRange("healthBarHeight", 10, 2, 50)

    @JvmField val HEALTH_BAR_POSITION_Y = builder
        .comment("血量条 Y 轴位置（距屏幕底部偏移）", "  默认: 30", "  范围: 0 ~ 1080")
        .defineInRange("healthBarPositionY", 30, 0, 1080)

    init { builder.pop() }

    val SPEC: ForgeConfigSpec = builder.build()

    var enableThirst: Boolean
        get() = enableThirstConfig.get()
        set(value) { enableThirstConfig.set(value) }

    var enableDropTheMeat: Boolean
        get() = enableDropTheMeatConfig.get()
        set(value) { enableDropTheMeatConfig.set(value) }

    var dayPhaseLength: Int
        get() = dayPhaseLengthConfig.get()
        set(value) { dayPhaseLengthConfig.set(value) }

    var hordeInterval: Int
        get() = hordeIntervalConfig.get()
        set(value) { hordeIntervalConfig.set(value) }

    var buildingSpawnChance: Double
        get() = buildingSpawnChanceConfig.get()
        set(value) { buildingSpawnChanceConfig.set(value) }

    var lootQualityChance: Double
        get() = lootQualityChanceConfig.get()
        set(value) { lootQualityChanceConfig.set(value) }

    var apiPort: Int
        get() = apiPortConfig.get()
        set(value) { apiPortConfig.set(value) }
}

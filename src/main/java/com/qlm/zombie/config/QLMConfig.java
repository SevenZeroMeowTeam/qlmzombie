package com.qlm.zombie.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class QLMConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<Integer> PEACEFUL_DAYS;
    public static final ForgeConfigSpec.ConfigValue<Integer> NORMAL_DAYS;
    public static final ForgeConfigSpec.ConfigValue<Integer> HARD_DAYS;
    public static final ForgeConfigSpec.ConfigValue<Integer> EXTREME_DAYS;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DIFFICULTY_LOCK;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_DIFFICULTY_LOCK;

    public static final ForgeConfigSpec.ConfigValue<Integer> BLOOD_MOON_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<Double> LUCKY_MOON_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Double> HARVEST_MOON_CHANCE;

    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_AI_OPTIMIZATION;
    public static final ForgeConfigSpec.ConfigValue<Double> ZOMBIE_FOLLOW_RANGE_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<Double> ZOMBIE_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ZOMBIE_BREAK_DOORS;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ZOMBIE_BREAK_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<Integer> ZOMBIE_BREAK_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ZOMBIE_PLACE_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<Integer> ZOMBIE_PLACE_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<Double> SUICIDE_ZOMBIE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Double> BARREL_ZOMBIE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Double> TNT_ZOMBIE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Double> POTION_ZOMBIE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Double> SUMMONER_ZOMBIE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Integer> SUMMONER_ZOMBIE_MAX_SUMMONS;
    public static final ForgeConfigSpec.ConfigValue<Integer> SUMMONER_ZOMBIE_SPAWN_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<Double> ZOMBIE_EVOLVE_CHANCE_EASY;
    public static final ForgeConfigSpec.ConfigValue<Double> ZOMBIE_EVOLVE_CHANCE_NORMAL;
    public static final ForgeConfigSpec.ConfigValue<Double> ZOMBIE_EVOLVE_CHANCE_HARD;
    public static final ForgeConfigSpec.ConfigValue<Double> ZOMBIE_EVOLVE_CHANCE_EXTREME;
    public static final ForgeConfigSpec.ConfigValue<Integer> ZOMBIE_EVOLVE_BONUS_HEALTH;

    public static final ForgeConfigSpec.ConfigValue<Double> SKELETON_PERFECT_SHOT_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> SKELETON_STRAFE;
    public static final ForgeConfigSpec.ConfigValue<Double> SKELETON_ACCURACY_BOOST;
    public static final ForgeConfigSpec.ConfigValue<Boolean> SKELETON_INFINITE_ARROWS;
    public static final ForgeConfigSpec.ConfigValue<Double> SKELETON_MOD_WEAPON_CHANCE;

    public static final ForgeConfigSpec.ConfigValue<Boolean> AGGRESSIVE_TARGETING;
    public static final ForgeConfigSpec.ConfigValue<Integer> AGGRESSIVE_TARGETING_RADIUS;

    public static final ForgeConfigSpec.ConfigValue<Integer> VILLAGER_FLEE_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<Double> VILLAGER_PANIC_BOOST;
    public static final ForgeConfigSpec.ConfigValue<Double> VILLAGER_GUARDIAN_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Double> VILLAGER_GUARDIAN_MOD_WEAPON_CHANCE;

    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_AI_PLAYER_SPAWN;
    public static final ForgeConfigSpec.ConfigValue<Integer> AI_PLAYER_SPAWN_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<Integer> AI_PLAYER_MAX_COUNT;
    public static final ForgeConfigSpec.ConfigValue<Double> AI_PLAYER_SPAWN_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Integer> AI_PLAYER_SPAWN_RADIUS;

    // 多算法 AI 决策器配置
    public static final ForgeConfigSpec.ConfigValue<Boolean> AI_ALGORITHM_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> AI_ALGORITHM_DEFAULT_MODE;
    public static final ForgeConfigSpec.ConfigValue<Double> AI_QL_LEARNING_RATE;
    public static final ForgeConfigSpec.ConfigValue<Double> AI_QL_DISCOUNT_FACTOR;
    public static final ForgeConfigSpec.ConfigValue<Double> AI_QL_EXPLORATION_RATE;
    public static final ForgeConfigSpec.ConfigValue<Integer> AI_DECISION_INTERVAL;

    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_HEALTH_BAR;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HIDE_VANILLA_HEALTH;
    public static final ForgeConfigSpec.ConfigValue<Integer> HEALTH_BAR_WIDTH;
    public static final ForgeConfigSpec.ConfigValue<Integer> HEALTH_BAR_HEIGHT;
    public static final ForgeConfigSpec.ConfigValue<Integer> HEALTH_BAR_POSITION_Y;

    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_LOOT_INJECTION;

    public static final ForgeConfigSpec.ConfigValue<Boolean> CHAIN_MINING_ENABLE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> TREE_CHOP_ENABLE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_CHAIN_MINING;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_TREE_CHOP;
    public static final ForgeConfigSpec.ConfigValue<Integer> CHAIN_MINING_MAX_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<Integer> CHAIN_MINING_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CHAIN_MINING_PICKAXE_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CHAIN_MINING_SHOVEL_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<Integer> TREE_CHOP_MAX_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<Boolean> TREE_CHOP_INCLUDE_LEAVES;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CHAIN_PLANKS_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CHAIN_FENCES_ENABLED;

    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_MUSIC;
    public static final ForgeConfigSpec.ConfigValue<Double> MUSIC_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<Integer> MUSIC_ADVENTURE_COOLDOWN;
    public static final ForgeConfigSpec.ConfigValue<Integer> MUSIC_BLOOD_COOLDOWN;

    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_PLAYER2_MCP;
    public static final ForgeConfigSpec.ConfigValue<String> PLAYER2_MCP_URL;
    public static final ForgeConfigSpec.ConfigValue<String> PLAYER2_MCP_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<Integer> PLAYER2_MCP_TIMEOUT;

    public static final ForgeConfigSpec.ConfigValue<Boolean> ENHANCED_MOB_AI;
    public static final ForgeConfigSpec.ConfigValue<Double> MOB_AI_SPEED_BONUS_PER_PHASE;
    public static final ForgeConfigSpec.ConfigValue<Double> MOB_AI_KNOCKBACK_RESISTANCE;
    public static final ForgeConfigSpec.ConfigValue<Double> MOB_AI_ARMOR_PER_PHASE;

    static {
        BUILDER.push("difficulty");
        PEACEFUL_DAYS = BUILDER.comment("安全日天数").define("peacefulDays", 25);
        NORMAL_DAYS = BUILDER.comment("简单截止天数").define("normalDays", 50);
        HARD_DAYS = BUILDER.comment("普通截止天数").define("hardDays", 100);
        EXTREME_DAYS = BUILDER.comment("困难截止天数").define("extremeDays", 150);
        DIFFICULTY_LOCK = BUILDER.comment("锁定困难期").define("difficultyLock", false);
        ENABLE_DIFFICULTY_LOCK = BUILDER.comment("启用难度锁定").define("enableDifficultyLock", false);
        BUILDER.pop();

        BUILDER.push("moon");
        BLOOD_MOON_INTERVAL = BUILDER.comment("血月间隔天数").define("bloodMoonInterval", 14);
        LUCKY_MOON_CHANCE = BUILDER.comment("幸运之月概率").define("luckyMoonChance", 0.15);
        HARVEST_MOON_CHANCE = BUILDER.comment("丰收之月概率").define("harvestMoonChance", 0.10);
        BUILDER.pop();

        BUILDER.push("ai");
        ENABLE_AI_OPTIMIZATION = BUILDER.comment("启用AI优化").define("enableAiOptimization", true);
        ZOMBIE_FOLLOW_RANGE_MULTIPLIER = BUILDER.comment("僵尸追踪范围倍率").define("zombieFollowRangeMultiplier", 1.5);
        ZOMBIE_SPEED_MULTIPLIER = BUILDER.comment("僵尸速度倍率").define("zombieSpeedMultiplier", 1.2);
        ZOMBIE_BREAK_DOORS = BUILDER.comment("僵尸破门").define("zombieBreakDoors", true);
        ZOMBIE_BREAK_BLOCKS = BUILDER.comment("僵尸破墙").define("zombieBreakBlocks", true);
        ZOMBIE_BREAK_INTERVAL = BUILDER.comment("僵尸破墙间隔(游戏tick)").define("zombieBreakInterval", 200);
        ZOMBIE_PLACE_BLOCKS = BUILDER.comment("僵尸放置方块").define("zombiePlaceBlocks", true);
        ZOMBIE_PLACE_INTERVAL = BUILDER.comment("僵尸放置方块间隔(游戏tick)").define("zombiePlaceInterval", 600);
        SUICIDE_ZOMBIE_CHANCE = BUILDER.comment("自爆僵尸概率").define("suicideZombieChance", 0.10);
        BARREL_ZOMBIE_CHANCE = BUILDER.comment("油桶僵尸概率").define("barrelZombieChance", 0.05);
        TNT_ZOMBIE_CHANCE = BUILDER.comment("TNT僵尸概率").define("tntZombieChance", 0.03);
        POTION_ZOMBIE_CHANCE = BUILDER.comment("药水僵尸概率").define("potionZombieChance", 0.07);
        SUMMONER_ZOMBIE_CHANCE = BUILDER.comment("召唤师僵尸概率").define("summonerZombieChance", 0.05);
        SUMMONER_ZOMBIE_MAX_SUMMONS = BUILDER.comment("召唤师僵尸最大召唤数").define("summonerZombieMaxSummons", 5);
        SUMMONER_ZOMBIE_SPAWN_INTERVAL = BUILDER.comment("召唤师僵尸召唤间隔(tick)").define("summonerZombieSpawnInterval", 100);
        ZOMBIE_EVOLVE_CHANCE_EASY = BUILDER.comment("简单难度僵尸进化概率").define("zombieEvolveChanceEasy", 0.1);
        ZOMBIE_EVOLVE_CHANCE_NORMAL = BUILDER.comment("普通难度僵尸进化概率").define("zombieEvolveChanceNormal", 0.2);
        ZOMBIE_EVOLVE_CHANCE_HARD = BUILDER.comment("困难难度僵尸进化概率").define("zombieEvolveChanceHard", 0.4);
        ZOMBIE_EVOLVE_CHANCE_EXTREME = BUILDER.comment("极限难度僵尸进化概率").define("zombieEvolveChanceExtreme", 0.6);
        ZOMBIE_EVOLVE_BONUS_HEALTH = BUILDER.comment("僵尸进化额外血量").define("zombieEvolveBonusHealth", 20);
        SKELETON_PERFECT_SHOT_CHANCE = BUILDER.comment("骷髅完美射击概率").define("skeletonPerfectShotChance", 0.3);
        SKELETON_STRAFE = BUILDER.comment("骷髅走位射击").define("skeletonStrafe", true);
        SKELETON_ACCURACY_BOOST = BUILDER.comment("骷髅精度加成").define("skeletonAccuracyBoost", 0.2);
        SKELETON_INFINITE_ARROWS = BUILDER.comment("骷髅无限箭矢").define("skeletonInfiniteArrows", true);
        SKELETON_MOD_WEAPON_CHANCE = BUILDER.comment("骷髅使用mod武器概率").define("skeletonModWeaponChance", 0.15);
        AGGRESSIVE_TARGETING = BUILDER.comment("主动目标锁定").define("aggressiveTargeting", true);
        AGGRESSIVE_TARGETING_RADIUS = BUILDER.comment("主动目标锁定范围").define("aggressiveTargetingRadius", 64);
        BUILDER.pop();

        BUILDER.push("villager");
        VILLAGER_FLEE_RADIUS = BUILDER.comment("村民逃跑半径").define("villagerFleeRadius", 32);
        VILLAGER_PANIC_BOOST = BUILDER.comment("村民恐慌速度加成").define("villagerPanicBoost", 0.5);
        VILLAGER_GUARDIAN_CHANCE = BUILDER.comment("村民守卫概率").define("villagerGuardianChance", 0.3);
        VILLAGER_GUARDIAN_MOD_WEAPON_CHANCE = BUILDER.comment("村民守卫使用mod武器概率").define("villagerGuardianModWeaponChance", 0.2);
        BUILDER.pop();

        BUILDER.push("ai_player");
        ENABLE_AI_PLAYER_SPAWN = BUILDER.comment("启用AI玩家生成").define("enableAiPlayerSpawn", true);
        AI_PLAYER_SPAWN_INTERVAL = BUILDER.comment("AI玩家生成间隔(分钟)").define("aiPlayerSpawnInterval", 30);
        AI_PLAYER_MAX_COUNT = BUILDER.comment("AI玩家最大数量").define("aiPlayerMaxCount", 3);
        AI_PLAYER_SPAWN_CHANCE = BUILDER.comment("AI玩家生成概率").define("aiPlayerSpawnChance", 0.3);
        AI_PLAYER_SPAWN_RADIUS = BUILDER.comment("AI玩家生成半径").define("aiPlayerSpawnRadius", 100);

        BUILDER.push("ai_algorithm");
        AI_ALGORITHM_ENABLED = BUILDER.comment("启用多算法AI决策器（行为树/FSM/Q-Learning/效用/模糊）")
                .define("aiAlgorithmEnabled", true);
        AI_ALGORITHM_DEFAULT_MODE = BUILDER.comment("默认算法模式 (AUTO/BEHAVIOR_TREE/FSM/Q_LEARNING/UTILITY/FUZZY/DISABLED)")
                .define("aiAlgorithmDefaultMode", "AUTO");
        AI_QL_LEARNING_RATE = BUILDER.comment("Q-Learning 学习率 alpha (0.0-1.0)")
                .define("aiQlLearningRate", 0.1);
        AI_QL_DISCOUNT_FACTOR = BUILDER.comment("Q-Learning 折扣因子 gamma (0.0-1.0)")
                .define("aiQlDiscountFactor", 0.9);
        AI_QL_EXPLORATION_RATE = BUILDER.comment("Q-Learning 初始探索率 epsilon (0.0-1.0)")
                .define("aiQlExplorationRate", 0.3);
        AI_DECISION_INTERVAL = BUILDER.comment("AI决策间隔 tick（越小越灵敏，越大越省性能）")
                .define("aiDecisionInterval", 20);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.push("health_ui");
        ENABLE_HEALTH_BAR = BUILDER.comment("启用经验条样式血条").define("enableHealthBar", true);
        HIDE_VANILLA_HEALTH = BUILDER.comment("隐藏原版心形血条").define("hideVanillaHealth", true);
        HEALTH_BAR_WIDTH = BUILDER.comment("血条宽度(像素)").define("healthBarWidth", 182);
        HEALTH_BAR_HEIGHT = BUILDER.comment("血条高度(像素)").define("healthBarHeight", 5);
        HEALTH_BAR_POSITION_Y = BUILDER.comment("血条Y位置(从底部)").define("healthBarPositionY", 50);
        BUILDER.pop();

        BUILDER.push("loot");
        ENABLE_LOOT_INJECTION = BUILDER.comment("启用建筑宝箱注入").define("enableLootInjection", true);
        BUILDER.pop();

        BUILDER.push("chain_mining");
        CHAIN_MINING_ENABLE = BUILDER.comment("启用连锁挖矿").define("chainMiningEnable", true);
        TREE_CHOP_ENABLE = BUILDER.comment("启用连锁砍树").define("treeChopEnable", true);
        ENABLE_CHAIN_MINING = BUILDER.comment("启用连锁挖矿").define("enableChainMining", true);
        ENABLE_TREE_CHOP = BUILDER.comment("启用连锁砍树").define("enableTreeChop", true);
        CHAIN_MINING_MAX_BLOCKS = BUILDER.comment("连锁挖矿最大方块数").define("chainMiningMaxBlocks", 128);
        CHAIN_MINING_RADIUS = BUILDER.comment("连锁挖矿半径").define("chainMiningRadius", 5);
        CHAIN_MINING_PICKAXE_ENABLED = BUILDER.comment("镐子连锁挖矿").define("chainMiningPickaxeEnabled", true);
        CHAIN_MINING_SHOVEL_ENABLED = BUILDER.comment("铲子连锁挖矿").define("chainMiningShovelEnabled", true);
        TREE_CHOP_MAX_BLOCKS = BUILDER.comment("连锁砍树最大方块数").define("treeChopMaxBlocks", 64);
        TREE_CHOP_INCLUDE_LEAVES = BUILDER.comment("连锁砍树包含树叶").define("treeChopIncludeLeaves", false);
        CHAIN_PLANKS_ENABLED = BUILDER.comment("连锁砍木板（斧头砍木板时连锁破坏相连同种木板，支持其他模组）").define("chainPlanksEnabled", true);
        CHAIN_FENCES_ENABLED = BUILDER.comment("连锁砍栅栏（斧头砍栅栏时连锁破坏相连同种栅栏，支持其他模组）").define("chainFencesEnabled", true);
        BUILDER.pop();

        BUILDER.push("music");
        ENABLE_MUSIC = BUILDER.comment("启用音乐播放").define("enableMusic", true);
        MUSIC_VOLUME = BUILDER.comment("音乐音量").define("musicVolume", 0.5);
        MUSIC_ADVENTURE_COOLDOWN = BUILDER.comment("冒险序曲间隔(分钟)").define("musicAdventureCooldown", 15);
        MUSIC_BLOOD_COOLDOWN = BUILDER.comment("血月战歌间隔(分钟)").define("musicBloodCooldown", 10);
        BUILDER.pop();

        BUILDER.push("player2_mcp");
        ENABLE_PLAYER2_MCP = BUILDER.comment("启用 Player2 MCP API").define("enablePlayer2MCP", true);
        PLAYER2_MCP_URL = BUILDER.comment("Player2 MCP 服务器地址 (来自 Player2 官网 MCP 集成页面)").define("player2McpUrl", "https://api.player2.game/api/v1/mcp");
        PLAYER2_MCP_API_KEY = BUILDER.comment("Player2 MCP API 密钥 (在 Player2 官网 MCP 集成页面生成)").define("player2McpApiKey", "p2_D_FP66wl-SznulcNpYGdPA");
        PLAYER2_MCP_TIMEOUT = BUILDER.comment("API 请求超时(毫秒)").define("player2McpTimeout", 30000);
        BUILDER.pop();

        BUILDER.push("enhanced_mob_ai");
        ENHANCED_MOB_AI = BUILDER.comment("启用动态难度怪物AI增强").define("enhancedMobAi", true);
        MOB_AI_SPEED_BONUS_PER_PHASE = BUILDER.comment("每阶段怪物速度加成倍率").define("mobAiSpeedBonusPerPhase", 0.15);
        MOB_AI_KNOCKBACK_RESISTANCE = BUILDER.comment("怪物击退抗性(每阶段)").define("mobAiKnockbackResistance", 0.15);
        MOB_AI_ARMOR_PER_PHASE = BUILDER.comment("每阶段怪物护甲值加成").define("mobAiArmorPerPhase", 2.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
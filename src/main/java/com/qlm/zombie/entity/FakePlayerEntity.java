/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * This file is part of QLM Zombie Mod.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *
 * This class is an ORIGINAL implementation inspired by the design patterns of:
 *   - PlayerEngine (https://github.com/Goodbird-git/PlayerEngine)
 *     Copyright (c) Goodbird-git
 *     Licensed under MIT License
 *   - Player2NPC (https://github.com/Goodbird-git/Player2NPC)
 *     FakePlayer entity pattern with NPC skin rendering
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements (zombie apocalypse AI).
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.entity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class FakePlayerEntity extends PathfinderMob implements MenuProvider {

    private static final EntityDataAccessor<Optional<UUID>> DATA_PLAYER_UUID =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_SKIN_URL =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_IS_SLIM =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_CUSTOM_NAME =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_TAMED =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_SITTING =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FOOD_LEVEL =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SATURATION =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_EATING =
            SynchedEntityData.defineId(FakePlayerEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_PLAYER_UUID = "PlayerUUID";
    private static final String TAG_SKIN_URL = "SkinURL";
    private static final String TAG_IS_SLIM = "IsSlim";
    private static final String TAG_CUSTOM_NAME = "CustomName";
    private static final String TAG_TAMED = "Tamed";
    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_SITTING = "Sitting";
    private static final String TAG_FOOD_LEVEL = "FoodLevel";
    private static final String TAG_SATURATION = "Saturation";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_ARMOR = "ArmorItems";
    private static final String TAG_TAMING_PROGRESS = "TamingProgress";

    private GameProfile gameProfile;
    private final SimpleContainer inventory = new SimpleContainer(27);
    private final SimpleContainer armorInventory = new SimpleContainer(4);
    private final SimpleContainer offhandInventory = new SimpleContainer(1);
    private int eatingTicks;
    private ItemStack eatingItem = ItemStack.EMPTY;

    private final Set<UUID> myDamagedEntities = new HashSet<>();
    private final Map<UUID, BlockPos> myKillPositions = new HashMap<>();
    private static final long KILL_TRACKING_TICKS = 200;

    // AI任务状态：当有活跃任务时，不执行自动跟随
    private boolean hasActiveTask = false;

    // 任务运行器 — 参考 PlayerEngine TaskRunner，管理任务生命周期
    private com.qlm.zombie.ai.task.TaskRunner taskRunner;

    // 多算法 AI 决策管理器 — 整合行为树/FSM/Q-Learning/效用/模糊
    private com.qlm.zombie.ai.algorithm.AIAlgorithmManager algorithmManager;

    // 驯服进度：0-100，达到100时驯服。参考 player2npc 的 SpawnReason.FIRST_MEETING 招募机制
    private int tamingProgress = 0;

    public FakePlayerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TamableSitGoal(this));
        this.goalSelector.addGoal(2, new AIEatFoodGoal(this));
        this.goalSelector.addGoal(3, new AIEquipGoal(this));
        // 自定义近战攻击：任务期间不抢占导航
        this.goalSelector.addGoal(4, new AITaskAwareMeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(5, new TamableFollowGoal(this, 1.0D, 4.0F, 2.0F));
        this.goalSelector.addGoal(6, new AIMineGoal(this));
        this.goalSelector.addGoal(7, new AITreeChopGoal(this));
        this.goalSelector.addGoal(8, new AIWorkstationGoal(this));
        this.goalSelector.addGoal(9, new AIPickupLootGoal(this));
        // 未驯服AI的好奇靠近：偶尔走向附近玩家，便于玩家驯服
        this.goalSelector.addGoal(10, new AICuriousApproachGoal(this));
        // 自由漫步：未驯服AI优先自由活动；驯服后作为空闲行为
        this.goalSelector.addGoal(11, new AIWanderFreelyGoal(this, 0.8D));
        // ★ 使用自定义 Goal 替换原版，活跃任务期间禁用随机漫步/看周围，避免与 Task 系统抢导航
        this.goalSelector.addGoal(12, new AITaskAwareStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(13, new AITaskAwareLookGoal(this));

        this.targetSelector.addGoal(1, new AIOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new AIOwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        // 主动攻击目标只对已驯服AI生效，未驯服AI保持中立
        this.targetSelector.addGoal(4, new AIHuntMonsterGoal(this));
        this.targetSelector.addGoal(5, new AIHuntAnimalGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 25.0D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                              @Nullable SpawnGroupData groupData, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
        if (this.random.nextFloat() < 0.50F) {
            this.giveRandomWeapon();
        }
        return result;
    }

    public void giveRandomWeapon() {
        ItemStack weapon = getRandomModWeapon(this.random);
        if (weapon.isEmpty()) {
            weapon = buildDefaultWeapon(this.random);
        }
        if (!weapon.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            if (isTaczWeapon(weapon)) {
                giveTaczAmmo(weapon);
            }
        }
    }

    public static ItemStack getRandomModWeapon(net.minecraft.util.RandomSource rnd) {
        List<Item> modWeapons = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            String namespace = id.getNamespace();
            if (namespace.equals("minecraft")) continue;
            String path = id.getPath();

            if (isWeaponNamespace(namespace) || isWeaponItem(item, path)) {
                modWeapons.add(item);
            }
        }
        if (modWeapons.isEmpty()) return ItemStack.EMPTY;
        Item chosen = modWeapons.get(rnd.nextInt(modWeapons.size()));
        return new ItemStack(chosen);
    }

    private static final Gson GSON = new Gson();
    private static WeaponDetectionConfig weaponConfig = null;

    private static final class WeaponDetectionConfig {
        Set<String> weaponNamespaces = new HashSet<>();
        Set<String> weaponKeywords = new HashSet<>();
    }

    private static WeaponDetectionConfig getWeaponConfig() {
        if (weaponConfig != null) return weaponConfig;
        weaponConfig = loadWeaponConfigOrDefault();
        return weaponConfig;
    }

    private static WeaponDetectionConfig loadWeaponConfigOrDefault() {
        WeaponDetectionConfig config = new WeaponDetectionConfig();
        try (InputStream is = FakePlayerEntity.class.getClassLoader()
                .getResourceAsStream("assets/qlmzombie/data/weapon_detection.json")) {
            if (is != null) {
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
                    Map<String, List<String>> data = GSON.fromJson(reader, type);
                    if (data != null) {
                        if (data.get("weaponNamespaces") != null) {
                            config.weaponNamespaces.addAll(data.get("weaponNamespaces"));
                        }
                        if (data.get("weaponKeywords") != null) {
                            config.weaponKeywords.addAll(data.get("weaponKeywords"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] Failed to load weapon_detection.json, using defaults: {}", e.getMessage());
        }
        if (config.weaponNamespaces.isEmpty() && config.weaponKeywords.isEmpty()) {
            loadDefaultWeaponConfig(config);
        }
        return config;
    }

    private static void loadDefaultWeaponConfig(WeaponDetectionConfig config) {
        config.weaponNamespaces.addAll(java.util.Set.of(
                "tacz", "taczjs", "slashblade", "flammpfeil.slashblade", "spartanweaponry",
                "tetra", "artifacts", "bloodmagic", "botania", "pneumaticcraft",
                "mekanism", "immersiveengineering", "create", "bettercombat", "footwork"
        ));
        config.weaponKeywords.addAll(java.util.List.of(
                "sword", "gun", "rifle", "pistol", "shotgun", "sniper", "smg", "machine_gun",
                "blade", "katana", "spear", "halberd", "dagger", "mace", "axe", "hammer",
                "greatsword", "longsword", "saber", "rapier", "battleaxe", "warhammer",
                "pike", "lance", "glaive", "scythe", "claymore", "cutlass", "broadsword",
                "flail", "morningstar", "trident", "javelin", "throwing", "bow", "crossbow",
                "bazooka", "launcher", "grenade", "cannon", "minigun", "flamethrower",
                "sickle", "sai", "nunchaku", "staff", "wand", "tachi", "ninjato", "wakizashi",
                "nodachi", "shuriken", "kunai", "tanto", "knife", "bo_staff",
                "quarterstaff", "club", "tomahawk", "kukri", "scimitar", "falchion", "estoc",
                "zweihander", "frying_pan", "crowbar", "baseball_bat", "fireaxe", "chainsaw",
                "cleaver", "machete", "hook", "whip", "chain", "battle", "combat", "tactical",
                "assault", "carbine", "revolver", "magnum", "handgun", "uzi", "ak", "m4", "m16",
                "glock", "desert_eagle", "awp", "ak47", "m249", "mp5", "p90", "aug", "scar",
                "vector", "rpg", "m32", "grenade_launcher", "double_barrel", "pump", "lever_action",
                "slashblade", "spartan", "tetra", "artifacts", "bloodmagic", "botania",
                "pneumaticcraft", "mekanism", "immersive", "superbwarfare", "footwork",
                "tool", "pickaxe", "shovel", "hoe", "waraxe",
                "parrying_dagger", "throwing_knife", "boomerang",
                "flanged_mace", "morning_star",
                "kama", "tonfa", "bo", "jo", "kanabo", "tetsubo", "naginata",
                "yari", "kusarigama", "kyoketsu_shoge", "uchigatana",
                "odachi", "kodachi", "yoroi_doshi", "kaiken",
                "firearm", "bullet", "magazine", "ammo_crate", "scope",
                "suppressor", "grip", "stock", "barrel", "receiver",
                "marksman", "dmr", "battle_rifle", "pdw", "lmg", "rocket",
                "missile", "torpedo", "depth_charge", "airstrike", "artillery",
                "howitzer", "mortar", "gatling", "maxim", "browning", "sten",
                "thompson", "garand", "kar98k", "mosin", "lee_enfield", "springfield",
                "fg42", "stg44", "mg42", "mg34", "ppsh", "pps", "dp28", "svt",
                "sks", "dragunov", "svd", "vss", "as_val", "groza", "an94",
                "rpk", "pkp", "pecheneg", "saiga", "vepr", "vityaz",
                "pp19", "bizon", "ots", "sr3m", "vsk94", "9a91", "sr2",
                "famas", "hk416", "hk417", "g36", "g3", "mp7", "ump45",
                "fn_fal", "fnc", "l85", "sa80", "f2000", "five_seven",
                "mpx", "mcx", "sg550", "sg552", "sg553", "sg556", "aug_a3",
                "tavor", "x95", "galil", "negev", "uzi_pro", "jericho",
                "tar21", "micro_tavor", "daniel", "colt", "sig", "beretta",
                "walther", "hk", "cz", "ruger", "smith_wesson", "taurus",
                "fn_herstal", "imi", "steyr", "sako", "remington", "winchester",
                "mossberg", "benelli", "franchi", "browning",
                "sauer", "blaser", "merkel", "krieghoff", "perazzi", "caesar",
                "mcmillan", "accuracy", "tikka", "heym", "rigby", "holland", "westley",
                "purdey", "boss", "greener", "cogswell", "harrison", "churchill", "webley",
                "enfield", "armalite", "bushmaster", "dpms", "larue", "noveske",
                "bcm", "aero", "psa", "anderson", "stag", "cmmg", "wilson",
                "nighthawk", "ed_brown", "les_baer", "cabot", "infinity",
                "staccato", "atlas", "limcat", "svi", "cz_custom", "tanfoglio",
                "phoenix", "redback", "alien", "laugo", "fk_brno", "psd",
                "archon", "strike_one", "arsenal", "plum", "zenitco", "magpul",
                "b5", "geissele", "larue", "kac", "lmt", "dd", "sionics",
                "centurion", "fcd", "slr", "midwest", "samson", "troy", "yhm",
                "silencerco", "dead_air", "rugged", "gemtech", "aac", "surefire",
                "oss", "cgs", "tbac", "area419", "form1", "solvent_trap",
                "drill", "electric", "gas", "powered", "energy", "plasma", "laser",
                "railgun", "coilgun", "gauss", "tesla", "arc", "pulse", "phaser",
                "blaster", "disruptor", "ion", "photon", "quantum", "antimatter",
                "dark_matter", "void", "chaos", "eldritch", "arcane", "mystic",
                "enchanted", "runic", "divine", "holy", "unholy", "demonic",
                "angelic", "draconic", "dragon", "wyrm", "wyvern", "serpent",
                "fangs", "claws", "talons", "stinger", "mandible", "pincer",
                "tentacle", "appendage", "limb", "cestus", "gauntlet", "knuckle",
                "brass_knuckles", "spiked", "bladed", "barbed", "serrated",
                "notched", "jagged", "toothed", "razor", "sharpened", "honed",
                "tempered", "forged", "folded", "damascus", "pattern_welded",
                "tamahagane", "orichalcum", "adamantite", "mythril", "hihi'irokane",
                "meteorite", "star_metal", "crystal", "obsidian", "flint", "bone",
                "ivory", "horn", "antler", "chitin", "shell", "scale", "leather",
                "studded", "ringed", "splinted", "banded", "lamellar", "brigandine",
                "coat_of_plates", "jack_of_plates", "gambeson", "aketon", "pourpoint",
                "doublet", "buff_coat", "hauberk", "haubergeon", "byrnie", "cuirass",
                "breastplate", "backplate", "fauld", "tasset", "cuisse", "greave",
                "sabaton", "gauntlet", "pauldron", "rerebrace", "vambrace", "couter",
                "gorget", "bevor", "sallet", "barbute", "armet", "close_helm",
                "great_helm", "bucket_helm", "kettle_hat", "morion", "cabasset",
                "burgonet", "pickelhaube", "stahlhelm", "brodie",
                "adrian", "m1", "pasgt", "ach", "fast", "airframe", "crye",
                "ops_core", "team_wendy", "galvion", "gentex", "msa", "3m",
                "avon", "scott", "drager", "survivair", "north", "honeywell",
                "bullard", "fibre_metal", "jackson", "sellstrom", "uvex", "bolle",
                "ess", "oakley", "wiley", "smith", "rudy", "revision", "gatorz",
                "randolph", "american_optical", "ao", "ray_ban", "persol", "maui_jim"
        ));
    }

    private static boolean isWeaponNamespace(String namespace) {
        return getWeaponConfig().weaponNamespaces.contains(namespace);
    }

    private static boolean isWeaponItem(Item item, String path) {
        if (item instanceof SwordItem || item instanceof ProjectileWeaponItem) return true;
        String lower = path.toLowerCase();
        for (String key : getWeaponConfig().weaponKeywords) {
            if (lower.contains(key)) return true;
        }
        return false;
    }

    private static boolean isTaczWeapon(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id.getNamespace().equals("tacz");
    }

    private void giveTaczAmmo(ItemStack weapon) {
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id.getNamespace().equals("tacz") && id.getPath().contains("ammo")) {
                ItemStack ammo = new ItemStack(item, 64);
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    if (inventory.getItem(i).isEmpty()) {
                        inventory.setItem(i, ammo);
                        break;
                    }
                }
                break;
            }
        }
    }

    public static ItemStack buildDefaultWeapon(net.minecraft.util.RandomSource rnd) {
        ItemStack stack = new ItemStack(Items.NETHERITE_SWORD);
        try {
            java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> emap = new java.util.HashMap<>();
            emap.put(Enchantments.SHARPNESS, 3 + rnd.nextInt(3));
            emap.put(Enchantments.FIRE_ASPECT, 2);
            emap.put(Enchantments.UNBREAKING, 3);
            int variant = rnd.nextInt(3);
            if (variant == 0) emap.put(Enchantments.SMITE, 4 + rnd.nextInt(2));
            else if (variant == 1) emap.put(Enchantments.BANE_OF_ARTHROPODS, 4 + rnd.nextInt(2));
            else emap.put(Enchantments.KNOCKBACK, 2);
            net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(emap, stack);
        } catch (Exception ignored) {
            com.qlm.zombie.QLMZombieMod.LOGGER.debug("Failed to enchant default weapon: {}", ignored.getMessage());
        }
        return stack;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PLAYER_UUID, Optional.empty());
        this.entityData.define(DATA_SKIN_URL, "");
        this.entityData.define(DATA_IS_SLIM, false);
        this.entityData.define(DATA_CUSTOM_NAME, "");
        this.entityData.define(DATA_TAMED, false);
        this.entityData.define(DATA_OWNER_UUID, Optional.empty());
        this.entityData.define(DATA_SITTING, false);
        this.entityData.define(DATA_FOOD_LEVEL, 20);
        this.entityData.define(DATA_SATURATION, 5.0F);
        this.entityData.define(DATA_EATING, false);
    }

    public Optional<UUID> getPlayerUUID() {
        return this.entityData.get(DATA_PLAYER_UUID);
    }

    public void setPlayerUUID(UUID uuid) {
        this.entityData.set(DATA_PLAYER_UUID, Optional.of(uuid));
        this.gameProfile = new GameProfile(uuid, this.getCustomNameStr());
    }

    public String getSkinURL() {
        return this.entityData.get(DATA_SKIN_URL);
    }

    public void setSkinURL(String url) {
        this.entityData.set(DATA_SKIN_URL, url);
    }

    public boolean isSlim() {
        return this.entityData.get(DATA_IS_SLIM);
    }

    public void setSlim(boolean slim) {
        this.entityData.set(DATA_IS_SLIM, slim);
    }

    public String getCustomNameStr() {
        return this.entityData.get(DATA_CUSTOM_NAME);
    }

    public void setCustomNameStr(String name) {
        this.entityData.set(DATA_CUSTOM_NAME, name);
        if (name != null && !name.isEmpty()) {
            this.setCustomName(Component.literal(name));
            this.setCustomNameVisible(true);
        }
    }

    public boolean isTamed() {
        return this.entityData.get(DATA_TAMED);
    }

    public void setTamed(boolean tamed) {
        this.entityData.set(DATA_TAMED, tamed);
    }

    public boolean hasActiveTask() {
        if (taskRunner != null) return taskRunner.hasActiveTask();
        return this.hasActiveTask;
    }

    public void setActiveTask(boolean active) {
        this.hasActiveTask = active;
    }

    /** 获取任务运行器（懒加载） */
    public com.qlm.zombie.ai.task.TaskRunner getTaskRunner() {
        if (taskRunner == null) {
            taskRunner = new com.qlm.zombie.ai.task.TaskRunner(this);
        }
        return taskRunner;
    }

    /**
     * 获取 AI 算法管理器（懒加载）
     * 整合行为树/FSM/Q-Learning/效用理论/模糊逻辑/A*寻路
     * 仅在未执行玩家指令任务时介入决策
     */
    public com.qlm.zombie.ai.algorithm.AIAlgorithmManager getAlgorithmManager() {
        if (algorithmManager == null) {
            algorithmManager = new com.qlm.zombie.ai.algorithm.AIAlgorithmManager(this);
            // 注入预设算法实例
            algorithmManager.setBehaviorTree(com.qlm.zombie.ai.algorithm.AIAlgorithmPresets.createDefaultBehaviorTree(this));
            algorithmManager.setFsm(com.qlm.zombie.ai.algorithm.AIAlgorithmPresets.createDefaultFSM(this));
            // 从配置读取 Q-Learning 超参数
            double alpha = com.qlm.zombie.config.QLMConfig.AI_QL_LEARNING_RATE.get();
            double gamma = com.qlm.zombie.config.QLMConfig.AI_QL_DISCOUNT_FACTOR.get();
            double epsilon = com.qlm.zombie.config.QLMConfig.AI_QL_EXPLORATION_RATE.get();
            algorithmManager.setQLearning(new com.qlm.zombie.ai.algorithm.qlearning.QLearningAgent(alpha, gamma, epsilon, 0.02, 0.9995));
            algorithmManager.setUtilitySystem(com.qlm.zombie.ai.algorithm.AIAlgorithmPresets.createDefaultUtilitySystem(this));
            algorithmManager.setFuzzySystem(com.qlm.zombie.ai.algorithm.AIAlgorithmPresets.createDefaultFuzzySystem(this));

            // 从配置读取默认模式
            try {
                String modeStr = com.qlm.zombie.config.QLMConfig.AI_ALGORITHM_DEFAULT_MODE.get();
                if (modeStr != null && !modeStr.isEmpty()) {
                    com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode m =
                            com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.valueOf(modeStr.toUpperCase());
                    algorithmManager.setMode(m);
                }
            } catch (Exception ignored) {
            }
        }
        return algorithmManager;
    }

    /** 设置 AI 算法模式 */
    public void setAlgorithmMode(com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode mode) {
        getAlgorithmManager().setMode(mode);
    }

    /** 获取当前 AI 算法模式 */
    public com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode getAlgorithmMode() {
        return algorithmManager != null ? algorithmManager.getMode()
                : com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.AUTO;
    }

    /** 获取当前任务名称（供内部 Goal 类检查任务类型） */
    public String getCurrentTaskName() {
        if (taskRunner != null) return taskRunner.getCurrentTaskName();
        return this.getCurrentTaskName();
    }

    /**
     * 任务期间阻止攻击目标被设置，防止 MeleeAttackGoal 抢占导航
     * 只有 attack/guard 任务才允许设置目标
     */
    @Override
    public void setTarget(LivingEntity target) {
        if (this.hasActiveTask && target != null) {
            String currentTask = this.getCurrentTaskName();
            if (currentTask != null) {
                String taskType = currentTask.split(":", 2)[0];
                if (!taskType.equals("attack") && !taskType.equals("guard")) {
                    return;
                }
            }
        }
        super.setTarget(target);
    }

    public int getTamingProgress() {
        return this.tamingProgress;
    }

    public void setTamingProgress(int progress) {
        this.tamingProgress = Math.max(0, Math.min(100, progress));
    }

    public void addTamingProgress(int amount) {
        this.tamingProgress = Math.max(0, Math.min(100, this.tamingProgress + amount));
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(DATA_OWNER_UUID);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_OWNER_UUID, Optional.ofNullable(uuid));
    }

    @Nullable
    public LivingEntity getOwner() {
        if (!this.level().isClientSide && this.getOwnerUUID().isPresent()) {
            Entity entity = ((ServerLevel) this.level()).getEntity(this.getOwnerUUID().get());
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    public boolean isSitting() {
        return this.entityData.get(DATA_SITTING);
    }

    public void setSitting(boolean sitting) {
        this.entityData.set(DATA_SITTING, sitting);
    }

    public int getFoodLevel() {
        return this.entityData.get(DATA_FOOD_LEVEL);
    }

    public void setFoodLevel(int foodLevel) {
        this.entityData.set(DATA_FOOD_LEVEL, Math.max(0, Math.min(20, foodLevel)));
    }

    public float getSaturation() {
        return this.entityData.get(DATA_SATURATION);
    }

    public void setSaturation(float saturation) {
        this.entityData.set(DATA_SATURATION, Math.max(0, Math.min(20, saturation)));
    }

    public boolean isEating() {
        return this.entityData.get(DATA_EATING);
    }

    private void setEating(boolean eating) {
        this.entityData.set(DATA_EATING, eating);
    }

    public SimpleContainer getOffhandInventory() {
        return offhandInventory;
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public SimpleContainer getArmorInventory() {
        return armorInventory;
    }

    public GameProfile getGameProfile() {
        if (this.gameProfile == null) {
            UUID uuid = this.getPlayerUUID().orElse(UUID.randomUUID());
            this.gameProfile = new GameProfile(uuid, this.getCustomNameStr());
        }
        return this.gameProfile;
    }

    @Override
    public void pickUpItem(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (this.wantsToPickUp(stack)) {
            if (this.tryEquipItem(stack)) {
                this.onItemPickup(itemEntity);
                this.take(itemEntity, stack.getCount());
                itemEntity.discard();
                return;
            }
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack slot = inventory.getItem(i);
                if (slot.isEmpty()) {
                    inventory.setItem(i, stack.copy());
                    this.onItemPickup(itemEntity);
                    this.take(itemEntity, stack.getCount());
                    itemEntity.discard();
                    return;
                }
                if (ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                    int canAdd = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(canAdd);
                    stack.shrink(canAdd);
                    if (stack.isEmpty()) {
                        itemEntity.discard();
                    }
                    this.onItemPickup(itemEntity);
                    this.take(itemEntity, canAdd);
                    return;
                }
            }
        }
        super.pickUpItem(itemEntity);
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return this.isTamed() && super.wantsToPickUp(stack);
    }

    private boolean tryEquipItem(ItemStack stack) {
        Item item = stack.getItem();
        EquipmentSlot slot = getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.ARMOR || slot == EquipmentSlot.MAINHAND) {
            ItemStack current = this.getItemBySlot(slot);
            if (isBetterThan(current, stack)) {
                this.setItemSlot(slot, stack.copy());
                return true;
            }
        }
        return false;
    }

    private boolean isBetterThan(ItemStack current, ItemStack candidate) {
        if (current.isEmpty()) return true;
        if (current.getItem() instanceof ArmorItem currentArmor && candidate.getItem() instanceof ArmorItem candidateArmor) {
            if (candidateArmor.getDefense() > currentArmor.getDefense()) return true;
            if (candidateArmor.getToughness() > currentArmor.getToughness()) return true;
        }
        if (current.getItem() instanceof TieredItem currentTier && candidate.getItem() instanceof TieredItem candidateTier) {
            @SuppressWarnings("deprecation")
            boolean result = candidateTier.getTier().getLevel() > currentTier.getTier().getLevel();
            if (result) return true;
        }
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTamed()) {
            int progress = getTamingProgressForFood(stack);
            if (progress > 0) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }

                this.addTamingProgress(progress);
                player.sendSystemMessage(Component.literal("§7[" + getCustomNameStr() + "] 信任度: " + getTamingProgress() + "/100"));

                if (this.getTamingProgress() >= 100) {
                    this.tame(player);
                    this.spawnTamingParticles(true);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                    player.sendSystemMessage(Component.literal("§a[" + getCustomNameStr() + "] 已被你驯服！"));
                } else {
                    this.spawnTamingParticles(false);
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        if (this.isTamed() && this.getOwnerUUID().map(uuid -> uuid.equals(player.getUUID())).orElse(false)) {
            if (stack.isEmpty()) {
                if (player.isShiftKeyDown()) {
                    this.setSitting(!this.isSitting());
                    this.setJumping(false);
                    this.navigation.stop();
                    this.setTarget(null);
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                } else {
                    this.openInventory(player);
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

            if (this.isFood(stack)) {
                if (this.getFoodLevel() < 20) {
                    FoodProperties food = stack.getFoodProperties(this);
                    if (food != null) {
                        this.setFoodLevel(Math.min(20, this.getFoodLevel() + food.getNutrition()));
                        this.setSaturation(Math.min(20, this.getSaturation() + food.getSaturationModifier() * 2 * food.getNutrition()));
                        this.heal(food.getNutrition() * 0.5F);
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        this.level().broadcastEntityEvent(this, (byte) 9);
                        return InteractionResult.sidedSuccess(this.level().isClientSide);
                    }
                }
            }

            if (stack.is(Items.NAME_TAG)) {
                return InteractionResult.PASS;
            }

            // 参考 TLM: 玩家手持武器/盔甲/工具右键 AI 时装备
            if (com.qlm.zombie.ai.EquipmentHelper.isWeapon(stack) ||
                com.qlm.zombie.ai.EquipmentHelper.isArmor(stack) ||
                com.qlm.zombie.ai.EquipmentHelper.isTool(stack)) {

                ItemStack handCopy = stack.copy();
                boolean equipped = com.qlm.zombie.ai.EquipmentHelper.tryEquipItem(this, handCopy);

                if (equipped) {
                    String typeDesc = com.qlm.zombie.ai.EquipmentHelper.getItemTypeDesc(stack);
                    player.sendSystemMessage(Component.literal("§a[" + getCustomNameStr() + "] §f已装备" + typeDesc + ": §e" +
                            stack.getHoverName().getString()));
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    this.level().broadcastEntityEvent(this, (byte) 45);
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                } else {
                    player.sendSystemMessage(Component.literal("§7[" + getCustomNameStr() + "] §f当前装备更好，无需替换"));
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }
        }

        return super.mobInteract(player, hand);
    }

    public void openInventory(Player player) {
        if (!this.level().isClientSide) {
            player.openMenu(this);
        }
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int windowId, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.world.entity.player.Player player) {
        // 参考 TLM: 返回完整的背包GUI，包含主背包+盔甲槽+副手槽
        return new FakePlayerMenu(windowId, playerInventory, this.inventory, this.armorInventory, this.offhandInventory);
    }

    private boolean isFood(ItemStack stack) {
        return stack.getFoodProperties(this) != null;
    }

    public void tame(Player player) {
        this.setTamed(true);
        this.setOwnerUUID(player.getUUID());
        this.setTamingProgress(100);
        if (this.getCustomNameStr().isEmpty()) {
            this.setCustomNameStr(player.getName().getString() + "'s AI");
        }
    }

    /**
     * 不同食物给予不同的驯服进度。参考 player2npc 的 SpawnReason.FIRST_MEETING 招募机制：
     * 高价值食物加快招募
     */
    private int getTamingProgressForFood(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return 50;
        if (stack.is(Items.GOLDEN_CARROT)) return 35;
        if (stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP) ||
                stack.is(Items.COOKED_MUTTON) || stack.is(Items.COOKED_RABBIT)) return 25;
        if (stack.is(Items.COOKED_CHICKEN) || stack.is(Items.COOKED_COD) ||
                stack.is(Items.COOKED_SALMON)) return 20;
        if (stack.is(Items.BREAD) || stack.is(Items.APPLE) ||
                stack.is(Items.CARROT) || stack.is(Items.BAKED_POTATO)) return 20;
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE)) return 15;
        if (stack.is(Items.BONE)) return 10;
        if (stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS) ||
                stack.is(Items.BEETROOT) || stack.is(Items.BEETROOT_SEEDS) ||
                stack.is(Items.MELON_SLICE) || stack.is(Items.PUMPKIN_PIE)) return 10;
        // 任意可食用食物默认 +8
        if (stack.getFoodProperties(this) != null) return 8;
        return 0;
    }

    private void spawnTamingParticles(boolean success) {
        if (this.level().isClientSide) return;
        ServerLevel serverLevel = (ServerLevel) this.level();
        for (int i = 0; i < 7; i++) {
            double x = this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth();
            double y = this.getY() + this.random.nextDouble() * this.getBbHeight();
            double z = this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth();
            serverLevel.sendParticles(success ? ParticleTypes.HEART : ParticleTypes.SMOKE, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (this.getCustomNameStr().isEmpty()) {
                String name = "AI_" + this.getStringUUID().substring(0, 6);
                this.setCustomNameStr(name);
            }

            // 任务期间保护：非攻击/守卫任务时，清除被误伤设置的目标
            if (this.hasActiveTask) {
                String taskType = this.getCurrentTaskName();
                if (taskType != null) {
                    String primaryTask = taskType.split(":", 2)[0];
                    if (!primaryTask.equals("attack") && !primaryTask.equals("guard") && this.getTarget() != null) {
                        this.setTarget(null);
                    }
                }
            }

            // TaskRunner 每 tick 执行任务逻辑（参考 PlayerEngine TaskRunner）
            if (this.taskRunner != null) {
                this.taskRunner.tick();
            }

            // 多算法 AI 决策器：仅在已驯服且无玩家指令任务时介入
            // 提供行为树/FSM/Q-Learning/效用/模糊 的自主决策能力
            if (this.isTamed() && !this.hasActiveTask) {
                try {
                    getAlgorithmManager().tick();
                } catch (Exception ignored) {
                    // 算法层异常不应影响实体正常 tick
                }
            }

            if (this.tickCount % 20 == 0) {
                trackMyKills();
            }

            if (this.tickCount % 200 == 0) {
                this.consumeFoodLevel();
            }

            if (this.tickCount % 40 == 0 && this.getFoodLevel() > 17 && this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }

            if (this.getFoodLevel() <= 0 && this.tickCount % 80 == 0) {
                // 修复: FakePlayer 不应饥饿掉血 - 饥饿伤害会触发 Footwork/Mekanism capability NPE 崩溃
                // 改为恢复饱食度，避免 hurt 链路中的 capability 检查
                this.setFoodLevel(20);
            }
        }

        if (this.isEating()) {
            eatingTicks++;
            if (eatingTicks >= 32) {
                this.finishEating();
            }
        }
    }

    private void consumeFoodLevel() {
        int foodLevel = this.getFoodLevel();
        if (foodLevel > 0) {
            float saturation = this.getSaturation();
            if (saturation > 0) {
                this.setSaturation(saturation - 1.0F);
            } else {
                this.setFoodLevel(foodLevel - 1);
            }
        }
    }

    private void trackMyKills() {
        if (this.level().isClientSide) return;
        Iterator<UUID> it = myDamagedEntities.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = ((ServerLevel) this.level()).getEntity(uuid);
            if (entity == null || !entity.isAlive() || entity.isRemoved()) {
                if (entity instanceof LivingEntity living && living.getLastHurtByMob() == this) {
                    myKillPositions.put(uuid, new BlockPos((int) living.getX(), (int) living.getY(), (int) living.getZ()));
                }
                it.remove();
            }
        }
        cleanupOldKillPositions();
    }

    private void cleanupOldKillPositions() {
        Iterator<Map.Entry<UUID, BlockPos>> it = myKillPositions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = it.next();
            double dist = this.distanceToSqr(entry.getValue().getX(), entry.getValue().getY(), entry.getValue().getZ());
            if (dist > 256) {
                it.remove();
            }
        }
        if (myKillPositions.size() > 20) {
            Iterator<Map.Entry<UUID, BlockPos>> trimIt = myKillPositions.entrySet().iterator();
            int toRemove = myKillPositions.size() - 10;
            while (trimIt.hasNext() && toRemove > 0) {
                trimIt.next();
                trimIt.remove();
                toRemove--;
            }
        }
    }

    public boolean isNearMyKill(BlockPos itemPos) {
        for (BlockPos killPos : myKillPositions.values()) {
            if (itemPos.closerThan(killPos, 8.0)) {
                return true;
            }
        }
        return false;
    }

    private void startEating(ItemStack food) {
        this.eatingItem = food.copy();
        this.eatingTicks = 0;
        this.setEating(true);
    }

    private void finishEating() {
        FoodProperties food = this.eatingItem.getFoodProperties(this);
        if (food != null) {
            this.setFoodLevel(Math.min(20, this.getFoodLevel() + food.getNutrition()));
            this.setSaturation(Math.min(20, this.getSaturation() + food.getSaturationModifier() * 2 * food.getNutrition()));
            this.heal(food.getNutrition() * 0.5F);
        }
        this.eatingItem = ItemStack.EMPTY;
        this.eatingTicks = 0;
        this.setEating(false);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && this.isTamed()) {
            if (this.getOwnerUUID().map(uuid -> uuid.equals(player.getUUID())).orElse(false)) {
                return false;
            }
        }
        if (source.getEntity() instanceof FakePlayerEntity other) {
            if (this.isTamed() && other.isTamed() &&
                    this.getOwnerUUID().equals(other.getOwnerUUID())) {
                return false;
            }
        }
        // 安全网: 防止 Footwork/Mekanism 等 mod 在 LivingHurtEvent 中检查 capability 时 NPE 崩服
        try {
            return super.hurt(source, amount);
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack weapon = this.getMainHandItem();
        if (!weapon.isEmpty() && weapon.getItem() instanceof TieredItem) {
            damage += 2.0F;
        }
        boolean result = target.hurt(this.damageSources().mobAttack(this), damage);
        if (result) {
            myDamagedEntities.add(target.getUUID());
        }
        return result;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.PLAYER_BIG_FALL, 0.15F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getPlayerUUID().ifPresent(uuid -> tag.putUUID(TAG_PLAYER_UUID, uuid));
        String skinURL = getSkinURL();
        if (!skinURL.isEmpty()) {
            tag.putString(TAG_SKIN_URL, skinURL);
        }
        tag.putBoolean(TAG_IS_SLIM, isSlim());
        String name = getCustomNameStr();
        if (!name.isEmpty()) {
            tag.putString(TAG_CUSTOM_NAME, name);
        }
        tag.putBoolean(TAG_TAMED, isTamed());
        getOwnerUUID().ifPresent(uuid -> tag.putUUID(TAG_OWNER_UUID, uuid));
        tag.putBoolean(TAG_SITTING, isSitting());
        tag.putInt(TAG_FOOD_LEVEL, getFoodLevel());
        tag.putFloat(TAG_SATURATION, getSaturation());
        tag.putInt(TAG_TAMING_PROGRESS, getTamingProgress());

        ListTag invList = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("Slot", i);
            inventory.getItem(i).save(slotTag);
            invList.add(slotTag);
        }
        tag.put(TAG_INVENTORY, invList);

        ListTag armorList = new ListTag();
        for (int i = 0; i < armorInventory.getContainerSize(); i++) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("Slot", i);
            armorInventory.getItem(i).save(slotTag);
            armorList.add(slotTag);
        }
        tag.put(TAG_ARMOR, armorList);

        ListTag offhandList = new ListTag();
        for (int i = 0; i < offhandInventory.getContainerSize(); i++) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("Slot", i);
            offhandInventory.getItem(i).save(slotTag);
            offhandList.add(slotTag);
        }
        tag.put("OffhandItems", offhandList);

        // 持久化 AI 算法管理器（Q-Table、模式等）
        if (algorithmManager != null) {
            CompoundTag algoTag = new CompoundTag();
            algorithmManager.save(algoTag);
            tag.put("QLMAlgorithm", algoTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(TAG_PLAYER_UUID)) {
            setPlayerUUID(tag.getUUID(TAG_PLAYER_UUID));
        }
        if (tag.contains(TAG_SKIN_URL)) {
            setSkinURL(tag.getString(TAG_SKIN_URL));
        }
        setSlim(tag.getBoolean(TAG_IS_SLIM));
        if (tag.contains(TAG_CUSTOM_NAME)) {
            setCustomNameStr(tag.getString(TAG_CUSTOM_NAME));
        }
        setTamed(tag.getBoolean(TAG_TAMED));
        if (tag.hasUUID(TAG_OWNER_UUID)) {
            setOwnerUUID(tag.getUUID(TAG_OWNER_UUID));
        }
        setSitting(tag.getBoolean(TAG_SITTING));
        setFoodLevel(tag.getInt(TAG_FOOD_LEVEL));
        setSaturation(tag.getFloat(TAG_SATURATION));
        if (tag.contains(TAG_TAMING_PROGRESS)) {
            setTamingProgress(tag.getInt(TAG_TAMING_PROGRESS));
        }

        if (tag.contains(TAG_INVENTORY)) {
            ListTag invList = tag.getList(TAG_INVENTORY, 10);
            for (int i = 0; i < invList.size(); i++) {
                CompoundTag slotTag = invList.getCompound(i);
                int slot = slotTag.getInt("Slot");
                inventory.setItem(slot, ItemStack.of(slotTag));
            }
        }
        if (tag.contains(TAG_ARMOR)) {
            ListTag armorList = tag.getList(TAG_ARMOR, 10);
            for (int i = 0; i < armorList.size(); i++) {
                CompoundTag slotTag = armorList.getCompound(i);
                int slot = slotTag.getInt("Slot");
                armorInventory.setItem(slot, ItemStack.of(slotTag));
            }
        }
        if (tag.contains("OffhandItems")) {
            ListTag offhandList = tag.getList("OffhandItems", 10);
            for (int i = 0; i < offhandList.size(); i++) {
                CompoundTag slotTag = offhandList.getCompound(i);
                int slot = slotTag.getInt("Slot");
                offhandInventory.setItem(slot, ItemStack.of(slotTag));
            }
        }

        // 加载 AI 算法管理器（Q-Table、模式等）
        if (tag.contains("QLMAlgorithm")) {
            getAlgorithmManager().load(tag.getCompound("QLMAlgorithm"));
        }
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return this.isTamed() && this.getOwnerUUID().map(uuid -> uuid.equals(player.getUUID())).orElse(false);
    }

    private static class TamableSitGoal extends Goal {
        private final FakePlayerEntity entity;

        public TamableSitGoal(FakePlayerEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return entity.isTamed() && entity.isSitting();
        }

        @Override
        public void start() {
            entity.getNavigation().stop();
        }
    }

    /**
     * 任务感知的近战攻击目标：聊天任务执行期间不抢占导航控制权
     * 仅在无活跃任务或任务为 attack/guard 时才生效
     */
    private static class AITaskAwareMeleeAttackGoal extends MeleeAttackGoal {
        private final FakePlayerEntity entity;

        public AITaskAwareMeleeAttackGoal(FakePlayerEntity entity, double speedModifier, boolean canBeStrafe) {
            super(entity, speedModifier, canBeStrafe);
            this.entity = entity;
        }

        @Override
        public boolean canUse() {
            if (entity.hasActiveTask()) {
                String task = entity.getCurrentTaskName();
                if (task != null) {
                    String taskType = task.split(":", 2)[0];
                    if (!taskType.equals("attack") && !taskType.equals("guard")) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            if (entity.hasActiveTask()) {
                String task = entity.getCurrentTaskName();
                if (task != null) {
                    String taskType = task.split(":", 2)[0];
                    if (!taskType.equals("attack") && !taskType.equals("guard")) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return super.canContinueToUse();
        }
    }

    private static class TamableFollowGoal extends Goal {
        private final FakePlayerEntity entity;
        private final double speedModifier;
        private final float stopDistance;
        private final float startDistance;
        private LivingEntity owner;
        private int timeToRecalcPath;

        public TamableFollowGoal(FakePlayerEntity entity, double speedModifier, float startDistance, float stopDistance) {
            this.entity = entity;
            this.speedModifier = speedModifier;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed() || entity.isSitting()) return false;
            if (entity.hasActiveTask()) return false;
            LivingEntity owner = entity.getOwner();
            if (owner == null) return false;
            if (owner.isSpectator()) return false;
            if (entity.distanceToSqr(owner) < (double) (startDistance * startDistance)) return false;
            this.owner = owner;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (entity.hasActiveTask()) return false;
            if (entity.getNavigation().isDone()) return false;
            return !entity.isSitting() && entity.distanceToSqr(owner) > (double) (stopDistance * stopDistance);
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void tick() {
            entity.getLookControl().setLookAt(owner, 10.0F, (float) entity.getMaxHeadXRot());
            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = this.adjustedTickDelay(10);
                entity.getNavigation().moveTo(owner, this.speedModifier);
            }
        }
    }

    private static class AIEatFoodGoal extends Goal {
        private final FakePlayerEntity entity;
        private int cooldown;

        public AIEatFoodGoal(FakePlayerEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (entity.hasActiveTask()) return false;
            if (entity.getFoodLevel() >= 16) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            return findFoodInInventory() != -1;
        }

        private int findFoodInInventory() {
            for (int i = 0; i < entity.inventory.getContainerSize(); i++) {
                ItemStack stack = entity.inventory.getItem(i);
                if (!stack.isEmpty() && stack.getFoodProperties(entity) != null) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public void start() {
            int slot = findFoodInInventory();
            if (slot != -1) {
                ItemStack food = entity.inventory.getItem(slot).copy();
                entity.inventory.getItem(slot).shrink(1);
                entity.startEating(food);
            }
            cooldown = 40;
        }

        @Override
        public boolean canContinueToUse() {
            return entity.isEating();
        }
    }

    private static class AIEquipGoal extends Goal {
        private final FakePlayerEntity entity;
        private int cooldown;

        public AIEquipGoal(FakePlayerEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (entity.hasActiveTask()) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            return entity.isTamed();
        }

        @Override
        public void start() {
            cooldown = 100;
            boolean equippedSomething = false;
            for (int i = 0; i < entity.inventory.getContainerSize(); i++) {
                ItemStack stack = entity.inventory.getItem(i);
                if (!stack.isEmpty()) {
                    EquipmentSlot slot = getEquipmentSlotForItem(stack);
                    if (slot.getType() == EquipmentSlot.Type.ARMOR || slot == EquipmentSlot.MAINHAND) {
                        ItemStack current = entity.getItemBySlot(slot);
                        if (isBetterThan(current, stack)) {
                            if (!current.isEmpty()) {
                                entity.inventory.setItem(i, current.copy());
                            } else {
                                entity.inventory.setItem(i, ItemStack.EMPTY);
                            }
                            entity.setItemSlot(slot, stack.copy());
                        }
                    }
                }
            }
        }

        private boolean isBetterThan(ItemStack current, ItemStack candidate) {
            if (current.isEmpty()) return true;
            if (current.getItem() instanceof ArmorItem currentArmor && candidate.getItem() instanceof ArmorItem candidateArmor) {
                if (candidateArmor.getDefense() > currentArmor.getDefense()) return true;
                if (candidateArmor.getToughness() > currentArmor.getToughness()) return true;
            }
            if (current.getItem() instanceof TieredItem currentTier && candidate.getItem() instanceof TieredItem candidateTier) {
                @SuppressWarnings("deprecation")
                boolean result = candidateTier.getTier().getLevel() > currentTier.getTier().getLevel();
                if (result) return true;
            }
            return false;
        }
    }

    private static final Block[] MINEABLE_ORES = {
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_QUARTZ_ORE,
    };

    private static class AIMineGoal extends Goal {
        private final FakePlayerEntity entity;
        private BlockPos targetPos;
        private int breakProgress;
        private int cooldown;

        public AIMineGoal(FakePlayerEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed()) return false;
            if (entity.isSitting()) return false;
            if (entity.hasActiveTask()) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            return findNearbyOre() != null;
        }

        private BlockPos findNearbyOre() {
            BlockPos entityPos = entity.blockPosition();
            int radius = 16;
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;

            for (int y = -8; y <= 8; y++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = entityPos.offset(x, y, z);
                        BlockState state = entity.level().getBlockState(pos);
                        for (Block ore : MINEABLE_ORES) {
                            if (state.is(ore)) {
                                double dist = entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    best = pos;
                                }
                            }
                        }
                    }
                }
            }
            return best;
        }

        @Override
        public void start() {
            targetPos = findNearbyOre();
            breakProgress = 0;
        }

        @Override
        public boolean canContinueToUse() {
            if (targetPos == null) return false;
            if (entity.isSitting()) return false;
            BlockState state = entity.level().getBlockState(targetPos);
            boolean isOre = false;
            for (Block ore : MINEABLE_ORES) {
                if (state.is(ore)) {
                    isOre = true;
                    break;
                }
            }
            return isOre && entity.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5) < 64;
        }

        @Override
        public void tick() {
            if (targetPos == null) return;

            double dist = entity.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            if (dist > 4.0) {
                entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0.8D);
                breakProgress = 0;
            } else {
                entity.getNavigation().stop();
                entity.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

                breakProgress++;
                if (breakProgress >= 60) {
                    entity.level().destroyBlock(targetPos, true, entity);
                    breakProgress = 0;
                    cooldown = 40;
                    BlockPos newTarget = findNearbyOre();
                    if (newTarget != null) {
                        targetPos = newTarget;
                    }
                }
            }
        }

        @Override
        public void stop() {
            targetPos = null;
            breakProgress = 0;
        }
    }

    private static final Block[] MINEABLE_LOGS = {
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG,
            Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
            Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG,
            Blocks.CRIMSON_STEM, Blocks.WARPED_STEM,
            Blocks.OAK_WOOD, Blocks.SPRUCE_WOOD, Blocks.BIRCH_WOOD,
            Blocks.JUNGLE_WOOD, Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD,
            Blocks.MANGROVE_WOOD, Blocks.CHERRY_WOOD,
            Blocks.CRIMSON_HYPHAE, Blocks.WARPED_HYPHAE,
            Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG,
            Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_JUNGLE_LOG,
            Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_CHERRY_LOG,
            Blocks.STRIPPED_CRIMSON_STEM, Blocks.STRIPPED_WARPED_STEM,
            Blocks.STRIPPED_OAK_WOOD, Blocks.STRIPPED_SPRUCE_WOOD,
            Blocks.STRIPPED_BIRCH_WOOD, Blocks.STRIPPED_JUNGLE_WOOD,
            Blocks.STRIPPED_ACACIA_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD,
            Blocks.STRIPPED_MANGROVE_WOOD, Blocks.STRIPPED_CHERRY_WOOD,
            Blocks.STRIPPED_CRIMSON_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE,
            Blocks.BAMBOO_BLOCK, Blocks.BAMBOO_PLANKS
    };

    private static class AITreeChopGoal extends Goal {
        private final FakePlayerEntity entity;
        private BlockPos targetPos;
        private int breakProgress;
        private int cooldown;

        public AITreeChopGoal(FakePlayerEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed()) return false;
            if (entity.isSitting()) return false;
            if (entity.hasActiveTask()) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            return findNearbyLog() != null;
        }

        private BlockPos findNearbyLog() {
            BlockPos entityPos = entity.blockPosition();
            int radius = 16;
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;

            for (int y = -2; y <= 16; y++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = entityPos.offset(x, y, z);
                        BlockState state = entity.level().getBlockState(pos);
                        for (Block log : MINEABLE_LOGS) {
                            if (state.is(log)) {
                                double dist = entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    best = pos;
                                }
                            }
                        }
                    }
                }
            }
            return best;
        }

        @Override
        public void start() {
            targetPos = findNearbyLog();
            breakProgress = 0;
        }

        @Override
        public boolean canContinueToUse() {
            if (targetPos == null) return false;
            if (entity.isSitting()) return false;
            BlockState state = entity.level().getBlockState(targetPos);
            boolean isLog = false;
            for (Block log : MINEABLE_LOGS) {
                if (state.is(log)) {
                    isLog = true;
                    break;
                }
            }
            return isLog && entity.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5) < 64;
        }

        @Override
        public void tick() {
            if (targetPos == null) return;

            double dist = entity.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            if (dist > 4.0) {
                entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0.8D);
                breakProgress = 0;
            } else {
                entity.getNavigation().stop();
                entity.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

                breakProgress++;
                if (breakProgress >= 40) {
                    entity.level().destroyBlock(targetPos, true, entity);
                    breakProgress = 0;
                    cooldown = 30;
                    BlockPos newTarget = findNearbyLog();
                    if (newTarget != null) {
                        targetPos = newTarget;
                    }
                }
            }
        }

        @Override
        public void stop() {
            targetPos = null;
            breakProgress = 0;
        }
    }

    private static class AIOwnerHurtByTargetGoal extends TargetGoal {
        private final FakePlayerEntity entity;
        private LivingEntity ownerLastHurtBy;
        private int timestamp;

        public AIOwnerHurtByTargetGoal(FakePlayerEntity entity) {
            super(entity, false);
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed()) return false;
            if (entity.hasActiveTask()) {
                String task = entity.getCurrentTaskName();
                if (task != null) {
                    String taskType = task.split(":", 2)[0];
                    if (!taskType.equals("attack") && !taskType.equals("guard")) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            LivingEntity owner = entity.getOwner();
            if (owner == null) return false;
            this.ownerLastHurtBy = owner.getLastHurtByMob();
            int lastHurtTimestamp = owner.getLastHurtByMobTimestamp();
            return lastHurtTimestamp != this.timestamp
                    && this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT)
                    && !(this.ownerLastHurtBy instanceof FakePlayerEntity other
                    && other.isTamed()
                    && entity.getOwnerUUID().equals(other.getOwnerUUID()));
        }

        @Override
        public void start() {
            this.mob.setTarget(this.ownerLastHurtBy);
            LivingEntity owner = entity.getOwner();
            if (owner != null) {
                this.timestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    private static class AIOwnerHurtTargetGoal extends TargetGoal {
        private final FakePlayerEntity entity;
        private LivingEntity ownerLastHurt;
        private int timestamp;

        public AIOwnerHurtTargetGoal(FakePlayerEntity entity) {
            super(entity, false);
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed()) return false;
            if (entity.hasActiveTask()) {
                String task = entity.getCurrentTaskName();
                if (task != null) {
                    String taskType = task.split(":", 2)[0];
                    if (!taskType.equals("attack") && !taskType.equals("guard")) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            LivingEntity owner = entity.getOwner();
            if (owner == null) return false;
            this.ownerLastHurt = owner.getLastHurtMob();
            int lastHurtTimestamp = owner.getLastHurtMobTimestamp();
            return lastHurtTimestamp != this.timestamp
                    && this.canAttack(this.ownerLastHurt, TargetingConditions.DEFAULT)
                    && !(this.ownerLastHurt instanceof FakePlayerEntity other
                    && other.isTamed()
                    && entity.getOwnerUUID().equals(other.getOwnerUUID()));
        }

        @Override
        public void start() {
            this.mob.setTarget(this.ownerLastHurt);
            LivingEntity owner = entity.getOwner();
            if (owner != null) {
                this.timestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    private static class AIWorkstationGoal extends Goal {
        private final FakePlayerEntity entity;
        private BlockPos workstationPos;
        private int workTimer;
        private int cooldown;
        private WorkstationType stationType;
        private String currentRecipe;
        private int currentRecipeIndex;

        private enum WorkstationType {
            CRAFTING, FURNACE, BLAST_FURNACE, SMOKER,
            SMITHING, FLETCHING, GRINDSTONE, ANVIL,
            BREWING, STONECUTTER, LOOM, CARTOGRAPHY
        }

        private static final String[][] CRAFTING_RECIPES = {
                {"minecraft:oak_log", "minecraft:oak_planks", "4"},
                {"minecraft:spruce_log", "minecraft:spruce_planks", "4"},
                {"minecraft:birch_log", "minecraft:birch_planks", "4"},
                {"minecraft:jungle_log", "minecraft:jungle_planks", "4"},
                {"minecraft:acacia_log", "minecraft:acacia_planks", "4"},
                {"minecraft:dark_oak_log", "minecraft:dark_oak_planks", "4"},
                {"minecraft:mangrove_log", "minecraft:mangrove_planks", "4"},
                {"minecraft:cherry_log", "minecraft:cherry_planks", "4"},
                {"minecraft:bamboo_block", "minecraft:bamboo_planks", "2"},
                {"minecraft:crimson_stem", "minecraft:crimson_planks", "4"},
                {"minecraft:warped_stem", "minecraft:warped_planks", "4"},
                {"minecraft:oak_planks", "minecraft:stick", "4"},
                {"minecraft:stick", "minecraft:oak_planks", "1"},
                {"minecraft:oak_planks", "minecraft:crafting_table", "1"},
                {"minecraft:cobblestone", "minecraft:stone_pickaxe", "1"},
                {"minecraft:cobblestone", "minecraft:stone_axe", "1"},
                {"minecraft:cobblestone", "minecraft:stone_shovel", "1"},
                {"minecraft:cobblestone", "minecraft:stone_hoe", "1"},
                {"minecraft:cobblestone", "minecraft:stone_sword", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_pickaxe", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_axe", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_shovel", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_hoe", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_sword", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_door", "3"},
                {"minecraft:iron_ingot", "minecraft:iron_trapdoor", "2"},
                {"minecraft:iron_ingot", "minecraft:iron_fence", "3"},
                {"minecraft:diamond", "minecraft:diamond_pickaxe", "1"},
                {"minecraft:diamond", "minecraft:diamond_axe", "1"},
                {"minecraft:diamond", "minecraft:diamond_shovel", "1"},
                {"minecraft:diamond", "minecraft:diamond_hoe", "1"},
                {"minecraft:diamond", "minecraft:diamond_sword", "1"},
                {"minecraft:gold_ingot", "minecraft:gold_pickaxe", "1"},
                {"minecraft:gold_ingot", "minecraft:gold_axe", "1"},
                {"minecraft:gold_ingot", "minecraft:gold_shovel", "1"},
                {"minecraft:gold_ingot", "minecraft:gold_hoe", "1"},
                {"minecraft:gold_ingot", "minecraft:gold_sword", "1"},
                {"minecraft:oak_planks", "minecraft:wooden_pickaxe", "1"},
                {"minecraft:oak_planks", "minecraft:wooden_axe", "1"},
                {"minecraft:oak_planks", "minecraft:wooden_shovel", "1"},
                {"minecraft:oak_planks", "minecraft:wooden_hoe", "1"},
                {"minecraft:oak_planks", "minecraft:wooden_sword", "1"},
                {"minecraft:oak_planks", "minecraft:oak_door", "3"},
                {"minecraft:oak_planks", "minecraft:oak_trapdoor", "2"},
                {"minecraft:oak_planks", "minecraft:oak_fence", "3"},
                {"minecraft:oak_planks", "minecraft:oak_stairs", "4"},
                {"minecraft:oak_planks", "minecraft:oak_slab", "6"},
                {"minecraft:cobblestone", "minecraft:stone_stairs", "4"},
                {"minecraft:cobblestone", "minecraft:stone_slab", "6"},
                {"minecraft:cobblestone", "minecraft:stone_bricks", "4"},
                {"minecraft:iron_ingot", "minecraft:bucket", "1"},
                {"minecraft:iron_ingot", "minecraft:shears", "1"},
                {"minecraft:iron_ingot", "minecraft:flint_and_steel", "1"},
                {"minecraft:iron_ingot", "minecraft:compass", "1"},
                {"minecraft:iron_ingot", "minecraft:cartography_table", "1"},
                {"minecraft:iron_ingot", "minecraft:loom", "1"},
                {"minecraft:iron_ingot", "minecraft:stonecutter", "1"},
                {"minecraft:diamond", "minecraft:enchanted_book", "1"},
                {"minecraft:diamond", "minecraft:anvil", "1"},
                {"minecraft:diamond", "minecraft:grindstone", "1"},
                {"minecraft:diamond", "minecraft:smithing_table", "1"},
                {"minecraft:diamond", "minecraft:fletching_table", "1"},
                {"minecraft:diamond", "minecraft:brewing_stand", "1"},
                {"minecraft:coal", "minecraft:torch", "4"},
                {"minecraft:coal", "minecraft:campfire", "1"},
                {"minecraft:redstone", "minecraft:redstone_torch", "1"},
                {"minecraft:redstone", "minecraft:repeater", "1"},
                {"minecraft:redstone", "minecraft:comparator", "1"},
                {"minecraft:gunpowder", "minecraft:tnt", "4"},
                {"minecraft:gunpowder", "minecraft:firework_rocket", "3"},
                {"minecraft:string", "minecraft:bow", "1"},
                {"minecraft:string", "minecraft:fishing_rod", "1"},
                {"minecraft:string", "minecraft:lead", "4"},
                {"minecraft:string", "minecraft:tripwire_hook", "2"},
                {"minecraft:leather", "minecraft:saddle", "1"},
                {"minecraft:leather", "minecraft:leather_boots", "1"},
                {"minecraft:leather", "minecraft:leather_leggings", "1"},
                {"minecraft:leather", "minecraft:leather_chestplate", "1"},
                {"minecraft:leather", "minecraft:leather_helmet", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_boots", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_leggings", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_chestplate", "1"},
                {"minecraft:iron_ingot", "minecraft:iron_helmet", "1"},
                {"minecraft:diamond", "minecraft:diamond_boots", "1"},
                {"minecraft:diamond", "minecraft:diamond_leggings", "1"},
                {"minecraft:diamond", "minecraft:diamond_chestplate", "1"},
                {"minecraft:diamond", "minecraft:diamond_helmet", "1"},
                {"minecraft:gold_ingot", "minecraft:golden_boots", "1"},
                {"minecraft:gold_ingot", "minecraft:golden_leggings", "1"},
                {"minecraft:gold_ingot", "minecraft:golden_chestplate", "1"},
                {"minecraft:gold_ingot", "minecraft:golden_helmet", "1"},
                {"minecraft:oak_planks", "minecraft:shield", "1"},
                {"minecraft:oak_planks", "minecraft:chest", "1"},
                {"minecraft:oak_planks", "minecraft:trapped_chest", "1"},
                {"minecraft:oak_planks", "minecraft:crafting_table", "1"},
                {"minecraft:oak_planks", "minecraft:furnace", "1"},
                {"minecraft:brick", "minecraft:brick_stairs", "4"},
                {"minecraft:brick", "minecraft:brick_slab", "6"},
                {"minecraft:brick", "minecraft:brick_wall", "6"},
                {"minecraft:netherrack", "minecraft:nether_brick", "2"},
                {"minecraft:nether_brick", "minecraft:nether_brick_stairs", "4"},
                {"minecraft:nether_brick", "minecraft:nether_brick_fence", "6"},
                {"minecraft:quartz", "minecraft:quartz_stairs", "4"},
                {"minecraft:quartz", "minecraft:quartz_slab", "6"},
                {"minecraft:ender_pearl", "minecraft:ender_eye", "1"},
                {"minecraft:ender_pearl", "minecraft:ender_chest", "1"},
                {"minecraft:glass", "minecraft:glass_pane", "16"},
                {"minecraft:glass", "minecraft:stained_glass", "8"},
                {"minecraft:sand", "minecraft:glass", "1"},
                {"minecraft:clay_ball", "minecraft:brick", "1"},
                {"minecraft:clay_ball", "minecraft:terracotta", "1"},
                {"minecraft:stone", "minecraft:smooth_stone", "1"},
                {"minecraft:stone", "minecraft:cobblestone", "1"},
                {"minecraft:gravel", "minecraft:flint", "1"},
                {"minecraft:gravel", "minecraft:gravel", "1"},
                {"minecraft:bone", "minecraft:bone_meal", "3"},
                {"minecraft:bone", "minecraft:dye", "3"},
                {"minecraft:ink_sac", "minecraft:black_dye", "1"},
                {"minecraft:cactus", "minecraft:green_dye", "1"},
                {"minecraft:lapis_lazuli", "minecraft:blue_dye", "1"},
                {"minecraft:red_mushroom", "minecraft:red_dye", "1"},
                {"minecraft:brown_mushroom", "minecraft:brown_dye", "1"},
                {"minecraft:rose_bush", "minecraft:red_dye", "2"},
                {"minecraft:dandelion", "minecraft:yellow_dye", "2"},
                {"minecraft:blue_orchid", "minecraft:light_blue_dye", "1"},
                {"minecraft:allium", "minecraft:purple_dye", "1"},
                {"minecraft:azure_bluet", "minecraft:light_gray_dye", "1"},
                {"minecraft:cornflower", "minecraft:blue_dye", "1"},
                {"minecraft:lily_of_the_valley", "minecraft:white_dye", "1"},
                {"minecraft:wither_rose", "minecraft:black_dye", "1"},
                {"minecraft:oak_leaves", "minecraft:oak_sapling", "1"},
                {"minecraft:spruce_leaves", "minecraft:spruce_sapling", "1"},
                {"minecraft:birch_leaves", "minecraft:birch_sapling", "1"},
                {"minecraft:jungle_leaves", "minecraft:jungle_sapling", "1"},
                {"minecraft:acacia_leaves", "minecraft:acacia_sapling", "1"},
                {"minecraft:dark_oak_leaves", "minecraft:dark_oak_sapling", "1"},
                {"minecraft:mangrove_leaves", "minecraft:mangrove_propagule", "1"},
                {"minecraft:cherry_leaves", "minecraft:cherry_sapling", "1"},
                {"minecraft:crimson_fungus", "minecraft:crimson_fungus", "1"},
                {"minecraft:warped_fungus", "minecraft:warped_fungus", "1"},
        };

        private static final String[][] SMELTING_RECIPES = {
                {"minecraft:raw_iron", "minecraft:iron_ingot"},
                {"minecraft:raw_gold", "minecraft:gold_ingot"},
                {"minecraft:raw_copper", "minecraft:copper_ingot"},
                {"minecraft:ancient_debris", "minecraft:netherite_scrap"},
                {"minecraft:iron_ore", "minecraft:iron_ingot"},
                {"minecraft:deepslate_iron_ore", "minecraft:iron_ingot"},
                {"minecraft:gold_ore", "minecraft:gold_ingot"},
                {"minecraft:deepslate_gold_ore", "minecraft:gold_ingot"},
                {"minecraft:copper_ore", "minecraft:copper_ingot"},
                {"minecraft:deepslate_copper_ore", "minecraft:copper_ingot"},
                {"minecraft:nether_gold_ore", "minecraft:gold_ingot"},
                {"minecraft:diamond_ore", "minecraft:diamond"},
                {"minecraft:deepslate_diamond_ore", "minecraft:diamond"},
                {"minecraft:emerald_ore", "minecraft:emerald"},
                {"minecraft:deepslate_emerald_ore", "minecraft:emerald"},
                {"minecraft:lapis_ore", "minecraft:lapis_lazuli"},
                {"minecraft:deepslate_lapis_ore", "minecraft:lapis_lazuli"},
                {"minecraft:redstone_ore", "minecraft:redstone"},
                {"minecraft:deepslate_redstone_ore", "minecraft:redstone"},
                {"minecraft:coal_ore", "minecraft:coal"},
                {"minecraft:deepslate_coal_ore", "minecraft:coal"},
                {"minecraft:nether_quartz_ore", "minecraft:quartz"},
                {"minecraft:clay_ball", "minecraft:brick"},
                {"minecraft:clay", "minecraft:terracotta"},
                {"minecraft:netherrack", "minecraft:nether_brick"},
                {"minecraft:sand", "minecraft:glass"},
                {"minecraft:red_sand", "minecraft:glass"},
                {"minecraft:cobblestone", "minecraft:stone"},
                {"minecraft:stone", "minecraft:smooth_stone"},
                {"minecraft:sandstone", "minecraft:smooth_sandstone"},
                {"minecraft:red_sandstone", "minecraft:smooth_red_sandstone"},
                {"minecraft:quartz_block", "minecraft:smooth_quartz"},
                {"minecraft:basalt", "minecraft:smooth_basalt"},
                {"minecraft:white_terracotta", "minecraft:white_glazed_terracotta"},
                {"minecraft:orange_terracotta", "minecraft:orange_glazed_terracotta"},
                {"minecraft:magenta_terracotta", "minecraft:magenta_glazed_terracotta"},
                {"minecraft:light_blue_terracotta", "minecraft:light_blue_glazed_terracotta"},
                {"minecraft:yellow_terracotta", "minecraft:yellow_glazed_terracotta"},
                {"minecraft:lime_terracotta", "minecraft:lime_glazed_terracotta"},
                {"minecraft:pink_terracotta", "minecraft:pink_glazed_terracotta"},
                {"minecraft:gray_terracotta", "minecraft:gray_glazed_terracotta"},
                {"minecraft:light_gray_terracotta", "minecraft:light_gray_glazed_terracotta"},
                {"minecraft:cyan_terracotta", "minecraft:cyan_glazed_terracotta"},
                {"minecraft:purple_terracotta", "minecraft:purple_glazed_terracotta"},
                {"minecraft:blue_terracotta", "minecraft:blue_glazed_terracotta"},
                {"minecraft:brown_terracotta", "minecraft:brown_glazed_terracotta"},
                {"minecraft:green_terracotta", "minecraft:green_glazed_terracotta"},
                {"minecraft:red_terracotta", "minecraft:red_glazed_terracotta"},
                {"minecraft:black_terracotta", "minecraft:black_glazed_terracotta"},
                {"minecraft:raw_beef", "minecraft:cooked_beef"},
                {"minecraft:raw_porkchop", "minecraft:cooked_porkchop"},
                {"minecraft:raw_chicken", "minecraft:cooked_chicken"},
                {"minecraft:raw_mutton", "minecraft:cooked_mutton"},
                {"minecraft:raw_rabbit", "minecraft:cooked_rabbit"},
                {"minecraft:raw_cod", "minecraft:cooked_cod"},
                {"minecraft:raw_salmon", "minecraft:cooked_salmon"},
                {"minecraft:potato", "minecraft:baked_potato"},
                {"minecraft:kelp", "minecraft:dried_kelp"},
                {"minecraft:wet_sponge", "minecraft:sponge"},
                {"minecraft:cactus", "minecraft:green_dye"},
                {"minecraft:sea_pickle", "minecraft:lime_dye"},
                {"minecraft:chorus_fruit", "minecraft:popped_chorus_fruit"},
        };

        private static final String[] LOGS = {
                "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
                "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
                "minecraft:mangrove_log", "minecraft:cherry_log", "minecraft:crimson_stem",
                "minecraft:warped_stem", "minecraft:oak_wood", "minecraft:spruce_wood",
                "minecraft:birch_wood", "minecraft:jungle_wood", "minecraft:acacia_wood",
                "minecraft:dark_oak_wood", "minecraft:mangrove_wood", "minecraft:cherry_wood",
                "minecraft:crimson_hyphae", "minecraft:warped_hyphae",
                "minecraft:stripped_oak_log", "minecraft:stripped_spruce_log",
                "minecraft:stripped_birch_log", "minecraft:stripped_jungle_log",
                "minecraft:stripped_acacia_log", "minecraft:stripped_dark_oak_log",
                "minecraft:stripped_mangrove_log", "minecraft:stripped_cherry_log",
                "minecraft:stripped_crimson_stem", "minecraft:stripped_warped_stem",
                "minecraft:stripped_oak_wood", "minecraft:stripped_spruce_wood",
                "minecraft:stripped_birch_wood", "minecraft:stripped_jungle_wood",
                "minecraft:stripped_acacia_wood", "minecraft:stripped_dark_oak_wood",
                "minecraft:stripped_mangrove_wood", "minecraft:stripped_cherry_wood",
                "minecraft:stripped_crimson_hyphae", "minecraft:stripped_warped_hyphae",
        };

        public AIWorkstationGoal(FakePlayerEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed()) return false;
            if (entity.isSitting()) return false;
            if (entity.hasActiveTask()) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            return findBestWorkstation();
        }

        private boolean findBestWorkstation() {
            BlockPos entityPos = entity.blockPosition();
            int radius = 8;
            BlockPos best = null;
            WorkstationType bestType = null;
            double bestDist = Double.MAX_VALUE;

            for (int y = -2; y <= 2; y++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = entityPos.offset(x, y, z);
                        BlockState state = entity.level().getBlockState(pos);
                        WorkstationType type = getWorkstationType(state);
                        if (type == null) continue;
                        String recipe = null;
                        int recipeIdx = -1;

                        if (type == WorkstationType.CRAFTING && hasCraftingRecipe() != null) {
                            recipe = hasCraftingRecipe();
                            recipeIdx = 0;
                        } else if ((type == WorkstationType.FURNACE || type == WorkstationType.BLAST_FURNACE || type == WorkstationType.SMOKER) && hasSmeltingRecipe(type) != null) {
                            recipe = hasSmeltingRecipe(type);
                            recipeIdx = 0;
                        } else if (type == WorkstationType.SMITHING && hasMaterial("minecraft:diamond_sword") && hasMaterial("minecraft:netherite_ingot")) {
                            recipe = "netherite_sword";
                            recipeIdx = 0;
                        } else if (type == WorkstationType.FLETCHING && hasMaterial("minecraft:flint") && hasMaterial("minecraft:stick") && hasMaterial("minecraft:feather")) {
                            recipe = "arrows";
                            recipeIdx = 0;
                        } else if (type == WorkstationType.STONECUTTER && hasMaterial("minecraft:stone")) {
                            recipe = "stone_bricks";
                            recipeIdx = 0;
                        } else {
                            continue;
                        }

                        double dist = entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = pos;
                            bestType = type;
                            currentRecipe = recipe;
                            currentRecipeIndex = recipeIdx;
                        }
                    }
                }
            }
            if (best != null) {
                workstationPos = best;
                stationType = bestType;
                return true;
            }
            return false;
        }

        private WorkstationType getWorkstationType(BlockState state) {
            Block block = state.getBlock();
            if (block instanceof CraftingTableBlock) return WorkstationType.CRAFTING;
            if (block instanceof BlastFurnaceBlock) return WorkstationType.BLAST_FURNACE;
            if (block instanceof SmokerBlock) return WorkstationType.SMOKER;
            if (block instanceof FurnaceBlock) return WorkstationType.FURNACE;
            if (block instanceof SmithingTableBlock) return WorkstationType.SMITHING;
            if (block instanceof FletchingTableBlock) return WorkstationType.FLETCHING;
            if (block instanceof GrindstoneBlock) return WorkstationType.GRINDSTONE;
            if (block instanceof AnvilBlock) return WorkstationType.ANVIL;
            if (block instanceof BrewingStandBlock) return WorkstationType.BREWING;
            if (block instanceof StonecutterBlock) return WorkstationType.STONECUTTER;
            if (block instanceof LoomBlock) return WorkstationType.LOOM;
            if (block instanceof CartographyTableBlock) return WorkstationType.CARTOGRAPHY;
            return null;
        }

        private String hasCraftingRecipe() {
            for (String[] recipe : CRAFTING_RECIPES) {
                if (recipe.length >= 2 && hasMaterial(recipe[0])) {
                    return recipe[0] + "|" + recipe[1] + "|" + (recipe.length > 2 ? recipe[2] : "1");
                }
            }
            return null;
        }

        private boolean hasAllMaterials(String materials) {
            String[] parts = materials.split(",");
            for (String mat : parts) {
                if (!hasMaterial(mat.trim())) return false;
            }
            return true;
        }

        private boolean consumeMaterials(String materials) {
            String[] parts = materials.split(",");
            for (String mat : parts) {
                String materialId = mat.trim();
                int slot = findMaterialSlot(materialId);
                if (slot == -1) return false;
                entity.inventory.getItem(slot).shrink(1);
            }
            return true;
        }

        private String hasSmeltingRecipe(WorkstationType type) {
            for (String[] recipe : SMELTING_RECIPES) {
                if (type == WorkstationType.SMOKER && !isFoodItem(recipe[0])) continue;
                if (type == WorkstationType.BLAST_FURNACE && !isOreItem(recipe[0])) continue;
                if (hasMaterial(recipe[0])) {
                    return recipe[0] + "|" + recipe[1] + "|1";
                }
            }
            return null;
        }

        private boolean hasFuel() {
            return hasMaterial("minecraft:coal") || hasMaterial("minecraft:charcoal") ||
                   hasMaterial("minecraft:coal_block") || hasMaterial("minecraft:oak_planks") ||
                   hasMaterial("minecraft:stick") || hasMaterial("minecraft:oak_log") ||
                   hasMaterial("minecraft:spruce_log") || hasMaterial("minecraft:birch_log") ||
                   hasMaterial("minecraft:jungle_log") || hasMaterial("minecraft:acacia_log") ||
                   hasMaterial("minecraft:dark_oak_log");
        }

        private boolean consumeFuel() {
            String[] fuels = {"minecraft:coal", "minecraft:charcoal", "minecraft:coal_block",
                              "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
                              "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
                              "minecraft:stick", "minecraft:oak_log", "minecraft:spruce_log",
                              "minecraft:birch_log", "minecraft:jungle_log", "minecraft:acacia_log",
                              "minecraft:dark_oak_log"};
            for (String fuel : fuels) {
                int slot = findMaterialSlot(fuel);
                if (slot != -1) {
                    entity.inventory.getItem(slot).shrink(1);
                    return true;
                }
            }
            return false;
        }

        private boolean isFoodItem(String itemId) {
            return itemId.contains("raw_") || itemId.contains("potato") || itemId.contains("kelp") || itemId.contains("chorus");
        }

        private boolean isOreItem(String itemId) {
            return itemId.contains("_ore") || itemId.contains("raw_") || itemId.contains("ancient_debris");
        }

        private boolean hasMaterial(String itemId) {
            ResourceLocation targetId = ResourceLocation.tryParse(itemId);
            if (targetId == null) return false;
            for (int i = 0; i < entity.inventory.getContainerSize(); i++) {
                ItemStack stack = entity.inventory.getItem(i);
                if (!stack.isEmpty() && ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(targetId)) {
                    return true;
                }
            }
            return false;
        }

        private int findMaterialSlot(String itemId) {
            ResourceLocation targetId = ResourceLocation.tryParse(itemId);
            if (targetId == null) return -1;
            for (int i = 0; i < entity.inventory.getContainerSize(); i++) {
                ItemStack stack = entity.inventory.getItem(i);
                if (!stack.isEmpty() && ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(targetId)) {
                    return i;
                }
            }
            return -1;
        }

        private int findEmptySlot() {
            for (int i = 0; i < entity.inventory.getContainerSize(); i++) {
                if (entity.inventory.getItem(i).isEmpty()) return i;
            }
            return -1;
        }

        private void addToInventory(ItemStack result) {
            if (result.isEmpty()) return;
            for (int i = 0; i < entity.inventory.getContainerSize(); i++) {
                ItemStack slotStack = entity.inventory.getItem(i);
                if (slotStack.isEmpty()) {
                    entity.inventory.setItem(i, result);
                    return;
                }
                if (ItemStack.isSameItemSameTags(slotStack, result) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                    int canAdd = Math.min(result.getCount(), slotStack.getMaxStackSize() - slotStack.getCount());
                    slotStack.grow(canAdd);
                    return;
                }
            }
        }

        private void spawnWorkParticles() {
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        workstationPos.getX() + 0.5, workstationPos.getY() + 1.2, workstationPos.getZ() + 0.5,
                        5, 0.3, 0.3, 0.3, 0);
            }
        }

        @Override
        public void start() {
            workTimer = 0;
        }

        @Override
        public boolean canContinueToUse() {
            if (workstationPos == null || stationType == null) return false;
            if (entity.isSitting()) return false;
            BlockState state = entity.level().getBlockState(workstationPos);
            if (getWorkstationType(state) != stationType) return false;
            return workTimer < 100;
        }

        @Override
        public void tick() {
            if (workstationPos == null) return;

            double dist = entity.distanceToSqr(workstationPos.getX() + 0.5, workstationPos.getY() + 0.5, workstationPos.getZ() + 0.5);
            if (dist > 4.0) {
                entity.getNavigation().moveTo(workstationPos.getX() + 0.5, workstationPos.getY(), workstationPos.getZ() + 0.5, 0.8D);
                workTimer = 0;
            } else {
                entity.getNavigation().stop();
                entity.getLookControl().setLookAt(workstationPos.getX() + 0.5, workstationPos.getY() + 0.5, workstationPos.getZ() + 0.5);

                workTimer++;
                if (workTimer >= 60) {
                    performWork();
                    workTimer = 0;
                    cooldown = 200;
                }
            }
        }

        private void performWork() {
            if (currentRecipe == null) return;
            String[] parts = currentRecipe.split("\\|");
            if (parts.length < 2) return;

            String materialId = parts[0];
            String resultId = parts[1];
            int resultCount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;

            int slot = findMaterialSlot(materialId);
            if (slot == -1) return;

            switch (stationType) {
                case CRAFTING:
                    entity.inventory.getItem(slot).shrink(1);
                    ItemStack craftResult = findItemStack(resultId, resultCount);
                    addToInventory(craftResult);
                    spawnWorkParticles();
                    break;

                case FURNACE:
                case BLAST_FURNACE:
                case SMOKER:
                    if (!hasFuel()) return;
                    consumeFuel();
                    entity.inventory.getItem(slot).shrink(1);
                    ItemStack smeltResult = findItemStack(resultId, resultCount);
                    addToInventory(smeltResult);
                    spawnWorkParticles();
                    break;

                case SMITHING:
                    int diamondSlot = findMaterialSlot("minecraft:diamond_sword");
                    int netheriteSlot = findMaterialSlot("minecraft:netherite_ingot");
                    if (diamondSlot != -1 && netheriteSlot != -1) {
                        entity.inventory.getItem(diamondSlot).shrink(1);
                        entity.inventory.getItem(netheriteSlot).shrink(1);
                        addToInventory(new ItemStack(Items.NETHERITE_SWORD));
                        spawnWorkParticles();
                    }
                    break;

                case FLETCHING:
                    int flintSlot = findMaterialSlot("minecraft:flint");
                    int stickSlot = findMaterialSlot("minecraft:stick");
                    int featherSlot = findMaterialSlot("minecraft:feather");
                    if (flintSlot != -1 && stickSlot != -1 && featherSlot != -1) {
                        entity.inventory.getItem(flintSlot).shrink(1);
                        entity.inventory.getItem(stickSlot).shrink(1);
                        entity.inventory.getItem(featherSlot).shrink(1);
                        addToInventory(new ItemStack(Items.ARROW, 4));
                        spawnWorkParticles();
                    }
                    break;

                case STONECUTTER:
                    addToInventory(new ItemStack(Items.STONE_BRICKS, 1));
                    spawnWorkParticles();
                    break;

                default:
                    break;
            }
        }

        private ItemStack findItemStack(String itemId, int count) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) return ItemStack.EMPTY;
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item, count);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void stop() {
            workstationPos = null;
            stationType = null;
            currentRecipe = null;
            workTimer = 0;
        }
    }

    private static class AIPickupLootGoal extends Goal {
        private final FakePlayerEntity entity;
        private ItemEntity targetItem;

        public AIPickupLootGoal(FakePlayerEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed()) return false;
            if (entity.isSitting()) return false;
            if (entity.hasActiveTask()) return false;

            List<ItemEntity> items = entity.level().getEntitiesOfClass(
                    ItemEntity.class,
                    entity.getBoundingBox().inflate(12.0, 4.0, 12.0)
            );

            if (items.isEmpty()) return false;

            double bestDist = Double.MAX_VALUE;
            for (ItemEntity item : items) {
                double dist = entity.distanceToSqr(item);
                if (dist < bestDist) {
                    bestDist = dist;
                    targetItem = item;
                }
            }
            return targetItem != null && targetItem.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return targetItem != null && targetItem.isAlive()
                    && !entity.isSitting();
        }

        @Override
        public void start() {
            if (targetItem != null) {
                entity.getNavigation().moveTo(targetItem, 1.0D);
            }
        }

        @Override
        public void tick() {
            if (targetItem == null) return;

            double dist = entity.distanceToSqr(targetItem);
            if (dist > 2.5) {
                entity.getNavigation().moveTo(targetItem, 1.0D);
            } else {
                entity.getNavigation().stop();
                pickupItem(targetItem);
                targetItem = null;
            }
        }

        private void pickupItem(ItemEntity itemEntity) {
            if (!itemEntity.isAlive()) return;

            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) return;

            ItemStack remaining = stack.copy();

            for (int i = 0; i < entity.inventory.getContainerSize(); i++) {
                ItemStack slot = entity.inventory.getItem(i);
                if (slot.isEmpty()) {
                    entity.inventory.setItem(i, remaining);
                    itemEntity.discard();
                    entity.level().playSound(null, entity.blockPosition(),
                            SoundEvents.ITEM_PICKUP, entity.getSoundSource(), 0.2F, 1.0F);
                    return;
                }
                if (ItemStack.isSameItem(slot, remaining) && ItemStack.isSameItemSameTags(slot, remaining)) {
                    int maxStack = slot.getMaxStackSize();
                    int canAdd = maxStack - slot.getCount();
                    if (canAdd > 0) {
                        int toAdd = Math.min(canAdd, remaining.getCount());
                        slot.grow(toAdd);
                        remaining.shrink(toAdd);
                        if (remaining.isEmpty()) {
                            itemEntity.discard();
                            entity.level().playSound(null, entity.blockPosition(),
                                    SoundEvents.ITEM_PICKUP, entity.getSoundSource(), 0.2F, 1.0F);
                            return;
                        }
                    }
                }
            }

            itemEntity.setItem(remaining);
        }

        @Override
        public void stop() {
            targetItem = null;
        }
    }

    /**
     * 自由漫步目标：参考 player2npc 的 TimeoutWanderTask
     * 未驯服AI在世界上自由活动；驯服后作为空闲漫步
     */
    private static class AIWanderFreelyGoal extends Goal {
        private final FakePlayerEntity entity;
        private final double speedModifier;
        private int cooldown;
        private int wanderTicks;

        public AIWanderFreelyGoal(FakePlayerEntity entity, double speedModifier) {
            this.entity = entity;
            this.speedModifier = speedModifier;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (entity.hasActiveTask()) return false;
            if (entity.isSitting()) return false;
            // 驯服后的AI只在主人不在附近时自由漫步
            if (entity.isTamed()) {
                LivingEntity owner = entity.getOwner();
                if (owner != null && entity.distanceToSqr(owner) < 64.0D) {
                    return false; // 主人在附近，让 TamableFollowGoal 处理
                }
            }
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            // 1/60 概率触发漫步
            if (entity.getRandom().nextInt(60) != 0) return false;

            Vec3 target = DefaultRandomPos.getPos(entity, 12, 7);
            if (target == null) return false;
            return entity.getNavigation().moveTo(target.x, target.y, target.z, speedModifier);
        }

        @Override
        public boolean canContinueToUse() {
            return !entity.getNavigation().isDone() && wanderTicks < 200 && !entity.isSitting();
        }

        @Override
        public void tick() {
            wanderTicks++;
        }

        @Override
        public void stop() {
            wanderTicks = 0;
            cooldown = entity.getRandom().nextInt(40) + 20;
        }
    }

    /**
     * 好奇靠近目标：未驯服AI偶尔走向附近玩家，便于玩家驯服
     */
    private static class AICuriousApproachGoal extends Goal {
        private final FakePlayerEntity entity;
        private Player nearestPlayer;
        private int cooldown;
        private int approachTicks;

        public AICuriousApproachGoal(FakePlayerEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (entity.isTamed()) return false;
            if (entity.hasActiveTask()) return false;
            if (entity.isSitting()) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            // 1/120 概率触发好奇靠近
            if (entity.getRandom().nextInt(120) != 0) return false;

            nearestPlayer = entity.level().getNearestPlayer(entity, 16.0D);
            return nearestPlayer != null && !nearestPlayer.isSpectator() && !nearestPlayer.isCreative();
        }

        @Override
        public boolean canContinueToUse() {
            return nearestPlayer != null && nearestPlayer.isAlive()
                    && approachTicks < 100
                    && entity.distanceToSqr(nearestPlayer) > 4.0D;
        }

        @Override
        public void start() {
            approachTicks = 0;
            if (nearestPlayer != null) {
                entity.getNavigation().moveTo(nearestPlayer, 0.7D);
            }
        }

        @Override
        public void tick() {
            approachTicks++;
            if (nearestPlayer != null) {
                entity.getLookControl().setLookAt(nearestPlayer, 30.0F, 30.0F);
                if (entity.getNavigation().isDone() && entity.distanceToSqr(nearestPlayer) > 4.0D) {
                    entity.getNavigation().moveTo(nearestPlayer, 0.7D);
                }
            }
        }

        @Override
        public void stop() {
            approachTicks = 0;
            cooldown = entity.getRandom().nextInt(100) + 60;
            nearestPlayer = null;
        }
    }

    /**
     * 条件攻击 Monster 目标：仅对已驯服AI生效
     */
    private static class AIHuntMonsterGoal extends TargetGoal {
        private final FakePlayerEntity entity;
        private LivingEntity target;

        public AIHuntMonsterGoal(FakePlayerEntity entity) {
            super(entity, false);
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed()) return false;
            if (entity.hasActiveTask()) return false;

            List<Monster> monsters = entity.level().getEntitiesOfClass(Monster.class,
                    entity.getBoundingBox().inflate(16.0D, 8.0D, 16.0D));
            LivingEntity best = null;
            double bestDist = 256.0D;
            for (Monster m : monsters) {
                if (!m.isAlive()) continue;
                double dist = entity.distanceToSqr(m);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = m;
                }
            }
            if (best != null && canAttack(best, TargetingConditions.DEFAULT)) {
                target = best;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            mob.setTarget(target);
            super.start();
        }
    }

    /**
     * 条件攻击 Animal 目标：仅对已驯服AI生效
     */
    private static class AIHuntAnimalGoal extends TargetGoal {
        private final FakePlayerEntity entity;
        private LivingEntity target;

        public AIHuntAnimalGoal(FakePlayerEntity entity) {
            super(entity, false);
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!entity.isTamed()) return false;
            if (entity.hasActiveTask()) return false;

            List<Animal> animals = entity.level().getEntitiesOfClass(Animal.class,
                    entity.getBoundingBox().inflate(16.0D, 8.0D, 16.0D));
            LivingEntity best = null;
            double bestDist = 256.0D;
            for (Animal a : animals) {
                if (!a.isAlive()) continue;
                double dist = entity.distanceToSqr(a);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = a;
                }
            }
            if (best != null && canAttack(best, TargetingConditions.DEFAULT)) {
                target = best;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            mob.setTarget(target);
            super.start();
        }
    }

    /**
     * 任务感知随机漫步 — 替换原版 WaterAvoidingRandomStrollGoal
     * 活跃任务期间禁用，避免与 Task 系统的 navigation.moveTo 冲突
     */
    private static class AITaskAwareStrollGoal extends WaterAvoidingRandomStrollGoal {
        private final FakePlayerEntity entity;

        public AITaskAwareStrollGoal(FakePlayerEntity entity, double speedModifier) {
            super(entity, speedModifier);
            this.entity = entity;
        }

        @Override
        public boolean canUse() {
            if (entity.hasActiveTask()) return false;
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            if (entity.hasActiveTask()) return false;
            return super.canContinueToUse();
        }
    }

    /**
     * 任务感知随机看周围 — 替换原版 RandomLookAroundGoal
     * 活跃任务期间禁用，避免干扰 Task 系统的 lookControl
     */
    private static class AITaskAwareLookGoal extends RandomLookAroundGoal {
        private final FakePlayerEntity entity;

        public AITaskAwareLookGoal(FakePlayerEntity entity) {
            super(entity);
            this.entity = entity;
        }

        @Override
        public boolean canUse() {
            if (entity.hasActiveTask()) return false;
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            if (entity.hasActiveTask()) return false;
            return super.canContinueToUse();
        }
    }
}
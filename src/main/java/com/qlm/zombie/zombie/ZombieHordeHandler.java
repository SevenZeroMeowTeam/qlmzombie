package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 僵尸潮系统：
 * - 每28天触发一次僵尸潮
 * - 共5波，每波敌人不同
 * - 第3波：召唤小Boss x1（500血，含技能）
 * - 第4波：小Boss（500血）+ 支援
 * - 第5波：大Boss（10000血，3阶段）+ 精英
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ZombieHordeHandler {

    private static final int HORDE_INTERVAL = 28;
    private static final int SPAWN_INTERVAL_TICKS = 60; // 每3秒一波

    // NBT标识
    public static final String NBT_IS_BOSS = "qlm_is_boss";
    public static final String NBT_BOSS_TYPE = "qlm_boss_type"; // "mini" 或 "big"
    public static final String NBT_BOSS_PHASE = "qlm_boss_phase"; // 1, 2, 3
    public static final String NBT_BOSS_MAX_PHASE = "qlm_boss_max_phase";

    private static boolean hordeActive = false;
    private static int currentWave = 0;
    private static int currentDay = -1;
    private static int tickCounter = 0;
    private static boolean daybreakCleared = false;
    // 僵尸潮生物标记
    public static final String NBT_HORDE_MOB = "qlm_horde_mob";
    private static final List<ServerPlayer> hordePlayers = new ArrayList<>();
    private static final ServerBossEvent hordeBossBar = new ServerBossEvent(
        Component.literal("§4§l⚔ 僵尸潮 ⚔"),
        BossEvent.BossBarColor.RED,
        BossEvent.BossBarOverlay.PROGRESS
    );

    // 大Boss阶段追踪
    private static final Map<Integer, Integer> bigBossPhaseMap = new HashMap<>(); // entityId -> phase
    private static int bigBossEntityId = -1;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;

        // 检查是否应该触发僵尸潮（每28天，夜晚开始时）
        if (!hordeActive && day >= HORDE_INTERVAL && day % HORDE_INTERVAL == 0 && day != currentDay) {
            if (timeOfDay >= 13000 && timeOfDay <= 13500) {
                startHorde(overworld, day);
            }
        }

        // 处理进行中的僵尸潮
        if (hordeActive) {
            // 白天清除：天亮了（清晨 23000-24000 或 0-1000）自动清除僵尸潮，留少量敌对生物
            if (timeOfDay >= 23000L || timeOfDay < 1000L) {
                clearHordeAtDaybreak(overworld);
            }
            tickCounter++;
            if (tickCounter >= SPAWN_INTERVAL_TICKS) {
                tickCounter = 0;
                spawnWave(overworld);
            }
            // 检查大Boss阶段
            checkBigBossPhase(overworld);
            updateBossBar();
        }
    }

    private static void startHorde(ServerLevel level, long day) {
        hordeActive = true;
        currentWave = 0;
        currentDay = (int) day;
        tickCounter = 0;
        daybreakCleared = false;
        bigBossEntityId = -1;
        bigBossPhaseMap.clear();

        hordePlayers.clear();
        hordePlayers.addAll(level.players());

        for (ServerPlayer player : hordePlayers) {
            player.sendSystemMessage(Component.literal("§4§l⚠⚠⚠ 僵尸潮来袭！⚠⚠⚠"));
            player.sendSystemMessage(Component.literal("§c§l第 " + day + " 天 - 僵尸潮已开始！共5波！"));
            player.sendSystemMessage(Component.literal("§6§l请做好准备！"));
        }

        for (ServerPlayer player : hordePlayers) {
            hordeBossBar.addPlayer(player);
        }
        hordeBossBar.setVisible(true);
        updateBossBar();

        QLMZombieMod.LOGGER.info("[僵尸潮] Day {}: 僵尸潮开始！", day);
    }

    private static void spawnWave(ServerLevel level) {
        if (currentWave >= 5) {
            endHorde(level, true); // 成功打完5波
            return;
        }

        currentWave++;
        QLMZombieMod.LOGGER.info("[僵尸潮] 第 {} 波开始！", currentWave);

        for (ServerPlayer player : hordePlayers) {
            if (player.isAlive()) {
                player.sendSystemMessage(Component.literal("§6§l=== 第 " + currentWave + " 波 === 共5波 ==="));
            }
        }

        for (ServerPlayer player : hordePlayers) {
            if (player.isAlive() && player.level().dimension() == Level.OVERWORLD) {
                BlockPos spawnPos = player.blockPosition();
                Random random = new Random();

                switch (currentWave) {
                    case 1 -> spawnWave1(level, spawnPos, random);
                    case 2 -> spawnWave2(level, spawnPos, random);
                    case 3 -> spawnWave3(level, spawnPos, random);
                    case 4 -> spawnWave4(level, spawnPos, random);
                    case 5 -> spawnWave5(level, spawnPos, random);
                }
            }
        }

        updateBossBar();
    }

    private static void spawnWave1(ServerLevel level, BlockPos center, Random random) {
        int count = 10 + random.nextInt(6);
        for (int i = 0; i < count; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie != null) setupHordeMob(zombie, level, center, random, 1.0);
        }
    }

    private static void spawnWave2(ServerLevel level, BlockPos center, Random random) {
        int zombieCount = 8 + random.nextInt(3);
        int skeletonCount = 6 + random.nextInt(3);
        for (int i = 0; i < zombieCount; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie != null) setupHordeMob(zombie, level, center, random, 1.5);
        }
        for (int i = 0; i < skeletonCount; i++) {
            Skeleton skeleton = EntityType.SKELETON.create(level);
            if (skeleton != null) setupHordeMob(skeleton, level, center, random, 1.2);
        }
    }

    private static void spawnWave3(ServerLevel level, BlockPos center, Random random) {
        // 第3波：召唤小Boss x1 + 混合敌人
        spawnMiniBoss(level, center, random);

        int zombieCount = 6 + random.nextInt(3);
        int spiderCount = 4 + random.nextInt(3);
        for (int i = 0; i < zombieCount; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie != null) setupHordeMob(zombie, level, center, random, 2.0);
        }
        for (int i = 0; i < spiderCount; i++) {
            Spider spider = EntityType.SPIDER.create(level);
            if (spider != null) setupHordeMob(spider, level, center, random, 1.8);
        }
    }

    private static void spawnWave4(ServerLevel level, BlockPos center, Random random) {
        // 第4波：小Boss + 支援
        spawnMiniBoss(level, center, random);

        int supportCount = 10 + random.nextInt(6);
        for (int i = 0; i < supportCount; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie != null) setupHordeMob(zombie, level, center, random, 2.5);
        }
    }

    private static void spawnWave5(ServerLevel level, BlockPos center, Random random) {
        // 第5波：大Boss + 精英僵尸
        spawnBigBoss(level, center, random);

        int eliteCount = 20 + random.nextInt(11);
        for (int i = 0; i < eliteCount; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie != null) {
                setupHordeMob(zombie, level, center, random, 3.0);
                zombie.setCustomName(Component.literal("§c[精英] ").withStyle(net.minecraft.ChatFormatting.RED)
                    .append(zombie.getName()));
                zombie.setCustomNameVisible(true);
            }
        }

        for (ServerPlayer player : hordePlayers) {
            player.sendSystemMessage(Component.literal("§4§l!!! 大Boss 出现了！血量 10000，3个阶段！"));
        }
    }

    /** 生成小Boss（500血，含技能） */
    private static void spawnMiniBoss(ServerLevel level, BlockPos center, Random random) {
        Zombie miniBoss = EntityType.ZOMBIE.create(level);
        if (miniBoss == null) return;

        setupHordeMob(miniBoss, level, center, random, 1.0);

        var healthAttr = miniBoss.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(500.0);
            miniBoss.setHealth(500.0f);
        }
        var damageAttr = miniBoss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) damageAttr.setBaseValue(30.0);
        var armorAttr = miniBoss.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(10.0);

        miniBoss.setCustomName(Component.literal("§c§l⚔ 小Boss ⚔").withStyle(net.minecraft.ChatFormatting.BOLD));
        miniBoss.setCustomNameVisible(true);

        // 标记为Boss
        CompoundTag tag = miniBoss.getPersistentData();
        tag.putBoolean(NBT_IS_BOSS, true);
        tag.putString(NBT_BOSS_TYPE, "mini");
        tag.putInt(NBT_BOSS_MAX_PHASE, 1);

        // 小Boss技能：范围伤害（通过LivingHurtEvent处理）
        QLMZombieMod.LOGGER.info("[僵尸潮] 小Boss 已生成于 {}", miniBoss.blockPosition());
    }

    /** 生成大Boss（10000血，3阶段） */
    private static void spawnBigBoss(ServerLevel level, BlockPos center, Random random) {
        Zombie bigBoss = EntityType.ZOMBIE.create(level);
        if (bigBoss == null) return;

        setupHordeMob(bigBoss, level, center, random, 1.0);

        var healthAttr = bigBoss.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(10000.0);
            bigBoss.setHealth(10000.0f);
        }
        var damageAttr = bigBoss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) damageAttr.setBaseValue(50.0);
        var armorAttr = bigBoss.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(20.0);
        var speedAttr = bigBoss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.3);

        bigBoss.setCustomName(Component.literal("§4§l☠ 大Boss ☠ [阶段1/3]").withStyle(net.minecraft.ChatFormatting.BOLD));
        bigBoss.setCustomNameVisible(true);

        // 标记为Boss
        CompoundTag tag = bigBoss.getPersistentData();
        tag.putBoolean(NBT_IS_BOSS, true);
        tag.putString(NBT_BOSS_TYPE, "big");
        tag.putInt(NBT_BOSS_PHASE, 1);
        tag.putInt(NBT_BOSS_MAX_PHASE, 3);

        bigBossEntityId = bigBoss.getId();
        bigBossPhaseMap.put(bigBossEntityId, 1);

        QLMZombieMod.LOGGER.info("[僵尸潮] 大Boss 已生成于 {}", bigBoss.blockPosition());
    }

    /** 检查大Boss阶段变化 */
    private static void checkBigBossPhase(ServerLevel level) {
        if (bigBossEntityId == -1) return;

        // 获取大Boss实体
        var entity = level.getEntity(bigBossEntityId);
        if (!(entity instanceof Zombie bigBoss) || !bigBoss.isAlive()) {
            bigBossEntityId = -1;
            return;
        }

        float health = bigBoss.getHealth();
        float maxHealth = bigBoss.getMaxHealth();
        float healthPercent = health / maxHealth;
        int currentPhase = bigBossPhaseMap.getOrDefault(bigBossEntityId, 1);
        int newPhase = currentPhase;

        if (healthPercent <= 0.33f && currentPhase < 3) {
            newPhase = 3;
        } else if (healthPercent <= 0.66f && currentPhase < 2) {
            newPhase = 2;
        }

        if (newPhase != currentPhase) {
            bigBossPhaseMap.put(bigBossEntityId, newPhase);
            bigBoss.getPersistentData().putInt(NBT_BOSS_PHASE, newPhase);

            // 阶段变化效果
            switch (newPhase) {
                case 2 -> {
                    // 阶段2：速度提升，召唤小Boss
                    var speedAttr = bigBoss.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null) speedAttr.setBaseValue(0.4);
                    var damageAttr = bigBoss.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (damageAttr != null) damageAttr.setBaseValue(70.0);
                    bigBoss.setCustomName(Component.literal("§4§l☠ 大Boss ☠ [阶段2/3]").withStyle(net.minecraft.ChatFormatting.BOLD));
                    bigBoss.heal(maxHealth * 0.1f); // 恢复10%血量

                    // 召唤小Boss
                    spawnMiniBoss(level, bigBoss.blockPosition(), new Random());
                    for (ServerPlayer player : hordePlayers) {
                        player.sendSystemMessage(Component.literal("§6§l大Boss进入阶段2！速度提升，召唤小Boss！"));
                    }
                }
                case 3 -> {
                    // 阶段3：狂暴，高伤害，召唤小Boss x2
                    var speedAttr = bigBoss.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null) speedAttr.setBaseValue(0.5);
                    var damageAttr = bigBoss.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (damageAttr != null) damageAttr.setBaseValue(100.0);
                    var armorAttr = bigBoss.getAttribute(Attributes.ARMOR);
                    if (armorAttr != null) armorAttr.setBaseValue(30.0);
                    bigBoss.setCustomName(Component.literal("§4§l☠ 大Boss ☠ [阶段3/3·狂暴]").withStyle(net.minecraft.ChatFormatting.BOLD));
                    bigBoss.heal(maxHealth * 0.15f); // 恢复15%血量

                    // 召唤2个小Boss
                    spawnMiniBoss(level, bigBoss.blockPosition(), new Random());
                    spawnMiniBoss(level, bigBoss.blockPosition(), new Random());
                    for (ServerPlayer player : hordePlayers) {
                        player.sendSystemMessage(Component.literal("§4§l大Boss进入阶段3！狂暴模式！召唤2个小Boss！"));
                    }
                }
            }

            QLMZombieMod.LOGGER.info("[僵尸潮] 大Boss 进入阶段 {}/3", newPhase);
        }
    }

    private static <T extends Mob> void setupHordeMob(T mob, ServerLevel level, BlockPos center, Random random, double healthMultiplier) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = 8 + random.nextDouble() * 10;
        int x = center.getX() + (int) (Math.cos(angle) * distance);
        int z = center.getZ() + (int) (Math.sin(angle) * distance);
        // 找到合适的地面高度
        int y = center.getY();
        BlockPos spawnPos = new BlockPos(x, y, z);
        // 确保在地面上
        for (int attempt = 0; attempt < 20; attempt++) {
            if (!level.getBlockState(spawnPos).isAir() || level.getBlockState(spawnPos).isAir()) {
                spawnPos = spawnPos.above();
            } else {
                break;
            }
        }
        // 向下找地面
        for (int attempt = 0; attempt < 20; attempt++) {
            if (level.getBlockState(spawnPos).isAir() && !level.getBlockState(spawnPos.below()).isAir()) {
                break;
            }
            spawnPos = spawnPos.below();
        }

        mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        mob.getPersistentData().putBoolean(NBT_HORDE_MOB, true);
        level.addFreshEntity(mob);

        // 血量加成
        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double base = healthAttr.getBaseValue();
            healthAttr.setBaseValue(base * healthMultiplier);
            mob.setHealth((float) (base * healthMultiplier));
        }

        // 伤害加成
        var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * healthMultiplier);
        }
    }

    private static void updateBossBar() {
        if (!hordeActive || hordePlayers.isEmpty()) {
            hordeBossBar.setVisible(false);
            return;
        }
        float progress = currentWave / 5.0f;
        hordeBossBar.setProgress(Math.min(1.0f, progress));
        hordeBossBar.setName(Component.literal("§4§l⚔ 僵尸潮 §7- §e第 " + currentWave + "/5 波"));
    }

    /** 白天到来：自动清除僵尸潮，保留少量敌对生物，判定失败并给予一定奖励 */
    private static void clearHordeAtDaybreak(ServerLevel level) {
        if (daybreakCleared) return;
        daybreakCleared = true;

        // 收集所有僵尸潮生物
        java.util.List<Zombie> hordeMobs = level.getEntitiesOfClass(Zombie.class,
            new net.minecraft.world.phys.AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
            z -> z.isAlive() && z.getPersistentData().getBoolean(NBT_HORDE_MOB));

        int removed = 0;
        // 保留 3 只，其余清除
        for (int i = 3; i < hordeMobs.size(); i++) {
            hordeMobs.get(i).discard();
            removed++;
        }
        // 保留的 3 只解除标记并弱化
        for (int i = 0; i < Math.min(3, hordeMobs.size()); i++) {
            Zombie z = hordeMobs.get(i);
            z.getPersistentData().remove(NBT_HORDE_MOB);
            try {
                var hp = z.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(hp.getBaseValue() * 0.5);
                z.setHealth(z.getMaxHealth());
            } catch (Exception ignored) {
            }
        }

        QLMZombieMod.LOGGER.info("[僵尸潮] 天亮了，自动清除 {} 只僵尸潮生物（保留少量）", removed);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal("§e§l☀ 天亮了！僵尸潮撤退，剩余僵尸被清除，只留下少量游荡的敌人。"));
        }
        endHorde(level, false);
    }

    private static void endHorde(ServerLevel level, boolean success) {
        hordeActive = false;
        tickCounter = 0;
        bigBossEntityId = -1;
        bigBossPhaseMap.clear();
        hordeBossBar.setVisible(false);
        hordeBossBar.removeAllPlayers();

        java.util.List<ServerPlayer> rewarded = new java.util.ArrayList<>(hordePlayers);
        hordePlayers.clear();

        QLMZombieMod.LOGGER.info("[僵尸潮] Day {}: 僵尸潮已结束！成功={}", currentDay, success);

        for (ServerPlayer player : rewarded) {
            if (success) {
                // 成功打完5波：丰厚奖励
                giveReward(player, true);
                // 解锁成就：僵尸潮征服者 + 幸存者
                try { com.qlm.zombie.achievement.AchievementManager.unlockHordeWin(player); } catch (Exception ignored) {}
                player.sendSystemMessage(Component.literal("§a§l✔ 你成功撑过了僵尸潮！获得丰厚奖励！"));
                player.sendSystemMessage(Component.literal("§6§l+ 附魔金苹果 x1 + 钻石 x8 + 下界合金锭 x2 + 技能点 5"));
            } else {
                // 失败（白天清除）：也有一定奖励
                giveReward(player, false);
                player.sendSystemMessage(Component.literal("§e§l☀ 你坚持到了天亮，获得撤退奖励！"));
                player.sendSystemMessage(Component.literal("§6§l+ 金苹果 x1 + 铁锭 x8 + 技能点 2"));
            }
            player.sendSystemMessage(Component.literal("§7§l下一次僵尸潮将在第 " + (currentDay + HORDE_INTERVAL) + " 天到来"));
        }
    }

    /** 给予僵尸潮奖励（成功丰厚 / 失败一定） */
    private static void giveReward(ServerPlayer player, boolean big) {
        try {
            if (big) {
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 1));
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 8));
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 2));
                com.qlm.zombie.skill.SkillPointHandler.addPoints(player, 5);
            } else {
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 1));
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 8));
                com.qlm.zombie.skill.SkillPointHandler.addPoints(player, 2);
            }
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[僵尸潮] 发放奖励失败: {}", e.getMessage());
        }
    }

    public static boolean isHordeActive() {
        return hordeActive;
    }

    public static int getCurrentWave() {
        return currentWave;
    }
}
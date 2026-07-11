package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.dayphase.DayPhase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.RandomSource;

public class ZombieEvolutionHandler {

    private static final String EVOLVED_TAG = "qlmzombie.evolved";
    private static final String BONUS_HEALTH_TAG = "qlmzombie.bonus_health_applied";
    private static final String ARMORED_TAG = "qlmzombie.armored";
    private static final int BUFF_DURATION = 72000;

    private static final List<MobEffect> NORMAL_POOL = Arrays.asList(
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DAMAGE_BOOST,
            MobEffects.DAMAGE_RESISTANCE);

    private static final List<MobEffect> HARD_POOL = Arrays.asList(
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DAMAGE_BOOST,
            MobEffects.DAMAGE_RESISTANCE,
            MobEffects.FIRE_RESISTANCE,
            MobEffects.WATER_BREATHING);

    private static final List<MobEffect> EXTREME_POOL = Arrays.asList(
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DAMAGE_BOOST,
            MobEffects.DAMAGE_RESISTANCE,
            MobEffects.FIRE_RESISTANCE,
            MobEffects.WATER_BREATHING,
            MobEffects.REGENERATION);

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie) && !(event.getEntity() instanceof Skeleton skeleton))
            return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;
        if (serverLevel.getDifficulty() == Difficulty.PEACEFUL)
            return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null)
            return;

        long currentDay = overworld.getDayTime() / 24000L;
        DayPhase phase = DayPhase.forDay(currentDay);

        if (event.getEntity() instanceof Zombie) {
            handleZombieEvolution((Zombie) event.getEntity(), phase, currentDay);
        } else if (event.getEntity() instanceof Skeleton) {
            handleSkeletonEvolution((Skeleton) event.getEntity(), phase, currentDay);
        }
    }

    private static void handleZombieEvolution(Zombie zombie, DayPhase phase, long currentDay) {
        if (zombie.getPersistentData().getBoolean(EVOLVED_TAG))
            return;

        double chance = switch (phase) {
            case SAFE -> 0.0;
            case EASY -> QLMConfig.ZOMBIE_EVOLVE_CHANCE_EASY.get();
            case NORMAL -> QLMConfig.ZOMBIE_EVOLVE_CHANCE_NORMAL.get();
            case HARD -> QLMConfig.ZOMBIE_EVOLVE_CHANCE_HARD.get();
            case EXTREME -> QLMConfig.ZOMBIE_EVOLVE_CHANCE_EXTREME.get();
        };

        if (chance <= 0.0)
            return;

        RandomSource rng = zombie.getRandom();
        if (rng.nextDouble() > chance)
            return;

        zombie.getPersistentData().putBoolean(EVOLVED_TAG, true);

        switch (phase) {
            case EASY -> applyBuffs(zombie, Collections.singletonList(MobEffects.MOVEMENT_SPEED), 0, rng);
            case NORMAL -> applyBuffs(zombie, pickRandom(NORMAL_POOL, 2, rng), 0, rng);
            case HARD -> {
                applyBuffs(zombie, pickRandom(HARD_POOL, 3, rng), 1, rng);
                applyBonusHealth(zombie, 1.0);
            }
            case EXTREME -> {
                applyBuffs(zombie, pickRandom(HARD_POOL, 4, rng), 2, rng);
                applyBonusHealth(zombie, 2.0);
            }
            default -> {
            }
        }

        zombie.setCustomName(net.minecraft.network.chat.Component.literal("进化僵尸"));
        zombie.setCustomNameVisible(true);

        QLMZombieMod.LOGGER.debug("[QLM Zombie] 僵尸进化 @ day {} ({}) at {}",
                currentDay, phase.displayName(), zombie.blockPosition());
    }

    private static void handleSkeletonEvolution(Skeleton skeleton, DayPhase phase, long currentDay) {
        if (skeleton.getPersistentData().getBoolean(EVOLVED_TAG))
            return;

        double chance = switch (phase) {
            case SAFE -> 0.0;
            case EASY -> QLMConfig.ZOMBIE_EVOLVE_CHANCE_EASY.get() * 0.5;
            case NORMAL -> QLMConfig.ZOMBIE_EVOLVE_CHANCE_NORMAL.get() * 0.7;
            case HARD -> QLMConfig.ZOMBIE_EVOLVE_CHANCE_HARD.get() * 0.8;
            case EXTREME -> QLMConfig.ZOMBIE_EVOLVE_CHANCE_EXTREME.get() * 0.9;
        };

        if (chance <= 0.0)
            return;

        RandomSource rng = skeleton.getRandom();
        if (rng.nextDouble() > chance)
            return;

        skeleton.getPersistentData().putBoolean(EVOLVED_TAG, true);

        switch (phase) {
            case EASY -> applyBuffs(skeleton, Collections.singletonList(MobEffects.MOVEMENT_SPEED), 0, rng);
            case NORMAL -> applyBuffs(skeleton, pickRandom(NORMAL_POOL, 2, rng), 0, rng);
            case HARD -> {
                applyBuffs(skeleton, pickRandom(HARD_POOL, 3, rng), 1, rng);
                applyBonusHealth(skeleton, 0.8);
            }
            case EXTREME -> {
                applyBuffs(skeleton, pickRandom(HARD_POOL, 4, rng), 2, rng);
                applyBonusHealth(skeleton, 1.5);
            }
            default -> {
            }
        }

        skeleton.setCustomName(net.minecraft.network.chat.Component.literal("进化骷髅"));
        skeleton.setCustomNameVisible(true);

        QLMZombieMod.LOGGER.debug("[QLM Zombie] 骷髅进化 @ day {} ({}) at {}",
                currentDay, phase.displayName(), skeleton.blockPosition());
    }

    private static void applyBuffs(Zombie zombie, List<MobEffect> effects, int amplifierBoost, RandomSource rng) {
        for (MobEffect effect : effects) {
            int amplifier = amplifierBoost + (rng.nextBoolean() ? 1 : 0);
            zombie.addEffect(new MobEffectInstance(effect, BUFF_DURATION, amplifier, false, true));
        }
    }

    private static void applyBuffs(Skeleton skeleton, List<MobEffect> effects, int amplifierBoost, RandomSource rng) {
        for (MobEffect effect : effects) {
            int amplifier = amplifierBoost + (rng.nextBoolean() ? 1 : 0);
            skeleton.addEffect(new MobEffectInstance(effect, BUFF_DURATION, amplifier, false, true));
        }
    }

    private static void applyBonusHealth(Zombie zombie, double multiplier) {
        if (zombie.getPersistentData().getBoolean(BONUS_HEALTH_TAG))
            return;
        zombie.getPersistentData().putBoolean(BONUS_HEALTH_TAG, true);
        int bonus = QLMConfig.ZOMBIE_EVOLVE_BONUS_HEALTH.get();
        if (bonus <= 0)
            return;
        AttributeInstance maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double originalBase = maxHealth.getBaseValue();
            maxHealth.setBaseValue(originalBase + bonus * multiplier);
            zombie.setHealth(zombie.getMaxHealth());
        }
    }

    private static void applyBonusHealth(Skeleton skeleton, double multiplier) {
        if (skeleton.getPersistentData().getBoolean(BONUS_HEALTH_TAG))
            return;
        skeleton.getPersistentData().putBoolean(BONUS_HEALTH_TAG, true);
        int bonus = QLMConfig.ZOMBIE_EVOLVE_BONUS_HEALTH.get();
        if (bonus <= 0)
            return;
        AttributeInstance maxHealth = skeleton.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double originalBase = maxHealth.getBaseValue();
            maxHealth.setBaseValue(originalBase + bonus * multiplier);
            skeleton.setHealth(skeleton.getMaxHealth());
        }
    }

    private static <T> List<T> pickRandom(List<T> pool, int count, RandomSource rng) {
        if (count >= pool.size())
            return List.copyOf(pool);
        java.util.List<T> copy = new java.util.ArrayList<>(pool);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            java.util.Collections.swap(copy, i, j);
        }
        return copy.subList(0, count);
    }
}
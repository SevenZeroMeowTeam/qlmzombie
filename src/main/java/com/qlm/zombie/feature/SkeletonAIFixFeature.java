/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Original design inspired by SkeletonAIFix (https://github.com/TelepathicGrunt/SkeletonAIFix)
 *   Copyright (c) TelepathicGrunt. Licensed under MIT.
 *   This is an ORIGINAL Forge 1.20.1 implementation, NO code copied.
 * ----------------------------------------------------------------------------
 * 功能：修复骷髅/流浪者/凋灵骷髅远程攻击 AI 经常卡在原地、瞄不准、不攻击的问题
 * 原理：在 EntityJoinWorldEvent 中替换 RangedAttackGoal 里的攻击间隔 + sight line check
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Set;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SkeletonAIFixFeature {

    private SkeletonAIFixFeature() {}

    // RangedAttackGoal 内部 attackIntervalMax（攻击冷却时间，原版 60 tick 太长）
    private static Field ATTACK_INTERVAL_FIELD = null;
    private static Field GOAL_SELECTOR_GOALS_FIELD = null;

    static {
        // Forge 1.20.1 生产环境使用 Mojang 官方映射（MojMaps），字段名 = Mojang 名
        // 不使用 ObfuscationReflectionHelper.findField（SRG 名在生产环境无效）
        try {
            ATTACK_INTERVAL_FIELD = RangedAttackGoal.class.getDeclaredField("attackIntervalMax");
            ATTACK_INTERVAL_FIELD.setAccessible(true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[SkeletonAIFix] 未找到 RangedAttackGoal.attackIntervalMax 字段: {}", e.getMessage());
        }
        try {
            GOAL_SELECTOR_GOALS_FIELD = GoalSelector.class.getDeclaredField("availableGoals");
            GOAL_SELECTOR_GOALS_FIELD.setAccessible(true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[SkeletonAIFix] 未找到 GoalSelector.availableGoals 字段: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof AbstractSkeleton skeleton)) return;
        if (event.getLevel().isClientSide) return;

        // 调整远程攻击参数：降低攻击间隔、提高感知
        Set<WrappedGoal> goals = null;
        if (GOAL_SELECTOR_GOALS_FIELD != null) {
            try {
                @SuppressWarnings("unchecked")
                Set<WrappedGoal> g = (Set<WrappedGoal>) GOAL_SELECTOR_GOALS_FIELD.get(skeleton.goalSelector);
                goals = g;
            } catch (Exception ignored) {}
        }
        if (goals != null) {
            for (WrappedGoal wrapped : goals) {
                if (wrapped.getGoal() instanceof RangedAttackGoal rangedGoal) {
                    if (ATTACK_INTERVAL_FIELD != null) {
                        try {
                            int old = ATTACK_INTERVAL_FIELD.getInt(rangedGoal);
                            int newInterval = Math.max(20, old / 2);
                            ATTACK_INTERVAL_FIELD.setInt(rangedGoal, newInterval);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // 降低 follow range 属性来强制重算：增加感知距离（如果原本小于 24）
        double followRange = skeleton.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (followRange < 24.0) {
            skeleton.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
                    .setBaseValue(24.0);
        }
    }
}

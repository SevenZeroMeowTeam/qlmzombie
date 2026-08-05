/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Original design inspired by AI-Improvements (https://github.com/telepathicgrunt/AI-Improvements)
 *   Copyright (c) TelepathicGrunt. Licensed under MIT.
 *   This is an ORIGINAL Forge 1.20.1 implementation, NO code copied.
 * ----------------------------------------------------------------------------
 * 功能：通用实体 AI 节流优化。
 *      1. 对 Zombie/Creeper/动物/村民/劫匪/Axolotl 等非 Skeleton 实体，
 *         包装 GoalSelector 内每一个 Goal 为 ThrottledGoal（每 2 tick 才判断/执行一次），
 *         感知判断（targetSelector）改为每 3 tick 一次；
 *      2. 减少 tick 期间大量 canUse()/canContinueToUse()/distanceToSqr / 路径重算调用，
 *         行为体验不变但 CPU 占用显著下降。
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AIImprovementsFeature {

    private AIImprovementsFeature() {}

    // ------------------------- 反射字段 -------------------------
    /** GoalSelector.availableGoals (SRG: goals) */
    private static final Field GS_AVAILABLE;
    /** WrappedGoal.goal (SRG: goal) */
    private static final Field WG_GOAL;

    static {
        // Forge 1.20.1 生产环境使用 Mojang 官方映射（MojMaps）
        Field gsa = null, wgg = null;
        try {
            gsa = GoalSelector.class.getDeclaredField("availableGoals");
            gsa.setAccessible(true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[AIImprovements] GoalSelector.availableGoals 反射失败：{}", e.getMessage());
        }
        try {
            wgg = WrappedGoal.class.getDeclaredField("goal");
            wgg.setAccessible(true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[AIImprovements] WrappedGoal.goal 反射失败：{}", e.getMessage());
        }
        GS_AVAILABLE = gsa;
        WG_GOAL = wgg;
    }

    // ------------------------- 包装器 -------------------------
    /** 对一个 Goal 的 throttle 包装：每 N tick 才真正调用一次 canUse/canContinue/tick。*/
    public static class ThrottledGoal extends Goal {
        final Goal delegate;
        final int period;     // 每 period tick 才真正 tick 一次
        int counter;
        // 缓存上一次 canUse / canContinue 结果，用于中间 tick 的快速返回
        boolean cachedCanUse;
        boolean cachedCanContinue;

        public ThrottledGoal(Goal delegate, int period) {
            this.delegate = delegate;
            this.period = Math.max(1, period);
            this.setFlags(delegate.getFlags());
        }

        @Override
        public EnumSet<Flag> getFlags() { return delegate.getFlags(); }

        @Override
        public boolean canUse() {
            if (delegate.requiresUpdateEveryTick()) return delegate.canUse();
            if (counter % period == 0) cachedCanUse = delegate.canUse();
            return cachedCanUse;
        }

        @Override
        public boolean canContinueToUse() {
            if (delegate.requiresUpdateEveryTick()) return delegate.canContinueToUse();
            if (counter % period == 0) cachedCanContinue = delegate.canContinueToUse();
            return cachedCanContinue;
        }

        @Override
        public void start() { delegate.start(); }

        @Override
        public void tick() {
            counter++;
            if (delegate.requiresUpdateEveryTick() || counter % period == 0) {
                delegate.tick();
            }
        }

        @Override
        public void stop() { delegate.stop(); }

        @Override
        public boolean isInterruptable() { return delegate.isInterruptable(); }
    }

    // ------------------------- 事件 -------------------------
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (e == null || event.getLevel().isClientSide) return;
        if (GS_AVAILABLE == null || WG_GOAL == null) return;

        // 筛选：只处理我们需要节流的实体类型（Skeleton 已经走 SkeletonAIFixFeature）
        boolean eligible = false;
        int goalPeriod = 2;   // goalSelector 每 2 tick 一次
        int targetPeriod = 3; // targetSelector 每 3 tick 一次
        if (e instanceof Zombie || e instanceof Creeper) {
            eligible = true;
        } else if (e instanceof Animal) {
            // 所有动物（含 Axolotl, Cow, Pig, Sheep, Chicken, Horse 等）均节流
            eligible = true;
            goalPeriod = 3;   // 动物动作更慢，3 tick 也够
            targetPeriod = 4;
        } else if (e instanceof AbstractVillager || e instanceof Raider) {
            eligible = true;
            goalPeriod = 3;
            targetPeriod = 4;
        } else if (e instanceof Enemy) {
            // 其他怪物（非 Skeleton/Zombie/Creeper 的 Enemy，例如 Slime, Witch, Ghast 等）也节流
            eligible = true;
            goalPeriod = 2;
            targetPeriod = 3;
        } else if (e instanceof Mob && !(e instanceof net.minecraft.world.entity.monster.AbstractSkeleton)) {
            // 剩下的 Mob（非 Skeleton） 统一节流但更保守（2 tick）
            eligible = true;
            goalPeriod = 2;
            targetPeriod = 3;
        }
        if (!eligible) return;

        Mob mob = (Mob) e;
        throttleGoalSelector(mob, mob.goalSelector,   goalPeriod);
        throttleGoalSelector(mob, mob.targetSelector, targetPeriod);
    }

    private static void throttleGoalSelector(Mob mob, GoalSelector selector, int period) {
        if (selector == null) return;
        try {
            @SuppressWarnings("unchecked")
            Set<WrappedGoal> goals = (Set<WrappedGoal>) GS_AVAILABLE.get(selector);
            if (goals == null || goals.isEmpty()) return;
            for (WrappedGoal wg : goals) {
                Goal inner = (Goal) WG_GOAL.get(wg);
                if (inner == null || inner instanceof ThrottledGoal) continue; // 已经被包过，防止二次包装
                WG_GOAL.set(wg, new ThrottledGoal(inner, period));
            }
        } catch (Exception ex) {
            // 个别实体包装失败不影响其它
        }
    }
}

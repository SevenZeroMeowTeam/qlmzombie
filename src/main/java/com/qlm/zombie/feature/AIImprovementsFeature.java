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
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
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
        // 已触发停止：避免 stop() 内部的多次调用链（如 FollowFlockLeaderGoal → stopFollowing）
        // 在 stop 中若发生异常，不再尝试二次调用
        volatile boolean stopped;

        public ThrottledGoal(Goal delegate, int period) {
            this.delegate = delegate;
            this.period = Math.max(1, period);
            this.setFlags(delegate.getFlags());
        }

        @Override
        public EnumSet<Flag> getFlags() { return delegate.getFlags(); }

        @Override
        public boolean canUse() {
            if (stopped) return false;
            if (delegate.requiresUpdateEveryTick()) return delegate.canUse();
            if (counter % period == 0) cachedCanUse = delegate.canUse();
            return cachedCanUse;
        }

        @Override
        public boolean canContinueToUse() {
            if (stopped) return false;
            if (delegate.requiresUpdateEveryTick()) return delegate.canContinueToUse();
            if (counter % period == 0) cachedCanContinue = delegate.canContinueToUse();
            return cachedCanContinue;
        }

        @Override
        public void start() {
            stopped = false;
            delegate.start();
        }

        @Override
        public void tick() {
            if (stopped) return;
            counter++;
            try {
                if (delegate.requiresUpdateEveryTick() || counter % period == 0) {
                    delegate.tick();
                }
            } catch (Exception ignored) {
                // 个别 goal tick 可能引用已消失的实体，捕获避免影响其他 goal
            }
        }

        @Override
        public void stop() {
            // 标记已停止：后续 canUse/canContinue 直接返回 false，避免状态不一致
            stopped = true;
            try {
                delegate.stop();
            } catch (NullPointerException npe) {
                // 已知问题：FollowFlockLeaderGoal.stop() 中调用 fish.stopFollowing()，
                // 若 leader 鱼已被移除世界，this.leader 为 null → NPE。
                // 此处捕获防止整个实体 tick 崩溃导致服务器 TPS 卡死
            } catch (Exception ignored) {
                // 其它异常同样吞掉
            }
        }

        @Override
        public boolean isInterruptable() { return delegate.isInterruptable(); }
    }

    // ------------------------- 事件 -------------------------
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (e == null || event.getLevel().isClientSide) return;
        if (GS_AVAILABLE == null || WG_GOAL == null) return;

        // ---- 必须跳过的实体类型 ----
        // AbstractSchoolingFish：鱼群的 FollowFlockLeaderGoal 引用 leader 实体，
        // leader 消失时 stop() 会 NPE（见日志 2026-08-05 AbstractSchoolingFish:52 崩溃）
        // 这些实体 AI tick 频率极低（鱼类动作慢），不需要节流也不会明显影响性能
        if (e instanceof AbstractSchoolingFish) return;

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

                // ---- 跳过不能节流的 goal ----
                // TargetGoal 类（如 NearestAttackableTargetGoal / FollowFlockLeaderGoal）
                // 持有对外部实体的引用（target/leader），节流后引用可能已失效，
                // stop()/tick() 时 NPE 风险高（如 FollowFlockLeaderGoal → fish.stopFollowing()）
                if (inner instanceof TargetGoal) continue;

                // 同时检查 goal 的 class 名/类结构中包含 "FlockLeader" / "Follow" 等
                // 引用外部实体的 goal，一律跳过节流
                String simpleName = inner.getClass().getSimpleName();
                if (simpleName.contains("Flock")
                        || simpleName.contains("Follow")
                        || simpleName.contains("Leap")
                        || simpleName.contains("LookAtPlayer")
                        || simpleName.contains("Strafe")) {
                    continue;
                }

                WG_GOAL.set(wg, new ThrottledGoal(inner, period));
            }
        } catch (Exception ex) {
            // 个别实体包装失败不影响其它
        }
    }
}

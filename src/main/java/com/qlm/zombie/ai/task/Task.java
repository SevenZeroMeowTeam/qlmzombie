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
 *   - AltoClef (PlayerEngine subproject)
 *     Task abstraction pattern (start/tick/stop lifecycle)
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements (no Baritone dependency).
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.task;

import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 任务抽象基类 — 参考 PlayerEngine/AltoClef 的 Task 设计模式
 * 生命周期: start → tick (每 tick 执行) → stop
 * 任务完成时 isFinished() 返回 true，由 TaskRunner 自动切换为 FollowTask
 */
public abstract class Task {

    protected final FakePlayerEntity ai;
    protected final Player owner;
    protected long startTime;
    protected int noTargetTicks;
    protected boolean finished;

    protected static final int TARGET_SEARCH_TIMEOUT = 100; // 5秒无目标超时
    protected static final long TASK_TIMEOUT_MS = 300000; // 5分钟总超时

    public Task(FakePlayerEntity ai, Player owner) {
        this.ai = ai;
        this.owner = owner;
    }

    // === 生命周期方法 ===

    /** 任务启动时调用（仅一次） */
    public abstract void start();

    /** 每 tick 调用，执行任务逻辑 */
    public abstract void tick();

    /** 任务停止时调用（清理导航、目标等） */
    public void stop() {
        ai.getNavigation().stop();
        ai.setTarget(null);
    }

    /** 任务是否已完成 */
    public boolean isFinished() {
        return finished;
    }

    /** 任务名称（用于 hasActiveTask 判断和日志） */
    public abstract String getName();

    /** 是否属于"活跃任务"（非 follow/wait 的任务会阻止自动跟随和目标设置） */
    public boolean isActiveTask() {
        return true;
    }

    // === 状态查询 ===

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    public boolean isTimedOut() {
        return getElapsedTime() > TASK_TIMEOUT_MS;
    }

    protected void finish() {
        finished = true;
    }

    // === 共享工具方法（原 AIPlayerChatHandler 中的 static 方法提取到此） ===

    /**
     * 无目标时的探索行为：随机漫步寻找新区域，递增无目标计数器
     * @return true 表示仍有机会找到目标，false 表示应放弃
     */
    protected boolean handleNoTarget(int timeout) {
        noTargetTicks++;
        if (noTargetTicks % 40 == 0) {
            Vec3 dir = new Vec3(ai.getRandom().nextDouble() - 0.5, 0, ai.getRandom().nextDouble() - 0.5).normalize();
            double dist = 8.0 + ai.getRandom().nextDouble() * 8.0;
            ai.getNavigation().moveTo(
                    ai.getX() + dir.x * dist,
                    ai.getY(),
                    ai.getZ() + dir.z * dist,
                    0.6D);
        }
        return noTargetTicks <= timeout;
    }

    protected void resetNoTargetCounter() {
        noTargetTicks = 0;
    }

    /** 查找最近的敌对生物 */
    protected LivingEntity findNearestHostile(double range) {
        AABB area = new AABB(
                ai.getX() - range, ai.getY() - range / 2, ai.getZ() - range,
                ai.getX() + range, ai.getY() + range / 2, ai.getZ() + range);
        LivingEntity best = null;
        double bestDist = range * range;
        for (Entity e : ai.level().getEntitiesOfClass(Entity.class, area)) {
            if (e instanceof Monster monster) {
                double dist = monster.distanceToSqr(ai);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = monster;
                }
            }
        }
        return best;
    }

    /** 判断是否为矿石方块 */
    protected static boolean isOreBlock(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE ||
               b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE ||
               b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE ||
               b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE ||
               b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE ||
               b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE ||
               b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE ||
               b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE;
    }

    /** 判断是否为原木方块 */
    protected static boolean isLogBlock(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.OAK_LOG || b == Blocks.SPRUCE_LOG ||
               b == Blocks.BIRCH_LOG || b == Blocks.JUNGLE_LOG ||
               b == Blocks.ACACIA_LOG || b == Blocks.DARK_OAK_LOG ||
               b == Blocks.MANGROVE_LOG || b == Blocks.CHERRY_LOG ||
               b == Blocks.CRIMSON_STEM || b == Blocks.WARPED_STEM;
    }

    /** 通知主人消息 */
    protected void notifyOwner(String message) {
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("§a[" + ai.getCustomNameStr() + "] §f" + message));
        }
    }

    /** 通知主人系统消息 */
    protected void notifyOwnerSystem(String message) {
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("§7[" + ai.getCustomNameStr() + "] " + message));
        }
    }
}

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
 *   - AltoClef TaskRunner lifecycle management pattern
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements (no Baritone dependency).
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.task;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 任务运行器 — 参考 PlayerEngine 的 TaskRunner 设计
 * 每个 FakePlayerEntity 持有一个 TaskRunner 实例，管理当前任务的生命周期
 *
 * 核心循环: tick() → 检查超时 → 检查完成 → 执行任务tick → 任务完成自动切换Follow
 */
public class TaskRunner {

    private final FakePlayerEntity ai;
    private Task currentTask;

    /** 任务链队列（LLM 规划的多步任务串行执行） */
    private final Queue<Task> taskQueue = new ConcurrentLinkedQueue<>();

    /** 冷却: 玩家UUID:AI的UUID → 上次指令时间（每个AI独立冷却） */
    private static final ConcurrentHashMap<String, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000;
    private static final long MIN_TASK_DURATION_MS = 2000;

    public TaskRunner(FakePlayerEntity ai) {
        this.ai = ai;
    }

    /** 生成冷却键：玩家UUID + AI的UUID */
    private static String cooldownKey(String playerUUID, String aiUUID) {
        return playerUUID + ":" + aiUUID;
    }

    /** 检查指定 AI 的冷却 */
    public static boolean checkCooldown(String playerUUID, String aiUUID) {
        String key = cooldownKey(playerUUID, aiUUID);
        long lastTime = COOLDOWN.getOrDefault(key, 0L);
        if (System.currentTimeMillis() - lastTime < COOLDOWN_MS) {
            return false;
        }
        COOLDOWN.put(key, System.currentTimeMillis());
        return true;
    }

    /** 启动新任务，自动停止旧任务（清空任务链队列） */
    public void startTask(Task task) {
        stopCurrent();
        taskQueue.clear();
        currentTask = task;
        currentTask.startTime = System.currentTimeMillis();
        currentTask.start();

        // 设置实体状态
        ai.setActiveTask(currentTask.isActiveTask());
        ai.setSitting(false);
    }

    /**
     * 启动任务链 — LLM 规划的多步任务串行执行
     * @param tasks 任务列表，按顺序执行
     */
    public void startTaskChain(List<Task> tasks) {
        stopCurrent();
        taskQueue.clear();
        if (tasks == null || tasks.isEmpty()) return;
        taskQueue.addAll(tasks);
        startNextInChain();
    }

    /** 启动任务链中的下一个任务 */
    private void startNextInChain() {
        Task next = taskQueue.poll();
        if (next != null) {
            currentTask = next;
            currentTask.startTime = System.currentTimeMillis();
            currentTask.start();
            ai.setActiveTask(currentTask.isActiveTask());
            ai.setSitting(false);
        } else {
            autoFollow();
        }
    }

    /** 每 tick 调用 */
    public void tick() {
        if (currentTask == null) return;

        // 超时检查
        if (currentTask.isTimedOut()) {
            if (!taskQueue.isEmpty()) {
                notifyOwnerSystem("当前任务超时，继续执行下一个任务");
            } else {
                notifyOwnerSystem("任务超时，自动切换为跟随模式");
            }
            autoFollow();
            return;
        }

        // 完成检查（受最小任务时长保护）
        if (currentTask.isFinished()) {
            long elapsed = currentTask.getElapsedTime();
            if (elapsed < MIN_TASK_DURATION_MS) {
                return; // 最小任务时长保护，不结束
            }
            autoFollow();
            return;
        }

        // 执行任务 tick
        currentTask.tick();
    }

    /** 停止当前任务 */
    public void stopCurrent() {
        if (currentTask != null) {
            currentTask.stop();
            currentTask = null;
        }
    }

    /** 任务完成/超时后自动切换为跟随（或继续任务链中的下一个任务） */
    public void autoFollow() {
        stopCurrent();
        // 任务链中还有任务 → 继续执行下一个
        if (!taskQueue.isEmpty()) {
            startNextInChain();
            return;
        }
        // 队列为空 → 切换为跟随模式
        LivingEntity ownerEntity = ai.getOwner();
        Player owner = ownerEntity instanceof Player ? (Player) ownerEntity : null;
        startTask(new FollowTask(ai, owner));
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("§a[" + ai.getCustomNameStr() + "] §f任务完成，已自动跟随你"));
        }
    }

    /** 强制停止（不自动跟随，清空任务链） */
    public void forceStop() {
        stopCurrent();
        taskQueue.clear();
        ai.setActiveTask(false);
    }

    /** 获取当前任务名称 */
    public String getCurrentTaskName() {
        return currentTask != null ? currentTask.getName() : null;
    }

    /** 是否有活跃任务（非 follow/wait） */
    public boolean hasActiveTask() {
        if (currentTask == null) return false;
        return currentTask.isActiveTask();
    }

    /** 获取任务链中剩余的任务数 */
    public int getQueuedTaskCount() {
        return taskQueue.size();
    }

    private void notifyOwnerSystem(String message) {
        LivingEntity ownerEntity = ai.getOwner();
        if (ownerEntity instanceof Player p) {
            p.sendSystemMessage(Component.literal("§7[系统] " + ai.getCustomNameStr() + " " + message));
        }
    }
}

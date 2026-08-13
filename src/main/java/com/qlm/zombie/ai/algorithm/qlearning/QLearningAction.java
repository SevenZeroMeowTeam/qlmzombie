/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * QLearningAction — Q-Learning 动作集
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.qlearning;

/**
 * Q-Learning 可选动作
 *
 * 动作映射到 AI 玩家的行为:
 *   ATTACK    — 攻击当前目标
 *   FLEE      — 远离敌人
 *   EAT       — 进食恢复
 *   FOLLOW    — 跟随主人
 *   EXPLORE   — 随机探索
 *   IDLE      — 原地待命
 */
public enum QLearningAction {
    ATTACK("攻击"),
    FLEE("逃跑"),
    EAT("进食"),
    FOLLOW("跟随"),
    EXPLORE("探索"),
    IDLE("待命");

    private final String displayName;

    QLearningAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static QLearningAction byIndex(int i) {
        QLearningAction[] values = values();
        if (i < 0 || i >= values.length) return IDLE;
        return values[i];
    }
}

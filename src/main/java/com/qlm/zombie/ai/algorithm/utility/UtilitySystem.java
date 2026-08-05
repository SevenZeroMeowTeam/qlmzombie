/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * UtilitySystem — 效用系统主类
 * 每帧评估所有动作，选择得分最高的执行
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.utility;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.entity.FakePlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 效用系统
 *
 * 用法:
 *   UtilitySystem us = new UtilitySystem(ai);
 *   us.addAction(new UtilityAction("attack", () -> doAttack(), SCORING));
 *   us.addAction(new UtilityAction("flee", () -> doFlee()));
 *   us.tick();
 *
 * 选择策略:
 *   - 默认: 选择得分最高的动作
 *   - 加入少量噪声，避免行为过于机械
 */
public class UtilitySystem {

    private final FakePlayerEntity ai;
    private final List<UtilityAction> actions = new ArrayList<>();
    private UtilityAction lastSelected;
    private double threshold = 0.1;   // 低于此分值的动作不执行
    private double stickiness = 0.7;  // 保持上一动作的概率（避免抖动）
    private double noise = 0.05;      // 随机噪声幅度

    public UtilitySystem(FakePlayerEntity ai) {
        this.ai = ai;
    }

    public void addAction(UtilityAction action) {
        actions.add(action);
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public void setStickiness(double stickiness) {
        this.stickiness = stickiness;
    }

    public void setNoise(double noise) {
        this.noise = noise;
    }

    /** 每 tick 评估并执行动作 */
    public void tick() {
        UtilityAction best = null;
        double bestScore = -1.0;

        for (UtilityAction action : actions) {
            double s = action.score();
            // 加入噪声
            s += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * noise;
            s = Math.max(0.0, Math.min(1.0, s));

            // 惯性: 上一动作有优先权
            if (action == lastSelected) {
                s += stickiness * 0.1;
                s = Math.min(1.0, s);
            }

            if (s > bestScore) {
                bestScore = s;
                best = action;
            }
        }

        if (best != null && bestScore >= threshold) {
            if (best != lastSelected) {
                QLMZombieMod.LOGGER.debug("[Utility] {} -> score {}",
                        best.getName(), String.format("%.3f", bestScore));
            }
            best.execute();
            lastSelected = best;
        }
    }

    public UtilityAction getLastSelected() {
        return lastSelected;
    }

    public String getLastActionName() {
        return lastSelected != null ? lastSelected.getName() : "<none>";
    }

    public List<UtilityAction> getActions() {
        return actions;
    }

    public FakePlayerEntity getAI() {
        return ai;
    }
}

/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * UtilityAction — 效用动作
 * 由多个 Consideration 组合而成，最终评分决定是否被选中
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.utility;

import com.qlm.zombie.entity.FakePlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 效用动作
 *
 * 评分策略:
 *   - GEOMETRIC: 所有考量分值相乘（任一为 0 则整体为 0，强调"必要条件"）
 *   - WEIGHTED_AVERAGE: 加权平均，允许部分高分补偿
 */
public class UtilityAction {

    public enum ScoringStrategy { GEOMETRIC, WEIGHTED_AVERAGE }

    private final String name;
    private final List<Consideration> considerations = new ArrayList<>();
    private final ScoringStrategy strategy;
    private final Runnable executor;

    public UtilityAction(String name, Runnable executor) {
        this(name, executor, ScoringStrategy.GEOMETRIC);
    }

    public UtilityAction(String name, Runnable executor, ScoringStrategy strategy) {
        this.name = name;
        this.executor = executor;
        this.strategy = strategy;
    }

    public UtilityAction addConsideration(Consideration c) {
        considerations.add(c);
        return this;
    }

    /** 计算该动作的效用分值 [0,1] */
    public double score() {
        if (considerations.isEmpty()) return 0.0;

        switch (strategy) {
            case GEOMETRIC -> {
                double product = 1.0;
                for (Consideration c : considerations) {
                    product *= Math.max(0.0, c.evaluate());
                    if (product <= 0.0) return 0.0;
                }
                // 调整: 考量越多分越低，避免"平庸但考虑多"压倒"必要条件"
                double compensation = 1.0 + (1.0 - 1.0 / considerations.size()) * 0.5;
                return Math.min(1.0, product * compensation);
            }
            case WEIGHTED_AVERAGE -> {
                double sum = 0.0;
                for (Consideration c : considerations) {
                    sum += c.evaluate();
                }
                return sum / considerations.size();
            }
            default -> {
                return 0.0;
            }
        }
    }

    public void execute() {
        executor.run();
    }

    public String getName() {
        return name;
    }
}

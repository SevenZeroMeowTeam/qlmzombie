/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * QLearningAgent — Q-Learning 学习代理
 *
 * 算法核心 (Watkins, 1989):
 *   Q(s,a) ← Q(s,a) + α * [ r + γ * max_a' Q(s',a') - Q(s,a) ]
 *
 * 其中:
 *   α (alpha)   — 学习率，控制新信息覆盖旧信息的程度
 *   γ (gamma)   — 折扣因子，未来回报的折扣权重
 *   ε (epsilon) — 探索率，随机探索的概率
 *   r           — 即时奖励
 *   s'          — 转移到的下一个状态
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.qlearning;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.entity.FakePlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Q-Learning 学习代理
 *
 * 用法:
 *   agent.observe(state, action, reward, nextState);
 *   QLearningAction next = agent.chooseAction(state);
 */
public class QLearningAgent {

    private final QTable qTable;
    private double alpha;
    private double gamma;
    private double epsilon;
    private final double epsilonMin;
    private final double epsilonDecay;

    private int totalSteps = 0;
    private double totalReward = 0.0;
    private double recentReward = 0.0;
    private int recentSteps = 0;

    public QLearningAgent(double alpha, double gamma, double epsilon,
                          double epsilonMin, double epsilonDecay) {
        this.qTable = new QTable(QLearningAction.values().length);
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.epsilonMin = epsilonMin;
        this.epsilonDecay = epsilonDecay;
    }

    public QLearningAgent() {
        this(0.1, 0.9, 0.3, 0.02, 0.9995);
    }

    /** 选择动作（epsilon-greedy） */
    public QLearningAction chooseAction(QLearningState state) {
        int actionIdx = qTable.epsilonGreedy(state.index(), epsilon);
        return QLearningAction.byIndex(actionIdx);
    }

    /** 贪心策略（无探索，用于实战） */
    public QLearningAction chooseBestAction(QLearningState state) {
        return QLearningAction.byIndex(qTable.bestAction(state.index()));
    }

    /**
     * 学习更新 Q 值
     *
     * @param state      当前状态
     * @param action     采取的动作
     * @param reward     获得的奖励
     * @param nextState  下一个状态
     */
    public void observe(QLearningState state, QLearningAction action,
                        double reward, QLearningState nextState) {
        int s = state.index();
        int a = action.ordinal();
        int ns = nextState.index();

        double qOld = qTable.getQ(s, a);
        double qNextMax = qTable.getQ(ns, qTable.bestAction(ns));
        double qNew = qOld + alpha * (reward + gamma * qNextMax - qOld);
        qTable.setQ(s, a, (float) qNew);

        // 统计
        totalSteps++;
        totalReward += reward;
        recentReward += reward;
        recentSteps++;

        // 衰减 epsilon
        if (epsilon > epsilonMin) {
            epsilon *= epsilonDecay;
            if (epsilon < epsilonMin) epsilon = epsilonMin;
        }

        if (totalSteps % 1000 == 0) {
            QLMZombieMod.LOGGER.debug("[Q-Learning] steps={} eps={} avgReward={}",
                    totalSteps, String.format("%.3f", epsilon),
                    String.format("%.2f", recentReward / Math.max(1, recentSteps)));
            recentReward = 0.0;
            recentSteps = 0;
        }
    }

    /** 探索率重置（用于环境剧变时重新探索） */
    public void boostExploration(double newEpsilon) {
        this.epsilon = Math.max(this.epsilon, newEpsilon);
    }

    public QTable getQTable() {
        return qTable;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public double getAverageReward() {
        return totalSteps > 0 ? totalReward / totalSteps : 0.0;
    }
}

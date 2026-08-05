/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * AI Algorithm Manager — 多算法整合与动态切换
 *
 * 整合的算法:
 *   1. Behavior Tree (行为树)   — 适用于复杂任务编排
 *   2. Finite State Machine     — 适用于状态切换明确的场景
 *   3. Q-Learning               — 适用于学习最优策略
 *   4. Utility AI               — 适用于多因素权衡
 *   5. Fuzzy Logic              — 适用于模糊输入的连续决策
 *   6. A* Pathfinding           — 适用于路径规划
 *
 * 切换策略:
 *   - AUTO: 自动根据场景选择最佳算法
 *   - MANUAL: 由配置或指令指定
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.ai.algorithm.behaviortree.BehaviorTree;
import com.qlm.zombie.ai.algorithm.fsm.FSMState;
import com.qlm.zombie.ai.algorithm.fsm.FiniteStateMachine;
import com.qlm.zombie.ai.algorithm.fuzzy.FuzzyInferenceSystem;
import com.qlm.zombie.ai.algorithm.qlearning.QLearningAction;
import com.qlm.zombie.ai.algorithm.qlearning.QLearningAgent;
import com.qlm.zombie.ai.algorithm.qlearning.QLearningState;
import com.qlm.zombie.ai.algorithm.qlearning.RewardFunction;
import com.qlm.zombie.ai.algorithm.utility.UtilitySystem;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * AI 算法管理器
 *
 * 每个 FakePlayerEntity 持有一个实例。它协调多个 AI 算法，
 * 根据配置或环境动态切换决策引擎。
 *
 * 数据流:
 *   1. 每 N tick 采集环境状态 → QLearningState
 *   2. 用当前算法选择动作
 *   3. 执行动作（驱动 Goal 或 Task）
 *   4. 记录奖励，反馈给 Q-Learning
 */
public class AIAlgorithmManager {

    public enum AlgorithmMode {
        /** 自动选择（默认） */
        AUTO,
        /** 行为树 */
        BEHAVIOR_TREE,
        /** 有限状态机 */
        FSM,
        /** Q-Learning 强化学习 */
        Q_LEARNING,
        /** 效用理论 */
        UTILITY,
        /** 模糊逻辑 */
        FUZZY,
        /** 禁用算法层（使用原 TaskRunner） */
        DISABLED
    }

    private final FakePlayerEntity ai;
    private AlgorithmMode mode = AlgorithmMode.AUTO;

    // 各算法实例（懒初始化或外部注入）
    private BehaviorTree behaviorTree;
    private FiniteStateMachine fsm;
    private QLearningAgent qLearning;
    private UtilitySystem utilitySystem;
    private FuzzyInferenceSystem fuzzySystem;

    // Q-Learning 状态追踪
    private QLearningState lastState;
    private QLearningAction lastAction;
    private final RewardFunction.ActionContext actionCtx = new RewardFunction.ActionContext();
    private float lastHealth;
    private int lastEnemyIdHash = 0;
    private int tickCounter = 0;
    private int decisionInterval = 20; // 决策间隔（每 N tick 决策一次），从配置加载

    public AIAlgorithmManager(FakePlayerEntity ai) {
        this.ai = ai;
        this.lastHealth = ai.getHealth();
        try {
            this.decisionInterval = QLMConfig.AI_DECISION_INTERVAL.get();
            if (this.decisionInterval < 1) this.decisionInterval = 20;
        } catch (Exception ignored) {
        }
    }

    public void setMode(AlgorithmMode mode) {
        this.mode = mode;
        QLMZombieMod.LOGGER.info("[AI算法] {} 切换到模式: {}", ai.getCustomNameStr(), mode);
    }

    public AlgorithmMode getMode() {
        return mode;
    }

    public void setBehaviorTree(BehaviorTree bt) {
        this.behaviorTree = bt;
    }

    public void setFsm(FiniteStateMachine fsm) {
        this.fsm = fsm;
    }

    public void setQLearning(QLearningAgent agent) {
        this.qLearning = agent;
    }

    public void setUtilitySystem(UtilitySystem system) {
        this.utilitySystem = system;
    }

    public void setFuzzySystem(FuzzyInferenceSystem system) {
        this.fuzzySystem = system;
    }

    public QLearningAgent getQLearning() {
        return qLearning;
    }

    /** 每 tick 调用 */
    public void tick() {
        if (mode == AlgorithmMode.DISABLED) return;
        if (!QLMConfig.AI_ALGORITHM_ENABLED.get()) return;
        if (ai.level().isClientSide) return;

        tickCounter++;

        // 每 decisionInterval tick 决策一次
        if (tickCounter % decisionInterval == 0) {
            try {
                runDecisionCycle();
            } catch (Exception e) {
                QLMZombieMod.LOGGER.error("[AI算法] 决策循环异常: {}", e.getMessage());
            }
        }

        // 持续 tick 当前算法（即使不在决策 tick）
        continuousTick();
    }

    /** 决策周期：感知、决策、学习 */
    private void runDecisionCycle() {
        QLearningState currentState = QLearningState.fromEnvironment(ai);

        // 学习：用上次动作的结果更新 Q 值
        if (lastState != null && lastAction != null && qLearning != null) {
            updateActionContext();
            double reward = RewardFunction.computeReward(lastState, currentState, lastAction, ai, actionCtx);
            qLearning.observe(lastState, lastAction, reward, currentState);
        }
        actionCtx.reset(); // 重置上下文供下次使用

        // 选择动作
        AlgorithmMode effectiveMode = mode == AlgorithmMode.AUTO
                ? pickAutoMode(currentState) : mode;

        QLearningAction chosen = chooseAction(effectiveMode, currentState);

        if (chosen != null) {
            executeAction(chosen);
            lastState = currentState;
            lastAction = chosen;
        }

        lastHealth = ai.getHealth();
    }

    /** 自动模式：根据状态选择算法 */
    private AlgorithmMode pickAutoMode(QLearningState state) {
        // 战斗场景 → Q-Learning（学习最优战术）
        if (state.enemy == QLearningState.EnemyDistance.NEAR) {
            return AlgorithmMode.Q_LEARNING;
        }
        // 低血量 → 模糊逻辑（连续决策逃跑/治疗的紧迫度）
        if (state.health == QLearningState.HealthLevel.LOW) {
            return AlgorithmMode.FUZZY;
        }
        // 安全场景 → 效用理论（选择最有价值的活动）
        if (state.enemy == QLearningState.EnemyDistance.NONE
                && state.health != QLearningState.HealthLevel.LOW) {
            return AlgorithmMode.UTILITY;
        }
        // 其他情况 → Q-Learning
        return AlgorithmMode.Q_LEARNING;
    }

    /** 用指定算法选择动作 */
    private QLearningAction chooseAction(AlgorithmMode algoMode, QLearningState state) {
        return switch (algoMode) {
            case Q_LEARNING -> qLearning != null ? qLearning.chooseAction(state) : null;
            case UTILITY -> utilitySystem != null ? mapUtilityToAction() : null;
            case FUZZY -> fuzzySystem != null ? mapFuzzyToAction(state) : null;
            case FSM -> fsm != null ? mapFSMToAction() : null;
            case BEHAVIOR_TREE -> behaviorTree != null ? mapBTToAction() : null;
            default -> null;
        };
    }

    private QLearningAction mapUtilityToAction() {
        String name = utilitySystem.getLastActionName();
        if (name == null) return QLearningAction.IDLE;
        return switch (name.toLowerCase()) {
            case "attack" -> QLearningAction.ATTACK;
            case "flee" -> QLearningAction.FLEE;
            case "eat" -> QLearningAction.EAT;
            case "follow" -> QLearningAction.FOLLOW;
            case "explore" -> QLearningAction.EXPLORE;
            default -> QLearningAction.IDLE;
        };
    }

    private QLearningAction mapFuzzyToAction(QLearningState state) {
        // 模糊系统输出 "behavior" ∈ [0, 1]
        // 0=IDLE, 0.2=FOLLOW, 0.4=EAT, 0.6=EXPLORE, 0.8=ATTACK, 1.0=FLEE
        java.util.Map<String, Double> outputs = fuzzySystem.evaluate(java.util.Map.of(
                "health", ai.getHealth() / Math.max(1.0, ai.getMaxHealth()),
                "food", ai.getFoodLevel() / 20.0,
                "threat", state.enemy == QLearningState.EnemyDistance.NEAR ? 1.0 :
                          state.enemy == QLearningState.EnemyDistance.FAR ? 0.5 : 0.0));
        Double behavior = outputs.get("behavior");
        if (behavior == null) return QLearningAction.IDLE;
        if (behavior < 0.1) return QLearningAction.IDLE;
        if (behavior < 0.3) return QLearningAction.FOLLOW;
        if (behavior < 0.5) return QLearningAction.EAT;
        if (behavior < 0.7) return QLearningAction.EXPLORE;
        if (behavior < 0.9) return QLearningAction.ATTACK;
        return QLearningAction.FLEE;
    }

    private QLearningAction mapFSMToAction() {
        if (fsm == null || fsm.getCurrentState() == null) return QLearningAction.IDLE;
        String name = fsm.getCurrentStateName().toLowerCase();
        return switch (name) {
            case "attack", "combat" -> QLearningAction.ATTACK;
            case "flee", "retreat" -> QLearningAction.FLEE;
            case "eat" -> QLearningAction.EAT;
            case "follow" -> QLearningAction.FOLLOW;
            case "explore" -> QLearningAction.EXPLORE;
            default -> QLearningAction.IDLE;
        };
    }

    private QLearningAction mapBTToAction() {
        // 行为树直接驱动行为，这里仅返回 IDLE 作为占位
        return QLearningAction.IDLE;
    }

    /** 持续 tick（行为树、FSM、效用系统需要每 tick 执行） */
    private void continuousTick() {
        if (behaviorTree != null && (mode == AlgorithmMode.BEHAVIOR_TREE
                || (mode == AlgorithmMode.AUTO && behaviorTree.getLastStatus() != null))) {
            behaviorTree.tick();
        }
        if (fsm != null && (mode == AlgorithmMode.FSM)) {
            fsm.tick();
        }
        if (utilitySystem != null && mode == AlgorithmMode.UTILITY) {
            utilitySystem.tick();
        }
    }

    /** 执行选定动作 — 通过 AI 玩家的行为接口 */
    private void executeAction(QLearningAction action) {
        switch (action) {
            case ATTACK -> {
                LivingEntity target = ai.getTarget();
                if (target == null || !target.isAlive()) {
                    // 通过 Goal 系统搜索目标
                    ai.setTarget(null);
                } else {
                    ai.getNavigation().moveTo(target, 1.2D);
                }
            }
            case FLEE -> {
                LivingEntity threat = ai.getTarget();
                if (threat != null) {
                    double dx = ai.getX() - threat.getX();
                    double dz = ai.getZ() - threat.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0.001) {
                        ai.getNavigation().moveTo(
                                ai.getX() + dx / len * 8,
                                ai.getY(),
                                ai.getZ() + dz / len * 8,
                                1.3D);
                    }
                }
            }
            case EAT -> {
                // 触发 AIEatFoodGoal（由 Goal 系统处理）
                // 此处仅作为决策信号
            }
            case FOLLOW -> {
                LivingEntity owner = ai.getOwner();
                if (owner != null && ai.distanceToSqr(owner) > 9.0D) {
                    ai.getNavigation().moveTo(owner, 1.0D);
                }
            }
            case EXPLORE -> {
                if (ai.getNavigation().isDone()) {
                    double angle = ai.getRandom().nextDouble() * Math.PI * 2;
                    double dist = 8.0 + ai.getRandom().nextDouble() * 12.0;
                    ai.getNavigation().moveTo(
                            ai.getX() + Math.cos(angle) * dist,
                            ai.getY(),
                            ai.getZ() + Math.sin(angle) * dist,
                            0.8D);
                }
            }
            case IDLE -> {
                ai.getNavigation().stop();
            }
        }
    }

    /** 更新动作上下文（用于奖励计算） */
    private void updateActionContext() {
        float currentHealth = ai.getHealth();
        if (currentHealth < lastHealth) {
            actionCtx.tookDamage += (lastHealth - currentHealth);
        }

        LivingEntity target = ai.getTarget();
        if (target != null) {
            int idHash = target.getId();
            if (lastEnemyIdHash != 0 && idHash != lastEnemyIdHash) {
                // 目标改变，可能是上一个敌人死亡
                actionCtx.enemyKilled = true;
            }
            lastEnemyIdHash = idHash;
        } else if (lastEnemyIdHash != 0) {
            actionCtx.enemyKilled = true;
            lastEnemyIdHash = 0;
        }
    }

    /** 持久化（保存到 AI 实体的 NBT） */
    public CompoundTag save(CompoundTag tag) {
        tag.putString("AlgorithmMode", mode.name());
        if (qLearning != null) {
            qLearning.getQTable().save(tag);
            tag.putInt("QLTotalSteps", qLearning.getTotalSteps());
        }
        return tag;
    }

    /** 加载持久化数据 */
    public void load(CompoundTag tag) {
        try {
            if (tag.contains("AlgorithmMode")) {
                mode = AlgorithmMode.valueOf(tag.getString("AlgorithmMode"));
            }
        } catch (Exception ignored) {
        }
        if (tag.contains("qtable") && qLearning != null) {
            // 重新加载 Q 表
            com.qlm.zombie.ai.algorithm.qlearning.QTable loaded =
                    com.qlm.zombie.ai.algorithm.qlearning.QTable.load(tag);
            // 直接替换内部 Q 表
            try {
                java.lang.reflect.Field f = QLearningAgent.class.getDeclaredField("qTable");
                f.setAccessible(true);
                f.set(qLearning, loaded);
            } catch (Exception ignored) {
            }
        }
    }
}

/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * AIAlgorithmPresets — 预设算法实例工厂
 * 为 AI 玩家提供开箱即用的行为树、FSM、效用系统、模糊系统
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm;

import com.qlm.zombie.ai.algorithm.behaviortree.*;
import com.qlm.zombie.ai.algorithm.fsm.FSMState;
import com.qlm.zombie.ai.algorithm.fsm.FiniteStateMachine;
import com.qlm.zombie.ai.algorithm.fuzzy.*;
import com.qlm.zombie.ai.algorithm.utility.Consideration;
import com.qlm.zombie.ai.algorithm.utility.UtilityAction;
import com.qlm.zombie.ai.algorithm.utility.UtilitySystem;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 预设算法实例工厂
 */
public final class AIAlgorithmPresets {

    private AIAlgorithmPresets() {}

    /**
     * 创建默认的行为树:
     *   Selector:
     *     - Sequence [危险响应]:
     *         Condition[敌人近] → Action[攻击/逃跑]
     *     - Sequence [自我维护]:
     *         Condition[饥饿] → Action[进食]
     *     - Sequence [跟随]:
     *         Condition[主人远] → Action[跟随]
     *     - Action[空闲]
     */
    public static BehaviorTree createDefaultBehaviorTree(FakePlayerEntity ai) {
        BTNode root = new BTSelector(ai,
                // 危险响应
                new BTSequence(ai,
                        new BTCondition(ai, () -> {
                            LivingEntity target = ai.getTarget();
                            return target != null && target.isAlive()
                                    && ai.distanceToSqr(target) < 64.0D;
                        }),
                        new BTAction(ai, () -> {
                            LivingEntity target = ai.getTarget();
                            if (target == null || !target.isAlive()) {
                                return BTNode.Status.FAILURE;
                            }
                            double dist = ai.distanceToSqr(target);
                            double hpRatio = ai.getHealth() / ai.getMaxHealth();
                            if (hpRatio < 0.3) {
                                // 低血量逃跑
                                double dx = ai.getX() - target.getX();
                                double dz = ai.getZ() - target.getZ();
                                double len = Math.sqrt(dx * dx + dz * dz);
                                if (len > 0.001) {
                                    ai.getNavigation().moveTo(
                                            ai.getX() + dx / len * 8,
                                            ai.getY(),
                                            ai.getZ() + dz / len * 8,
                                            1.3D);
                                }
                                return BTNode.Status.RUNNING;
                            }
                            if (dist > 4.0) {
                                ai.getNavigation().moveTo(target, 1.2D);
                            }
                            return BTNode.Status.RUNNING;
                        })
                ),
                // 自我维护
                new BTSequence(ai,
                        new BTCondition(ai, () -> ai.getFoodLevel() < 14),
                        new BTAction(ai, () -> {
                            // 由 AIEatFoodGoal 处理实际进食
                            return BTNode.Status.RUNNING;
                        })
                ),
                // 跟随
                new BTSequence(ai,
                        new BTCondition(ai, () -> {
                            LivingEntity owner = ai.getOwner();
                            return owner != null && ai.distanceToSqr(owner) > 16.0D;
                        }),
                        new BTAction(ai, () -> {
                            LivingEntity owner = ai.getOwner();
                            if (owner == null) return BTNode.Status.FAILURE;
                            if (ai.distanceToSqr(owner) > 9.0D) {
                                ai.getNavigation().moveTo(owner, 1.0D);
                            }
                            return BTNode.Status.RUNNING;
                        })
                ),
                // 空闲
                new BTAction(ai, () -> BTNode.Status.SUCCESS)
        );

        return new BehaviorTree(ai, root);
    }

    /**
     * 创建默认 FSM:
     *   IDLE  → (敌人近) → COMBAT
     *   COMBAT → (敌人远/死) → IDLE
     *   IDLE  → (饥饿) → EAT
     *   EAT   → (吃饱) → IDLE
     *   IDLE  → (主人远) → FOLLOW
     *   FOLLOW → (主人近) → IDLE
     */
    public static FiniteStateMachine createDefaultFSM(FakePlayerEntity ai) {
        FiniteStateMachine fsm = new FiniteStateMachine(ai);

        fsm.registerState(new FSMState("idle", ai) {
            @Override public void tick() {
                ai.getNavigation().stop();
            }
        });
        fsm.registerState(new FSMState("combat", ai) {
            @Override public void tick() {
                LivingEntity target = ai.getTarget();
                if (target != null && target.isAlive()) {
                    if (ai.distanceToSqr(target) > 4.0D) {
                        ai.getNavigation().moveTo(target, 1.2D);
                    }
                }
            }
        });
        fsm.registerState(new FSMState("flee", ai) {
            @Override public void tick() {
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
        });
        fsm.registerState(new FSMState("follow", ai) {
            @Override public void tick() {
                LivingEntity owner = ai.getOwner();
                if (owner != null && ai.distanceToSqr(owner) > 9.0D) {
                    ai.getNavigation().moveTo(owner, 1.0D);
                }
            }
        });
        fsm.registerState(new FSMState("eat", ai) {
            @Override public void tick() {
                // 进食由 Goal 系统处理
            }
        });

        // 转换
        fsm.addTransition("idle", "combat",
                () -> ai.getTarget() != null && ai.getTarget().isAlive()
                        && ai.distanceToSqr(ai.getTarget()) < 1024.0D);
        fsm.addTransition("idle", "flee",
                () -> ai.getTarget() != null && ai.getHealth() / ai.getMaxHealth() < 0.3);
        fsm.addTransition("idle", "follow",
                () -> ai.getOwner() != null && ai.distanceToSqr(ai.getOwner()) > 25.0D);
        fsm.addTransition("idle", "eat", () -> ai.getFoodLevel() < 10);

        fsm.addTransition("combat", "idle",
                () -> ai.getTarget() == null || !ai.getTarget().isAlive()
                        || ai.distanceToSqr(ai.getTarget()) > 1024.0D);
        fsm.addTransition("combat", "flee",
                () -> ai.getHealth() / ai.getMaxHealth() < 0.3);

        fsm.addTransition("flee", "idle",
                () -> ai.getTarget() == null || !ai.getTarget().isAlive()
                        || ai.distanceToSqr(ai.getTarget()) > 400.0D);

        fsm.addTransition("follow", "idle",
                () -> ai.getOwner() == null || ai.distanceToSqr(ai.getOwner()) < 16.0D);
        fsm.addTransition("follow", "combat",
                () -> ai.getTarget() != null && ai.getTarget().isAlive());

        fsm.addTransition("eat", "idle", () -> ai.getFoodLevel() >= 18);

        fsm.setCurrentState("idle");
        return fsm;
    }

    /**
     * 创建默认效用系统:
     *   动作: 攻击 / 逃跑 / 进食 / 跟随 / 探索 / 空闲
     *   每个动作由多个 Consideration 评分
     */
    public static UtilitySystem createDefaultUtilitySystem(FakePlayerEntity ai) {
        UtilitySystem us = new UtilitySystem(ai);

        // 攻击
        us.addAction(new UtilityAction("attack", () -> {
            LivingEntity target = ai.getTarget();
            if (target != null && target.isAlive() && ai.distanceToSqr(target) > 4.0D) {
                ai.getNavigation().moveTo(target, 1.2D);
            }
        })
                .addConsideration(new Consideration(
                        () -> ai.getTarget() != null && ai.getTarget().isAlive() ? 1.0 : 0.0,
                        Consideration.ResponseCurve.STEP))
                .addConsideration(new Consideration(
                        () -> 1.0 - Math.min(1.0, ai.distanceToSqr(ai.getTarget()) / 1024.0),
                        Consideration.ResponseCurve.INVERSE_QUADRATIC))
                .addConsideration(new Consideration(
                        () -> ai.getHealth() / ai.getMaxHealth(),
                        Consideration.ResponseCurve.LOGISTIC))
                .addConsideration(new Consideration(
                        () -> ai.getMainHandItem().isEmpty() ? 0.0 : 1.0,
                        Consideration.ResponseCurve.STEP)));

        // 逃跑
        us.addAction(new UtilityAction("flee", () -> {
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
        })
                .addConsideration(new Consideration(
                        () -> 1.0 - ai.getHealth() / ai.getMaxHealth(),
                        Consideration.ResponseCurve.QUADRATIC))
                .addConsideration(new Consideration(
                        () -> ai.getTarget() != null ? 1.0 : 0.0,
                        Consideration.ResponseCurve.STEP)));

        // 进食
        us.addAction(new UtilityAction("eat", () -> {})
                .addConsideration(new Consideration(
                        () -> 1.0 - ai.getFoodLevel() / 20.0,
                        Consideration.ResponseCurve.QUADRATIC)));

        // 跟随
        us.addAction(new UtilityAction("follow", () -> {
            LivingEntity owner = ai.getOwner();
            if (owner != null && ai.distanceToSqr(owner) > 9.0D) {
                ai.getNavigation().moveTo(owner, 1.0D);
            }
        })
                .addConsideration(new Consideration(
                        () -> {
                            LivingEntity owner = ai.getOwner();
                            if (owner == null) return 0.0;
                            double d = ai.distanceToSqr(owner);
                            return Math.min(1.0, d / 1024.0);
                        },
                        Consideration.ResponseCurve.LOGISTIC)));

        // 探索
        us.addAction(new UtilityAction("explore", () -> {
            if (ai.getNavigation().isDone()) {
                double angle = ai.getRandom().nextDouble() * Math.PI * 2;
                double dist = 8.0 + ai.getRandom().nextDouble() * 12.0;
                ai.getNavigation().moveTo(
                        ai.getX() + Math.cos(angle) * dist,
                        ai.getY(),
                        ai.getZ() + Math.sin(angle) * dist,
                        0.8D);
            }
        })
                .addConsideration(new Consideration(
                        () -> ai.getTarget() == null ? 1.0 : 0.0,
                        Consideration.ResponseCurve.STEP))
                .addConsideration(new Consideration(
                        () -> ai.getFoodLevel() / 20.0,
                        Consideration.ResponseCurve.LOGISTIC))
                .addConsideration(new Consideration(
                        () -> 1.0 - Math.min(1.0, ai.getHealth() < ai.getMaxHealth() ? 1.0 : 0.0),
                        Consideration.ResponseCurve.STEP)));

        // 空闲
        us.addAction(new UtilityAction("idle", () -> ai.getNavigation().stop())
                .addConsideration(new Consideration(
                        () -> ai.getTarget() == null ? 1.0 : 0.0,
                        Consideration.ResponseCurve.STEP))
                .addConsideration(new Consideration(
                        () -> ai.getFoodLevel() / 20.0,
                        Consideration.ResponseCurve.LOGISTIC)));

        return us;
    }

    /**
     * 创建默认模糊推理系统:
     *   输入: health, food, threat
     *   输出: behavior (0=IDLE ... 1=FLEE)
     */
    public static FuzzyInferenceSystem createDefaultFuzzySystem(FakePlayerEntity ai) {
        FuzzyInferenceSystem fis = new FuzzyInferenceSystem();

        // 健康变量 [0,1]
        FuzzyVariable health = new FuzzyVariable("health", 0.0, 1.0);
        health.addSet("LOW", FuzzySet.leftShoulder(0.0, 0.4));
        health.addSet("MEDIUM", FuzzySet.triangle(0.2, 0.5, 0.8));
        health.addSet("HIGH", FuzzySet.rightShoulder(0.6, 1.0));
        fis.addInputVariable(health);

        // 食物变量 [0,1]
        FuzzyVariable food = new FuzzyVariable("food", 0.0, 1.0);
        food.addSet("LOW", FuzzySet.leftShoulder(0.0, 0.35));
        food.addSet("MEDIUM", FuzzySet.triangle(0.2, 0.5, 0.8));
        food.addSet("HIGH", FuzzySet.rightShoulder(0.65, 1.0));
        fis.addInputVariable(food);

        // 威胁变量 [0,1]
        FuzzyVariable threat = new FuzzyVariable("threat", 0.0, 1.0);
        threat.addSet("NONE", FuzzySet.leftShoulder(0.0, 0.2));
        threat.addSet("SOME", FuzzySet.triangle(0.1, 0.5, 0.9));
        threat.addSet("HIGH", FuzzySet.rightShoulder(0.8, 1.0));
        fis.addInputVariable(threat);

        // 输出变量: behavior [0,1]
        // 0=IDLE, 0.2=FOLLOW, 0.4=EAT, 0.6=EXPLORE, 0.8=ATTACK, 1.0=FLEE
        FuzzyVariable behavior = new FuzzyVariable("behavior", 0.0, 1.0);
        behavior.addSet("IDLE", FuzzySet.singleton(0.0));
        behavior.addSet("FOLLOW", FuzzySet.singleton(0.2));
        behavior.addSet("EAT", FuzzySet.singleton(0.4));
        behavior.addSet("EXPLORE", FuzzySet.singleton(0.6));
        behavior.addSet("ATTACK", FuzzySet.singleton(0.8));
        behavior.addSet("FLEE", FuzzySet.singleton(1.0));
        fis.addOutputVariable(behavior);

        // 规则库
        // 威胁高 + 血量低 → FLEE
        fis.addRule(new FuzzyRule("behavior", "FLEE").when("threat", "HIGH").when("health", "LOW"));
        // 威胁高 + 血量中 → ATTACK
        fis.addRule(new FuzzyRule("behavior", "ATTACK").when("threat", "HIGH").when("health", "MEDIUM"));
        fis.addRule(new FuzzyRule("behavior", "ATTACK").when("threat", "HIGH").when("health", "HIGH"));
        // 威胁中 → ATTACK
        fis.addRule(new FuzzyRule("behavior", "ATTACK").when("threat", "SOME"));
        // 威胁无 + 食物低 → EAT
        fis.addRule(new FuzzyRule("behavior", "EAT").when("threat", "NONE").when("food", "LOW"));
        // 威胁无 + 食物中 → EXPLORE
        fis.addRule(new FuzzyRule("behavior", "EXPLORE").when("threat", "NONE").when("food", "MEDIUM"));
        fis.addRule(new FuzzyRule("behavior", "EXPLORE").when("threat", "NONE").when("food", "HIGH"));
        // 兜底
        fis.addRule(new FuzzyRule("behavior", "IDLE").when("threat", "NONE").when("food", "HIGH").when("health", "HIGH"));

        return fis;
    }
}

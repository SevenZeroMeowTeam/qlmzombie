/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * FiniteStateMachine — 状态机主类
 * 管理 states 和 transitions，每 tick 检查转换并执行当前状态
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.fsm;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.entity.FakePlayerEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 有限状态机
 *
 * 用法:
 *   FiniteStateMachine fsm = new FiniteStateMachine(ai);
 *   fsm.registerState(new IdleState(ai));
 *   fsm.registerState(new CombatState(ai));
 *   fsm.addTransition("idle", "combat", () -> enemyNearby);
 *   fsm.setCurrentState("idle");
 *   fsm.tick();
 */
public class FiniteStateMachine {

    private final FakePlayerEntity ai;
    private final Map<String, FSMState> states = new HashMap<>();
    private final Map<String, java.util.List<FSMTransition>> transitions = new HashMap<>();
    private FSMState currentState;

    public FiniteStateMachine(FakePlayerEntity ai) {
        this.ai = ai;
    }

    public void registerState(FSMState state) {
        states.put(state.getName(), state);
        transitions.computeIfAbsent(state.getName(), k -> new java.util.ArrayList<>());
    }

    public void addTransition(FSMTransition transition) {
        transitions.computeIfAbsent(transition.getFromState(), k -> new java.util.ArrayList<>())
                .add(transition);
    }

    public void addTransition(String from, String to, java.util.function.Supplier<Boolean> condition) {
        addTransition(new FSMTransition(from, to, condition));
    }

    public void setCurrentState(String name) {
        FSMState next = states.get(name);
        if (next == null) {
            QLMZombieMod.LOGGER.warn("[FSM] 状态不存在: {}", name);
            return;
        }
        if (currentState != null) {
            currentState.onExit();
        }
        currentState = next;
        currentState.onEnter();
    }

    public void tick() {
        if (currentState == null) return;

        // 检查转换
        java.util.List<FSMTransition> stateTransitions = transitions.get(currentState.getName());
        if (stateTransitions != null) {
            for (FSMTransition t : stateTransitions) {
                if (t.canTrigger()) {
                    String target = t.getToState();
                    FSMState next = states.get(target);
                    if (next != null) {
                        currentState.onExit();
                        currentState = next;
                        currentState.onEnter();
                        break;
                    }
                }
            }
        }

        // 执行当前状态
        currentState.tick();
    }

    public FSMState getCurrentState() {
        return currentState;
    }

    public String getCurrentStateName() {
        return currentState != null ? currentState.getName() : "<none>";
    }

    public Collection<FSMState> getStates() {
        return states.values();
    }
}

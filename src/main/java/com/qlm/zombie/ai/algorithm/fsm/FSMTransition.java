/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * FSMTransition — 状态转换
 * 持有条件谓词，当条件为真时从源状态转换到目标状态
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.fsm;

import java.util.function.Supplier;

/**
 * 状态转换
 *
 * 当 condition 返回 true 时，FSM 从 fromState 切换到 toState
 */
public class FSMTransition {

    private final String fromState;
    private final String toState;
    private final Supplier<Boolean> condition;

    public FSMTransition(String fromState, String toState, Supplier<Boolean> condition) {
        this.fromState = fromState;
        this.toState = toState;
        this.condition = condition;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public boolean canTrigger() {
        return condition.get();
    }
}

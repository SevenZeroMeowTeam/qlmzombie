/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Finite State Machine Framework — 原创实现
 * FSMState — 状态基类
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.fsm;

import com.qlm.zombie.entity.FakePlayerEntity;

/**
 * 有限状态机状态
 *
 * 生命周期:
 *   onEnter()  — 进入状态时调用一次
 *   tick()     — 每 tick 调用
 *   onExit()   — 离开状态时调用一次
 */
public abstract class FSMState {

    protected final FakePlayerEntity ai;
    protected final String name;

    public FSMState(String name, FakePlayerEntity ai) {
        this.name = name;
        this.ai = ai;
    }

    public String getName() {
        return name;
    }

    public void onEnter() {}

    public abstract void tick();

    public void onExit() {}
}

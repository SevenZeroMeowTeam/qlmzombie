/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * BTAction — 动作节点
 * 执行具体的 AI 行为（攻击、移动、挖矿等），可返回 RUNNING 表示持续执行
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

import java.util.function.Supplier;

public class BTAction extends BTNode {

    private final Supplier<Status> action;

    public BTAction(FakePlayerEntity ai, Supplier<Status> action) {
        super(ai);
        this.action = action;
    }

    @Override
    public Status tick() {
        return action.get();
    }
}

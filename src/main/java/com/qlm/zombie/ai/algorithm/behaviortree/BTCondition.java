/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * BTCondition — 条件节点
 * 包装一个布尔判断，返回 SUCCESS 或 FAILURE，不产生 RUNNING
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

import java.util.function.Supplier;

public class BTCondition extends BTNode {

    private final Supplier<Boolean> predicate;

    public BTCondition(FakePlayerEntity ai, Supplier<Boolean> predicate) {
        super(ai);
        this.predicate = predicate;
    }

    @Override
    public Status tick() {
        return predicate.get() ? Status.SUCCESS : Status.FAILURE;
    }
}

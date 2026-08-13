/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * BTInverter — 反转装饰器
 * SUCCESS ↔ FAILURE，RUNNING 保持不变
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

public class BTInverter extends BTDecorator {

    public BTInverter(FakePlayerEntity ai, BTNode child) {
        super(ai, child);
    }

    @Override
    public Status tick() {
        Status status = child.tick();
        if (status == Status.SUCCESS) return Status.FAILURE;
        if (status == Status.FAILURE) return Status.SUCCESS;
        return Status.RUNNING;
    }
}

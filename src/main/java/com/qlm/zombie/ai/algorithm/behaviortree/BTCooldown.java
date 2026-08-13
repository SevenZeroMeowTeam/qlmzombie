/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * BTCooldown — 冷却装饰器
 * 冷却时间内不执行子节点，直接返回 FAILURE
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

public class BTCooldown extends BTDecorator {

    private final long cooldownMs;
    private long lastExecuted = -1;

    public BTCooldown(FakePlayerEntity ai, long cooldownMs, BTNode child) {
        super(ai, child);
        this.cooldownMs = cooldownMs;
    }

    @Override
    public Status tick() {
        long now = System.currentTimeMillis();
        if (lastExecuted < 0 || now - lastExecuted >= cooldownMs) {
            Status status = child.tick();
            if (status == Status.SUCCESS || status == Status.FAILURE) {
                lastExecuted = now;
            }
            return status;
        }
        return Status.FAILURE;
    }

    @Override
    public void reset() {
        super.reset();
        lastExecuted = -1;
    }
}

/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Behavior Tree Framework — 原创实现
 * 装饰器基类: 仅持有单个子节点，对其结果做包装或修改
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

/**
 * 装饰器节点基类
 *
 * 装饰器有一个子节点，可以修改其行为:
 *   - Inverter: 反转子节点结果
 *   - Repeater: 重复执行子节点
 *   - Succeeder: 总是返回 SUCCESS
 *   - UntilFail: 重复执行直到失败
 *   - Cooldown: 冷却时间内不允许执行
 */
public abstract class BTDecorator extends BTNode {

    protected final BTNode child;

    public BTDecorator(FakePlayerEntity ai, BTNode child) {
        super(ai);
        this.child = child;
    }

    @Override
    public void onEnter() {
        child.onEnter();
    }

    @Override
    public void reset() {
        child.reset();
    }
}

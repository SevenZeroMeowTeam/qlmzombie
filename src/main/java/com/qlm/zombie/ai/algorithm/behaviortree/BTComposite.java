/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Behavior Tree Framework — 原创实现
 * 组合节点：可包含多个子节点
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 组合节点基类
 * 持有子节点列表，按特定策略执行
 */
public abstract class BTComposite extends BTNode {

    protected final List<BTNode> children = new ArrayList<>();
    protected int currentIndex = 0;

    public BTComposite(FakePlayerEntity ai, BTNode... nodes) {
        super(ai);
        children.addAll(Arrays.asList(nodes));
    }

    @Override
    public void onEnter() {
        currentIndex = 0;
        if (!children.isEmpty()) {
            children.get(0).onEnter();
        }
    }

    @Override
    public void reset() {
        currentIndex = 0;
        for (BTNode child : children) {
            child.reset();
        }
    }
}

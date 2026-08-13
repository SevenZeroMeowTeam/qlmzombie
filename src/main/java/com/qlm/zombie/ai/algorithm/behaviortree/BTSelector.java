/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Behavior Tree Framework — 原创实现
 * 选择节点: 依次尝试子节点，任一成功即返回 SUCCESS，全部失败才返回 FAILURE
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

/**
 * 选择节点 (Selector / Fallback)
 *
 * 行为:
 *   - 从左到右依次执行子节点
 *   - 子节点返回 SUCCESS → 整体返回 SUCCESS
 *   - 子节点返回 FAILURE → 继续尝试下一个
 *   - 子节点返回 RUNNING → 整体返回 RUNNING
 *   - 全部失败 → 返回 FAILURE
 *
 * 语义: OR（或）— 提供降级方案
 */
public class BTSelector extends BTComposite {

    public BTSelector(FakePlayerEntity ai, BTNode... nodes) {
        super(ai, nodes);
    }

    @Override
    public Status tick() {
        while (currentIndex < children.size()) {
            BTNode current = children.get(currentIndex);
            Status status = current.tick();

            if (status == Status.RUNNING) {
                return Status.RUNNING;
            }
            if (status == Status.SUCCESS) {
                current.onExit(status);
                return Status.SUCCESS;
            }
            // FAILURE → 推进到下一个子节点
            current.onExit(Status.FAILURE);
            currentIndex++;
            if (currentIndex < children.size()) {
                children.get(currentIndex).onEnter();
            }
        }
        return Status.FAILURE;
    }
}

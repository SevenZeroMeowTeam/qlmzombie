/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Behavior Tree Framework — 原创实现
 * 顺序节点: 按顺序执行子节点，全部成功才返回 SUCCESS，任一失败立即返回 FAILURE
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

/**
 * 顺序节点 (Sequence)
 *
 * 行为:
 *   - 从左到右依次执行子节点
 *   - 子节点返回 SUCCESS → 继续执行下一个
 *   - 子节点返回 FAILURE → 整体返回 FAILURE
 *   - 子节点返回 RUNNING → 整体返回 RUNNING，下次从该节点继续
 *   - 全部成功 → 返回 SUCCESS
 *
 * 语义: AND（与）
 */
public class BTSequence extends BTComposite {

    public BTSequence(FakePlayerEntity ai, BTNode... nodes) {
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
            if (status == Status.FAILURE) {
                current.onExit(status);
                return Status.FAILURE;
            }
            // SUCCESS → 推进到下一个子节点
            current.onExit(Status.SUCCESS);
            currentIndex++;
            if (currentIndex < children.size()) {
                children.get(currentIndex).onEnter();
            }
        }
        return Status.SUCCESS;
    }
}

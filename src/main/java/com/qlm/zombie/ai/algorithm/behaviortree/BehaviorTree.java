/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * BehaviorTree — 行为树主类
 * 持有根节点，每 tick 驱动整棵树执行
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

/**
 * 行为树
 *
 * 每帧调用 tick() 驱动根节点执行。如果根节点返回 SUCCESS 或 FAILURE，
 * 下一帧将从根节点重新开始（重新 onEnter）。
 *
 * 用法:
 *   BehaviorTree bt = new BehaviorTree(ai, root);
 *   bt.tick();
 */
public class BehaviorTree {

    private final FakePlayerEntity ai;
    private final BTNode root;
    private BTNode.Status lastStatus = BTNode.Status.RUNNING;

    public BehaviorTree(FakePlayerEntity ai, BTNode root) {
        this.ai = ai;
        this.root = root;
        root.onEnter();
    }

    public void tick() {
        if (lastStatus == BTNode.Status.SUCCESS || lastStatus == BTNode.Status.FAILURE) {
            root.reset();
            root.onEnter();
        }
        lastStatus = root.tick();
    }

    /** 重置整棵树，下个 tick 重新开始 */
    public void restart() {
        root.reset();
        root.onEnter();
        lastStatus = BTNode.Status.RUNNING;
    }

    public BTNode.Status getLastStatus() {
        return lastStatus;
    }

    public FakePlayerEntity getAI() {
        return ai;
    }
}

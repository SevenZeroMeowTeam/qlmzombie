/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Behavior Tree Framework — 原创实现
 * 参考: "Behavior Trees in Robotics and AI" (Colledanchise & Ögren, 2018)
 * 通用行为树节点抽象，所有节点继承自此
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.behaviortree;

import com.qlm.zombie.entity.FakePlayerEntity;

/**
 * 行为树节点基类
 *
 * 节点执行结果:
 *  - SUCCESS: 节点成功完成
 *  - FAILURE: 节点执行失败
 *  - RUNNING: 节点正在执行，需要后续 tick 继续
 */
public abstract class BTNode {

    public enum Status { SUCCESS, FAILURE, RUNNING }

    protected final FakePlayerEntity ai;

    public BTNode(FakePlayerEntity ai) {
        this.ai = ai;
    }

    /** 节点进入时调用（仅一次） */
    public void onEnter() {}

    /** 每 tick 执行 */
    public abstract Status tick();

    /** 节点退出时调用 */
    public void onExit(Status status) {}

    /** 节点重置，便于复用 */
    public void reset() {}
}

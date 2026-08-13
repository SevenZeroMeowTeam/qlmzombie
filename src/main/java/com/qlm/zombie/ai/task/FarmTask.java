/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * This file is part of QLM Zombie Mod.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *
 * This class is an ORIGINAL implementation inspired by the design patterns of:
 *   - PlayerEngine (https://github.com/Goodbird-git/PlayerEngine)
 *     Copyright (c) Goodbird-git
 *     Licensed under MIT License
 *   - Task subclass pattern (start/tick/stop lifecycle)
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements.
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.task;

import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.world.entity.player.Player;

/** 种田任务 — 保持执行直到超时（占位实现） */
public class FarmTask extends Task {

    protected final FakePlayerEntity ai;

    public FarmTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
        this.ai = ai;
    }

    @Override
    protected void onStart() {
        ai.setTarget(null);
    }

    @Override
    protected void doTick() {
        if (ai.isSitting()) return;
        // 种田任务保持执行直到超时
    }

    @Override
    public String getName() { return "farm"; }
}

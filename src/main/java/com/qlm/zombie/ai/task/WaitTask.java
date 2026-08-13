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

/** 等待任务 — AI 原地待命 */
public class WaitTask extends Task {

    protected final FakePlayerEntity ai;

    public WaitTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
        this.ai = ai;
    }

    @Override
    protected void onStart() {
        ai.setSitting(true);
        ai.getNavigation().stop();
    }

    @Override
    protected void doTick() {
        // 原地待命，不执行任何操作
    }

    @Override
    public String getName() { return "wait"; }

    @Override
    public boolean isActiveTask() { return false; }
}

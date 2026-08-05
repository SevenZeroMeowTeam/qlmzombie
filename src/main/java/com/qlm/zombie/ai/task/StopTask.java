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

/** 停止任务 — 停止一切工作，原地休息 */
public class StopTask extends Task {

    public StopTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
    }

    @Override
    public void start() {
        ai.setSitting(true);
        ai.setActiveTask(false);
        ai.getNavigation().stop();
        ai.setTarget(null);
        notifyOwner("好的，我停止工作");
    }

    @Override
    public void tick() {
        // 停止状态，不操作
    }

    @Override
    public void stop() {
        // 停止任务被停止时不做额外清理
    }

    @Override
    public String getName() { return "stop"; }

    @Override
    public boolean isActiveTask() { return false; }
}

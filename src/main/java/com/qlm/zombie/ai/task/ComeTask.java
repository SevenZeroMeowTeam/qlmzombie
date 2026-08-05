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

/** 过来任务 — AI 走向主人后自动跟随 */
public class ComeTask extends Task {

    public ComeTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
    }

    @Override
    public void start() {
        if (owner != null) {
            ai.getNavigation().moveTo(owner, 1.2D);
        }
    }

    @Override
    public void tick() {
        if (owner == null) {
            finish();
            return;
        }
        double distSq = ai.distanceToSqr(owner);
        if (distSq < 4.0D) {
            finish();
        } else {
            ai.getNavigation().moveTo(owner, 1.2D);
        }
    }

    @Override
    public String getName() { return "come"; }

    @Override
    public boolean isActiveTask() { return false; }
}

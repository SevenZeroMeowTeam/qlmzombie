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
import net.minecraft.world.phys.Vec3;

/** 探索任务 — 随机方向漫步15秒 */
public class ExploreTask extends Task {

    private static final long EXPLORE_DURATION_MS = 15000;

    protected final FakePlayerEntity ai;

    public ExploreTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
        this.ai = ai;
    }

    @Override
    protected void onStart() {
        ai.setTarget(null);
        Vec3 dir = new Vec3(ai.getRandom().nextDouble() - 0.5, 0, ai.getRandom().nextDouble() - 0.5).normalize();
        ai.getNavigation().moveTo(ai.getX() + dir.x * 30, ai.getY(), ai.getZ() + dir.z * 30, 0.8D);
    }

    @Override
    protected void doTick() {
        if (ai.isSitting()) return;
        ai.setTarget(null);
        if (getElapsedTime() > EXPLORE_DURATION_MS) {
            finish();
            return;
        }
        if (ai.getNavigation().isDone() || ai.tickCount % 200 == 0) {
            Vec3 dir = new Vec3(ai.getRandom().nextDouble() - 0.5, 0, ai.getRandom().nextDouble() - 0.5).normalize();
            ai.getNavigation().moveTo(ai.getX() + dir.x * 20, ai.getY(), ai.getZ() + dir.z * 20, 0.8D);
        }
    }

    @Override
    public String getName() { return "explore"; }
}

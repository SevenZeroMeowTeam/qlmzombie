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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** 守护任务 — 保护主人，附近有敌时攻击，无敌时跟随 */
public class GuardTask extends Task {

    protected final FakePlayerEntity ai;
    protected final Player owner;

    public GuardTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
        this.ai = ai;
        this.owner = owner;
    }

    @Override
    protected void onStart() {
        ai.setTarget(null);
        LivingEntity target = findNearestHostile(32);
        if (target != null) {
            ai.setTarget(target);
            ai.getNavigation().moveTo(target, 1.2D);
        }
    }

    @Override
    protected void doTick() {
        if (ai.isSitting()) return;
        LivingEntity target = findNearestHostile(32);
        if (target != null) {
            ai.setTarget(target);
            double distSq = ai.distanceToSqr(target);
            if (distSq > 4.0D) {
                ai.getNavigation().moveTo(target, 1.2D);
            } else {
                ai.getNavigation().stop();
            }
        } else {
            ai.setTarget(null);
            if (owner != null) {
                double distSq = ai.distanceToSqr(owner);
                if (distSq > 9.0D) {
                    ai.getNavigation().moveTo(owner, 1.0D);
                }
            }
        }
    }

    @Override
    public String getName() { return "guard"; }
}

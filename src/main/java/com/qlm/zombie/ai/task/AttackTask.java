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

/** 攻击任务 — 搜索并攻击附近敌对生物 */
public class AttackTask extends Task {

    protected final FakePlayerEntity ai;

    public AttackTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
        this.ai = ai;
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
        LivingEntity currentTarget = ai.getTarget();
        if (currentTarget == null || !currentTarget.isAlive() || ai.distanceToSqr(currentTarget) > 1024.0D) {
            ai.setTarget(null);
            LivingEntity target = findNearestHostile(32);
            if (target != null) {
                resetNoTargetCounter();
                ai.setTarget(target);
                ai.getNavigation().moveTo(target, 1.2D);
            } else {
                if (!handleNoTarget(TARGET_SEARCH_TIMEOUT)) {
                    finish();
                }
            }
        } else {
            resetNoTargetCounter();
            double distSq = ai.distanceToSqr(currentTarget);
            if (distSq > 4.0D) {
                ai.getNavigation().moveTo(currentTarget, 1.2D);
            } else {
                ai.getNavigation().stop();
            }
        }
    }

    @Override
    public String getName() { return "attack"; }
}

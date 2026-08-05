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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 丢弃任务 — 从AI背包丢弃物品到地上 */
public class DropTask extends Task {

    private final String itemName;
    private final int count;

    public DropTask(FakePlayerEntity ai, Player owner, String itemName, int count) {
        super(ai, owner);
        this.itemName = itemName;
        this.count = count;
    }

    @Override
    public void start() {
        if (itemName == null || itemName.isEmpty()) {
            notifyOwnerSystem("请告诉我丢什么物品");
            finish();
            return;
        }

        int dropped = 0;
        for (int i = 0; i < ai.getInventory().getContainerSize() && dropped < count; i++) {
            ItemStack stack = ai.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                int dropCount = Math.min(count - dropped, stack.getCount());
                ItemStack dropStack = stack.copy();
                dropStack.setCount(dropCount);
                ItemEntity itemEntity = new ItemEntity(ai.level(), ai.getX(), ai.getY() + 1, ai.getZ(), dropStack);
                itemEntity.setDeltaMovement(ai.getLookAngle().scale(0.3));
                ai.level().addFreshEntity(itemEntity);
                stack.shrink(dropCount);
                dropped += dropCount;
            }
        }
        notifyOwner("丢弃了 " + dropped + " 个" + itemName);
        finish();
    }

    @Override
    public void tick() {
        // 即时任务
    }

    @Override
    public String getName() { return "drop"; }
}

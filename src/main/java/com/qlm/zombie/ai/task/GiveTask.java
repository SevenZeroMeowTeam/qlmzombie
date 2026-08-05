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

import com.qlm.zombie.ai.AIItemRegistry;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** 给予任务 — 从AI背包取出物品给玩家 */
public class GiveTask extends Task {

    private final String itemName;
    private final int count;

    public GiveTask(FakePlayerEntity ai, Player owner, String itemName, int count) {
        super(ai, owner);
        this.itemName = itemName;
        this.count = count;
    }

    @Override
    public void start() {
        if (itemName == null || itemName.isEmpty()) {
            notifyOwnerSystem("请告诉我你想要什么物品");
            finish();
            return;
        }

        String resolvedId = AIItemRegistry.findItemId(itemName);
        int foundCount = 0;
        ItemStack foundStack = ItemStack.EMPTY;
        int foundSlot = -1;

        for (int i = 0; i < ai.getInventory().getContainerSize(); i++) {
            ItemStack stack = ai.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String registryName = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                if ((resolvedId != null && resolvedId.equals(registryName)) ||
                    AIItemRegistry.matches(itemName, registryName) ||
                    registryName.toLowerCase().contains(itemName.toLowerCase())) {
                    foundStack = stack;
                    foundSlot = i;
                    foundCount += stack.getCount();
                    break;
                }
            }
        }

        if (foundCount > 0 && owner != null) {
            int transferCount = Math.min(count, foundCount);
            ItemStack transferStack = foundStack.copy();
            transferStack.setCount(transferCount);
            if (owner.getInventory().add(transferStack)) {
                foundStack.shrink(transferCount);
                ai.getInventory().setItem(foundSlot, foundStack);
                notifyOwner("给你 " + transferCount + " 个" + itemName);
            } else {
                notifyOwnerSystem("你的背包满了");
            }
        } else {
            notifyOwnerSystem("我没有" + itemName);
        }
        finish();
    }

    @Override
    public void tick() {
        // 即时任务，start 中完成
    }

    @Override
    public String getName() { return "give"; }
}

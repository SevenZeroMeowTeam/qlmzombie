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
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/** 治疗任务 — 从背包吃食物恢复饱食度 */
public class HealTask extends Task {

    protected final FakePlayerEntity ai;

    public HealTask(FakePlayerEntity ai, Player owner) {
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
        ai.setTarget(null);

        // 饱食度已满，完成
        if (ai.getFoodLevel() >= 20 && ai.getHealth() >= ai.getMaxHealth()) {
            finish();
            return;
        }

        // 尝试从背包吃食物
        if (ai.getFoodLevel() < 20) {
            for (int i = 0; i < ai.getInventory().getContainerSize(); i++) {
                ItemStack stack = ai.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    FoodProperties foodProps = stack.getFoodProperties(ai);
                    if (foodProps != null) {
                        ai.getInventory().setItem(i, ItemStack.EMPTY);
                        ai.setFoodLevel(Math.min(20, ai.getFoodLevel() + foodProps.getNutrition()));
                        ai.setSaturation(Math.min(20, ai.getSaturation() + foodProps.getSaturationModifier() * 2 * foodProps.getNutrition()));
                        ai.heal(foodProps.getNutrition() * 0.5F);
                        notifyOwner("吃了食物，恢复了饱食度");
                        finish();
                        return;
                    }
                }
            }
        }

        // 背包没食物，探索
        if (!handleNoTarget(TARGET_SEARCH_TIMEOUT)) {
            notifyOwnerSystem("背包没有食物，无法恢复");
            finish();
        }
    }

    @Override
    public String getName() { return "heal"; }
}

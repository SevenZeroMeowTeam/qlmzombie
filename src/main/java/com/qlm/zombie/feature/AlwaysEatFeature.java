/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Original design inspired by AlwaysEat (https://github.com/MaxNeedsSnacks/AlwaysEat)
 *   Copyright (c) MaxNeedsSnacks. Licensed under MIT.
 *   This is an ORIGINAL Forge 1.20.1 implementation, NO code copied.
 * ----------------------------------------------------------------------------
 * 功能：玩家饱食度满时仍可吃食物（不再被 Minecraft 原版阻止）
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AlwaysEatFeature {

    private AlwaysEatFeature() {}

    /**
     * 原版 MC 中 Player.canEat(false) 会在饱食度满时禁止吃食物（非 instant 食物）。
     * 我们在 RIGHT_CLICK_ITEM 事件中强制让玩家进入 eating 状态（FoodStats 会正常扣加）。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        // 只处理食物
        if (stack.isEmpty() || !stack.isEdible()) return;

        // 如果饱食度已满，原版会取消使用。我们在这里显式启动使用物品，
        // 这样即使 saturation 满也能吃（模拟原版 creative 或 Mod 设置 bypass 逻辑）。
        // 注意：此方法只需要在饱食度满时介入；其余情况原版正常走。
        if (player.getFoodData().getFoodLevel() >= 20) {
            player.startUsingItem(event.getHand());
        }
    }
}

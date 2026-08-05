/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * 纯净水物品：
 *   1. 饮用后恢复口渴值
 *   2. 基于 Thirst-Mod DrinkableItem 模式实现
 */
package com.qlm.zombie.item;

import com.qlm.zombie.feature.ThirstFeature;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 纯净水物品 — 可饮用，恢复口渴值。
 */
public class PurifiedWaterItem extends Item {

    /** 饮用动画长度（tick） */
    private static final int DRINK_DURATION = 32;

    public PurifiedWaterItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) {
            if (!level.isClientSide) {
                ThirstFeature.drinkPurifiedWater((net.minecraft.server.level.ServerPlayer) player);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DRINK_DURATION;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
}

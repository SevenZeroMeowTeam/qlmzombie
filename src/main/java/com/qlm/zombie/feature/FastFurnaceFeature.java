/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Original design inspired by FastFurnace (https://github.com/Shadows-of-Fire/FastFurnace)
 *   Copyright (c) Shadows-of-Fire. Licensed under MIT.
 *   This is an ORIGINAL Forge 1.20.1 implementation, NO code copied.
 * ----------------------------------------------------------------------------
 * 功能：熔炉/烟熏炉/高炉配方查找缓存，减少每次燃烧时的配方表全量扫描
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FastFurnaceFeature {

    private FastFurnaceFeature() {}

    private static final int MAX_CACHE = 4096;

    // 三种熔炉类型各自的缓存：Level -> (InputId, RecipeType) -> Recipe
    private static final IdentityHashMap<Level, HashMap<FurnaceKey, Optional<? extends Recipe<?>>>> FURNACE_CACHES = new IdentityHashMap<>();

    private record FurnaceKey(int itemId, RecipeType<?> type) {}

    /**
     * 在 Furnace 烧炼逻辑中优先调用此方法替换原版 recipe manager 查询。
     * 输入单槽（AbstractFurnaceBlockEntity.inputSlots.get(0)）。
     */
    @SuppressWarnings("unchecked")
    public static <C extends Container, T extends Recipe<C>> Optional<T> findRecipe(
            RecipeType<T> type, C container, Level level, ItemStack input) {

        if (level.isClientSide) return level.getRecipeManager().getRecipeFor(type, container, level);

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(input.getItem());
        FurnaceKey key = new FurnaceKey(itemId != null ? itemId.hashCode() : 0, type);
        HashMap<FurnaceKey, Optional<? extends Recipe<?>>> lvlCache =
                FURNACE_CACHES.computeIfAbsent(level, lv -> new HashMap<>());

        Optional<? extends Recipe<?>> cached = lvlCache.get(key);
        if (cached != null) {
            // 命中缓存：再验证一次真正 matches，确保 NBT/损坏的物品不会产生问题
            if (cached.isPresent() && ((T) cached.get()).matches(container, level)) {
                return (Optional<T>) cached;
            }
            if (cached.isEmpty()) {
                // 缓存了"找不到" → 直接返回空（前提：输入相同 item id，之前找不到就不会突然找到）
                return Optional.empty();
            }
        }

        // 未命中 → 原版查询
        Optional<T> found = level.getRecipeManager().getRecipeFor(type, container, level);
        if (lvlCache.size() > MAX_CACHE) lvlCache.clear();
        lvlCache.put(key, found);
        return found;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 破坏熔炉方块时清理该 Level 缓存，避免旧数据干扰（可选安全机制）
        if (event.getLevel() instanceof Level lv && !lv.isClientSide) {
            String blockName = event.getState().getBlock().getName().getString();
            if (blockName.contains("furnace") || blockName.contains("smoker") || blockName.contains("blast")) {
                FURNACE_CACHES.remove(lv);
            }
        }
    }
}

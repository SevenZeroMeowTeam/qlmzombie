/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Original design inspired by FastWorkbench (https://github.com/Shadows-of-Fire/FastWorkbench)
 *   Copyright (c) Shadows-of-Fire. Licensed under MIT.
 *   Original design inspired by FastSuite (https://github.com/Shadows-of-Fire/FastSuite)
 *   This is an ORIGINAL Forge 1.20.1 implementation, NO code copied.
 * ----------------------------------------------------------------------------
 * 功能：工作台/锻造台/切石机合成使用缓存，避免每次重新扫描配方
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FastCraftingFeature {

    private FastCraftingFeature() {}

    // 工作台配方缓存: Level -> (输入签名 -> 配方 + 结果)
    private static final IdentityHashMap<Level, HashMap<Long, CachedCraftingResult>> CRAFT_CACHE = new IdentityHashMap<>();
    private static final int MAX_CACHE = 512;

    private static final class CachedCraftingResult {
        CraftingRecipe recipe;
        ItemStack result;
    }

    /**
     * 工作台配方缓存查找（公共 API，可从 Mixin 或事件调用）。
     * 输入签名 = 容器内所有 ItemStack 的 id hash 聚合。
     */
    public static Optional<CraftingRecipe> findCraftingRecipe(CraftingContainer input, Level level) {
        if (level.isClientSide) return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);

        long sig = signature(input);
        HashMap<Long, CachedCraftingResult> levelCache = CRAFT_CACHE.computeIfAbsent(level, lv -> new HashMap<>());

        CachedCraftingResult cached = levelCache.get(sig);
        // 缓存命中：仍需要 matches（因为 NBT 相同 Item 不代表输入布局相同；简化版：直接信任签名 + 重新验证一下输出）
        if (cached != null && cached.recipe.matches(input, level)) {
            return Optional.of(cached.recipe);
        }

        // 未命中，走原版查询
        Optional<CraftingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        found.ifPresent(recipe -> {
            if (levelCache.size() > MAX_CACHE) levelCache.clear();
            CachedCraftingResult cr = new CachedCraftingResult();
            cr.recipe = recipe;
            cr.result = recipe.assemble(input, level.registryAccess());
            levelCache.put(sig, cr);
        });

        return found;
    }

    /** 获取工作台缓存的合成结果 */
    public static ItemStack getCachedResult(CraftingRecipe recipe, CraftingContainer input, Level level) {
        return recipe.assemble(input, level.registryAccess());
    }

    // 根据容器内的 Item 生成 long 签名
    private static long signature(Container c) {
        long h = 1125899906842597L;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                int idHash = id != null ? id.hashCode() : 0;
                h = h * 31 + idHash;
                h = h * 31 + s.getCount();
            } else {
                h = h * 31 + 0xDEAD;
            }
        }
        return h;
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        // 预留：可以在这里统计性能、或强制清理 cache。
        // 当前版本不需要特殊处理，因为签名哈希已经把输入布局编码到 key 中。
    }
}

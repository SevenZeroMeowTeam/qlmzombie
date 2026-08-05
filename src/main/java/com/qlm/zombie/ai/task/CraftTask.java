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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;

/** 合成任务 — 参考 Player2NPC CraftInTableTask，寻找工作台并按配方合成 */
public class CraftTask extends Task {

    private final String targetItemId;
    private final int targetCount;
    private int craftedCount = 0;
    private int stallTicks = 0;

    public CraftTask(FakePlayerEntity ai, Player owner, String targetItemId, int targetCount) {
        super(ai, owner);
        this.targetItemId = targetItemId;
        this.targetCount = Math.max(1, targetCount);
    }

    @Override
    public void start() {
        ai.setTarget(null);
    }

    @Override
    public void tick() {
        if (ai.isSitting()) return;
        ai.setTarget(null);

        if (craftedCount >= targetCount) {
            notifyOwner("已完成 " + targetCount + " 个 " + targetItemId + " 的合成");
            finish();
            return;
        }

        if (stallTicks++ > 200) {
            notifyOwnerSystem("无法继续合成 " + targetItemId + "（材料不足或无配方）");
            finish();
            return;
        }

        Level level = ai.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos table = findNearestCraftingTable(level, ai.blockPosition(), 16);
        boolean useTable = table != null;

        if (useTable) {
            double distSq = ai.distanceToSqr(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5);
            if (distSq > 4.0D) {
                ai.getNavigation().moveTo(table.getX() + 0.5, table.getY(), table.getZ() + 0.5, 1.0D);
                return;
            }
            ai.getNavigation().stop();
            ai.getLookControl().setLookAt(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5, 30.0F, 30.0F);
        }

        if (ai.tickCount % 20 != 0) return;

        var rm = serverLevel.getRecipeManager();
        Collection<CraftingRecipe> recipes = rm.getAllRecipesFor(RecipeType.CRAFTING);

        for (CraftingRecipe recipe : recipes) {
            ItemStack result = recipe.getResultItem(serverLevel.registryAccess());
            if (result.isEmpty()) continue;
            String resultId = ForgeRegistries.ITEMS.getKey(result.getItem()).toString();
            if (!targetItemId.equals(resultId) && !targetItemId.equals(resultId.replace("minecraft:", ""))) continue;
            if (!recipe.canCraftInDimensions(2, 2) && !useTable) continue;

            if (tryCraftRecipe(recipe, serverLevel)) {
                craftedCount += result.getCount();
                stallTicks = 0;
                notifyOwner("合成进度: " + craftedCount + "/" + targetCount);
                return;
            }
        }
    }

    @Override
    public String getName() { return "craft"; }

    @SuppressWarnings("deprecation")
    private boolean tryCraftRecipe(CraftingRecipe recipe, ServerLevel serverLevel) {
        SimpleContainer inv = ai.getInventory();
        var craftGrid = createCraftGrid();
        int gridSize = craftGrid.getContainerSize();
        int[] slotMap = new int[gridSize];
        java.util.Arrays.fill(slotMap, -1);
        int[] remaining = new int[inv.getContainerSize()];
        for (int i = 0; i < inv.getContainerSize(); i++) remaining[i] = inv.getItem(i).getCount();

        List<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.getIngredients();
        if (!fillGridPruned(recipe, craftGrid, inv, serverLevel, 0, slotMap, remaining, ingredients)) return false;

        ItemStack result = recipe.assemble(craftGrid, serverLevel.registryAccess());
        if (result.isEmpty()) return false;

        for (int g = 0; g < gridSize; g++) {
            int invSlot = slotMap[g];
            if (invSlot >= 0) inv.getItem(invSlot).shrink(1);
        }
        addItemToInventory(result.copy());
        return true;
    }

    private net.minecraft.world.inventory.TransientCraftingContainer createCraftGrid() {
        var dummyMenu = new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player p) { return true; }
        };
        return new net.minecraft.world.inventory.TransientCraftingContainer(dummyMenu, 3, 3);
    }

    private boolean fillGridPruned(CraftingRecipe recipe, net.minecraft.world.inventory.TransientCraftingContainer grid,
                                   SimpleContainer inv, ServerLevel serverLevel,
                                   int slotIndex, int[] slotMap, int[] remaining,
                                   List<net.minecraft.world.item.crafting.Ingredient> ingredients) {
        if (slotIndex >= grid.getContainerSize()) return recipe.matches(grid, serverLevel);
        var ing = slotIndex < ingredients.size() ? ingredients.get(slotIndex) : null;
        grid.setItem(slotIndex, ItemStack.EMPTY); slotMap[slotIndex] = -1;
        if (fillGridPruned(recipe, grid, inv, serverLevel, slotIndex + 1, slotMap, remaining, ingredients)) return true;
        if (ing != null && !ing.isEmpty()) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (remaining[i] <= 0) continue;
                ItemStack candidate = inv.getItem(i);
                if (candidate.isEmpty() || !ing.test(candidate)) continue;
                grid.setItem(slotIndex, candidate.copy()); slotMap[slotIndex] = i; remaining[i]--;
                if (fillGridPruned(recipe, grid, inv, serverLevel, slotIndex + 1, slotMap, remaining, ingredients)) return true;
                remaining[i]++;
            }
        }
        grid.setItem(slotIndex, ItemStack.EMPTY); slotMap[slotIndex] = -1;
        return false;
    }

    private void addItemToInventory(ItemStack stack) {
        if (stack.isEmpty()) return;
        SimpleContainer inv = ai.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int canAdd = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(canAdd); stack.shrink(canAdd);
                if (stack.isEmpty()) return;
            }
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) { inv.setItem(i, stack.copy()); return; }
        }
    }

    private BlockPos findNearestCraftingTable(Level level, BlockPos center, int range) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 1; r <= range; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (level.getBlockState(pos).getBlock() instanceof CraftingTableBlock) {
                            double dist = center.distSqr(pos);
                            if (dist < bestDist) { bestDist = dist; best = pos; }
                        }
                    }
        return best;
    }
}

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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 建造任务 — 参考 Player2NPC BuildStructureTask，构建5x5小屋蓝图 */
public class BuildTask extends Task {

    private final List<BlockPos> positions = new ArrayList<>();
    private final List<Block> blueprint = new ArrayList<>();
    private int currentIndex = 0;
    private int placeCooldown = 0;
    private boolean initialized = false;

    public BuildTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
    }

    @Override
    public void start() {
        ai.setTarget(null);
        // 选择建造基点：玩家前方 3-5 格
        if (owner != null) {
            Vec3 look = owner.getLookAngle();
            BlockPos basePos = owner.blockPosition().offset(
                    (int) Math.round(look.x * 4), 0, (int) Math.round(look.z * 4));
            while (basePos.getY() > ai.level().getMinBuildHeight() && !ai.level().getBlockState(basePos.below()).isSolidRender(ai.level(), basePos.below())) {
                basePos = basePos.below();
            }
            while (basePos.getY() < ai.level().getMaxBuildHeight() - 5 && ai.level().getBlockState(basePos).isSolidRender(ai.level(), basePos)) {
                basePos = basePos.above();
            }
            buildHouseBlueprint(basePos, ai.level());
            notifyOwner("在 (" + basePos.getX() + "," + basePos.getY() + "," + basePos.getZ() + ") 附近搭建小屋");
        }
    }

    @Override
    public void tick() {
        if (ai.isSitting()) return;
        ai.setTarget(null);

        if (!initialized) {
            initialized = true;
        }

        if (currentIndex >= positions.size()) {
            notifyOwner("小屋建造完成！");
            finish();
            return;
        }

        BlockPos targetPos = positions.get(currentIndex);
        double distSq = ai.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq > 16.0D) {
            ai.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0.8D);
            return;
        }

        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        Block targetBlock = blueprint.get(currentIndex);
        ItemStack materialStack = findBuildingMaterial(targetBlock);

        if (materialStack == null && targetBlock != null) {
            currentIndex++;
            placeCooldown = 5;
            return;
        }

        if (materialStack != null) {
            materialStack.shrink(1);
        }

        if (targetBlock != null) {
            var state = targetBlock.defaultBlockState();
            var existing = ai.level().getBlockState(targetPos);
            if (existing.isAir() || existing.canBeReplaced()) {
                ai.level().setBlock(targetPos, state, 3);
                ai.level().playSound(null, targetPos, state.getSoundType().getPlaceSound(),
                        ai.getSoundSource(), 0.8F, 1.0F);
            }
        }

        currentIndex++;
        placeCooldown = 4;
    }

    @Override
    public String getName() { return "build"; }

    private void buildHouseBlueprint(BlockPos base, Level level) {
        int size = 5;
        int height = 4;
        Block wallBlock = Blocks.OAK_PLANKS;
        Block roofBlock = Blocks.OAK_PLANKS;
        Block pillarBlock = Blocks.OAK_LOG;

        // 地基
        for (int x = 0; x < size; x++)
            for (int z = 0; z < size; z++) {
                positions.add(base.offset(x, 0, z));
                blueprint.add(wallBlock);
            }
        // 墙壁
        for (int y = 1; y < height; y++)
            for (int x = 0; x < size; x++)
                for (int z = 0; z < size; z++) {
                    boolean edge = (x == 0 || x == size - 1 || z == 0 || z == size - 1);
                    if (!edge) continue;
                    if (z == size - 1 && x == size / 2 && y == 1) {
                        positions.add(base.offset(x, y, z)); blueprint.add(null); continue;
                    }
                    if (z == 0 && x == size / 2 && y == 2) {
                        positions.add(base.offset(x, y, z)); blueprint.add(null); continue;
                    }
                    positions.add(base.offset(x, y, z));
                    boolean corner = (x == 0 || x == size - 1) && (z == 0 || z == size - 1);
                    blueprint.add(corner ? pillarBlock : wallBlock);
                }
        // 屋顶
        for (int x = 0; x < size; x++)
            for (int z = 0; z < size; z++) {
                positions.add(base.offset(x, height, z));
                blueprint.add(roofBlock);
            }
    }

    @SuppressWarnings("deprecation")
    private ItemStack findBuildingMaterial(Block targetBlock) {
        if (targetBlock == null) return null;
        var inv = ai.getInventory();
        var targetItem = targetBlock.asItem();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem) return stack;
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return stack;
        }
        return null;
    }
}

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/** 收集任务 — 搜索掉落物，找不到则回退砍树/挖矿 */
public class GatherTask extends Task {

    private final String itemName;

    public GatherTask(FakePlayerEntity ai, Player owner, String itemName) {
        super(ai, owner);
        this.itemName = itemName;
    }

    @Override
    public void start() {
        ai.setTarget(null);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick() {
        if (ai.isSitting()) return;
        ai.setTarget(null);

        BlockPos aiPos = ai.blockPosition();
        String resolvedId = AIItemRegistry.findItemId(itemName);

        // 第一步：搜索掉落物
        for (Entity e : ai.level().getEntitiesOfClass(Entity.class,
                new AABB(aiPos.getX() - 16, aiPos.getY() - 8, aiPos.getZ() - 16,
                        aiPos.getX() + 16, aiPos.getY() + 8, aiPos.getZ() + 16))) {
            if (e instanceof ItemEntity itemEntity) {
                String itemId = ForgeRegistries.ITEMS.getKey(itemEntity.getItem().getItem()).toString();
                boolean match = (resolvedId != null && resolvedId.equals(itemId)) ||
                                (itemName != null && AIItemRegistry.matches(itemName, itemId));
                if (match) {
                    resetNoTargetCounter();
                    double distSq = ai.distanceToSqr(itemEntity);
                    if (distSq > 2.5D) {
                        ai.getNavigation().moveTo(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), 1.0D);
                    } else {
                        ai.pickUpItem(itemEntity);
                    }
                    return;
                }
            }
        }

        // 第二步：回退到源方块（砍树/挖矿）
        if (tryGatherFromSourceBlock(itemName, resolvedId)) {
            return;
        }

        // 第三步：探索
        if (!handleNoTarget(TARGET_SEARCH_TIMEOUT)) {
            finish();
        }
    }

    private boolean tryGatherFromSourceBlock(String itemName, String resolvedId) {
        if (itemName == null) return false;

        // 木头类 → 砍树
        if (itemName.contains("木") || itemName.contains("wood") || itemName.contains("log")
                || (resolvedId != null && resolvedId.contains("_log"))) {
            BlockPos aiPos = ai.blockPosition();
            BlockPos bestLog = null;
            double bestDist = Double.MAX_VALUE;
            for (int y = -2; y <= 6; y++) {
                for (int x = -16; x <= 16; x++) {
                    for (int z = -16; z <= 16; z++) {
                        BlockPos pos = aiPos.offset(x, y, z);
                        BlockState state = ai.level().getBlockState(pos);
                        if (isLogBlock(state)) {
                            double dist = ai.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                            if (dist < bestDist) { bestDist = dist; bestLog = pos; }
                        }
                    }
                }
            }
            if (bestLog != null) {
                resetNoTargetCounter();
                if (bestDist > 4.0D) {
                    ai.getNavigation().moveTo(bestLog.getX() + 0.5, bestLog.getY(), bestLog.getZ() + 0.5, 0.8D);
                } else {
                    ai.getLookControl().setLookAt(bestLog.getX() + 0.5, bestLog.getY() + 0.5, bestLog.getZ() + 0.5, 30.0F, 30.0F);
                    if (ai.tickCount % 25 == 0) ai.level().destroyBlock(bestLog, true, ai);
                }
                return true;
            }
            return false;
        }

        // 矿物类 → 挖矿
        if (itemName.contains("矿") || itemName.contains("锭") || itemName.contains("ore")
                || (resolvedId != null && resolvedId.contains("_ore"))) {
            BlockPos aiPos = ai.blockPosition();
            BlockPos bestOre = null;
            double bestDist = Double.MAX_VALUE;
            for (int y = -4; y <= 4; y++) {
                for (int x = -16; x <= 16; x++) {
                    for (int z = -16; z <= 16; z++) {
                        BlockPos pos = aiPos.offset(x, y, z);
                        BlockState state = ai.level().getBlockState(pos);
                        if (isOreBlock(state)) {
                            double dist = ai.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                            if (dist < bestDist) { bestDist = dist; bestOre = pos; }
                        }
                    }
                }
            }
            if (bestOre != null) {
                resetNoTargetCounter();
                if (bestDist > 4.0D) {
                    ai.getNavigation().moveTo(bestOre.getX() + 0.5, bestOre.getY(), bestOre.getZ() + 0.5, 0.8D);
                } else {
                    ai.getLookControl().setLookAt(bestOre.getX() + 0.5, bestOre.getY() + 0.5, bestOre.getZ() + 0.5, 30.0F, 30.0F);
                    if (ai.tickCount % 30 == 0) ai.level().destroyBlock(bestOre, true, ai);
                }
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public String getName() { return "gather"; }
}

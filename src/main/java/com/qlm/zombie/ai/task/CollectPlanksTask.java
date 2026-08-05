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
 *   - AltoClef Task subclass pattern (start/tick/stop lifecycle)
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements.
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.task;

import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** 收集木板任务 — 搜索并拾取附近所有木板掉落物，支持其他模组（通过 planks 标签） */
public class CollectPlanksTask extends Task {

    private static final double SEARCH_RANGE = 16.0D;
    private int collectedCount = 0;

    public CollectPlanksTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
    }

    @Override
    public void start() {
        ai.setTarget(null);
        notifyOwner("开始收集附近的木板掉落物...");
    }

    @Override
    public void tick() {
        if (ai.isSitting()) return;
        ai.setTarget(null);

        BlockPos aiPos = ai.blockPosition();

        // 搜索附近所有掉落物
        AABB searchArea = new AABB(
                aiPos.getX() - SEARCH_RANGE, aiPos.getY() - 8, aiPos.getZ() - SEARCH_RANGE,
                aiPos.getX() + SEARCH_RANGE, aiPos.getY() + 8, aiPos.getZ() + SEARCH_RANGE);

        ItemEntity nearestPlank = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity e : ai.level().getEntitiesOfClass(Entity.class, searchArea)) {
            if (e instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) continue;

                // 通过 minecraft:planks 标签识别木板（支持其他模组）
                if (stack.is(net.minecraft.tags.ItemTags.PLANKS)) {
                    double dist = ai.distanceToSqr(itemEntity);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestPlank = itemEntity;
                    }
                }
            }
        }

        if (nearestPlank != null) {
            resetNoTargetCounter();
            if (nearestDist > 2.5D) {
                // 走向木板掉落物
                ai.getNavigation().moveTo(
                        nearestPlank.getX(), nearestPlank.getY(), nearestPlank.getZ(), 1.0D);
            } else {
                // 拾取
                int count = nearestPlank.getItem().getCount();
                ai.pickUpItem(nearestPlank);
                collectedCount += count;
            }
        } else {
            // 没有更多木板掉落物
            if (collectedCount > 0) {
                notifyOwner("收集完成！共收集了 " + collectedCount + " 个木板");
            } else {
                notifyOwner("附近没有找到木板掉落物");
            }
            finish();
        }

        // 超时检查
        if (isTimedOut()) {
            notifyOwner("收集任务超时，共收集了 " + collectedCount + " 个木板");
            finish();
        }
    }

    @Override
    public String getName() { return "collect_planks"; }
}

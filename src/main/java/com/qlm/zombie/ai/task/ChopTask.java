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

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.ai.EquipmentHelper;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** 砍树任务 — 搜索原木并砍伐 */
public class ChopTask extends Task {

    protected final FakePlayerEntity ai;
    protected final Player owner;

    public ChopTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
        this.ai = ai;
        this.owner = owner;
    }

    @Override
    protected void onStart() {
        ai.setTarget(null);
        QLMZombieMod.LOGGER.info("[AI任务] ChopTask 启动 - AI: {}, 位置:({}, {}, {})",
                ai.getCustomNameStr(), (int)ai.getX(), (int)ai.getY(), (int)ai.getZ());

        // 参考 TLM: 任务开始时切换到斧子
        boolean hasAxe = EquipmentHelper.ensureToolEquipped(ai, "axe");
        if (hasAxe) {
            notifyOwnerSystem("开始砍树任务，已装备斧子，搜索附近原木...");
        } else {
            notifyOwnerSystem("开始砍树任务（无斧子，效率较低），搜索附近原木...");
        }
    }

    @Override
    protected void doTick() {
        if (ai.isSitting()) return;
        ai.setTarget(null);

        BlockPos aiPos = ai.blockPosition();
        BlockPos bestLog = null;
        double bestDist = Double.MAX_VALUE;

        // 扩大搜索范围：y=-2~8，半径24
        for (int y = -2; y <= 8; y++) {
            for (int x = -24; x <= 24; x++) {
                for (int z = -24; z <= 24; z++) {
                    BlockPos pos = aiPos.offset(x, y, z);
                    BlockState state = ai.level().getBlockState(pos);
                    if (isLogBlock(state)) {
                        double dist = ai.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestLog = pos;
                        }
                    }
                }
            }
        }

        if (bestLog != null) {
            resetNoTargetCounter();
            if (bestDist > 4.0D) {
                QLMZombieMod.LOGGER.info("[AI任务] ChopTask 导航到原木: ({},{},{}) 距离: {}",
                        bestLog.getX(), bestLog.getY(), bestLog.getZ(), (int)Math.sqrt(bestDist));
                ai.getNavigation().moveTo(bestLog.getX() + 0.5, bestLog.getY(), bestLog.getZ() + 0.5, 0.8D);
            } else {
                QLMZombieMod.LOGGER.info("[AI任务] ChopTask 砍伐原木: ({},{},{})", bestLog.getX(), bestLog.getY(), bestLog.getZ());
                ai.getLookControl().setLookAt(bestLog.getX() + 0.5, bestLog.getY() + 0.5, bestLog.getZ() + 0.5, 30.0F, 30.0F);
                if (ai.tickCount % 25 == 0) {
                    ai.level().destroyBlock(bestLog, true, ai);
                }
            }
        } else {
            // 找不到原木：通知主人并跟随探索
            noTargetTicks++;
            if (noTargetTicks == 1) {
                QLMZombieMod.LOGGER.warn("[AI任务] ChopTask 附近48格内未找到原木 - AI: {}", ai.getCustomNameStr());
                notifyOwner("附近没有找到树木，跟随你探索新区域");
            }
            if (owner != null && noTargetTicks % 20 == 0) {
                double distToOwner = ai.distanceToSqr(owner);
                if (distToOwner > 16.0D) {
                    ai.getNavigation().moveTo(owner, 1.0D);
                }
            }
            if (noTargetTicks > TARGET_SEARCH_TIMEOUT) {
                notifyOwnerSystem("长时间未找到树木，任务结束");
                finish();
            }
        }
    }

    @Override
    public String getName() { return "chop"; }
}

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

/** 挖矿任务 — 搜索矿石并挖掘 */
public class MineTask extends Task {

    public MineTask(FakePlayerEntity ai, Player owner) {
        super(ai, owner);
    }

    @Override
    public void start() {
        ai.setTarget(null);
        QLMZombieMod.LOGGER.info("[AI任务] MineTask 启动 - AI: {}, 位置:({}, {}, {})",
                ai.getCustomNameStr(), (int)ai.getX(), (int)ai.getY(), (int)ai.getZ());

        // 参考 TLM: 任务开始时切换到镐子
        boolean hasPickaxe = EquipmentHelper.ensureToolEquipped(ai, "pickaxe");
        if (hasPickaxe) {
            notifyOwnerSystem("开始挖矿任务，已装备镐子，搜索附近矿石...");
        } else {
            notifyOwnerSystem("开始挖矿任务（无镐子，效率较低），搜索附近矿石...");
        }
    }

    @Override
    public void tick() {
        if (ai.isSitting()) return;
        ai.setTarget(null);

        BlockPos aiPos = ai.blockPosition();
        BlockPos bestOre = null;
        double bestDist = Double.MAX_VALUE;

        // 扩大搜索范围：y=-8~8（地下8格到地上8格），半径24
        for (int y = -8; y <= 8; y++) {
            for (int x = -24; x <= 24; x++) {
                for (int z = -24; z <= 24; z++) {
                    BlockPos pos = aiPos.offset(x, y, z);
                    BlockState state = ai.level().getBlockState(pos);
                    if (isOreBlock(state)) {
                        double dist = ai.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestOre = pos;
                        }
                    }
                }
            }
        }

        if (bestOre != null) {
            resetNoTargetCounter();
            if (bestDist > 4.0D) {
                QLMZombieMod.LOGGER.info("[AI任务] MineTask 导航到矿石: ({},{},{}) 距离: {}",
                        bestOre.getX(), bestOre.getY(), bestOre.getZ(), (int)Math.sqrt(bestDist));
                ai.getNavigation().moveTo(bestOre.getX() + 0.5, bestOre.getY(), bestOre.getZ() + 0.5, 0.8D);
            } else {
                QLMZombieMod.LOGGER.info("[AI任务] MineTask 破坏矿石: ({},{},{})", bestOre.getX(), bestOre.getY(), bestOre.getZ());
                ai.getLookControl().setLookAt(bestOre.getX() + 0.5, bestOre.getY() + 0.5, bestOre.getZ() + 0.5, 30.0F, 30.0F);
                if (ai.tickCount % 30 == 0) {
                    ai.level().destroyBlock(bestOre, true, ai);
                }
            }
        } else {
            // 找不到矿石：每5秒通知一次主人，并跟随主人探索新区域
            noTargetTicks++;
            if (noTargetTicks == 1) {
                QLMZombieMod.LOGGER.warn("[AI任务] MineTask 附近48格内未找到矿石 - AI: {}", ai.getCustomNameStr());
                notifyOwner("附近没有找到矿石，跟随你探索新区域");
            }
            // 跟随主人而不是随机移动
            if (owner != null && noTargetTicks % 20 == 0) {
                double distToOwner = ai.distanceToSqr(owner);
                if (distToOwner > 16.0D) {
                    ai.getNavigation().moveTo(owner, 1.0D);
                }
            }
            if (noTargetTicks > TARGET_SEARCH_TIMEOUT) {
                notifyOwnerSystem("长时间未找到矿石，任务结束");
                finish();
            }
        }
    }

    @Override
    public String getName() { return "mine"; }
}

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
 * This interface is an ORIGINAL implementation inspired by:
 *   - PlayerEngine IInteractionManagerProvider pattern
 *     (https://github.com/Goodbird-git/PlayerEngine)
 *     Copyright (c) Goodbird-git, Licensed under MIT License
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** 交互能力接口 — 参考 PlayerEngine IInteractionManagerProvider */
public interface IInteractionProvider {
    boolean breakBlock(BlockPos pos);
    boolean placeBlock(BlockPos pos, net.minecraft.world.item.ItemStack stack, Direction facing);
    void useItem(net.minecraft.world.item.ItemStack stack);
}

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
 *   - PlayerEngine IHungerManagerProvider pattern
 *     (https://github.com/Goodbird-git/PlayerEngine)
 *     Copyright (c) Goodbird-git, Licensed under MIT License
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.capability;

/** 饥饿能力接口 — 参考 PlayerEngine IHungerManagerProvider */
public interface IHungerProvider {
    int getFoodLevel();
    void setFoodLevel(int level);
    float getSaturation();
    void setSaturation(float level);
    void consumeFoodLevel();
}

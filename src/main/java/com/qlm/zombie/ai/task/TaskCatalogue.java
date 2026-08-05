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
 *   - AltoClef TaskCatalogue command-to-task mapping pattern
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements.
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.task;

import com.qlm.zombie.ai.Player2APIService.AIResponse;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 任务目录 — 参考 PlayerEngine TaskCatalogue
 * 将 AIResponse (解析后的指令) 映射到具体的 Task 实例
 */
public class TaskCatalogue {

    /**
     * 根据解析后的指令创建任务
     */
    public static Task createTask(FakePlayerEntity ai, Player owner, AIResponse response) {
        switch (response.action()) {
            case "follow" -> { return new FollowTask(ai, owner); }
            case "wait" -> { return new WaitTask(ai, owner); }
            case "come" -> { return new ComeTask(ai, owner); }
            case "stop" -> { return new StopTask(ai, owner); }
            case "mine" -> { return new MineTask(ai, owner); }
            case "chop" -> { return new ChopTask(ai, owner); }
            case "gather" -> { return new GatherTask(ai, owner, response.targetItem()); }
            case "collect_planks" -> { return new CollectPlanksTask(ai, owner); }
            case "attack" -> { return new AttackTask(ai, owner); }
            case "guard" -> { return new GuardTask(ai, owner); }
            case "build", "house" -> { return new BuildTask(ai, owner); }
            case "craft" -> { return new CraftTask(ai, owner, response.targetItem(), response.targetCount()); }
            case "give" -> { return new GiveTask(ai, owner, response.targetItem(), response.targetCount()); }
            case "drop" -> { return new DropTask(ai, owner, response.targetItem(), response.targetCount()); }
            case "explore" -> { return new ExploreTask(ai, owner); }
            case "heal" -> { return new HealTask(ai, owner); }
            case "farm" -> { return new FarmTask(ai, owner); }
            default -> { return null; }
        }
    }
}

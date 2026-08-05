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
 *   - Player2NPC (https://github.com/Goodbird-git/Player2NPC)
 *     Copyright (c) Goodbird-git
 *     Licensed under MIT License
 *   - CompanionManager companion tracking pattern
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements.
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.companion;

import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同伴管理器 — 参考 Player2NPC CompanionManager
 * 每个玩家追踪其拥有的 AI 同伴（name → UUID）
 * 管理召唤/解散/查询生命周期
 */
public class CompanionManager {

    private static final Map<UUID, Map<String, UUID>> PLAYER_COMPANIONS = new ConcurrentHashMap<>();

    /** 注册同伴到玩家名下 */
    public static void registerCompanion(UUID playerUuid, String companionName, UUID companionUuid) {
        PLAYER_COMPANIONS.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(companionName, companionUuid);
    }

    /** 注销同伴 */
    public static void unregisterCompanion(UUID playerUuid, String companionName) {
        Map<String, UUID> companions = PLAYER_COMPANIONS.get(playerUuid);
        if (companions != null) {
            companions.remove(companionName);
        }
    }

    /** 注销所有同伴 */
    public static void unregisterAll(UUID playerUuid) {
        PLAYER_COMPANIONS.remove(playerUuid);
    }

    /** 获取玩家所有活跃同伴 */
    public static List<FakePlayerEntity> getActiveCompanions(UUID playerUuid, MinecraftServer server) {
        List<FakePlayerEntity> companions = new ArrayList<>();
        Map<String, UUID> map = PLAYER_COMPANIONS.get(playerUuid);
        if (map == null || server == null) return companions;

        for (UUID uuid : map.values()) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof FakePlayerEntity fakePlayer && fakePlayer.isAlive()) {
                    companions.add(fakePlayer);
                    break;
                }
            }
        }
        return companions;
    }

    /** 获取玩家附近15格内的同伴（G键切换用） */
    public static List<FakePlayerEntity> getNearbyCompanions(ServerPlayer player) {
        List<FakePlayerEntity> all = getActiveCompanions(player.getUUID(), player.getServer());
        List<FakePlayerEntity> nearby = new ArrayList<>();
        for (FakePlayerEntity ai : all) {
            if (ai.distanceToSqr(player) < 225.0D) { // 15 blocks
                nearby.add(ai);
            }
        }
        nearby.sort(Comparator.comparingDouble(ai -> ai.distanceToSqr(player)));
        return nearby;
    }

    /** 按名字查找同伴 */
    public static FakePlayerEntity findCompanionByName(UUID playerUuid, String name, MinecraftServer server) {
        Map<String, UUID> map = PLAYER_COMPANIONS.get(playerUuid);
        if (map == null || server == null) return null;
        UUID uuid = map.get(name);
        if (uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof FakePlayerEntity fakePlayer && fakePlayer.isAlive()) {
                return fakePlayer;
            }
        }
        return null;
    }

    /** 获取玩家拥有的同伴数量 */
    public static int getCompanionCount(UUID playerUuid) {
        Map<String, UUID> map = PLAYER_COMPANIONS.get(playerUuid);
        return map != null ? map.size() : 0;
    }

    /** 获取同伴名列表 */
    public static Set<String> getCompanionNames(UUID playerUuid) {
        Map<String, UUID> map = PLAYER_COMPANIONS.get(playerUuid);
        return map != null ? new TreeSet<>(map.keySet()) : Collections.emptySet();
    }
}

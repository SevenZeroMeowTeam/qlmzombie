/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Original design inspired by Clumps (https://github.com/jaredlll08/Clumps)
 *   Copyright (c) Jaredlll08. Licensed under MIT.
 *   This is an ORIGINAL Forge 1.20.1 implementation, NO code copied.
 * ----------------------------------------------------------------------------
 * 功能：经验球自动合并，将邻近（8 格内）的多个 ExperienceOrb 合并为 1 个大 orb
 *      减少实体数量 / 降低 tick 开销，同时不损失总经验值
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClumpsFeature {

    private ClumpsFeature() {}

    /** 搜索半径（方块距离）：在 N x N x N 邻域内合并 orb */
    private static final double MERGE_RADIUS = 8.0;
    /** 单个 orb 合并上限（防止 value 过大溢出 / XP 奖励异常） */
    private static final int MAX_VALUE_PER_ORB = Short.MAX_VALUE / 4;

    /** ExperienceOrb.value 字段（SRG: f_20785_） */
    private static final Field XP_VALUE_FIELD;

    static {
        // Forge 1.20.1 生产环境使用 Mojang 官方映射，字段名 = "value"
        Field f = null;
        try {
            f = ExperienceOrb.class.getDeclaredField("value");
            f.setAccessible(true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[Clumps] 未找到 ExperienceOrb.value 字段：{}", e.getMessage());
        }
        XP_VALUE_FIELD = f;
    }

    // ------------------------------------------------------------------
    // 每次经验球加入世界时，搜索周围已有的 ExperienceOrb，吸收合并
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onOrbJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ExperienceOrb incoming)) return;
        Level level = event.getLevel();
        if (level.isClientSide || level == null) return;
        if (XP_VALUE_FIELD == null) return;

        try {
            int incomingVal = XP_VALUE_FIELD.getInt(incoming);
            if (incomingVal <= 0) return;

            AABB searchBox = new AABB(
                incoming.getX() - MERGE_RADIUS, incoming.getY() - MERGE_RADIUS, incoming.getZ() - MERGE_RADIUS,
                incoming.getX() + MERGE_RADIUS, incoming.getY() + MERGE_RADIUS, incoming.getZ() + MERGE_RADIUS
            );
            List<ExperienceOrb> nearby = level.getEntitiesOfClass(ExperienceOrb.class, searchBox,
                orb -> orb != null && orb.isAlive() && orb != incoming);

            if (nearby.isEmpty()) return;

            // 找到邻域中最大 value 的 orb（作为"主 orb"吸收所有其它，包括 incoming）
            ExperienceOrb biggest = incoming;
            int biggestVal = incomingVal;
            for (ExperienceOrb orb : nearby) {
                int v;
                try {
                    v = XP_VALUE_FIELD.getInt(orb);
                } catch (Exception ignored) {
                    v = 0;
                }
                if (v > biggestVal) {
                    biggest = orb;
                    biggestVal = v;
                }
            }

            // 汇总 total
            int total = biggestVal;
            for (ExperienceOrb orb : nearby) {
                if (orb == biggest) continue;
                try {
                    int v = XP_VALUE_FIELD.getInt(orb);
                    total = addCapped(total, v);
                    orb.discard();
                } catch (Exception ignored) {}
            }
            if (biggest != incoming) {
                total = addCapped(total, incomingVal);
                incoming.discard();
            }
            // 写入主 orb
            XP_VALUE_FIELD.setInt(biggest, total);

        } catch (Exception ex) {
            // 合并失败静默跳过（极端反射错误不影响游戏主流程）
        }
    }

    private static int addCapped(int a, int b) {
        long r = (long) a + b;
        if (r > MAX_VALUE_PER_ORB) return MAX_VALUE_PER_ORB;
        if (r < 0) return MAX_VALUE_PER_ORB;
        return (int) r;
    }
}

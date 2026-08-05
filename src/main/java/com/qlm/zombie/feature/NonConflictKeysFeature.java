/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Original design inspired by NonConflictKeys (https://github.com/Corgi-Taco/NonConflictKeys)
 *   Copyright (c) Corgi Taco. Licensed under MIT.
 *   This is an ORIGINAL Forge 1.20.1 implementation, NO code copied.
 * ----------------------------------------------------------------------------
 * 功能：彻底移除所有 KeyMapping 之间的按键冲突。
 *      原版 + Forge 默认：
 *        ① 相同 key + 相同 IKeyConflictContext → 互斥触发
 *        ② 相同 key + 相同 KeyModifier (SHIFT/CTRL/ALT) → 互斥
 *      本 Feature：在 Client 首帧 tick：
 *        ① 反射替换所有 KeyMapping 的 keyConflictContext 为自定义 NO_CONFLICT
 *           (IKeyConflictContext.conflicts() 永远返回 false)；
 *        ② 把 KeyMapping.keyModifier 全部重置为 KeyModifier.NONE（避免 modifier 维度互斥）
 *           → 最终效果：同按键绑定到 N 个功能时 N 个同时触发（"全键无冲"）。
 *
 * 注意：CLIENT-ONLY。服务端完全不加载。
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class NonConflictKeysFeature {

    private NonConflictKeysFeature() {}

    // ==================== 自定义"永远不冲突" IKeyConflictContext ====================
    public static final IKeyConflictContext NO_CONFLICT = new IKeyConflictContext() {
        @Override public boolean isActive() {
            return true; // 等效于 UNIVERSAL：任何场景都允许触发
        }
        @Override public boolean conflicts(IKeyConflictContext other) {
            return false; // 核心：永远不冲突 → 同 key 多 mapping 全部触发
        }
    };

    // ==================== 反射字段 ====================
    /** KeyMapping.keyConflictContext (SRG: f_90897_) */
    private static final Field F_KEY_CONFLICT;
    /** KeyMapping.keyModifier (SRG: f_90901_) */
    private static final Field F_KEY_MODIFIER;
    /** KeyMapping.ALL (SRG: f_90899_) — Map<String, KeyMapping> */
    private static final Field F_ALL;

    static {
        // Forge 1.20.1 生产环境使用 Mojang 官方映射（MojMaps），字段名 = Mojang 名
        Field kc = null, km = null, all = null;
        try {
            kc = KeyMapping.class.getDeclaredField("keyConflictContext");
            kc.setAccessible(true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[NonConflictKeys] KeyMapping.keyConflictContext 反射失败：{}", e.getMessage());
        }
        try {
            km = KeyMapping.class.getDeclaredField("keyModifier");
            km.setAccessible(true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[NonConflictKeys] KeyMapping.keyModifier 反射失败：{}", e.getMessage());
        }
        try {
            all = KeyMapping.class.getDeclaredField("ALL");
            all.setAccessible(true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[NonConflictKeys] KeyMapping.ALL 反射失败：{}", e.getMessage());
        }
        F_KEY_CONFLICT = kc;
        F_KEY_MODIFIER = km;
        F_ALL = all;
    }

    // 已处理的 KeyMapping 去重
    private static final IdentityHashMap<KeyMapping, Boolean> APPLIED = new IdentityHashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (F_KEY_CONFLICT == null && F_KEY_MODIFIER == null) return;
        if (event.phase != TickEvent.Phase.END) return;
        apply();
    }

    public static synchronized void apply() {
        // 拿到 KeyMapping.ALL
        Map<String, KeyMapping> allMap = null;
        if (F_ALL != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, KeyMapping> m = (Map<String, KeyMapping>) F_ALL.get(null);
                allMap = m;
            } catch (Exception ignored) {}
        }
        if (allMap == null || allMap.isEmpty()) return;

        Collection<KeyMapping> values = allMap.values();
        int changed = 0;
        for (KeyMapping km : values) {
            if (km == null) continue;
            if (APPLIED.containsKey(km)) continue;

            boolean c1 = false, c2 = false;
            if (F_KEY_CONFLICT != null) {
                try {
                    Object current = F_KEY_CONFLICT.get(km);
                    if (current != NO_CONFLICT) {
                        F_KEY_CONFLICT.set(km, NO_CONFLICT);
                        c1 = true;
                    }
                } catch (Exception ignored) {}
            }
            // Modifier 维度解除冲突：统一设置为 NONE，这样即使 SHIFT+B / SHIFT+B 的两个 mapping 也不会互斥。
            if (F_KEY_MODIFIER != null) {
                try {
                    Object current = F_KEY_MODIFIER.get(km);
                    if (current != KeyModifier.NONE) {
                        F_KEY_MODIFIER.set(km, KeyModifier.NONE);
                        c2 = true;
                    }
                } catch (Exception ignored) {}
            }
            APPLIED.put(km, Boolean.TRUE);
            if (c1 || c2) changed++;
        }
        if (changed > 0) {
            QLMZombieMod.LOGGER.debug("[NonConflictKeys] 已应用到 {} 个 KeyMapping（conflictCtx=NO_CONFLICT, modifier=NONE）", changed);
        }
    }
}

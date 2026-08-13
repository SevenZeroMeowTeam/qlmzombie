/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * 功能：Forge 事件 → Python 回调桥接
 * 监听方块破坏、实体死亡等 Forge 事件，分发到 Python 脚本注册的回调
 */
package com.qlm.zombie.script;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.QLMZombieMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Forge 事件桥接器：将游戏事件分发到 Python 脚本回调。
 * <p>
 * 自动监听以下 Forge 事件：
 * - BlockEvent.BreakEvent → PythonAPI.onBlockBreak()
 * - LivingDeathEvent      → PythonAPI.onEntityDeath()
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PythonEventBridge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static PythonAPI api;

    /**
     * 绑定 PythonAPI 实例（在 PythonScriptEngine 初始化后调用）。
     */
    public static void bind(PythonAPI pythonAPI) {
        api = pythonAPI;
        LOGGER.info("[QLM Zombie] Python 事件桥接器已绑定");
    }

    // ── 方块破坏事件 ──

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (api == null || !api.hasBlockBreakCallback()) return;

        Player player = event.getPlayer();
        String blockId = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock()).toString();
        String playerUuid = player != null ? player.getUUID().toString() : "";

        api.fireBlockBreak(
                event.getPos().getX(),
                event.getPos().getY(),
                event.getPos().getZ(),
                blockId,
                playerUuid
        );
    }

    // ── 实体死亡事件 ──

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (api == null || !api.hasEntityDeathCallback()) return;

        LivingEntity entity = event.getEntity();
        String entityType = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        String entityUuid = entity.getUUID().toString();
        String sourceName = event.getSource().getEntity() != null
                ? event.getSource().getEntity().getName().getString()
                : event.getSource().getMsgId();

        api.fireEntityDeath(
                entityType,
                entityUuid,
                sourceName,
                entity.getX(),
                entity.getY(),
                entity.getZ()
        );
    }
}

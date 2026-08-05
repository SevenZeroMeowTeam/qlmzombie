/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ----------------------------------------------------------------------------
 * QLM ModSDK — Forge 原生事件 → SDK 事件桥接
 * 用 @SubscribeEvent 监听 Forge 事件，转换为 SDKEvent 后 post 到 SDKEventBus。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk;

import com.qlm.zombie.sdk.event.SDKEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 原生事件桥接器。通过 {@code @SubscribeEvent} 监听 Forge 事件，
 * 转换为 {@link SDKEvent} 后通过 {@link QLMModSDK#getEventBus()} 发布。
 *
 * <p>注册方式：在 mod 构造时调用
 * {@code MinecraftForge.EVENT_BUS.register(ForgeEventBridge.class);}，
 * 或通过 {@link Mod.EventBusSubscriber} 自动注册（宿主 mod id 为 qlmzombie）。</p>
 */
@Mod.EventBusSubscriber(modid = "qlmzombie", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEventBridge {

    private ForgeEventBridge() {}

    // ====================================================================
    // Block
    // ====================================================================

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Level level = asLevel(event.getLevel());
        if (level == null) return;
        SDKEvent.BlockBreakEvent sdk = new SDKEvent.BlockBreakEvent(
                level, event.getPos(), event.getPlayer(), event.getState());
        QLMModSDK.getEventBus().post(sdk);
        if (sdk.isCanceled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Level level = asLevel(event.getLevel());
        if (level == null) return;
        Entity entity = event.getEntity();
        Player player = entity instanceof Player ? (Player) entity : null;
        SDKEvent.BlockPlaceEvent sdk = new SDKEvent.BlockPlaceEvent(
                level, event.getPos(), player, event.getPlacedBlock());
        QLMModSDK.getEventBus().post(sdk);
        if (sdk.isCanceled()) {
            event.setCanceled(true);
        }
    }

    // ====================================================================
    // Entity
    // ====================================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        Level level = entity != null ? entity.getCommandSenderWorld() : null;
        if (level == null) return;
        SDKEvent.EntityDeathEvent sdk = new SDKEvent.EntityDeathEvent(
                level, entity, event.getSource());
        QLMModSDK.getEventBus().post(sdk);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity entity = event.getEntity();
        Level level = entity != null ? entity.getCommandSenderWorld() : null;
        if (level == null) return;
        SDKEvent.EntityHurtEvent sdk = new SDKEvent.EntityHurtEvent(
                level, entity, event.getAmount(), event.getSource());
        QLMModSDK.getEventBus().post(sdk);
        if (sdk.isCanceled()) {
            event.setCanceled(true);
        }
    }

    // ====================================================================
    // Player
    // ====================================================================

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        QLMModSDK.getEventBus().post(new SDKEvent.PlayerJoinEvent((Player) event.getEntity()));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        QLMModSDK.getEventBus().post(new SDKEvent.PlayerQuitEvent((Player) event.getEntity()));
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        SDKEvent.PlayerChatEvent sdk = new SDKEvent.PlayerChatEvent(player, event.getMessage().getString());
        QLMModSDK.getEventBus().post(sdk);
        if (sdk.isCanceled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 仅在 END 阶段触发，避免每 tick 双触发
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof Player)) return;
        QLMModSDK.getEventBus().post(new SDKEvent.PlayerTickEvent(event.player));
    }

    // ====================================================================
    // World
    // ====================================================================

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        QLMModSDK.getEventBus().post(new SDKEvent.WorldTickEvent(event.level));
    }

    // ====================================================================
    // Server
    // ====================================================================

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        QLMModSDK.getEventBus().post(new SDKEvent.ServerStartEvent(event.getServer()));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QLMModSDK.getEventBus().post(new SDKEvent.ServerStopEvent(event.getServer()));
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    private static Level asLevel(LevelAccessor accessor) {
        return accessor instanceof Level ? (Level) accessor : null;
    }
}

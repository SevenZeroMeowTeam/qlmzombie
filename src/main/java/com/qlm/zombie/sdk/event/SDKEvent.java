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
 * QLM ModSDK — SDK Event 基类与常见事件子类
 * 类似网易我的世界 ModSDK 的统一事件抽象层
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SDK 事件基类。所有 SDK 内部传递的事件都继承自此。
 * 提供事件名称、可取消标志与取消方法。
 */
public abstract class SDKEvent {

    private final String eventName;
    private final boolean cancelable;
    private boolean canceled;

    protected SDKEvent(String eventName, boolean cancelable) {
        this.eventName = eventName;
        this.cancelable = cancelable;
        this.canceled = false;
    }

    public String getEventName() {
        return eventName;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public boolean isCanceled() {
        return canceled;
    }

    /**
     * 取消事件。仅对 cancelable=true 的事件生效。
     */
    public void cancel() {
        if (cancelable) {
            this.canceled = true;
        }
    }

    // ====================================================================
    // 常见事件子类
    // ====================================================================

    /** 方块被破坏 */
    public static class BlockBreakEvent extends SDKEvent {
        public static final String NAME = "block_break";
        private final Level level;
        private final BlockPos pos;
        private final Player player;
        private final BlockState blockState;

        public BlockBreakEvent(Level level, BlockPos pos, Player player, BlockState blockState) {
            super(NAME, true);
            this.level = level;
            this.pos = pos;
            this.player = player;
            this.blockState = blockState;
        }

        public Level getLevel() { return level; }
        public BlockPos getPos() { return pos; }
        public Player getPlayer() { return player; }
        public BlockState getBlockState() { return blockState; }
    }

    /** 方块被放置 */
    public static class BlockPlaceEvent extends SDKEvent {
        public static final String NAME = "block_place";
        private final Level level;
        private final BlockPos pos;
        private final Player player;
        private final BlockState blockState;

        public BlockPlaceEvent(Level level, BlockPos pos, Player player, BlockState blockState) {
            super(NAME, true);
            this.level = level;
            this.pos = pos;
            this.player = player;
            this.blockState = blockState;
        }

        public Level getLevel() { return level; }
        public BlockPos getPos() { return pos; }
        public Player getPlayer() { return player; }
        public BlockState getBlockState() { return blockState; }
    }

    /** 实体死亡 */
    public static class EntityDeathEvent extends SDKEvent {
        public static final String NAME = "entity_death";
        private final Level level;
        private final Entity entity;
        private final DamageSource source;

        public EntityDeathEvent(Level level, Entity entity, DamageSource source) {
            super(NAME, false);
            this.level = level;
            this.entity = entity;
            this.source = source;
        }

        public Level getLevel() { return level; }
        public Entity getEntity() { return entity; }
        public DamageSource getSource() { return source; }
    }

    /** 实体受伤 */
    public static class EntityHurtEvent extends SDKEvent {
        public static final String NAME = "entity_hurt";
        private final Level level;
        private final Entity entity;
        private final float amount;
        private final DamageSource source;

        public EntityHurtEvent(Level level, Entity entity, float amount, DamageSource source) {
            super(NAME, true);
            this.level = level;
            this.entity = entity;
            this.amount = amount;
            this.source = source;
        }

        public Level getLevel() { return level; }
        public Entity getEntity() { return entity; }
        public float getAmount() { return amount; }
        public DamageSource getSource() { return source; }
    }

    /** 玩家加入服务器 */
    public static class PlayerJoinEvent extends SDKEvent {
        public static final String NAME = "player_join";
        private final Player player;

        public PlayerJoinEvent(Player player) {
            super(NAME, false);
            this.player = player;
        }

        public Player getPlayer() { return player; }
    }

    /** 玩家离开服务器 */
    public static class PlayerQuitEvent extends SDKEvent {
        public static final String NAME = "player_quit";
        private final Player player;

        public PlayerQuitEvent(Player player) {
            super(NAME, false);
            this.player = player;
        }

        public Player getPlayer() { return player; }
    }

    /** 玩家发送聊天消息 */
    public static class PlayerChatEvent extends SDKEvent {
        public static final String NAME = "player_chat";
        private final Player player;
        private final String message;

        public PlayerChatEvent(Player player, String message) {
            super(NAME, true);
            this.player = player;
            this.message = message;
        }

        public Player getPlayer() { return player; }
        public String getMessage() { return message; }
    }

    /** 玩家每 tick 触发 */
    public static class PlayerTickEvent extends SDKEvent {
        public static final String NAME = "player_tick";
        private final Player player;

        public PlayerTickEvent(Player player) {
            super(NAME, false);
            this.player = player;
        }

        public Player getPlayer() { return player; }
    }

    /** 世界每 tick 触发 */
    public static class WorldTickEvent extends SDKEvent {
        public static final String NAME = "world_tick";
        private final Level level;

        public WorldTickEvent(Level level) {
            super(NAME, false);
            this.level = level;
        }

        public Level getLevel() { return level; }
    }

    /** 世界加载（保留事件名以兼容旧接口；当前等同于 world_load） */
    public static class WorldLoadEvent extends SDKEvent {
        public static final String NAME = "world_load";
        private final Level level;

        public WorldLoadEvent(Level level) {
            super(NAME, false);
            this.level = level;
        }

        public Level getLevel() { return level; }
    }

    /** 服务器启动 */
    public static class ServerStartEvent extends SDKEvent {
        public static final String NAME = "server_start";
        private final MinecraftServer server;

        public ServerStartEvent(MinecraftServer server) {
            super(NAME, false);
            this.server = server;
        }

        public MinecraftServer getServer() { return server; }
    }

    /** 服务器停止 */
    public static class ServerStopEvent extends SDKEvent {
        public static final String NAME = "server_stop";
        private final MinecraftServer server;

        public ServerStopEvent(MinecraftServer server) {
            super(NAME, false);
            this.server = server;
        }

        public MinecraftServer getServer() { return server; }
    }
}

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
 * QLM ModSDK — 音效 API
 * 提供静态方法在坐标/玩家/全局播放与停止音效，兼容客户端与服务端。
 * 音效名通过 switch 映射到 SoundEvents.xxx，未知音效自动跳过。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.effect;

import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * 音效 API（静态方法）。
 *
 * <p>支持的音效名：</p>
 * <ul>
 *   <li>block.stone.break, entity.player.hurt, ambient.weather.thunder</li>
 *   <li>block.anvil.land, entity.experience_orb.pickup, block.note_block.harp</li>
 *   <li>entity.zombie.ambient, random.levelup, block.chest.open, block.chest.close</li>
 * </ul>
 *
 * <p>用法：</p>
 * <pre>{@code
 * SoundAPI.playSound(level, "block.chest.open", x, y, z, 1.0f, 1.0f);
 * SoundAPI.playSoundToPlayer(player, "random.levelup", 1.0f, 1.0f);
 * SoundAPI.playSoundGlobal(level, "ambient.weather.thunder", 1.0f, 1.0f);
 * SoundAPI.stopSound(level, "block.chest.open");
 * }</pre>
 */
public final class SoundAPI {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SoundAPI() {
    }

    /**
     * 在指定坐标播放音效（附近所有玩家可听见）。
     */
    public static void playSound(Level level, String soundName,
                                 double x, double y, double z,
                                 float volume, float pitch) {
        SoundEvent event = getSound(soundName);
        if (event == null) {
            LOGGER.warn("[QLM ModSDK] 未知音效: {}", soundName);
            return;
        }
        level.playSound(null, x, y, z, event, getSourceFor(soundName), volume, pitch);
    }

    /**
     * 仅对指定玩家播放音效（其他玩家听不见）。
     */
    public static void playSoundToPlayer(Player player, String soundName,
                                         float volume, float pitch) {
        SoundEvent event = getSound(soundName);
        if (event == null) {
            LOGGER.warn("[QLM ModSDK] 未知音效: {}", soundName);
            return;
        }
        // player 参数非 null 时，服务端仅向该玩家发送音效包
        player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                event, getSourceFor(soundName), volume, pitch);
    }

    /**
     * 全场播放音效（所有在线玩家均可听见）。
     */
    public static void playSoundGlobal(Level level, String soundName,
                                       float volume, float pitch) {
        SoundEvent event = getSound(soundName);
        if (event == null) {
            LOGGER.warn("[QLM ModSDK] 未知音效: {}", soundName);
            return;
        }
        for (Player player : level.players()) {
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                    event, getSourceFor(soundName), volume, pitch);
        }
    }

    /**
     * 停止播放指定音效。服务端向所有玩家发送停止包，客户端直接停止本地音效。
     */
    public static void stopSound(Level level, String soundName) {
        ResourceLocation id = ResourceLocation.tryParse(soundName);
        if (id == null) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(id, null);
            for (ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
                sp.connection.send(packet);
            }
        }
    }

    /**
     * 根据音效名前缀推断合适的声音分类。
     */
    private static SoundSource getSourceFor(String soundName) {
        if (soundName == null) {
            return SoundSource.NEUTRAL;
        }
        if (soundName.startsWith("block.")) {
            return SoundSource.BLOCKS;
        }
        if (soundName.startsWith("entity.player.")) {
            return SoundSource.PLAYERS;
        }
        if (soundName.startsWith("entity.zombie.")) {
            return SoundSource.HOSTILE;
        }
        if (soundName.startsWith("ambient.")) {
            return SoundSource.AMBIENT;
        }
        return SoundSource.NEUTRAL;
    }

    /**
     * 将音效名字符串映射到 {@link SoundEvent}。
     * 未知音效返回 null。
     */
    private static SoundEvent getSound(String name) {
        if (name == null) {
            return null;
        }
        switch (name) {
            case "block.stone.break":
                return SoundEvents.STONE_BREAK;
            case "entity.player.hurt":
                return SoundEvents.PLAYER_HURT;
            case "ambient.weather.thunder":
                return SoundEvents.LIGHTNING_BOLT_THUNDER;
            case "block.anvil.land":
                return SoundEvents.ANVIL_LAND;
            case "entity.experience_orb.pickup":
                return SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "block.note_block.harp":
                return SoundEvents.NOTE_BLOCK_HARP.get();
            case "entity.zombie.ambient":
                return SoundEvents.ZOMBIE_AMBIENT;
            case "random.levelup":
                return SoundEvents.PLAYER_LEVELUP;
            case "block.chest.open":
                return SoundEvents.CHEST_OPEN;
            case "block.chest.close":
                return SoundEvents.CHEST_CLOSE;
            default:
                return null;
        }
    }
}

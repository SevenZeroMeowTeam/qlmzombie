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
 * QLM ModSDK — 粒子特效 API
 * 提供静态方法在指定坐标生成各类粒子效果，兼容客户端与服务端。
 * 客户端调用 level.addParticle，服务端调用 ServerLevel.sendParticles。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.effect;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * 粒子特效 API（静态方法）。
 *
 * <p>支持的粒子类型字符串（不区分大小写）：</p>
 * <ul>
 *   <li>flame, smoke, portal, heart, villager_angry, happy_villager</li>
 *   <li>lava, redstone, snowball, large_smoke, enchant, end_rod</li>
 *   <li>damage_indicator, cloud, totem, spit, splash, witch</li>
 * </ul>
 *
 * <p>用法：</p>
 * <pre>{@code
 * ParticleAPI.spawnParticle(level, "flame", x, y, z);
 * ParticleAPI.spawnParticle(level, "smoke", x, y, z, 0.0, 0.5, 0.0, 10, 0.1);
 * ParticleAPI.spawnExplosion(level, x, y, z, false);
 * }</pre>
 */
public final class ParticleAPI {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ParticleAPI() {
    }

    /**
     * 在坐标生成单个粒子（无速度）。
     */
    public static void spawnParticle(Level level, String particleType, double x, double y, double z) {
        spawnParticle(level, particleType, x, y, z, 0.0, 0.0, 0.0, 1, 0.0);
    }

    /**
     * 在坐标生成单个粒子（带速度）。
     */
    public static void spawnParticle(Level level, String particleType,
                                     double x, double y, double z,
                                     double dx, double dy, double dz) {
        spawnParticle(level, particleType, x, y, z, dx, dy, dz, 1, 0.0);
    }

    /**
     * 在坐标批量生成粒子（带速度与数量）。
     */
    public static void spawnParticle(Level level, String particleType,
                                     double x, double y, double z,
                                     double dx, double dy, double dz,
                                     int count) {
        spawnParticle(level, particleType, x, y, z, dx, dy, dz, count, 0.0);
    }

    /**
     * 在坐标批量生成粒子（带速度、数量与扩散速度）。
     *
     * @param speed 扩散速度因子（0.0 = 无随机扩散）
     */
    public static void spawnParticle(Level level, String particleType,
                                     double x, double y, double z,
                                     double dx, double dy, double dz,
                                     int count, double speed) {
        ParticleOptions options = getParticle(particleType);
        if (options == null) {
            LOGGER.warn("[QLM ModSDK] 未知粒子类型: {}", particleType);
            return;
        }
        if (level.isClientSide) {
            // 客户端：直接调用 addParticle
            for (int i = 0; i < count; i++) {
                level.addParticle(options, x, y, z, dx, dy, dz);
            }
        } else if (level instanceof ServerLevel serverLevel) {
            // 服务端：发送粒子包给附近玩家
            serverLevel.sendParticles(options, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /**
     * 在坐标生成爆炸特效（含方块破坏与可选火焰）。
     *
     * @param fire 是否产生火焰
     */
    public static void spawnExplosion(Level level, double x, double y, double z, boolean fire) {
        level.explode(null, x, y, z, 4.0f, fire, Level.ExplosionInteraction.BLOCK);
    }

    /**
     * 将粒子类型字符串映射到 {@link ParticleOptions}。
     * 不区分大小写，未知类型返回 null。
     */
    private static ParticleOptions getParticle(String type) {
        if (type == null) {
            return null;
        }
        switch (type.toLowerCase()) {
            case "flame":
                return ParticleTypes.FLAME;
            case "smoke":
                return ParticleTypes.SMOKE;
            case "portal":
                return ParticleTypes.PORTAL;
            case "heart":
                return ParticleTypes.HEART;
            case "villager_angry":
                return ParticleTypes.ANGRY_VILLAGER;
            case "happy_villager":
                return ParticleTypes.HAPPY_VILLAGER;
            case "lava":
                return ParticleTypes.LAVA;
            case "redstone":
                return DustParticleOptions.REDSTONE;
            case "snowball":
                return ParticleTypes.SNOWFLAKE;
            case "large_smoke":
                return ParticleTypes.LARGE_SMOKE;
            case "enchant":
                return ParticleTypes.ENCHANT;
            case "end_rod":
                return ParticleTypes.END_ROD;
            case "damage_indicator":
                return ParticleTypes.DAMAGE_INDICATOR;
            case "cloud":
                return ParticleTypes.CLOUD;
            case "totem":
                return ParticleTypes.TOTEM_OF_UNDYING;
            case "spit":
                return ParticleTypes.SPIT;
            case "splash":
                return ParticleTypes.SPLASH;
            case "witch":
                return ParticleTypes.WITCH;
            default:
                return null;
        }
    }
}

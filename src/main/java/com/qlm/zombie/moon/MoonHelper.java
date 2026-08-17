package com.qlm.zombie.moon;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.util.Optional;

/**
 * EnhancedCelestials 5.x 兼容层。
 *
 * <p>EC 5.0+ 使用 DataAnchor 库的 TrackedDataKey 系统存储月亮数据，
 * 不再有旧的 EnhancedCelestialsContext 类。本类通过反射适配新 API：</p>
 *
 * <ol>
 *   <li>从 {@code EnhancedCelestials.LUNAR_FORECAST_WORLD_DATA} 获取 TrackedDataKey</li>
 *   <li>通过 Level mixin 的 {@code dataAnchor$getTrackedData(key)} 获取数据实例</li>
 *   <li>调用 {@code currentLunarEventHolder()} 获取当前月亮事件 Holder</li>
 *   <li>通过 {@code Holder.unwrapKey()} 获取 ResourceKey，用 {@code location().toString()} 转为字符串 ID</li>
 * </ol>
 */
public class MoonHelper {

    private static final boolean EC_LOADED = ModList.get().isLoaded("enhancedcelestials");

    /** 缓存当前月亮阶段，避免每 tick 重复反射调用 */
    private static String cachedMoonId = null;
    private static long cachedMoonIdTime = 0;
    private static final long CACHE_DURATION_MS = 5000; // 5秒缓存

    /** 是否已经报告过 EnhancedCelestials API 失败 */
    private static boolean reportedApiFailure = false;

    // 反射缓存：避免每次调用都重新查找类和方法
    private static Class<?> trackedDataKeyClass;
    private static java.lang.reflect.Method getTrackedDataMethod;
    private static Object lunarForecastKey; // EnhancedCelestials.LUNAR_FORECAST_WORLD_DATA
    private static java.lang.reflect.Method currentLunarEventHolderMethod;
    private static java.lang.reflect.Method setLunarEventMethod;

    // DefaultLunarEvents 常量缓存
    private static Object bloodMoonKey;
    private static Object harvestMoonKey;
    private static Object blueMoonKey;

    /** 初始化反射缓存（首次调用时执行） */
    private static boolean initReflection() {
        if (lunarForecastKey != null) return true;
        try {
            // 1. 获取 TrackedDataKey 类
            trackedDataKeyClass = Class.forName("dev.corgitaco.dataanchor.data.registry.TrackedDataKey");

            // 2. 获取 EnhancedCelestials.LUNAR_FORECAST_WORLD_DATA 静态字段
            Class<?> ecClass = Class.forName("dev.corgitaco.enhancedcelestials.EnhancedCelestials");
            lunarForecastKey = ecClass.getField("LUNAR_FORECAST_WORLD_DATA").get(null);

            // 3. 获取 DefaultLunarEvents 常量
            Class<?> defaultEventsClass = Class.forName("dev.corgitaco.enhancedcelestials.api.lunarevent.DefaultLunarEvents");
            bloodMoonKey = defaultEventsClass.getField("BLOOD_MOON").get(null);
            harvestMoonKey = defaultEventsClass.getField("HARVEST_MOON").get(null);
            blueMoonKey = defaultEventsClass.getField("BLUE_MOON").get(null);

            return true;
        } catch (Exception e) {
            QLMZombieMod.LOGGER.error("[QLM Zombie] EnhancedCelestials 反射初始化失败", e);
            return false;
        }
    }

    /**
     * 从 ServerLevel 获取 EnhancedCelestialsLunarForecastWorldData 实例。
     * Level 通过 DataAnchor 的 mixin 实现了 TrackedDataContainer 接口，
     * 通过 dataAnchor$getTrackedData(key) 方法获取数据。
     */
    private static Object getLunarForecastData(ServerLevel level) {
        try {
            if (!initReflection()) return null;

            // 查找 dataAnchor$getTrackedData 方法（mixin 注入到 Level 类）
            if (getTrackedDataMethod == null) {
                getTrackedDataMethod = level.getClass().getMethod("dataAnchor$getTrackedData", trackedDataKeyClass);
            }
            Object optional = getTrackedDataMethod.invoke(level, lunarForecastKey);
            if (optional == null) return null;

            // Optional.orElse(null)
            java.lang.reflect.Method orElse = optional.getClass().getMethod("orElse", Object.class);
            return orElse.invoke(optional, (Object) null);
        } catch (Exception e) {
            if (!reportedApiFailure) {
                QLMZombieMod.LOGGER.warn("[QLM Zombie] getLunarForecastData failed: {}", e.getMessage());
            }
            return null;
        }
    }

    public static boolean forceBloodMoon(ServerLevel level) {
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置血月");
            return false;
        }
        return setLunarEvent(level, bloodMoonKey);
    }

    public static boolean forceHarvestMoon(ServerLevel level) {
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置丰收之月");
            return false;
        }
        return setLunarEvent(level, harvestMoonKey);
    }

    public static boolean forceBlueMoon(ServerLevel level) {
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置蓝月");
            return false;
        }
        return setLunarEvent(level, blueMoonKey);
    }

    public static boolean forceLuckyMoon(ServerLevel level) {
        // EnhancedCelestials 5.x 中没有 "lucky_moon"，使用 BLUE_MOON 作为替代
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置幸运之月");
            return false;
        }
        return setLunarEvent(level, blueMoonKey);
    }

    /**
     * 设置月亮事件。
     * 调用 EnhancedCelestialsLunarForecastWorldData.setLunarEvent(ResourceKey<LunarEvent>)
     */
    private static boolean setLunarEvent(ServerLevel level, Object lunarEventKey) {
        if (!EC_LOADED || lunarEventKey == null) return false;
        try {
            Object data = getLunarForecastData(level);
            if (data == null) return false;

            if (setLunarEventMethod == null) {
                setLunarEventMethod = data.getClass().getMethod("setLunarEvent", ResourceKey.class);
            }
            setLunarEventMethod.invoke(data, lunarEventKey);
            return true;
        } catch (Exception e) {
            QLMZombieMod.LOGGER.error("[QLM Zombie] setLunarEvent failed", e);
            return false;
        }
    }

    /**
     * 获取当前月亮事件 ID。
     * 返回格式如 "enhancedcelestials:blood_moon" 或 "none"。
     */
    public static String getCurrentMoonId(ServerLevel level) {
        if (!EC_LOADED || level == null) return "none";

        // 检查缓存是否有效
        long now = System.currentTimeMillis();
        if (cachedMoonId != null && (now - cachedMoonIdTime) < CACHE_DURATION_MS) {
            return cachedMoonId;
        }

        try {
            Object data = getLunarForecastData(level);
            if (data == null) return "none";

            // 调用 currentLunarEventHolder() 获取 Holder<LunarEvent>
            if (currentLunarEventHolderMethod == null) {
                currentLunarEventHolderMethod = data.getClass().getMethod("currentLunarEventHolder");
            }
            Object holder = currentLunarEventHolderMethod.invoke(data);
            if (holder == null) return "none";

            // ★ 直接使用 MC 的 Holder 接口（编译期方法调用，reobf 时自动映射到运行时名称 m_203543_）。
            //   不能用反射 getMethod("getKey")：1.20.1 中该方法正式名为 unwrapKey（不是 getKey），
            //   且字符串不会随 reobf 重映射，运行时必然 NoSuchMethodException。
            if (holder instanceof Holder<?> h) {
                Optional<? extends ResourceKey<?>> keyOpt = h.unwrapKey();
                if (keyOpt.isPresent()) {
                    // 用 location().toString() 得到 "enhancedcelestials:blood_moon"；
                    // ResourceKey.toString() 是 "ResourceKey[注册表 / 位置]" 格式，不能直接用于比较
                    String result = keyOpt.get().location().toString();

                    // 更新缓存
                    cachedMoonId = result;
                    cachedMoonIdTime = now;
                    reportedApiFailure = false; // 成功后重置失败标志
                    return result;
                }
            }
            return "none";
        } catch (Exception e) {
            // 只报告一次失败，避免日志刷屏
            if (!reportedApiFailure) {
                QLMZombieMod.LOGGER.warn("[QLM Zombie] getCurrentMoonId failed (EnhancedCelestials API error, will not report again): {}", e.getMessage());
                reportedApiFailure = true;
            }
            return "none";
        }
    }

    public static boolean isNight(ServerLevel level) {
        return level != null && level.isNight();
    }

    public static long getDay(ServerLevel level) {
        return level == null ? 0 : level.getDayTime() / 57600L;
    }

    public static long getDayTime(ServerLevel level) {
        return level == null ? 0 : level.getDayTime();
    }

    public static boolean isHarvestMoon(ServerLevel level) {
        return level != null && "enhancedcelestials:harvest_moon".equals(getCurrentMoonId(level));
    }

    public static boolean isLuckyMoon(ServerLevel level) {
        // EC 5.x 用 blue_moon 替代 lucky_moon
        return level != null && "enhancedcelestials:blue_moon".equals(getCurrentMoonId(level));
    }

    public static boolean isBloodMoon(ServerLevel level) {
        return level != null && "enhancedcelestials:blood_moon".equals(getCurrentMoonId(level));
    }

    public static boolean forceGrowCrop(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            crop.growCrops(level, pos, state);
            return true;
        }
        return false;
    }
}

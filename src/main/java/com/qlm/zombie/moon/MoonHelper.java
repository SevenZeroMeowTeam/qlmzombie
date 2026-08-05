package com.qlm.zombie.moon;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

public class MoonHelper {

    private static final boolean EC_LOADED = ModList.get().isLoaded("enhancedcelestials");

    /** 缓存当前月亮阶段，避免每 tick 重复反射调用 */
    private static String cachedMoonId = null;
    private static long cachedMoonIdTime = 0;
    private static final long CACHE_DURATION_MS = 5000; // 5秒缓存

    /** 是否已经报告过 EnhancedCelestials API 失败 */
    private static boolean reportedApiFailure = false;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceKey LUCKY_MOON_KEY;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceKey getLuckyMoonKey() {
        if (LUCKY_MOON_KEY == null) {
            ResourceKey registryKey = ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath("enhancedcelestials", "lunar_event"));
            LUCKY_MOON_KEY = ResourceKey.create(registryKey,
                    ResourceLocation.fromNamespaceAndPath("enhancedcelestials", "lucky_moon"));
        }
        return LUCKY_MOON_KEY;
    }

    public static boolean forceBloodMoon(ServerLevel level) {
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置血月");
            return false;
        }
        try {
            Class<?> defaultEvents = Class.forName("dev.corgitaco.enhancedcelestials.api.lunarevent.DefaultLunarEvents");
            java.lang.reflect.Field bloodMoonField = defaultEvents.getField("BLOOD_MOON");
            Object bloodMoon = bloodMoonField.get(null);
            return setLunarEvent(level, bloodMoon);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.error("[QLM Zombie] forceBloodMoon failed", e);
            return false;
        }
    }

    public static boolean forceLuckyMoon(ServerLevel level) {
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置幸运之月");
            return false;
        }
        return setLunarEvent(level, getLuckyMoonKey());
    }

    public static boolean forceHarvestMoon(ServerLevel level) {
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置丰收之月");
            return false;
        }
        try {
            Class<?> defaultEvents = Class.forName("dev.corgitaco.enhancedcelestials.api.lunarevent.DefaultLunarEvents");
            java.lang.reflect.Field harvestMoonField = defaultEvents.getField("HARVEST_MOON");
            Object harvestMoon = harvestMoonField.get(null);
            return setLunarEvent(level, harvestMoon);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.error("[QLM Zombie] forceHarvestMoon failed", e);
            return false;
        }
    }

    public static boolean forceBlueMoon(ServerLevel level) {
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置蓝月");
            return false;
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean setLunarEvent(ServerLevel level, Object lunarEvent) {
        if (!EC_LOADED) return false;
        try {
            Class<?> lunarUtil = Class.forName("dev.corgitaco.enhancedcelestials.core.EnhancedCelestialsContext");
            java.lang.reflect.Method getContext = lunarUtil.getMethod("get", Level.class);
            Object context = getContext.invoke(null, level);
            if (context == null) return false;
            java.lang.reflect.Method getData = context.getClass().getMethod("getLunarData");
            Object data = getData.invoke(context);
            if (data == null) return false;
            java.lang.reflect.Method setEvent = data.getClass().getMethod("setLunarEvent", ResourceKey.class);
            setEvent.invoke(data, lunarEvent);
            return true;
        } catch (Exception e) {
            QLMZombieMod.LOGGER.error("[QLM Zombie] setLunarEvent failed", e);
            return false;
        }
    }

    public static String getCurrentMoonId(ServerLevel level) {
        if (!EC_LOADED || level == null) return "none";

        // 检查缓存是否有效
        long now = System.currentTimeMillis();
        if (cachedMoonId != null && (now - cachedMoonIdTime) < CACHE_DURATION_MS) {
            return cachedMoonId;
        }

        try {
            Class<?> lunarUtil = Class.forName("dev.corgitaco.enhancedcelestials.core.EnhancedCelestialsContext");
            java.lang.reflect.Method getContext = lunarUtil.getMethod("get", Level.class);
            Object context = getContext.invoke(null, level);
            if (context == null) return "none";
            java.lang.reflect.Method getData = context.getClass().getMethod("getLunarData");
            Object data = getData.invoke(context);
            if (data == null) return "none";
            java.lang.reflect.Method getCurrentEvent = data.getClass().getMethod("getCurrentLunarEvent");
            Object event = getCurrentEvent.invoke(data);
            if (event == null) return "none";
            java.lang.reflect.Method getKey = event.getClass().getMethod("getKey");
            Object key = getKey.invoke(event);
            String result = key != null ? key.toString() : "none";

            // 更新缓存
            cachedMoonId = result;
            cachedMoonIdTime = now;
            reportedApiFailure = false; // 成功后重置失败标志
            return result;
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
        return level != null && "enhancedcelestials:lucky_moon".equals(getCurrentMoonId(level));
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

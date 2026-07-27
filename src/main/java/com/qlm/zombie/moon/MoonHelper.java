package com.qlm.zombie.moon;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public class MoonHelper {

    private static final boolean EC_LOADED = ModList.get().isLoaded("enhancedcelestials");

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
        try {
            Class<?> defaultEvents = Class.forName("dev.corgitaco.enhancedcelestials.api.lunarevent.DefaultLunarEvents");
            java.lang.reflect.Field blueMoonField = defaultEvents.getField("BLUE_MOON");
            Object blueMoon = blueMoonField.get(null);
            return setLunarEvent(level, blueMoon);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.error("[QLM Zombie] forceBlueMoon failed", e);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean setLunarEvent(ServerLevel level, Object key) {
        if (!EC_LOADED) return false;
        try {
            Class<?> enhancedCelestials = Class.forName("dev.corgitaco.enhancedcelestials.EnhancedCelestials");
            Method lunarForecastMethod = enhancedCelestials.getMethod("lunarForecastWorldData", ServerLevel.class);
            Object opt = lunarForecastMethod.invoke(null, level);
            Method orElseMethod = opt.getClass().getMethod("orElse", Object.class);
            Object data = orElseMethod.invoke(opt, new Object[]{null});
            if (data == null) {
                QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials lunar forecast data not available");
                return false;
            }
            Method setLunarEventMethod = data.getClass().getMethod("setLunarEvent", ResourceKey.class);
            setLunarEventMethod.invoke(data, key);
            QLMZombieMod.LOGGER.info("[QLM Zombie] Forced lunar event");
            return true;
        } catch (Exception e) {
            QLMZombieMod.LOGGER.error("[QLM Zombie] setLunarEvent failed", e);
            return false;
        }
    }

    public static String getCurrentMoonId(ServerLevel level) {
        if (!EC_LOADED || level == null) return "none";
        try {
            Class<?> enhancedCelestials = Class.forName("dev.corgitaco.enhancedcelestials.EnhancedCelestials");
            Method lunarForecastMethod = enhancedCelestials.getMethod("lunarForecastWorldData", ServerLevel.class);
            Object opt = lunarForecastMethod.invoke(null, level);
            Method orElseMethod = opt.getClass().getMethod("orElse", Object.class);
            Object data = orElseMethod.invoke(opt, new Object[]{null});
            if (data == null) return "none";
            if (level.isDay()) {
                return "enhancedcelestials:default";
            }
            Method currentEventMethod = data.getClass().getMethod("currentLunarEventHolder");
            Object holder = currentEventMethod.invoke(data);
            Method unwrapKeyMethod = holder.getClass().getMethod("unwrapKey");
            Object keyOpt = unwrapKeyMethod.invoke(holder);
            Method mapMethod = keyOpt.getClass().getMethod("map", java.util.function.Function.class);
            Object result = mapMethod.invoke(keyOpt, new Object[]{(java.util.function.Function<ResourceKey, String>) k -> k.location().toString()});
            return "enhancedcelestials:default";
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] getCurrentMoonId failed: {}", e.getMessage());
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
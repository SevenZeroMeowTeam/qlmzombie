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

/**
 * KubeJS 脚本通过 Java.loadClass('com.qlm.zombie.moon.MoonHelper') 调用本类静态方法，
 * 间接操作 Enhanced Celestials 的月相调度引擎。
 */
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
        return setLunarEvent(level, dev.corgitaco.enhancedcelestials.api.lunarevent.DefaultLunarEvents.BLOOD_MOON);
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
        return setLunarEvent(level, dev.corgitaco.enhancedcelestials.api.lunarevent.DefaultLunarEvents.HARVEST_MOON);
    }

    public static boolean forceBlueMoon(ServerLevel level) {
        if (!EC_LOADED) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials 未加载，无法设置蓝月");
            return false;
        }
        return setLunarEvent(level, dev.corgitaco.enhancedcelestials.api.lunarevent.DefaultLunarEvents.BLUE_MOON);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean setLunarEvent(ServerLevel level, ResourceKey key) {
        if (!EC_LOADED) return false;
        dev.corgitaco.enhancedcelestials.lunarevent.EnhancedCelestialsLunarForecastWorldData data =
                dev.corgitaco.enhancedcelestials.EnhancedCelestials.lunarForecastWorldData(level).orElse(null);
        if (data == null) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] EnhancedCelestials lunar forecast data not available for level {}",
                    level);
            return false;
        }
        try {
            data.setLunarEvent(key);
            QLMZombieMod.LOGGER.info("[QLM Zombie] Forced lunar event: {}", key.location());
            return true;
        } catch (Exception e) {
            QLMZombieMod.LOGGER.error("[QLM Zombie] Failed to set lunar event {}", key.location(), e);
            return false;
        }
    }

    public static String getCurrentMoonId(ServerLevel level) {
        if (!EC_LOADED || level == null) return "none";
        dev.corgitaco.enhancedcelestials.lunarevent.EnhancedCelestialsLunarForecastWorldData data =
                dev.corgitaco.enhancedcelestials.EnhancedCelestials.lunarForecastWorldData(level).orElse(null);
        if (data == null) return "none";
        try {
            if (level.isDay()) {
                return "enhancedcelestials:default";
            }
            net.minecraft.core.Holder<dev.corgitaco.enhancedcelestials.api.lunarevent.LunarEvent> holder = data.currentLunarEventHolder();
            return holder.unwrapKey()
                    .map(k -> k.location().toString())
                    .orElse("enhancedcelestials:default");
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] getCurrentMoonId failed: {}", e.getMessage());
            return "none";
        }
    }

    public static boolean isNight(ServerLevel level) {
        return level != null && level.isNight();
    }

    public static long getDay(ServerLevel level) {
        return level == null ? 0 : level.getDayTime() / 24000L;
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
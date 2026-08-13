package com.qlm.zombie.moon;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 血月调度器：
 * - 前25天（Day 0-24）不生成血月
 * - 第25天起，每14天生成一次血月
 * - 只在夜晚触发，且同一晚只触发一次
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class BloodMoonScheduler {

    /** 血月间隔天数 */
    private static final int BLOOD_MOON_INTERVAL = 14;
    /** 前25天不生成血月 */
    private static final int SAFE_DAYS = 25;

    /** 记录上一次血月发生的天数，避免重复触发 */
    private static long lastBloodMoonDay = -1L;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;

        // 只在夜晚处理（13000-23000 ticks）
        // 在夜晚中间时刻（18000）检查并触发
        if (timeOfDay < 18000 || timeOfDay > 18500) return;

        // 检查是否已过安全期
        if (currentDay < SAFE_DAYS) return;

        // 检查是否为血月日：从第25天起，每14天一次
        // 血月日 = (currentDay - 25) % 14 == 0
        if ((currentDay - SAFE_DAYS) % BLOOD_MOON_INTERVAL != 0) return;

        // 避免重复触发
        if (lastBloodMoonDay == currentDay) return;
        lastBloodMoonDay = currentDay;

        // 强制触发血月
        boolean success = MoonHelper.forceBloodMoon(overworld);
        if (success) {
            QLMZombieMod.LOGGER.info("[血月] Day {}: 血月已降临！", currentDay);
            // 通知所有在线玩家
            for (var player : overworld.players()) {
                player.sendSystemMessage(Component.literal("§4§l☠☠☠ 血月降临！☠☠☠"));
                player.sendSystemMessage(Component.literal("§c§l今夜怪物将疯狂肆虐，请做好准备！"));
                player.sendSystemMessage(Component.literal("§4§l☠☠☠☠☠☠☠☠☠☠☠☠☠"));
            }
        } else {
            QLMZombieMod.LOGGER.warn("[血月] Day {}: 强制触发血月失败（EnhancedCelestials 未加载）", currentDay);
        }
    }

    /** 重置血月调度器（用于 /qlm moon reset 等命令） */
    public static void reset() {
        lastBloodMoonDay = -1;
    }

    /** 获取下一次血月的天数 */
    public static long getNextBloodMoonDay(long currentDay) {
        if (currentDay < SAFE_DAYS) return SAFE_DAYS;
        long daysSinceSafe = currentDay - SAFE_DAYS;
        long nextIn = BLOOD_MOON_INTERVAL - (daysSinceSafe % BLOOD_MOON_INTERVAL);
        if (nextIn == 0) nextIn = BLOOD_MOON_INTERVAL;
        return currentDay + nextIn;
    }
}
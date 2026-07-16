package com.qlm.zombie.dayphase;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.config.QLMConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

public class DayPhaseManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DAY_LENGTH = 57600L;  // 2400 tick = 1 小时
    private static int tickCounter = 0;
    private static boolean firstDayInitialized = false;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        long currentDay = overworld.getDayTime() / DAY_LENGTH;
        DayPhase phase = DayPhase.forDay(currentDay);

        Difficulty currentDifficulty = overworld.getDifficulty();
        Difficulty targetDifficulty = phase.difficulty();

        if (currentDifficulty != targetDifficulty) {
            server.setDifficulty(targetDifficulty, true);
            LOGGER.info("[QLM Zombie] 服务器启动: 第{}天 -> 设为阶段 {} (难度 {})", currentDay, phase.displayName(), targetDifficulty.getKey());
        }

        DifficultyLockState lockState = DifficultyLockState.get(server);
        lockState.setLastAppliedDay(currentDay);
        lockState.setLastPhase(phase.name());
        lockState.setLocked(phase.isLocked());
        firstDayInitialized = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        if (++tickCounter < 200) return;
        tickCounter = 0;

        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / DAY_LENGTH;

        DayPhase phase = DayPhase.forDay(currentDay);

        DifficultyLockState lockState = DifficultyLockState.get(server);

        if (!firstDayInitialized && currentDay >= 0) {
            firstDayInitialized = true;
            Difficulty initialDifficulty = overworld.getDifficulty();

            if (initialDifficulty != phase.difficulty()) {
                forceSetDifficulty(server, phase.difficulty());
                LOGGER.info("[QLM Zombie] 游戏初始化 -> 第{}天 -> 阶段 {} (难度 {})", currentDay, phase.displayName(), phase.difficulty().getKey());
            }
            lockState.setLastAppliedDay(currentDay);
            lockState.setLastPhase(phase.name());
            return;
        }

        if (phase.isLocked()) {
            lockState.setLocked(true);
        } else {
            lockState.setLocked(false);
        }

        if (QLMConfig.ENABLE_DIFFICULTY_LOCK.get() && lockState.isLocked()) {
            if (overworld.getDifficulty() != Difficulty.HARD) {
                forceSetDifficulty(server, Difficulty.HARD);
                LOGGER.info("[QLM Zombie] 难度已锁定为 HARD (第{}天，{}阶段)，禁止更改", currentDay, phase.displayName());
            }
        } else if (lockState.getLastAppliedDay() != currentDay) {
            if (overworld.getDifficulty() != phase.difficulty()) {
                forceSetDifficulty(server, phase.difficulty());
                LOGGER.info("[QLM Zombie] 第{}天 -> 阶段 {} (难度 {})", currentDay, phase.displayName(), phase.difficulty().getKey());
            }
            lockState.setLastAppliedDay(currentDay);
            lockState.setLastPhase(phase.name());
        }
    }

    private static void forceSetDifficulty(MinecraftServer server, Difficulty difficulty) {
        server.setDifficulty(difficulty, true);
    }

    public static DayPhase getCurrentPhase() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return DayPhase.SAFE;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return DayPhase.SAFE;

        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / DAY_LENGTH;
        return DayPhase.forDay(currentDay);
    }

    public static long getCurrentDay() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        return overworld.getDayTime() / DAY_LENGTH;
    }
}
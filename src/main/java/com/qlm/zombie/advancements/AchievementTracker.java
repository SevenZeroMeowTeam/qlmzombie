package com.qlm.zombie.advancements;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.dayphase.DayPhase;
import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AchievementTracker {

    private static final Map<UUID, Integer> zombieKillCount = new HashMap<>();
    private static final Map<UUID, Integer> bloodMoonSurvived = new HashMap<>();
    private static final Map<UUID, Long> awardedDays = new HashMap<>();
    private static final Map<UUID, String> awardedPhase = new HashMap<>();
    private static final Set<UUID> wasInBloodMoon = new HashSet<>();

    private static final int[] DAY_MILESTONES = {7, 14, 30, 60, 100, 150, 365};
    private static final int[] KILL_MILESTONES = {10, 50, 100, 500, 1000};
    private static final int[] BLOOD_MOON_MILESTONES = {1, 3, 10};

    private static net.minecraft.server.MinecraftServer currentServer = null;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        currentServer = event.getServer();
        if (currentServer == null) return;

        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            checkSurvivalDays(player);
            checkPhaseSurvival(player);
            checkBloodMoonSurvival(player);
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        UUID playerId = player.getUUID();
        int kills = zombieKillCount.getOrDefault(playerId, 0) + 1;
        zombieKillCount.put(playerId, kills);

        for (int milestone : KILL_MILESTONES) {
            if (kills == milestone) {
                AdvancementManager.awardAdvancement(player, "zombie_kills/kill_" + milestone, "kill_" + milestone);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§a🏆 成就解锁: 击杀 " + milestone + " 只僵尸！"),
                    false
                );
                break;
            }
        }
    }

    private static void checkSurvivalDays(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long currentDay = player.serverLevel().getDayTime() / 24000L;
        long lastAwarded = awardedDays.getOrDefault(playerId, 0L);

        for (int milestone : DAY_MILESTONES) {
            if (currentDay >= milestone && lastAwarded < milestone) {
                awardedDays.put(playerId, (long) milestone);
                AdvancementManager.awardAdvancement(player, "survival_days/day_" + milestone, "day_" + milestone);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§a🏆 成就解锁: 存活 " + milestone + " 天！"),
                    false
                );
                break;
            }
        }
    }

    private static void checkPhaseSurvival(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long currentDay = player.serverLevel().getDayTime() / 24000L;
        DayPhase phase = DayPhase.forDay(currentDay);

        String phaseName = phase.name().toLowerCase();
        String lastAwarded = awardedPhase.getOrDefault(playerId, "");

        if (!phaseName.equals(lastAwarded) && phase != DayPhase.SAFE) {
            awardedPhase.put(playerId, phaseName);
            AdvancementManager.awardAdvancement(player, "phase_survival/" + phaseName, phaseName);
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§a🏆 成就解锁: 进入 " + phase.displayName() + " 阶段！"),
                false
            );
        }
    }

    private static void checkBloodMoonSurvival(ServerPlayer player) {
        UUID playerId = player.getUUID();
        boolean isCurrentlyBloodMoon = MoonHelper.isBloodMoon(player.serverLevel());

        if (isCurrentlyBloodMoon) {
            wasInBloodMoon.add(playerId);
        } else if (wasInBloodMoon.contains(playerId)) {
            wasInBloodMoon.remove(playerId);
            int survived = bloodMoonSurvived.getOrDefault(playerId, 0) + 1;
            bloodMoonSurvived.put(playerId, survived);

            for (int milestone : BLOOD_MOON_MILESTONES) {
                if (survived == milestone) {
                    AdvancementManager.awardAdvancement(player, "blood_moon/survive_" + milestone, "survive_" + milestone);
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a🏆 成就解锁: 存活 " + milestone + " 次血月！"),
                        false
                    );
                    break;
                }
            }
        }
    }
}
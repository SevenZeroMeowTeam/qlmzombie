package com.qlm.zombie.advancements;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AdvancementManager {

    private static final Map<String, String> NEXT_ADVANCEMENT = new HashMap<>();

    static {
        NEXT_ADVANCEMENT.put("survival_days/day_7", "survival_days/day_14");
        NEXT_ADVANCEMENT.put("survival_days/day_14", "survival_days/day_30");
        NEXT_ADVANCEMENT.put("survival_days/day_30", "survival_days/day_60");
        NEXT_ADVANCEMENT.put("survival_days/day_60", "survival_days/day_100");
        NEXT_ADVANCEMENT.put("survival_days/day_100", "survival_days/day_150");
        NEXT_ADVANCEMENT.put("survival_days/day_150", "survival_days/day_365");

        NEXT_ADVANCEMENT.put("blood_moon/survive_1", "blood_moon/survive_3");
        NEXT_ADVANCEMENT.put("blood_moon/survive_3", "blood_moon/survive_10");

        NEXT_ADVANCEMENT.put("zombie_kills/kill_10", "zombie_kills/kill_50");
        NEXT_ADVANCEMENT.put("zombie_kills/kill_50", "zombie_kills/kill_100");
        NEXT_ADVANCEMENT.put("zombie_kills/kill_100", "zombie_kills/kill_500");
        NEXT_ADVANCEMENT.put("zombie_kills/kill_500", "zombie_kills/kill_1000");
        NEXT_ADVANCEMENT.put("zombie_kills/kill_1000", "zombie_kills/kill_evolution");

        NEXT_ADVANCEMENT.put("phase_survival/peaceful", "phase_survival/easy");
        NEXT_ADVANCEMENT.put("phase_survival/easy", "phase_survival/normal");
        NEXT_ADVANCEMENT.put("phase_survival/normal", "phase_survival/hard");
        NEXT_ADVANCEMENT.put("phase_survival/hard", "phase_survival/extreme");

        NEXT_ADVANCEMENT.put("horde_waves", "survive_horde");
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Advancement earnedAdvancement = event.getAdvancement();
        ResourceLocation earnedId = earnedAdvancement.getId();
        String earnedPath = earnedId.getPath();

        QLMZombieMod.LOGGER.info("[QLM Zombie] Player {} earned advancement: {}",
            player.getName().getString(), earnedId);

        String nextPath = NEXT_ADVANCEMENT.get(earnedPath);
        if (nextPath != null) {
            ResourceLocation nextId = ResourceLocation.fromNamespaceAndPath(QLMZombieMod.MOD_ID, nextPath);
            Advancement nextAdvancement = player.server.getAdvancements().getAdvancement(nextId);
            if (nextAdvancement != null) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§a✨ 新的挑战已解锁: " + nextAdvancement.getDisplay().getTitle().getString()),
                    false
                );
            }
        }
    }

    public static void initializeAdvancements(ServerPlayer player) {
        PlayerAdvancements playerAdvancements = player.getAdvancements();

        ResourceLocation rootId = ResourceLocation.fromNamespaceAndPath(QLMZombieMod.MOD_ID, "root");
        Advancement root = player.server.getAdvancements().getAdvancement(rootId);

        if (root != null) {
            AdvancementProgress rootProgress = playerAdvancements.getOrStartProgress(root);
            if (!rootProgress.isDone()) {
                playerAdvancements.award(root, "tick");
                QLMZombieMod.LOGGER.info("[QLM Zombie] Initialized root advancement for player: {}", player.getName().getString());
            }
        }
    }

    public static void awardAdvancement(ServerPlayer player, String advancementId, String criterion) {
        ResourceLocation advancementLoc = ResourceLocation.fromNamespaceAndPath(QLMZombieMod.MOD_ID, advancementId);
        Advancement advancement = player.server.getAdvancements().getAdvancement(advancementLoc);

        if (advancement != null) {
            PlayerAdvancements playerAdvancements = player.getAdvancements();
            AdvancementProgress progress = playerAdvancements.getOrStartProgress(advancement);

            if (!progress.isDone()) {
                playerAdvancements.award(advancement, criterion);
                QLMZombieMod.LOGGER.info("[QLM Zombie] Awarded advancement {} to player {}", advancementId, player.getName().getString());
            }
        }
    }

    public static boolean hasAdvancement(ServerPlayer player, String advancementId) {
        ResourceLocation advancementLoc = ResourceLocation.fromNamespaceAndPath(QLMZombieMod.MOD_ID, advancementId);
        Advancement advancement = player.server.getAdvancements().getAdvancement(advancementLoc);

        if (advancement != null) {
            return player.getAdvancements().getOrStartProgress(advancement).isDone();
        }
        return false;
    }
}
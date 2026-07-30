package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AISelectionHandler {

    private static final ConcurrentHashMap<UUID, UUID> playerSelectedAI = new ConcurrentHashMap<>();

    public static void handleServerSelection(UUID playerUUID, UUID selectedAIUUID) {
        if (playerUUID == null || selectedAIUUID == null) return;
        playerSelectedAI.put(playerUUID, selectedAIUUID);
    }

    public static UUID getSelectedAIUUID(UUID playerUUID) {
        return playerSelectedAI.get(playerUUID);
    }

    public static void clearSelection(UUID playerUUID) {
        playerSelectedAI.remove(playerUUID);
    }

    public static FakePlayerEntity findSelectedAI(Player player, BlockPos pos, double range) {
        UUID selectedUUID = getSelectedAIUUID(player.getUUID());
        if (selectedUUID == null) return null;

        List<FakePlayerEntity> nearbyAI = player.level().getEntitiesOfClass(FakePlayerEntity.class,
                new AABB(
                        pos.getX() - range, pos.getY() - range, pos.getZ() - range,
                        pos.getX() + range, pos.getY() + range, pos.getZ() + range
                ));

        for (FakePlayerEntity ai : nearbyAI) {
            if (ai.getUUID().equals(selectedUUID)) {
                return ai;
            }
        }
        return null;
    }
}
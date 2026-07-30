package com.qlm.zombie.client;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.client.render.FakePlayerEntityRenderer;
import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.entity.QLMEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEventHandler {

    private static final int CYCLE_KEY = GLFW.GLFW_KEY_G;

    private static final ConcurrentHashMap<UUID, Integer> playerSelectedIndex = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, List<UUID>> playerNearbyAIList = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(QLMEntities.FAKE_PLAYER.get(), FakePlayerEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
    }

    @Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, value = Dist.CLIENT)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (event.getKey() == CYCLE_KEY && event.getAction() == GLFW.GLFW_PRESS) {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                if (player == null) return;
                cycleNearbyAIPlayers(player);
            }
        }
    }

    private static void cycleNearbyAIPlayers(LocalPlayer player) {
        BlockPos pos = player.blockPosition();
        double range = 15.0;

        List<FakePlayerEntity> nearbyAI = player.level().getEntitiesOfClass(FakePlayerEntity.class,
                new AABB(
                        pos.getX() - range, pos.getY() - range, pos.getZ() - range,
                        pos.getX() + range, pos.getY() + range, pos.getZ() + range
                ));

        if (nearbyAI.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[QLM] §7附近没有AI玩家"));
            return;
        }

        nearbyAI.sort((a, b) -> {
            double distA = a.distanceToSqr(player);
            double distB = b.distanceToSqr(player);
            return Double.compare(distA, distB);
        });

        UUID playerUUID = player.getUUID();
        List<UUID> aiUUIDs = nearbyAI.stream().map(Entity::getUUID).toList();
        playerNearbyAIList.put(playerUUID, aiUUIDs);

        int currentIndex = playerSelectedIndex.getOrDefault(playerUUID, -1);
        int nextIndex = (currentIndex + 1) % nearbyAI.size();
        playerSelectedIndex.put(playerUUID, nextIndex);

        FakePlayerEntity selected = nearbyAI.get(nextIndex);
        String aiName = selected.getCustomNameStr();

        player.sendSystemMessage(Component.literal("§e[QLM] §7已选择: §a" + aiName + " §7(" + (nextIndex + 1) + "/" + nearbyAI.size() + ")"));

        if (selected.isTamed()) {
            player.sendSystemMessage(Component.literal("§7  已驯服,可以交流 (发送: " + aiName + " <指令>)"));
        } else {
            player.sendSystemMessage(Component.literal("§7  未驯服,无法交流"));
        }
    }

    public static UUID getSelectedAIUUID(UUID playerUUID) {
        List<UUID> uuids = playerNearbyAIList.get(playerUUID);
        Integer index = playerSelectedIndex.get(playerUUID);
        if (uuids == null || index == null || index < 0 || index >= uuids.size()) {
            return null;
        }
        return uuids.get(index);
    }

    public static void clearSelection(UUID playerUUID) {
        playerSelectedIndex.remove(playerUUID);
        playerNearbyAIList.remove(playerUUID);
    }
}
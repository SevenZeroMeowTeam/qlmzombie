package com.qlm.zombie.restriction;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MODID)
public class MobRestrictionHandler {

    @SubscribeEvent
    public static void onEntitySpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        
        if (isBannedMob(entity)) {
            event.setCanceled(true);
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Banned mob spawn prevented: {}", entity.getType().getDescriptionId());
        }

        if (isBannedDimension(event.getLevel())) {
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
                event.setCanceled(true);
            } else {
                net.minecraft.world.entity.player.Player player = (net.minecraft.world.entity.player.Player) entity;
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    net.minecraft.server.level.ServerLevel overworld = serverPlayer.server.getLevel(net.minecraft.server.level.ServerLevel.OVERWORLD);
                    if (overworld != null) {
                        serverPlayer.teleportTo(overworld, player.getX(), 64, player.getZ(), player.getYRot(), player.getXRot());
                    }
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠️ 该维度已被封禁！"), false);
                    QLMZombieMod.LOGGER.info("[QLM Zombie] Player {} attempted to enter banned dimension, teleported back", player.getName().getString());
                }
            }
        }
    }

    private static boolean isBannedMob(Entity entity) {
        return entity.getType() == EntityType.WITCH || 
               entity.getType() == EntityType.SPIDER || 
               entity.getType() == EntityType.CAVE_SPIDER ||
               entity.getType() == EntityType.ENDERMAN;
    }

    private static boolean isBannedDimension(Level level) {
        return level.dimension() == Level.NETHER || level.dimension() == Level.END;
    }
}
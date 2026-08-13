package com.qlm.zombie.restriction;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class MobRestrictionHandler {

    @SubscribeEvent
    public static void onEntitySpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        
        if (isBannedMob(entity)) {
            event.setCanceled(true);
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Banned mob spawn prevented: {}", entity.getType().getDescriptionId());
        }

        if (isBannedDimension(event.getLevel())) {
            if (!(entity instanceof ServerPlayer)) {
                if (!(entity instanceof Player)) {
                    event.setCanceled(true);
                }
            }
            if (entity instanceof ServerPlayer serverPlayer) {
                ServerLevel overworld = serverPlayer.server.getLevel(ServerLevel.OVERWORLD);
                if (overworld != null) {
                    serverPlayer.teleportTo(overworld, serverPlayer.getX(), 64, serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot());
                }
                serverPlayer.displayClientMessage(Component.literal("§c⚠ 该维度已被封禁！无法进入下界/末地！"), false);
                QLMZombieMod.LOGGER.info("[QLM Zombie] Player {} attempted to enter banned dimension, teleported back", serverPlayer.getName().getString());
            } else if (entity instanceof Player player) {
                // 非 ServerPlayer 的玩家实体也阻止
                event.setCanceled(true);
            }
        }
    }

    /** 玩家改变维度时拦截（传送门/指令均无法进入下界/末地） */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        // 如果传送到下界/末地，立即拉回主世界
        if (event.getTo() == Level.NETHER || event.getTo() == Level.END) {
            ServerLevel overworld = player.server.getLevel(ServerLevel.OVERWORLD);
            if (overworld != null) {
                player.teleportTo(overworld, player.getX(), 64, player.getZ(), player.getYRot(), player.getXRot());
            }
            player.displayClientMessage(Component.literal("§c⚠ 下界/末地已被封禁！已强制返回主世界！"), false);
            QLMZombieMod.LOGGER.info("[QLM Zombie] Player {} changed to banned dimension {}, teleported back", player.getName().getString(), event.getTo());
        }
    }

    /** 玩家尝试前往下界/末地时拦截（传送门打开前取消） */
    @SubscribeEvent
    public static void onTravelToDimension(net.minecraftforge.event.entity.EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            if (event.getDimension() == Level.NETHER || event.getDimension() == Level.END) {
                event.setCanceled(true);
                ((ServerPlayer) event.getEntity()).displayClientMessage(Component.literal("§c⚠ 下界/末地已被封禁！无法前往！"), false);
                QLMZombieMod.LOGGER.info("[QLM Zombie] Player {} attempted to travel to banned dimension {}, blocked", event.getEntity().getName().getString(), event.getDimension());
            }
        }
    }

    private static boolean isBannedMob(Entity entity) {
        return entity.getType() == EntityType.WITCH || 
               entity.getType() == EntityType.SPIDER || 
               entity.getType() == EntityType.CAVE_SPIDER ||
               entity.getType() == EntityType.ENDERMAN ||
               entity.getType() == EntityType.CREEPER;
    }

    private static boolean isBannedDimension(Level level) {
        return level.dimension() == Level.NETHER || level.dimension() == Level.END;
    }
}
package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class PlayerHealthHandler {

    private static final Map<UUID, Long> lastHealTime = new HashMap<>();
    private static final double HEALTH_THRESHOLD = 0.1;
    private static final int HEAL_DURATION = 60 * 20;
    private static final int HEAL_LEVEL = 2;
    private static final int COOLDOWN_TICKS = 5 * 60 * 20;

    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        double healthPercent = player.getHealth() / player.getMaxHealth();
        
        if (healthPercent <= HEALTH_THRESHOLD && !player.isDeadOrDying()) {
            long currentTime = player.level().getGameTime();
            Long lastHeal = lastHealTime.get(playerId);
            
            if (lastHeal == null || currentTime - lastHeal >= COOLDOWN_TICKS) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.REGENERATION,
                        HEAL_DURATION,
                        HEAL_LEVEL - 1,
                        false,
                        true,
                        true
                ));
                lastHealTime.put(playerId, currentTime);
                player.displayClientMessage(
                    Component.literal("§c❤ 血量过低！已自动施加 §a生命恢复 II §7(60秒) §c(冷却: 5分钟)"),
                    true
                );
                QLMZombieMod.LOGGER.debug("[QLM Zombie] Player {} health low ({}%), giving Regeneration {} for {}s",
                        player.getName().getString(),
                        Math.round(healthPercent * 100),
                        HEAL_LEVEL,
                        HEAL_DURATION / 20);
            }
        }
    }
}
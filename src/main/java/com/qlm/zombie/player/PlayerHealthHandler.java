package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class PlayerHealthHandler {

    private static final double MAX_HEALTH = 200.0;
    private static final UUID HEALTH_BOOST_UUID = UUID.fromString("ffffffff-0001-4000-8000-000000000001");

    private static final Map<UUID, Long> lastHealTime = new HashMap<>();
    private static final double HEALTH_THRESHOLD = 0.05; // 5%
    private static final int HEAL_DURATION = 60 * 20; // 60秒
    private static final int HEAL_LEVEL = 3; // 再生 III（更快治疗）
    private static final int COOLDOWN_TICKS = 5 * 60 * 20; // 5分钟冷却

    /** 玩家登录/重生时设置最大血量 200 */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getLevel().isClientSide()) return;

        setMaxHealth(player);
    }

    /** 玩家重生时保持血量 200 */
    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            setMaxHealth(player);
        }
    }

    private static void setMaxHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;

        // 移除旧的 modifier
        if (attr.getModifier(HEALTH_BOOST_UUID) != null) {
            attr.removeModifier(HEALTH_BOOST_UUID);
        }

        // 添加新的 modifier：玩家基础 20 血 + 180 = 200
        AttributeModifier modifier = new AttributeModifier(
                HEALTH_BOOST_UUID,
                "QLM Health Boost",
                MAX_HEALTH - 20.0,
                AttributeModifier.Operation.ADDITION
        );
        attr.addPermanentModifier(modifier);

        // 仅在生命低于目标上限时补满，绝不强制扣减高于上限的生命
        // （旧代码每 tick 强制 setHealth(200)，会把击杀奖励堆起来的
        //   更高生命上限直接扣到 200 —— 这就是"生命上限还在扣血"）
        if (player.getHealth() < MAX_HEALTH) {
            player.setHealth((float) MAX_HEALTH);
        }

        QLMZombieMod.LOGGER.debug("[QLM Zombie] Player {} max health set to {}", player.getName().getString(), MAX_HEALTH);
    }

    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 确保血量上限：仅在加成缺失时重新应用
        // （旧代码用 attr.getBaseValue() < MAX_HEALTH 判断 —— 基础值恒为 20 < 200，
        //   导致每 tick 移除+重加 modifier 并强制回血，造成血量上限闪烁/扣血；
        //   现在改为检测 modifier 是否仍存在，缺失才重新注入）
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null && attr.getModifier(HEALTH_BOOST_UUID) == null) {
            setMaxHealth(player);
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
                    Component.literal("§c❤ 血量过低！已自动施加 §a生命恢复 III §7(60秒) §c(冷却: 5分钟)"),
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
/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * 功能：击杀任何生物随机增加玩家攻击力或血量上限
 * 原理：监听 LivingDeathEvent，任何生物被玩家击杀时有概率获得永久属性加成
 *       通过 AttributeModifier 修改玩家最大生命值/攻击力，NBT 持久化存储
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KillBonusFeature {

    private KillBonusFeature() {}

    // AttributeModifier 的唯一 UUID（必须固定，用于移除旧修饰符后重新添加）
    private static final UUID HEALTH_BONUS_UUID = UUID.fromString("a3d2b4e1-7c8f-4a2b-9e6d-1f3a5c7b9d0e");
    private static final UUID ATTACK_BONUS_UUID = UUID.fromString("b4e3c5f2-8d9f-5b3c-af7e-2f4b6d8c0e1f");

    // NBT 键名
    private static final String NKEY_HEALTH_BONUS = "qlmzombie.kill_bonus.health";
    private static final String NKEY_ATTACK_BONUS = "qlmzombie.kill_bonus.attack";

    // 配置参数
    private static final double BONUS_CHANCE = 0.01;       // 1% 概率触发
    private static final double HEALTH_PER_KILL = 2.0;     // +1 颗心（2 点生命）
    private static final double ATTACK_PER_KILL = 1.0;     // +1 点攻击力
    private static final double HEALTH_CAP = 1024.0;        // 生命上限 +1024
    private static final double ATTACK_CAP = 1024.0;        // 攻击上限 +1024

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        // 排除玩家击杀玩家
        if (target instanceof Player) return;

        // 击杀者必须是玩家
        if (!(target.getLastDamageSource() != null
                && target.getLastDamageSource().getEntity() instanceof Player)) return;

        Player player = (Player) target.getLastDamageSource().getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            tryRollBonus(serverPlayer);
        }
    }

    /**
     * 掷骰子决定是否给予击杀奖励。
     */
    private static void tryRollBonus(ServerPlayer player) {
        if (player.getRandom().nextDouble() >= BONUS_CHANCE) return;

        CompoundTag persistentData = player.getPersistentData();
        double currentHealth = persistentData.getDouble(NKEY_HEALTH_BONUS);
        double currentAttack = persistentData.getDouble(NKEY_ATTACK_BONUS);

        boolean healthMaxed = currentHealth >= HEALTH_CAP;
        boolean attackMaxed = currentAttack >= ATTACK_CAP;
        if (healthMaxed && attackMaxed) return; // 两项都满级

        // 50/50 随机选择（如果某项已满则给另一项）
        boolean giveHealth;
        if (healthMaxed) {
            giveHealth = false;
        } else if (attackMaxed) {
            giveHealth = true;
        } else {
            giveHealth = player.getRandom().nextBoolean();
        }

        if (giveHealth) {
            double newTotal = Math.min(currentHealth + HEALTH_PER_KILL, HEALTH_CAP);
            persistentData.putDouble(NKEY_HEALTH_BONUS, newTotal);
            applyHealthBonus(player, newTotal);
            sendBonusMessage(player, true, newTotal);
        } else {
            double newTotal = Math.min(currentAttack + ATTACK_PER_KILL, ATTACK_CAP);
            persistentData.putDouble(NKEY_ATTACK_BONUS, newTotal);
            applyAttackBonus(player, newTotal);
            sendBonusMessage(player, false, newTotal);
        }
    }

    /**
     * 应用最大生命值加成到玩家属性。
     */
    private static void applyHealthBonus(Player player, double amount) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(HEALTH_BONUS_UUID);
        if (amount > 0) {
            attr.addTransientModifier(new AttributeModifier(
                    HEALTH_BONUS_UUID, "qlmzombie.kill_bonus.health",
                    amount, AttributeModifier.Operation.ADDITION));
        }
        // 如果当前生命值低于新的上限，补满
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * 应用攻击力加成到玩家属性。
     */
    private static void applyAttackBonus(Player player, double amount) {
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) return;
        attr.removeModifier(ATTACK_BONUS_UUID);
        if (amount > 0) {
            attr.addTransientModifier(new AttributeModifier(
                    ATTACK_BONUS_UUID, "qlmzombie.kill_bonus.attack",
                    amount, AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * 发送奖励提示到玩家动作栏。
     */
    private static void sendBonusMessage(ServerPlayer player, boolean isHealth, double totalBonus) {
        MutableComponent msg;
        if (isHealth) {
            int hearts = (int) (totalBonus / 2.0);
            msg = Component.literal("击杀奖励！最大生命值 +" + (int) (HEALTH_PER_KILL / 2) + " ❤")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" (累计 +" + hearts + " ❤)").withStyle(ChatFormatting.GRAY));
        } else {
            msg = Component.literal("击杀奖励！攻击力 +" + (int) ATTACK_PER_KILL + " ⚔")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" (累计 +" + (int) totalBonus + " ⚔)").withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(msg);
    }

    // ── 持久化：玩家死亡/重生后保留加成 ──

    /**
     * 玩家重生 / 从末地返回时复制 NBT 数据到新实体。
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();
        newData.putDouble(NKEY_HEALTH_BONUS, oldData.getDouble(NKEY_HEALTH_BONUS));
        newData.putDouble(NKEY_ATTACK_BONUS, oldData.getDouble(NKEY_ATTACK_BONUS));
    }

    /**
     * 玩家登录时重新应用属性加成。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();
            applyHealthBonus(player, data.getDouble(NKEY_HEALTH_BONUS));
            applyAttackBonus(player, data.getDouble(NKEY_ATTACK_BONUS));
        }
    }

    /**
     * 玩家重生后重新应用属性加成。
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();
            applyHealthBonus(player, data.getDouble(NKEY_HEALTH_BONUS));
            applyAttackBonus(player, data.getDouble(NKEY_ATTACK_BONUS));
        }
    }
}

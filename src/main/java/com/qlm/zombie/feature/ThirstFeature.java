/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * 功能：口渴模式（基于 Thirst-Mod 架构重写）
 * 原理：
 *   1. 玩家有口渴值（0~20）和消耗值（exhaustion），采用原版食物的衰减模型
 *   2. 活动（跑动/跳跃/挖掘）积累 exhaustion，满了增加口渴值
 *   3. 口渴值归零时扣血（类似饥饿伤害）
 *   4. 原版水瓶不能直接饮用，需熔炉烧制为纯净水
 *   5. 饮用纯净水恢复口渴值并返还空瓶
 * 参考：https://github.com/ghen-git/Thirst-Mod/tree/1.20.1
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.item.QLMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ThirstFeature {

    private ThirstFeature() {}

    // NBT 键
    private static final String NKEY_THIRST = "qlmzombie.thirst";
    private static final String NKEY_EXHAUSTION = "qlmzombie.exhaustion";
    private static final String NKEY_DAMAGE_TIMER = "qlmzombie.damage_timer";

    // 配置参数
    private static final int MAX_THIRST = 20;           // 最大口渴值（参考原版饥饿值）
    private static final float EXHAUSTION_THRESHOLD = 4.0f; // exhaustion 累积到 4 时 +1 口渴
    private static final int DAMAGE_THRESHOLD = 0;         // 口渴值 = 0 时开始扣血
    private static final int THIRST_PER_PURIFIED = 8;      // 喝一瓶纯净水恢复 8 点口渴（相当于 4 颗心）
    private static final float EXHAUSTION_PER_PURIFIED = 3.0f; // 喝一瓶纯净水增加 3.0 exhaustion

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            tick(player);
        }
    }

    /**
     * 核心 tick 逻辑（参考 Thirst-Mod PlayerThirst.tick 实现）。
     */
    private static void tick(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int thirst = data.getInt(NKEY_THIRST);
        float exhaustion = data.getFloat(NKEY_EXHAUSTION);
        int damageTimer = data.getInt(NKEY_DAMAGE_TIMER);

        // 创造模式：重置口渴值为满值
        if (player.getAbilities().instabuild) {
            if (thirst < MAX_THIRST) {
                setThirst(player, MAX_THIRST);
                data.putFloat(NKEY_EXHAUSTION, 0.0f);
            }
            return;
        }

        // 旁观者模式跳过
        if (player.isSpectator()) return;

        // === 消耗值转口渴值 ===
        if (exhaustion > EXHAUSTION_THRESHOLD) {
            exhaustion -= EXHAUSTION_THRESHOLD;
            if (thirst > DAMAGE_THRESHOLD) {
                thirst = Math.max(thirst - 1, DAMAGE_THRESHOLD);
                data.putInt(NKEY_THIRST, thirst);
            }
        }

        // === 口渴归零扣血 ===
        if (thirst <= DAMAGE_THRESHOLD) {
            damageTimer++;
            if (damageTimer >= 40) { // 2 秒一次
                float difficultyDamage = 1.0f;
                if (player.getHealth() > 10.0f || difficultyDamage < 1.0f || player.getHealth() > 0) {
                    player.hurt(player.damageSources().starve(), difficultyDamage);
                }
                damageTimer = 0;
            }
            data.putInt(NKEY_DAMAGE_TIMER, damageTimer);
        } else if (damageTimer > 0) {
            damageTimer = 0;
            data.putInt(NKEY_DAMAGE_TIMER, 0);
        }

        // === 特殊：雨天仰望天空自动补水 ===
        if (player.level().isRaining() && !player.level().isDay() == false) {
            if (player.level().canSeeSky(player.blockPosition())) {
                // 每 120 tick（6 秒）回 1 点口渴
                if (player.tickCount % 120 == 0 && thirst < MAX_THIRST) {
                    thirst = Math.min(thirst + 1, MAX_THIRST);
                    data.putInt(NKEY_THIRST, thirst);
                }
            }
        }

        data.putFloat(NKEY_EXHAUSTION, exhaustion);

        // === 移除 ThirstWasTaken mod 施加的挖掘疲劳/缓慢 ===
        // 每 40 tick（2秒）检查一次，减少性能开销
        if (player.tickCount % 40 == 0) {
            removeThirstDebuffs(player);
        }
    }

    /**
     * 仅在玩家不口渴时移除 ThirstWasTaken 施加的挖掘疲劳/缓慢。
     * 口渴值 <= 6（严重缺水）时保留 debuff，让玩家感受到口渴惩罚。
     * 口渴值 > 6 时清除 debuff，避免非口渴状态下被施加效果。
     */
    private static void removeThirstDebuffs(ServerPlayer player) {
        int thirst = player.getPersistentData().getInt(NKEY_THIRST);
        if (thirst > 6) {
            // 不口渴：清除 ThirstWasTaken 的短时长低放大器 debuff
            MobEffectInstance digFatigue = player.getEffect(MobEffects.DIG_SLOWDOWN);
            if (digFatigue != null && digFatigue.getDuration() <= 200 && digFatigue.getAmplifier() <= 1) {
                player.removeEffect(MobEffects.DIG_SLOWDOWN);
            }
            MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (slowness != null && slowness.getDuration() <= 200 && slowness.getAmplifier() <= 1) {
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
    }

    /**
     * 拦截原版水瓶饮用：禁止直接喝生水。
     */
    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack item = event.getItem();
        if (item.is(Items.GLASS_BOTTLE) && PotionUtils.getPotion(item) == Potions.WATER) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("生水中有细菌，需要熔炉加热净化后才能饮用！")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * 纯净水饮用完成：恢复口渴值 + 返还空瓶。
     */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack item = event.getItem();
        if (item.is(QLMItems.PURIFIED_WATER_BOTTLE.get())) {
            drinkPurifiedWater(serverPlayer);
        }
    }

    /**
     * 饮用纯净水的核心逻辑。
     */
    public static void drinkPurifiedWater(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int thirst = data.getInt(NKEY_THIRST);
        float exhaustion = data.getFloat(NKEY_EXHAUSTION);

        // 扣除口渴值
        int newThirst = Math.max(thirst - THIRST_PER_PURIFIED, 0);
        data.putInt(NKEY_THIRST, newThirst);

        // 扣除消耗值（防滥用）
        data.putFloat(NKEY_EXHAUSTION, Math.max(0, exhaustion - EXHAUSTION_PER_PURIFIED));

        player.sendSystemMessage(Component.literal("咕噜咕噜，解渴了！口渴值 -" + THIRST_PER_PURIFIED)
                .withStyle(ChatFormatting.AQUA));
    }

    // ── 口渴值 API ──

    public static int getThirst(Player player) {
        return player.getPersistentData().getInt(NKEY_THIRST);
    }

    public static void setThirst(ServerPlayer player, int value) {
        player.getPersistentData().putInt(NKEY_THIRST, Math.max(0, Math.min(MAX_THIRST, value)));
    }

    public static float getExhaustion(Player player) {
        return player.getPersistentData().getFloat(NKEY_EXHAUSTION);
    }

    public static void addExhaustion(ServerPlayer player, float amount) {
        CompoundTag data = player.getPersistentData();
        float current = data.getFloat(NKEY_EXHAUSTION);
        data.putFloat(NKEY_EXHAUSTION, current + amount);
    }

    public static int getMaxThirst() {
        return MAX_THIRST;
    }

    // ── 持久化：玩家数据在死亡/重生/登录后保留 ──

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();
        newData.putInt(NKEY_THIRST, oldData.getInt(NKEY_THIRST));
        newData.putFloat(NKEY_EXHAUSTION, oldData.getFloat(NKEY_EXHAUSTION));
        newData.putInt(NKEY_DAMAGE_TIMER, oldData.getInt(NKEY_DAMAGE_TIMER));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();
            if (!data.contains(NKEY_THIRST)) {
                data.putInt(NKEY_THIRST, MAX_THIRST);
            }
            if (!data.contains(NKEY_EXHAUSTION)) {
                data.putFloat(NKEY_EXHAUSTION, 0.0f);
            }
            if (!data.contains(NKEY_DAMAGE_TIMER)) {
                data.putInt(NKEY_DAMAGE_TIMER, 0);
            }
            // 登录时立即清除 ThirstWasTaken 施加的挖掘疲劳/缓慢
            removeThirstDebuffs(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();
            if (!data.contains(NKEY_THIRST)) {
                data.putInt(NKEY_THIRST, MAX_THIRST);
            }
            if (!data.contains(NKEY_EXHAUSTION)) {
                data.putFloat(NKEY_EXHAUSTION, 0.0f);
            }
            if (!data.contains(NKEY_DAMAGE_TIMER)) {
                data.putInt(NKEY_DAMAGE_TIMER, 0);
            }
        }
    }
}

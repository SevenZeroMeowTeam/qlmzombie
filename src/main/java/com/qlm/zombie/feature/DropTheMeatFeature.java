/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Original design inspired by DropTheMeat (https://github.com/Fuzss/dropthemeat)
 *   Copyright (c) Fuzss. Licensed under MIT.
 *   This is an ORIGINAL Forge 1.20.1 implementation, NO code copied.
 * ----------------------------------------------------------------------------
 * 功能：动物死亡时额外掉落生肉，亡灵几率额外掉落腐肉
 */
package com.qlm.zombie.feature;

import com.qlm.zombie.QLMZombieMod;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DropTheMeatFeature {

    private static final Random RAND = new Random();

    private DropTheMeatFeature() {}

    /**
     * 由 Global Loot Modifier 调用（或直接 LivingDeathEvent 钩子）。
     * 动物：额外掉落 0~2 块对应的生肉；亡灵：35% 额外掉落 0~1 腐肉。
     */
    public static ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext ctx) {
        if (!ctx.hasParam(LootContextParams.THIS_ENTITY)) return generatedLoot;
        if (!(ctx.getParam(LootContextParams.THIS_ENTITY) instanceof LivingEntity victim)) return generatedLoot;

        // 动物：掉落生肉 x2~x5
        if (victim instanceof Animal) {
            Item meatItem = getMeatForAnimal(victim.getType());
            if (meatItem != null) {
                int extra = 2 + RAND.nextInt(4); // 2~5
                for (int i = 0; i < extra; i++) {
                    generatedLoot.add(new ItemStack(meatItem));
                }
            }
        }

        // 亡灵怪物：几率掉落腐肉
        if (victim instanceof Monster && isUndead(victim)) {
            if (RAND.nextFloat() < 0.5f) {
                int extra = 1 + RAND.nextInt(2); // 1~2
                for (int i = 0; i < extra; i++) {
                    generatedLoot.add(new ItemStack(Items.ROTTEN_FLESH));
                }
            }
        }

        return generatedLoot;
    }

    /** 获取动物对应的生肉 */
    private static Item getMeatForAnimal(EntityType<?> type) {
        String key = ForgeRegistries.ENTITY_TYPES.getKey(type).toString();
        if (key.contains("cow") || key.contains("mooshroom")) return Items.BEEF;
        if (key.contains("pig") && !key.contains("zombie")) return Items.PORKCHOP;
        if (key.contains("chicken")) return Items.CHICKEN;
        if (key.contains("sheep")) return Items.MUTTON;
        if (key.contains("rabbit")) return Items.RABBIT;
        if (key.contains("horse") || key.contains("mule") || key.contains("donkey")) return Items.LEATHER;
        if (key.contains("llama")) return Items.LEATHER;
        if (key.contains("cod")) return Items.COD;
        if (key.contains("salmon")) return Items.SALMON;
        if (key.contains("squid")) return Items.INK_SAC;
        if (key.contains("goat")) return Items.MUTTON;
        return null; // 未知动物不附加，避免乱给
    }

    private static boolean isUndead(LivingEntity e) {
        return e.isInvertedHealAndHarm(); // 治愈药水伤害 = 亡灵
    }
}

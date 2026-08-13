/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *   Inspired by TouhouLittleMaid (https://github.com/TartaricAcid/TouhouLittleMaid)
 *   item classification and tool switching pattern.
 *   This is an ORIGINAL implementation for Forge 1.20.1.
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai;

import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 装备助手 — 参考 TouhouLittleMaid 的物品分类和工具切换模式
 *
 * 功能：
 * 1. 通过 Forge Tags 识别工具类型（支持所有模组的工具/武器/盔甲）
 * 2. 从 AI 背包中查找最适合的工具并装备到主手
 * 3. 自动装备更好的盔甲/武器
 */
public final class EquipmentHelper {

    private EquipmentHelper() {}

    // === 工具类型识别（使用 Item 类型检查，兼容所有模组） ===

    public static boolean isPickaxe(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PickaxeItem;
    }

    public static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof AxeItem;
    }

    public static boolean isShovel(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ShovelItem;
    }

    public static boolean isHoe(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof HoeItem;
    }

    public static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem;
    }

    public static boolean isBow(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem);
    }

    public static boolean isWeapon(ItemStack stack) {
        return isSword(stack) || isBow(stack);
    }

    public static boolean isTool(ItemStack stack) {
        return isPickaxe(stack) || isAxe(stack) || isShovel(stack) || isHoe(stack);
    }

    // === 盔甲类型识别（使用 Mob.getEquipmentSlotForItem，兼容所有模组） ===

    public static boolean isHelmet(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem &&
               Mob.getEquipmentSlotForItem(stack) == EquipmentSlot.HEAD;
    }

    public static boolean isChestplate(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem &&
               Mob.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST;
    }

    public static boolean isLeggings(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem &&
               Mob.getEquipmentSlotForItem(stack) == EquipmentSlot.LEGS;
    }

    public static boolean isBoots(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem &&
               Mob.getEquipmentSlotForItem(stack) == EquipmentSlot.FEET;
    }

    public static boolean isArmor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem;
    }

    /** 获取盔甲对应的装备槽 */
    @Nullable
    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (!isArmor(stack)) return null;
        return Mob.getEquipmentSlotForItem(stack);
    }

    // === 工具切换（参考 TLM 根据任务模式切换主手工具） ===

    /**
     * 确保 AI 主手持有指定类型的工具
     * @param toolType "pickaxe", "axe", "shovel", "hoe", "sword", "bow"
     * @return true 如果已装备或成功切换
     */
    public static boolean ensureToolEquipped(FakePlayerEntity ai, String toolType) {
        ItemStack mainHand = ai.getMainHandItem();

        // 已持有正确工具
        if (isCorrectTool(mainHand, toolType)) {
            return true;
        }

        // 从背包查找最好的工具
        ItemStack bestTool = findBestToolInContainer(ai.getInventory(), toolType);
        if (bestTool.isEmpty()) {
            return false;
        }

        // 切换：当前主手物品放回背包，取出工具到主手
        int slot = findItemSlot(ai.getInventory(), bestTool);
        if (slot >= 0) {
            ai.getInventory().setItem(slot, mainHand.copy());
            ai.setItemSlot(EquipmentSlot.MAINHAND, bestTool.copy());
            ai.getInventory().getItem(slot).setCount(mainHand.getCount());
            return true;
        }
        return false;
    }

    private static boolean isCorrectTool(ItemStack stack, String toolType) {
        if (stack.isEmpty()) return false;
        switch (toolType) {
            case "pickaxe": return isPickaxe(stack);
            case "axe": return isAxe(stack);
            case "shovel": return isShovel(stack);
            case "hoe": return isHoe(stack);
            case "sword": return isSword(stack);
            case "bow": return isBow(stack);
            default: return false;
        }
    }

    /** 从容器中查找最好的指定类型工具 */
    public static ItemStack findBestToolInContainer(Container container, String toolType) {
        ItemStack best = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (isCorrectTool(stack, toolType)) {
                if (best.isEmpty() || isBetterTool(stack, best)) {
                    best = stack;
                }
            }
        }
        return best;
    }

    /** 比较工具优劣：Tier 等级高的优先 */
    public static boolean isBetterTool(ItemStack candidate, ItemStack current) {
        if (current.isEmpty()) return true;
        if (candidate.isEmpty()) return false;

        // TieredItem 比较等级
        if (candidate.getItem() instanceof TieredItem && current.getItem() instanceof TieredItem) {
            int candidateLevel = getTierLevel(candidate);
            int currentLevel = getTierLevel(current);
            return candidateLevel > currentLevel;
        }

        // 非 TieredItem（如模组武器）比较攻击伤害
        float candidateDamage = getAttackDamage(candidate);
        float currentDamage = getAttackDamage(current);
        return candidateDamage > currentDamage;
    }

    @SuppressWarnings("deprecation")
    private static int getTierLevel(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tiered) {
            return tiered.getTier().getLevel();
        }
        return 0;
    }

    private static float getAttackDamage(ItemStack stack) {
        // 使用属性获取攻击伤害（兼容模组武器）
        return (float) stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                .stream()
                .mapToDouble(m -> m.getAmount())
                .sum();
    }

    private static int findItemSlot(Container container, ItemStack target) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i) == target) {
                return i;
            }
        }
        return -1;
    }

    // === 自动装备盔甲/武器（参考 TLM 自动装备更好的装备） ===

    /**
     * 尝试将物品装备到 AI 身上
     * @return true 如果成功装备
     */
    public static boolean tryEquipItem(FakePlayerEntity ai, ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 盔甲装备
        EquipmentSlot armorSlot = getArmorSlot(stack);
        if (armorSlot != null) {
            ItemStack current = ai.getItemBySlot(armorSlot);
            if (isBetterArmor(stack, current)) {
                // 当前盔甲放回背包
                if (!current.isEmpty() && !addItemToInventory(ai.getInventory(), current)) {
                    return false; // 背包满了
                }
                ai.setItemSlot(armorSlot, stack.copy());
                return true;
            }
        }

        // 武器装备
        if (isWeapon(stack)) {
            ItemStack currentWeapon = ai.getMainHandItem();
            if (isBetterWeapon(stack, currentWeapon)) {
                if (!currentWeapon.isEmpty() && !addItemToInventory(ai.getInventory(), currentWeapon)) {
                    return false;
                }
                ai.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                return true;
            }
        }

        return false;
    }

    /** 比较盔甲优劣 */
    public static boolean isBetterArmor(ItemStack candidate, ItemStack current) {
        if (current.isEmpty()) return true;
        if (candidate.isEmpty()) return false;

        if (candidate.getItem() instanceof ArmorItem candidateArmor &&
            current.getItem() instanceof ArmorItem currentArmor) {
            if (candidateArmor.getDefense() != currentArmor.getDefense()) {
                return candidateArmor.getDefense() > currentArmor.getDefense();
            }
            return candidateArmor.getToughness() > currentArmor.getToughness();
        }
        return false;
    }

    /** 比较武器优劣 */
    public static boolean isBetterWeapon(ItemStack candidate, ItemStack current) {
        if (current.isEmpty()) return true;
        if (candidate.isEmpty()) return false;
        return isBetterTool(candidate, current);
    }

    /** 尝试将物品添加到容器，返回是否成功 */
    public static boolean addItemToInventory(Container container, ItemStack stack) {
        if (stack.isEmpty()) return true;
        // 先尝试堆叠到已有槽位
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack existing = container.getItem(i);
            if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int canAdd = existing.getMaxStackSize() - existing.getCount();
                int toAdd = Math.min(canAdd, stack.getCount());
                existing.grow(toAdd);
                stack.shrink(toAdd);
                if (stack.isEmpty()) return true;
            }
        }
        // 再找空槽位
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, stack.copy());
                stack.setCount(0);
                return true;
            }
        }
        return false;
    }

    /** 获取物品的中文类型描述 */
    public static String getItemTypeDesc(ItemStack stack) {
        if (isPickaxe(stack)) return "镐";
        if (isAxe(stack)) return "斧";
        if (isShovel(stack)) return "铲";
        if (isHoe(stack)) return "锄";
        if (isSword(stack)) return "剑";
        if (isBow(stack)) return "弓";
        if (isHelmet(stack)) return "头盔";
        if (isChestplate(stack)) return "胸甲";
        if (isLeggings(stack)) return "护腿";
        if (isBoots(stack)) return "靴子";
        return "";
    }
}

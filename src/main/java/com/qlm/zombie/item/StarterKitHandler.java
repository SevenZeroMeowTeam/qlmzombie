package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 玩家初始装备给予系统：
 *  - 首次登录时给予全套铁质装备（已正确附魔）
 *  - 武器/工具：铁剑、铁斧、铁镐、铁锹、铁锄（随机 5 种附魔）
 *  - 弓 + 64 支箭（弓的全部附魔）
 *  - 全套铁盔甲（每件随机 5 种附魔）
 *  - 64 个附魔金苹果
 *  - 64 个面包
 *  - 装备自动正确穿戴（头盔→头、胸甲→胸、护腿→腿、靴子→脚）
 *  - 后续登录不再发放
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class StarterKitHandler {

    public static final String NBT_RECEIVED = "qlm_starter_kit_received";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        CompoundTag persistent = player.getPersistentData();
        if (persistent.getBoolean(NBT_RECEIVED)) return;

        giveStarterKit(player);
        persistent.putBoolean(NBT_RECEIVED, true);

        player.sendSystemMessage(Component.empty()
                .append(Component.literal("[七零喵] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("已发放初始装备！请查收物品栏。").withStyle(ChatFormatting.GREEN)));
        player.sendSystemMessage(Component.empty()
                .append(Component.literal("[七零喵] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("铁剑攻击 999 / 铁斧攻击 55 / 铁镐攻击 44，全装备附魔 + 弓满附魔 + 64 附魔金苹果 + 64 面包")
                        .withStyle(ChatFormatting.AQUA)));
    }

    private static void giveStarterKit(ServerPlayer player) {
        Random rnd = new Random();

        // ==================== 武器 / 工具（铁质统一·无限耐久） ====================
        ItemStack ironSword = makeUnbreakable(new ItemStack(Items.IRON_SWORD));
        ItemStack ironAxe = makeUnbreakable(new ItemStack(Items.IRON_AXE));
        ItemStack ironPickaxe = makeUnbreakable(new ItemStack(Items.IRON_PICKAXE));
        ItemStack ironShovel = makeUnbreakable(new ItemStack(Items.IRON_SHOVEL));
        ItemStack ironHoe = makeUnbreakable(new ItemStack(Items.IRON_HOE));
        applyRandomEnchantments(ironSword, 5, rnd, EnchantmentTarget.WEAPON);
        applyRandomEnchantments(ironAxe, 5, rnd, EnchantmentTarget.WEAPON);
        applyRandomEnchantments(ironPickaxe, 5, rnd, EnchantmentTarget.TOOL);
        applyRandomEnchantments(ironShovel, 5, rnd, EnchantmentTarget.TOOL);
        applyRandomEnchantments(ironHoe, 5, rnd, EnchantmentTarget.TOOL);

        // ==================== 弓 + 全部附魔 + 64 支箭（无限耐久） ====================
        ItemStack bow = makeUnbreakable(new ItemStack(Items.BOW));
        applyAllBowEnchantments(bow);
        ItemStack arrows = new ItemStack(Items.ARROW, 64);

        // ==================== 全套铁盔甲（每件 5 附魔·无限耐久） ====================
        ItemStack helmet  = makeUnbreakable(new ItemStack(Items.IRON_HELMET));
        ItemStack chest   = makeUnbreakable(new ItemStack(Items.IRON_CHESTPLATE));
        ItemStack leggings= makeUnbreakable(new ItemStack(Items.IRON_LEGGINGS));
        ItemStack boots   = makeUnbreakable(new ItemStack(Items.IRON_BOOTS));
        applyRandomEnchantments(helmet,  5, rnd, EnchantmentTarget.ARMOR_HELMET);
        applyRandomEnchantments(chest,   5, rnd, EnchantmentTarget.ARMOR_CHEST);
        applyRandomEnchantments(leggings,5, rnd, EnchantmentTarget.ARMOR_LEGS);
        applyRandomEnchantments(boots,   5, rnd, EnchantmentTarget.ARMOR_BOOTS);

        // ==================== 消耗品 ====================
        ItemStack enchantedGoldenApples = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64);
        ItemStack bread = new ItemStack(Items.BREAD, 64);

        // ==================== 自动正确穿戴盔甲 ====================
        equipArmor(player, EquipmentSlot.HEAD, helmet);
        equipArmor(player, EquipmentSlot.CHEST, chest);
        equipArmor(player, EquipmentSlot.LEGS, leggings);
        equipArmor(player, EquipmentSlot.FEET, boots);

        // ==================== 其余物品放入背包 ====================
        List<ItemStack> items = new ArrayList<>();
        items.add(ironSword);
        items.add(ironAxe);
        items.add(ironPickaxe);
        items.add(ironShovel);
        items.add(ironHoe);
        items.add(bow);
        items.add(arrows);
        items.add(enchantedGoldenApples);
        items.add(bread);

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.inventoryMenu.broadcastChanges();
        player.getInventory().setChanged();
    }

    /** 安全穿戴盔甲：若槽位空则直接穿上，否则放入背包 */
    private static void equipArmor(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        ItemStack current = player.getItemBySlot(slot);
        if (current.isEmpty()) {
            player.setItemSlot(slot, stack);
        } else {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** 设置物品为无限耐久（Unbreakable） */
    private static ItemStack makeUnbreakable(ItemStack stack) {
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        // HideFlags 位掩码：1=附魔列表 4=Unbreakable标签，合并隐藏
        stack.getOrCreateTag().putInt("HideFlags", 1 | 4);
        return stack;
    }

    // ==================== 附魔类型分类 ====================
    private enum EnchantmentTarget {
        WEAPON, TOOL, ARMOR_HELMET, ARMOR_CHEST, ARMOR_LEGS, ARMOR_BOOTS, BOW
    }

    /** 随机挑选 N 个附魔并应用 */
    private static void applyRandomEnchantments(ItemStack stack, int count, Random rnd, EnchantmentTarget target) {
        List<Enchantment> pool = getEnchantmentPool(target);
        if (pool.isEmpty()) return;

        Collections.shuffle(pool, rnd);
        int applied = 0;
        for (Enchantment e : pool) {
            if (applied >= count) break;
            try {
                if (!e.canEnchant(stack)) continue;
                int level = 1 + rnd.nextInt(Math.max(1, e.getMaxLevel()));
                if (level > e.getMaxLevel()) level = e.getMaxLevel();
                stack.enchant(e, level);
                applied++;
            } catch (Throwable ignored) {}
        }
    }

    /** 弓的全部附魔 */
    private static void applyAllBowEnchantments(ItemStack bow) {
        tryEnchant(bow, "minecraft:power", 5);
        tryEnchant(bow, "minecraft:punch", 2);
        tryEnchant(bow, "minecraft:flame", 1);
        tryEnchant(bow, "minecraft:infinity", 1);
        tryEnchant(bow, "minecraft:unbreaking", 3);
        tryEnchant(bow, "minecraft:mending", 1);
        tryEnchant(bow, "minecraft:vanishing_curse", 1);
    }

    private static void tryEnchant(ItemStack stack, String id, int level) {
        Enchantment e = resolve(id);
        if (e == null) return;
        try {
            if (e.canEnchant(stack)) stack.enchant(e, level);
        } catch (Throwable ignored) {}
    }

    private static Enchantment resolve(String id) {
        return ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(id));
    }

    /** 获取附魔池（按目标分类） */
    private static List<Enchantment> getEnchantmentPool(EnchantmentTarget target) {
        List<Enchantment> list = new ArrayList<>();
        switch (target) {
            case WEAPON -> {
                addSafe(list, "minecraft:sharpness");
                addSafe(list, "minecraft:smite");
                addSafe(list, "minecraft:bane_of_arthropods");
                addSafe(list, "minecraft:knockback");
                addSafe(list, "minecraft:fire_aspect");
                addSafe(list, "minecraft:looting");
                addSafe(list, "minecraft:sweeping_edge");
                addSafe(list, "minecraft:unbreaking");
                addSafe(list, "minecraft:mending");
            }
            case TOOL -> {
                addSafe(list, "minecraft:efficiency");
                addSafe(list, "minecraft:silk_touch");
                addSafe(list, "minecraft:fortune");
                addSafe(list, "minecraft:unbreaking");
                addSafe(list, "minecraft:mending");
            }
            case ARMOR_HELMET -> {
                addSafe(list, "minecraft:protection");
                addSafe(list, "minecraft:fire_protection");
                addSafe(list, "minecraft:blast_protection");
                addSafe(list, "minecraft:projectile_protection");
                addSafe(list, "minecraft:respiration");
                addSafe(list, "minecraft:aqua_affinity");
                addSafe(list, "minecraft:thorns");
                addSafe(list, "minecraft:unbreaking");
                addSafe(list, "minecraft:mending");
            }
            case ARMOR_CHEST -> {
                addSafe(list, "minecraft:protection");
                addSafe(list, "minecraft:fire_protection");
                addSafe(list, "minecraft:blast_protection");
                addSafe(list, "minecraft:projectile_protection");
                addSafe(list, "minecraft:thorns");
                addSafe(list, "minecraft:unbreaking");
                addSafe(list, "minecraft:mending");
            }
            case ARMOR_LEGS -> {
                addSafe(list, "minecraft:protection");
                addSafe(list, "minecraft:fire_protection");
                addSafe(list, "minecraft:blast_protection");
                addSafe(list, "minecraft:projectile_protection");
                addSafe(list, "minecraft:swift_sneak");
                addSafe(list, "minecraft:thorns");
                addSafe(list, "minecraft:unbreaking");
                addSafe(list, "minecraft:mending");
            }
            case ARMOR_BOOTS -> {
                addSafe(list, "minecraft:protection");
                addSafe(list, "minecraft:fire_protection");
                addSafe(list, "minecraft:feather_falling");
                addSafe(list, "minecraft:blast_protection");
                addSafe(list, "minecraft:projectile_protection");
                addSafe(list, "minecraft:thorns");
                addSafe(list, "minecraft:depth_strider");
                addSafe(list, "minecraft:frost_walker");
                addSafe(list, "minecraft:soul_speed");
                addSafe(list, "minecraft:unbreaking");
                addSafe(list, "minecraft:mending");
            }
            case BOW -> {
                addSafe(list, "minecraft:power");
                addSafe(list, "minecraft:punch");
                addSafe(list, "minecraft:flame");
                addSafe(list, "minecraft:infinity");
                addSafe(list, "minecraft:unbreaking");
                addSafe(list, "minecraft:mending");
            }
        }
        return list;
    }

    private static void addSafe(List<Enchantment> list, String id) {
        Enchantment e = resolve(id);
        if (e != null) list.add(e);
    }
}

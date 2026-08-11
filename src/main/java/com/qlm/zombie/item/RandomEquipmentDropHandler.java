package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;

/**
 * 击杀掉落随机品质装备系统。
 *
 * 击杀除玩家、村民、铁傀儡外的所有生物 → 随机掉落武器/工具/盔甲，随机品质。
 *
 * 品质：劣质→一般→普通→精良→高级→稀有→神器→传说→史诗→神话
 * 神话品质：攻击力 99999，无耐久，可破坏基岩。
 *
 * 盔甲特殊：穿上不扣生命值，脱下扣生命值（生命上限减少），虚空不掉生命值。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class RandomEquipmentDropHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 排除的实体类型：玩家、村民、铁傀儡
     */
    private static boolean isExcluded(LivingEntity entity) {
        if (entity instanceof Player) return true;
        // 村民
        if (entity.getClass().getName().contains("Villager")) return true;
        // 铁傀儡
        if (entity.getClass().getName().contains("IronGolem")) return true;
        return false;
    }

    /**
     * 击杀掉落
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        // 排除玩家、村民、铁傀儡
        if (isExcluded(entity)) return;

        // 仅服务端
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // 30% 概率掉落装备
        if (serverLevel.getRandom().nextFloat() > 0.30F) return;

        // 随机品质
        EquipmentQuality quality = EquipmentQuality.randomRoll(serverLevel.getRandom());

        // 随机装备类型
        ItemStack dropStack = generateRandomEquipment(quality, serverLevel.getRandom());
        if (dropStack == null) return;

        // 品质写入 NBT
        quality.applyToStack(dropStack);

        // 附加 Tooltip
        applyTooltipNBT(dropStack, quality);

        // 在死亡位置掉落
        BlockPos pos = entity.blockPosition();
        ItemEntity itemEntity = new ItemEntity(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, dropStack);
        itemEntity.setDeltaMovement(0, 0.2, 0);
        serverLevel.addFreshEntity(itemEntity);

        LOGGER.info("[QLM Zombie] 随机品质装备掉落: {}品质 {} 于 ({}, {}, {})",
                quality.getDisplayName(), dropStack.getItem(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * 随机生成武器/工具/盔甲
     */
    private static ItemStack generateRandomEquipment(EquipmentQuality quality, net.minecraft.util.RandomSource rnd) {
        int type = rnd.nextInt(3); // 0=武器, 1=工具, 2=盔甲
        switch (type) {
            case 0 -> { return generateWeapon(quality, rnd); }
            case 1 -> { return generateTool(quality, rnd); }
            case 2 -> { return generateArmor(quality, rnd); }
        }
        return null;
    }

    /**
     * 生成武器
     */
    private static ItemStack generateWeapon(EquipmentQuality quality, net.minecraft.util.RandomSource rnd) {
        Item[] weapons = {
            Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.BOW, Items.CROSSBOW, Items.TRIDENT,
            Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE
        };
        Item weapon = weapons[rnd.nextInt(weapons.length)];
        ItemStack stack = new ItemStack(weapon);

        // 品质攻击力加成
        float bonusAttack = quality.getBonusAttack();
        if (bonusAttack > 0) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putFloat(EquipmentQuality.NBT_ATTACK, bonusAttack);
        }

        return stack;
    }

    /**
     * 生成工具
     */
    private static ItemStack generateTool(EquipmentQuality quality, net.minecraft.util.RandomSource rnd) {
        Item[] tools = {
            Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
            Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
            Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
            Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE,
            Items.FISHING_ROD, Items.SHEARS
        };
        Item tool = tools[rnd.nextInt(tools.length)];
        ItemStack stack = new ItemStack(tool);

        // 如果是镐子，随机赋予一个镐子能力
        if (tool instanceof PickaxeItem) {
            rollSinglePickaxeAbility(stack, quality, rnd);
        }

        return stack;
    }

    /**
     * 生成盔甲
     */
    private static ItemStack generateArmor(EquipmentQuality quality, net.minecraft.util.RandomSource rnd) {
        Item[][] armorSets = {
            { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
            { Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS },
            { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS },
            { Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
            { Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS },
            { Items.TURTLE_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS }
        };
        Item[] set = armorSets[rnd.nextInt(armorSets.length)];
        Item armor = set[rnd.nextInt(4)];

        ItemStack stack = new ItemStack(armor);

        // 盔甲生命上限加成
        float bonusHealth = quality.getBonusHealth();
        if (bonusHealth > 0) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putFloat(EquipmentQuality.NBT_HEALTH, bonusHealth);
        }

        // 盔甲护甲加成
        float bonusArmor = quality.getBonusArmor();
        if (bonusArmor > 0) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putFloat(EquipmentQuality.NBT_ARMOR, bonusArmor);
        }

        return stack;
    }

    /**
     * 随机赋予镐子一个能力（仅一个）
     * 黑曜石破坏 5% / 3×3 范围 3% / 5×5 范围 1%
     */
    private static void rollSinglePickaxeAbility(ItemStack stack, EquipmentQuality quality, net.minecraft.util.RandomSource rnd) {
        // 高品质镐子概率更高
        float qualityBonus = quality.getId() * 0.01F; // 品质越高概率越高

        int roll = rnd.nextInt(100);
        // 5% 黑曜石破坏
        if (roll < (int)(5 + qualityBonus * 100)) {
            PickaxeAbility.addAbility(stack, PickaxeAbility.OBSIDIAN_BREAKER);
            return;
        }
        // 3% 3×3 范围
        if (roll < (int)(8 + qualityBonus * 100)) {
            PickaxeAbility.addAbility(stack, PickaxeAbility.RANGE_3X3);
            return;
        }
        // 1% 5×5 范围
        if (roll < (int)(9 + qualityBonus * 100)) {
            PickaxeAbility.addAbility(stack, PickaxeAbility.RANGE_5X5);
        }
    }

    /**
     * 写入 Tooltip NBT
     */
    private static void applyTooltipNBT(ItemStack stack, EquipmentQuality quality) {
        CompoundTag tag = stack.getOrCreateTag();
        // 标记为品质装备
        tag.putBoolean("qlm_has_quality", true);
        // 自定义显示名
        Component name = Component.empty()
                .append(quality.getDisplayComponent())
                .append(Component.translatable(stack.getDescriptionId()))
                .withStyle(quality.getFormatting());
        stack.setHoverName(name);
    }

    // ==================== Tooltip 事件 ====================

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        EquipmentQuality quality = EquipmentQuality.fromStack(stack);
        if (quality == null) return;

        List<Component> tooltip = event.getToolTip();

        // 品质行
        tooltip.add(1, Component.empty()
                .append(Component.literal("✦ 品质: ").withStyle(ChatFormatting.GRAY))
                .append(quality.getDisplayComponent()));

        // 攻击力加成
        float bonusAttack = stack.getTag() != null ? stack.getTag().getFloat(EquipmentQuality.NBT_ATTACK) : 0;
        if (bonusAttack > 0) {
            tooltip.add(2, Component.empty()
                    .append(Component.literal("  攻击力 +").withStyle(ChatFormatting.RED))
                    .append(Component.literal(String.format("%.0f", bonusAttack)).withStyle(ChatFormatting.RED)));
        }

        // 生命上限加成
        float bonusHealth = stack.getTag() != null ? stack.getTag().getFloat(EquipmentQuality.NBT_HEALTH) : 0;
        if (bonusHealth > 0) {
            tooltip.add(3, Component.empty()
                    .append(Component.literal("  生命上限 +").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(String.format("%.0f", bonusHealth)).withStyle(ChatFormatting.GREEN)));
        }

        // 护甲加成
        float bonusArmor = stack.getTag() != null ? stack.getTag().getFloat(EquipmentQuality.NBT_ARMOR) : 0;
        if (bonusArmor > 0) {
            tooltip.add(4, Component.empty()
                    .append(Component.literal("  护甲 +").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(String.format("%.0f", bonusArmor)).withStyle(ChatFormatting.BLUE)));
        }

        // 神话品质特殊属性
        if (quality.isIndestructible()) {
            tooltip.add(5, Component.empty()
                    .append(Component.literal("  ✦ 无耐久消耗").withStyle(ChatFormatting.GOLD)));
            tooltip.add(6, Component.empty()
                    .append(Component.literal("  ✦ 可破坏基岩").withStyle(ChatFormatting.DARK_PURPLE)));
            tooltip.add(7, Component.empty()
                    .append(Component.literal("  ✦ 虚空不掉生命值").withStyle(ChatFormatting.AQUA)));
        }
    }
}

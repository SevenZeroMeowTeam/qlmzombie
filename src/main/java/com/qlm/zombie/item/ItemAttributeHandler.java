package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ItemAttributeHandler {

    private static final UUID IRON_SWORD_DAMAGE_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID IRON_AXE_DAMAGE_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    // 合成品质属性专用 UUID
    private static final UUID QUALITY_DAMAGE_UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-234567890123");
    private static final UUID QUALITY_ARMOR_UUID = UUID.fromString("d4e5f6a7-b8c9-0123-def0-345678901234");
    private static final UUID QUALITY_TOUGHNESS_UUID = UUID.fromString("e5f6a7b8-c9d0-1234-ef01-456789012345");

    /**
     * 判断物品是否属于品质系统覆盖类型（武器 / 工具 / 盔甲）。
     */
    private static boolean isQualityItem(Item item) {
        return item instanceof SwordItem          // 剑
            || item instanceof DiggerItem         // 镐、铲、锄、斧
            || item instanceof ArmorItem          // 盔甲
            || item instanceof ShieldItem         // 盾
            || item instanceof ShearsItem         // 剪刀
            || item instanceof BowItem            // 弓
            || item instanceof CrossbowItem       // 弩
            || item instanceof TridentItem        // 三叉戟
            || item instanceof FishingRodItem     // 钓鱼竿
            || item instanceof FlintAndSteelItem; // 打火石
    }

    /**
     * 监听玩家合成事件：当合成武器/工具/盔甲时，随机生成品质与伤害并写入 NBT。
     * 高品质也可能出现劣质武器（按权重随机）。
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack stack = event.getCrafting();
        if (stack.isEmpty()) return;
        if (!isQualityItem(stack.getItem())) return;
        if (WeaponQuality.hasQuality(stack)) return;

        RandomSource rnd = event.getEntity() != null
                ? event.getEntity().getRandom()
                : RandomSource.create();

        WeaponQuality quality = WeaponQuality.randomQuality(rnd);
        double damage = quality.randomDamage(rnd);
        WeaponQuality.applyQuality(stack, quality, damage);

        QLMZombieMod.LOGGER.debug("[QLM Zombie] 合成品质: {} 伤害加成: {} ({})",
                quality.getTranslationKey(), damage, stack.getDescriptionId());
    }

    /**
     * 物品属性计算：优先应用合成品质属性，否则保留原版固定加成。
     */
    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null) return;

        if (WeaponQuality.hasQuality(stack)) {
            applyQualityAttributeModifiers(event, stack);
        } else {
            applyVanillaOverrideAttributeModifiers(event, stack);
        }
    }

    private static void applyQualityAttributeModifiers(ItemAttributeModifierEvent event, ItemStack stack) {
        double damage = WeaponQuality.getDamage(stack);
        Item item = stack.getItem();

        // 所有品质物品均获得攻击伤害加成
        event.addModifier(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(QUALITY_DAMAGE_UUID, "Quality Damage",
                        damage, AttributeModifier.Operation.ADDITION));

        // 盔甲额外获得护甲值与护甲韧性加成
        if (item instanceof ArmorItem || item instanceof ShieldItem) {
            event.addModifier(Attributes.ARMOR,
                    new AttributeModifier(QUALITY_ARMOR_UUID, "Quality Armor",
                            damage / 2.0, AttributeModifier.Operation.ADDITION));
            event.addModifier(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(QUALITY_TOUGHNESS_UUID, "Quality Toughness",
                            damage / 4.0, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void applyVanillaOverrideAttributeModifiers(ItemAttributeModifierEvent event, ItemStack stack) {
        if (stack.getItem() == Items.IRON_SWORD) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(IRON_SWORD_DAMAGE_UUID, "Iron Sword Damage Boost",
                            46.0, AttributeModifier.Operation.ADDITION));
        } else if (stack.getItem() == Items.IRON_AXE) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(IRON_AXE_DAMAGE_UUID, "Iron Axe Damage Boost",
                            19.0, AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * 物品悬浮提示：在物品名下方显示品质与伤害加成。
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!WeaponQuality.hasQuality(stack)) return;

        WeaponQuality quality = WeaponQuality.getQuality(stack);
        if (quality == null) return;

        double damage = WeaponQuality.getDamage(stack);
        List<Component> tooltip = event.getToolTip();

        Component qualityLine = Component.translatable("qlmzombie.tooltip.quality", quality.getDisplayName());
        Component damageLine = Component.translatable("qlmzombie.tooltip.damage_bonus",
                String.format("%.1f", damage)).withStyle(ChatFormatting.GRAY);

        if (!tooltip.isEmpty()) {
            tooltip.add(1, qualityLine);
            tooltip.add(2, damageLine);
        } else {
            tooltip.add(qualityLine);
            tooltip.add(damageLine);
        }
    }
}

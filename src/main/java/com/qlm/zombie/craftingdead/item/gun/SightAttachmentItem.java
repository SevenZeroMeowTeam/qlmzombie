package com.qlm.zombie.craftingdead.item.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 瞄准镜附件物品：实现 IAttachmentItem，SlotType.SIGHT
 * 种类：acog（先进战斗光学瞄准镜）、red_dot（红点）、eotech_holographic（全息）、8x_scope（8倍镜）
 * 稀有度根据种类自动映射，也可自定义
 * 使用方法：new SightAttachmentItem("red_dot", Rarity.UNCOMMON, "+10% 精准")
 */
public class SightAttachmentItem extends Item implements IAttachmentItem {

    /** 附件名称标识：acog / red_dot / eotech_holographic / 8x_scope */
    private final String sightName;

    /** 稀有度 */
    private final Rarity rarity;

    /** 属性修饰描述 */
    private final String modifierString;

    /**
     * 瞄准镜附件构造函数
     * @param name           瞄准镜种类名（acog/red_dot/eotech_holographic/8x_scope）
     * @param rarity         物品稀有度（UNCOMMON ~ EPIC）
     * @param modifierString 属性修饰描述，如 "+20% 射速"
     */
    public SightAttachmentItem(String name, Rarity rarity, String modifierString) {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(rarity));
        this.sightName = name;
        this.rarity = rarity;
        this.modifierString = modifierString;
    }

    /**
     * 便捷构造：根据瞄准镜名称自动匹配推荐稀有度
     * red_dot → UNCOMMON, eotech_holographic → RARE,
     * acog → RARE, 8x_scope → EPIC
     * @param name           瞄准镜种类名
     * @param modifierString 属性修饰描述
     */
    public SightAttachmentItem(String name, String modifierString) {
        this(name, autoRarity(name), modifierString);
    }

    /**
     * 根据瞄准镜名称自动推荐稀有度
     */
    private static Rarity autoRarity(String name) {
        return switch (name) {
            case "red_dot"             -> Rarity.UNCOMMON;
            case "eotech_holographic"  -> Rarity.RARE;
            case "acog"                -> Rarity.RARE;
            case "8x_scope"            -> Rarity.EPIC;
            default                    -> Rarity.UNCOMMON;
        };
    }

    // ==================== IAttachmentItem 接口实现 ====================

    @Override
    public IGun.SlotType getSlotType() {
        return IGun.SlotType.SIGHT;
    }

    @Override
    public String getModifierString() {
        return modifierString;
    }

    @Override
    public Rarity getQualityRarity() {
        return rarity;
    }

    // ==================== Tooltip 显示 ====================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7附件类型：§e瞄准镜 [SIGHT]")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7型号：§6" + getSightDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§a" + modifierString)
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * 获取瞄准镜中文显示名
     */
    private String getSightDisplayName() {
        return switch (sightName) {
            case "acog"                -> "ACOG 先进战斗光学瞄准镜";
            case "red_dot"             -> "红点瞄准镜";
            case "eotech_holographic"  -> "EOTech 全息瞄准镜";
            case "8x_scope"            -> "8倍光学瞄准镜";
            default                    -> sightName;
        };
    }
}

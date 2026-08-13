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
 * 弹匣附件物品：实现 IAttachmentItem，SlotType.MAGAZINE
 * 种类：standard_mag（标准弹匣，备用）、extended_mag（扩容弹匣+50%容量）、
 *       drum_mag（弹鼓+100%~150%容量，降低换弹速度）
 * 使用方法：new MagazineAttachmentItem("extended_mag", Rarity.RARE, "+50% 弹匣容量")
 */
public class MagazineAttachmentItem extends Item implements IAttachmentItem {

    /** 弹匣种类名：standard_mag / extended_mag / drum_mag */
    private final String magName;

    /** 稀有度 */
    private final Rarity rarity;

    /** 属性修饰描述 */
    private final String modifierString;

    /**
     * 弹匣附件构造函数
     * @param magName        弹匣种类名（standard_mag/extended_mag/drum_mag）
     * @param rarity         物品稀有度
     * @param modifierString 属性修饰描述
     */
    public MagazineAttachmentItem(String magName, Rarity rarity, String modifierString) {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(rarity));
        this.magName = magName;
        this.rarity = rarity;
        this.modifierString = modifierString;
    }

    // ==================== IAttachmentItem 接口实现 ====================

    @Override
    public IGun.SlotType getSlotType() {
        return IGun.SlotType.MAGAZINE;
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
        tooltip.add(Component.literal("§7附件类型：§e弹匣 [MAGAZINE]")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7型号：§6" + getMagDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§a" + modifierString)
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * 获取弹匣中文显示名
     */
    private String getMagDisplayName() {
        return switch (magName) {
            case "standard_mag" -> "标准弹匣";
            case "extended_mag" -> "扩容弹匣";
            case "drum_mag"     -> "弹鼓";
            default             -> magName;
        };
    }
}

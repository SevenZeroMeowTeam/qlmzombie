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
 * 枪管附件物品：实现 IAttachmentItem，SlotType.BARREL
 * 种类：suppressor（消音器，降低噪音+隐藏开火）、
 *       compensator（补偿器，减少后坐力+散布）、
 *       extended_barrel（加长枪管，增加伤害+射程）
 * 使用方法：new BarrelAttachmentItem("suppressor", Rarity.RARE, "-90% 噪音")
 */
public class BarrelAttachmentItem extends Item implements IAttachmentItem {

    /** 枪管附件种类名：suppressor / compensator / extended_barrel */
    private final String barrelName;

    /** 稀有度 */
    private final Rarity rarity;

    /** 属性修饰描述 */
    private final String modifierString;

    /**
     * 枪管附件构造函数
     * @param barrelName     枪管种类名（suppressor/compensator/extended_barrel）
     * @param rarity         物品稀有度
     * @param modifierString 属性修饰描述
     */
    public BarrelAttachmentItem(String barrelName, Rarity rarity, String modifierString) {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(rarity));
        this.barrelName = barrelName;
        this.rarity = rarity;
        this.modifierString = modifierString;
    }

    // ==================== IAttachmentItem 接口实现 ====================

    @Override
    public IGun.SlotType getSlotType() {
        return IGun.SlotType.BARREL;
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
        tooltip.add(Component.literal("§7附件类型：§e枪管 [BARREL]")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7型号：§6" + getBarrelDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§a" + modifierString)
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * 获取枪管附件中文显示名
     */
    private String getBarrelDisplayName() {
        return switch (barrelName) {
            case "suppressor"      -> "消音器";
            case "compensator"     -> "补偿器";
            case "extended_barrel" -> "加长枪管";
            default                -> barrelName;
        };
    }
}

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
 * 握把附件物品：实现 IAttachmentItem，SlotType.GRIP
 * 种类：vertical_grip（垂直握把）、angled_grip（转角握把）、bipod（两脚架，可兼作 BIPOD 槽）
 * 注意：bipod 作为握把+脚架两用附件，同时可被 GRIP 槽与 BIPOD 槽识别
 * 使用方法：new GripAttachmentItem("vertical_grip", Rarity.UNCOMMON, "-15% 后坐力")
 */
public class GripAttachmentItem extends Item implements IAttachmentItem {

    /** 握把种类名：vertical_grip / angled_grip / bipod */
    private final String gripName;

    /** 稀有度 */
    private final Rarity rarity;

    /** 属性修饰描述 */
    private final String modifierString;

    /**
     * 握把附件构造函数
     * @param gripName       握把种类名（vertical_grip/angled_grip/bipod）
     * @param rarity         物品稀有度
     * @param modifierString 属性修饰描述
     */
    public GripAttachmentItem(String gripName, Rarity rarity, String modifierString) {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(rarity));
        this.gripName = gripName;
        this.rarity = rarity;
        this.modifierString = modifierString;
    }

    // ==================== IAttachmentItem 接口实现 ====================

    @Override
    public IGun.SlotType getSlotType() {
        // bipod 默认挂到 GRIP 槽，但也允许 BIPOD 槽使用（外部判断时可检测 gripName=="bipod"）
        return IGun.SlotType.GRIP;
    }

    @Override
    public String getModifierString() {
        return modifierString;
    }

    @Override
    public Rarity getQualityRarity() {
        return rarity;
    }

    /**
     * 判断此附件是否兼作脚架（当 gripName == bipod 时返回 true）
     * 外部枪械检查 BIPOD 槽兼容性时可调用此方法
     */
    public boolean isBipodDualUse() {
        return "bipod".equals(gripName);
    }

    // ==================== Tooltip 显示 ====================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7附件类型：§e握把 [GRIP]" + (isBipodDualUse() ? " + 脚架 [BIPOD]" : ""))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7型号：§6" + getGripDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§a" + modifierString)
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * 获取握把中文显示名
     */
    private String getGripDisplayName() {
        return switch (gripName) {
            case "vertical_grip" -> "垂直握把";
            case "angled_grip"   -> "转角握把";
            case "bipod"         -> "两脚架（握把两用）";
            default              -> gripName;
        };
    }
}

package com.qlm.zombie.craftingdead.item.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 弹药物品类：对应 AmmoType 枚举的具体物品
 * 堆叠数量、Tooltip 信息根据弹药类型自动配置
 * 使用方法：new AmmoItem(AmmoType._556x45) 注册物品即可
 */
public class AmmoItem extends Item {

    /** 该物品对应的弹药类型 */
    public final AmmoType ammoType;

    /**
     * 弹药物品构造函数
     * @param ammoType 弹药类型枚举，决定堆叠数和显示信息
     */
    public AmmoItem(AmmoType ammoType) {
        super(new Item.Properties()
                .stacksTo(ammoType.stackSize));
        this.ammoType = ammoType;
    }

    /**
     * 物品悬停提示：显示弹药名称与每发伤害
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 显示弹药类型名称
        tooltip.add(Component.literal("§7弹药类型：§e" + ammoType.displayName)
                .withStyle(ChatFormatting.GRAY));
        // 显示每发基础伤害
        tooltip.add(Component.literal("§7每发伤害：§c" + ammoType.damage)
                .withStyle(ChatFormatting.GRAY));
    }
}

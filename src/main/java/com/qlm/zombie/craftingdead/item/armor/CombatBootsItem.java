package com.qlm.zombie.craftingdead.item.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class CombatBootsItem extends ArmorItem {

    public CombatBootsItem() {
        super(CDArmorMaterial.INSTANCE, ArmorItem.Type.BOOTS,
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("军用作战靴，提供脚踝保护与移动支持").withStyle(ChatFormatting.GRAY));
    }
}

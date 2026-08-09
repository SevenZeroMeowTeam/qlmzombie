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

public class PlateCarrierItem extends ArmorItem {

    public PlateCarrierItem() {
        super(CDArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE,
                new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("插板式防弹衣，大幅减少躯干受到的伤害").withStyle(ChatFormatting.GRAY));
    }
}

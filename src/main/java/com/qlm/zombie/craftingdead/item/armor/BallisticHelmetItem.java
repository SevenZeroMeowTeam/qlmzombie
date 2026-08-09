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

public class BallisticHelmetItem extends ArmorItem {

    public BallisticHelmetItem() {
        super(CDArmorMaterial.INSTANCE, ArmorItem.Type.HELMET,
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("军用防弹头盔，减少头部受到的伤害").withStyle(ChatFormatting.GRAY));
    }
}

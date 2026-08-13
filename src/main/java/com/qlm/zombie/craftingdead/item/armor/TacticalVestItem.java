package com.qlm.zombie.craftingdead.item.armor;

import com.qlm.zombie.craftingdead.item.CDArmorMaterial;
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

public class TacticalVestItem extends ArmorItem {

    public TacticalVestItem() {
        super(CDArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE,
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("轻量化战术背心，提供良好防护的同时保持机动性").withStyle(ChatFormatting.GRAY));
    }
}

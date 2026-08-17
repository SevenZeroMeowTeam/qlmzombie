package com.qlm.zombie.craftingdead.item.medical;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class SalineBagItem extends MedicalUseItem {

    public SalineBagItem() {
        super(new Item.Properties()
                .stacksTo(4)
                .rarity(Rarity.UNCOMMON));
    }

    @Override
    protected void applyEffect(Level level, Player player, ItemStack stack) {
        FoodData foodData = player.getFoodData();
        if (foodData != null) {
            foodData.setFoodLevel(foodData.getFoodLevel() + 8);
        }
        player.heal(10.0F);
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键输注生理盐水，补充体液和能量").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("全面恢复饱食度和生命值").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("恢复8点饱食度 + 10点生命 + 10秒饱和 + 5秒再生II").withStyle(ChatFormatting.GRAY));
    }
}

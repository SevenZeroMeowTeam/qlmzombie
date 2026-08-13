package com.qlm.zombie.craftingdead.item.medical;

import com.qlm.zombie.craftingdead.effect.CDEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class PainkillersItem extends Item {

    public PainkillersItem() {
        super(new Item.Properties()
                .stacksTo(16)
                .rarity(Rarity.COMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(CDEffects.PAIN_SUPPRESSION.get(), 1800, 0));
            FoodData foodData = player.getFoodData();
            if (foodData != null) {
                foodData.setFoodLevel(foodData.getFoodLevel() + 2);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (stack.isEmpty()) {
                return InteractionResultHolder.sidedSuccess(ItemStack.EMPTY, level.isClientSide());
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键服用止痛药，抑制疼痛感知").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("减缓出血和骨折带来的负面影响").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("90秒疼痛抑制 + 恢复2点饱食度").withStyle(ChatFormatting.GRAY));
    }
}

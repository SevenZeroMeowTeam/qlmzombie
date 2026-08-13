package com.qlm.zombie.craftingdead.item.medical;

import com.qlm.zombie.craftingdead.effect.CDEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class SurgicalScissorsItem extends Item {

    public SurgicalScissorsItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
                .durability(32));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            Map<MobEffect, MobEffectInstance> activeEffects = player.getActiveEffectsMap();
            for (Map.Entry<MobEffect, MobEffectInstance> entry : activeEffects.entrySet()) {
                if (entry.getKey().getCategory() == MobEffectCategory.HARMFUL) {
                    player.removeEffect(entry.getKey());
                }
            }
            player.removeEffect(CDEffects.BROKEN_BONE.get());
            player.removeEffect(CDEffects.BLEEDING.get());
            player.heal(15.0F);
            level.playSound(null, player.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键进行紧急外科手术治疗").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("清除所有负面效果，修复重伤").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("清除全部负面效果 + 修复骨折止血 + 恢复15点生命（可重复使用32次）").withStyle(ChatFormatting.GRAY));
    }
}

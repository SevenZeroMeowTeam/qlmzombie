package com.qlm.zombie.craftingdead.item.medical;

import com.qlm.zombie.craftingdead.effect.CDEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class BandageItem extends Item {

    public BandageItem() {
        super(new Item.Properties()
                .stacksTo(16)
                .rarity(Rarity.COMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.hasEffect(CDEffects.BLEEDING.get())) {
            if (!level.isClientSide) {
                player.removeEffect(CDEffects.BLEEDING.get());
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0));
                player.heal(3.0F);
                level.playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    return InteractionResultHolder.sidedSuccess(ItemStack.EMPTY, level.isClientSide());
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        } else {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.literal("§7未发现出血，绷带未使用"),
                        true
                );
            }
            return InteractionResultHolder.pass(stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键使用以止血并恢复少量生命").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("仅在出血状态下可使用").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("恢复3点生命值 + 2秒生命再生I").withStyle(ChatFormatting.GRAY));
    }
}

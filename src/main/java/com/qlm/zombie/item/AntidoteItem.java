package com.qlm.zombie.item;

import com.qlm.zombie.effect.QLMEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class AntidoteItem extends Item {

    public AntidoteItem() {
        super(new Item.Properties()
                .stacksTo(16)
                .rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        MobEffectInstance infection = player.getEffect(QLMEffects.INFECTION.get());
        if (infection != null) {
            if (!level.isClientSide) {
                player.removeEffect(QLMEffects.INFECTION.get());
                player.displayClientMessage(
                        Component.literal("§a✓ 解毒剂生效！感染已被清除"),
                        true
                );
                level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
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
                        Component.literal("§7你没有中毒，解毒剂暂未生效"),
                        true
                );
            }
            return InteractionResultHolder.pass(stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7右键使用以清除感染效果").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7可被感染者或玩家使用").withStyle(ChatFormatting.GRAY));
    }
}

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

public class SplintItem extends MedicalUseItem {

    public SplintItem() {
        super(new Item.Properties()
                .stacksTo(8)
                .rarity(Rarity.COMMON));
    }

    @Override
    protected boolean canUse(Level level, Player player, ItemStack stack) {
        return player.hasEffect(CDEffects.BROKEN_BONE.get());
    }

    @Override
    protected void onCannotUse(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("§7未发现骨折，夹板未使用"), true);
        }
    }

    @Override
    protected void applyEffect(Level level, Player player, ItemStack stack) {
        player.removeEffect(CDEffects.BROKEN_BONE.get());
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0));
        level.playSound(null, player.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键使用夹板固定骨折部位").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("仅在骨折状态下可使用").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("清除骨折 + 60秒速度I辅助恢复行动").withStyle(ChatFormatting.GRAY));
    }
}

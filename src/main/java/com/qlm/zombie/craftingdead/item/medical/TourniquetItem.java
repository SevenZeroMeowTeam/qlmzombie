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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class TourniquetItem extends MedicalUseItem {

    public TourniquetItem() {
        super(new Item.Properties()
                .stacksTo(8)
                .rarity(Rarity.COMMON));
    }

    @Override
    protected void applyEffect(Level level, Player player, ItemStack stack) {
        player.removeEffect(CDEffects.BLEEDING.get());
        player.addEffect(new MobEffectInstance(CDEffects.PAIN_SUPPRESSION.get(), 2400, 1));
        level.playSound(null, player.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键使用止血带，紧急控制出血").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("强力止血并提供长效疼痛抑制").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("清除出血 + 120秒强效疼痛抑制II").withStyle(ChatFormatting.GRAY));
    }
}

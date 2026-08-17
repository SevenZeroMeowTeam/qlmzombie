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

public class FirstAidKitItem extends MedicalUseItem {

    public FirstAidKitItem() {
        super(new Item.Properties()
                .stacksTo(4)
                .rarity(Rarity.UNCOMMON));
    }

    @Override
    protected void applyEffect(Level level, Player player, ItemStack stack) {
        player.removeEffect(CDEffects.BLEEDING.get());
        player.removeEffect(CDEffects.BROKEN_BONE.get());
        player.removeEffect(CDEffects.PAIN_SUPPRESSION.get());
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
        player.heal(12.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键使用以进行紧急医疗救治").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("清除出血和骨折效果，大幅恢复生命").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("恢复12点生命值 + 10秒生命再生III").withStyle(ChatFormatting.GRAY));
    }
}

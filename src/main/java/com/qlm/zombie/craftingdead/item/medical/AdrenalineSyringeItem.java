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

public class AdrenalineSyringeItem extends MedicalUseItem {

    public AdrenalineSyringeItem() {
        super(new Item.Properties()
                .stacksTo(8)
                .rarity(Rarity.UNCOMMON));
    }

    @Override
    protected void applyEffect(Level level, Player player, ItemStack stack) {
        player.addEffect(new MobEffectInstance(CDEffects.ADRENALINE_RUSH.get(), 800, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
        level.playSound(null, player.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键注射肾上腺素，激发身体潜能").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("大幅提升移动速度和攻击力量").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("40秒肾上腺素 + 30秒速度II + 20秒力量II").withStyle(ChatFormatting.GRAY));
    }
}

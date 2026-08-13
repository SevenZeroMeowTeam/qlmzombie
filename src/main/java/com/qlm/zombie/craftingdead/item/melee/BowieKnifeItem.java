package com.qlm.zombie.craftingdead.item.melee;

import com.qlm.zombie.craftingdead.effect.CDEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class BowieKnifeItem extends SwordItem {

    public BowieKnifeItem() {
        super(Tiers.DIAMOND, 5, 1.5F, new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (result && !attacker.level().isClientSide) {
            if (attacker.getRandom().nextFloat() < 0.25F) {
                target.addEffect(new MobEffectInstance(CDEffects.BLEEDING.get(), 240, 0));
            }
            if (attacker.getRandom().nextFloat() < 0.10F) {
                target.addEffect(new MobEffectInstance(CDEffects.BROKEN_BONE.get(), 300, 0));
            }
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("重型猎刀，25%流血+10%骨折").withStyle(ChatFormatting.GRAY));
    }
}

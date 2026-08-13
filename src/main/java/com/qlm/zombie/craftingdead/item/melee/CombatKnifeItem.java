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

public class CombatKnifeItem extends SwordItem {

    public CombatKnifeItem() {
        super(Tiers.IRON, 4, 1.6F, new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (result && !attacker.level().isClientSide) {
            if (attacker.getRandom().nextFloat() < 0.15F) {
                target.addEffect(new MobEffectInstance(CDEffects.BLEEDING.get(), 200, 0));
            }
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("近距离战斗武器，15%概率造成流血").withStyle(ChatFormatting.GRAY));
    }
}

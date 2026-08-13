package com.qlm.zombie.item

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class AntidoteItem : Item(Item.Properties().stacksTo(1)) {

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (player.hasEffect(MobEffects.POISON)) {
            if (!level.isClientSide) {
                player.removeEffect(MobEffects.POISON)
            }
            return InteractionResultHolder.consume(stack)
        }
        return InteractionResultHolder.pass(stack)
    }
}

package com.qlm.zombie.item

import com.qlm.zombie.craftingdead.item.medical.MedicalUseItem
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 解毒剂：带使用动画（DRINK），仅在中毒时可用。
 */
class AntidoteItem : MedicalUseItem(Item.Properties().stacksTo(1)) {

    override fun canUse(level: Level, player: Player, stack: ItemStack): Boolean {
        return player.hasEffect(MobEffects.POISON)
    }

    override fun onCannotUse(level: Level, player: Player, stack: ItemStack) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("§7未中毒，解毒剂未使用"), true)
        }
    }

    override fun applyEffect(level: Level, player: Player, stack: ItemStack) {
        player.removeEffect(MobEffects.POISON)
    }
}

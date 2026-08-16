package com.qlm.zombie.item

import com.qlm.zombie.thirst.foundation.common.capability.ModCapabilities
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level

class PurifiedWaterItem(properties: Item.Properties) : Item(properties.food(
    net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationMod(2.0f).build()
)) {

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        player.startUsingItem(usedHand)
        return InteractionResultHolder.consume(stack)
    }

    override fun getUseAnimation(stack: ItemStack): UseAnim {
        return UseAnim.DRINK
    }

    override fun getUseDuration(stack: ItemStack): Int {
        return 40
    }

    override fun finishUsingItem(stack: ItemStack, level: Level, livingEntity: LivingEntity): ItemStack {
        val result = super.finishUsingItem(stack, level, livingEntity)

        if (!level.isClientSide && livingEntity is Player) {
            runCatching {
                // 接入 Thirst-Mod 口渴能力系统（合并后）：恢复 8 点口渴 + 4 点解渴
                livingEntity.getCapability(ModCapabilities.PLAYER_THIRST).ifPresent { cap ->
                    cap.drink(livingEntity, 8, 4)
                }
            }
        }

        return if (result.isEmpty) ItemStack(Items.GLASS_BOTTLE) else result
    }
}

package com.qlm.zombie.item

import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraftforge.registries.ForgeRegistries

class PlankCollectorItem : Item(Item.Properties()) {

    companion object {
        private const val COLLECT_RADIUS = 16.0
    }

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (level.isClientSide) return InteractionResultHolder.success(stack)

        val playerPos = player.position()
        val aabb = AABB(
            playerPos.x - COLLECT_RADIUS, playerPos.y - COLLECT_RADIUS, playerPos.z - COLLECT_RADIUS,
            playerPos.x + COLLECT_RADIUS, playerPos.y + COLLECT_RADIUS, playerPos.z + COLLECT_RADIUS
        )

        val itemEntities = level.getEntitiesOfClass(ItemEntity::class.java, aabb)
        var collectedCount = 0

        for (itemEntity in itemEntities) {
            val itemStack = itemEntity.item
            val item = itemStack.item
            val itemKey = ForgeRegistries.ITEMS.getKey(item)

            if (itemKey?.path?.endsWith("_planks") == true) {
                val copy = itemStack.copy()
                itemEntity.discard()
                if (player.inventory.add(copy)) {
                    collectedCount += copy.count
                } else {
                    player.drop(copy, false)
                }
            }
        }

        if (collectedCount > 0) {
            player.displayClientMessage(
                Component.literal("§a已收集 $collectedCount 个木板"),
                true
            )
        } else {
            player.displayClientMessage(
                Component.literal("§7附近没有木板物品"),
                true
            )
        }

        return InteractionResultHolder.consume(stack)
    }
}

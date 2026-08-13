package com.qlm.zombie.item

import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Tiers
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks

class PlankAxeItem : AxeItem(Tiers.WOOD, 6.0f, 2.0f, Item.Properties()) {

    companion object {
        private val LOG_TO_PLANK: Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> = mapOf(
            Blocks.OAK_LOG to Blocks.OAK_PLANKS,
            Blocks.BIRCH_LOG to Blocks.BIRCH_PLANKS,
            Blocks.SPRUCE_LOG to Blocks.SPRUCE_PLANKS,
            Blocks.JUNGLE_LOG to Blocks.JUNGLE_PLANKS,
            Blocks.ACACIA_LOG to Blocks.ACACIA_PLANKS,
            Blocks.DARK_OAK_LOG to Blocks.DARK_OAK_PLANKS,
            Blocks.MANGROVE_LOG to Blocks.MANGROVE_PLANKS,
            Blocks.CHERRY_LOG to Blocks.CHERRY_PLANKS,
            Blocks.CRIMSON_STEM to Blocks.CRIMSON_PLANKS,
            Blocks.WARPED_STEM to Blocks.WARPED_PLANKS,
        )
    }

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (level.isClientSide) return InteractionResultHolder.success(stack)

        var convertedCount = 0
        val inventory = player.inventory

        for (i in 0 until inventory.containerSize) {
            val invStack = inventory.getItem(i)
            if (invStack.isEmpty) continue

            val item = invStack.item
            if (item !is BlockItem) continue

            val block = item.block
            val plankBlock = LOG_TO_PLANK[block] ?: continue

            val count = invStack.count
            val plankItemStack = ItemStack(plankBlock.asItem(), count)
            inventory.setItem(i, plankItemStack)
            convertedCount += count
        }

        if (convertedCount > 0) {
            player.displayClientMessage(
                Component.literal("§a已将 $convertedCount 个原木转换为木板"),
                true
            )
        } else {
            player.displayClientMessage(
                Component.literal("§7背包中没有原木可转换"),
                true
            )
        }

        return InteractionResultHolder.success(stack)
    }
}

package com.qlm.zombie.feature

import com.qlm.zombie.QLMZombieMod
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object FastCraftingFeature {

    @SubscribeEvent
    fun onItemCrafted(event: PlayerEvent.ItemCraftedEvent) {
        val player = event.entity
        val resultStack = event.crafting
        val crafting = event.inventory as? CraftingContainer ?: return

        if (resultStack.isEmpty) return
        if (player.level().isClientSide) return

        val recipe = player.level().recipeManager.getRecipeFor(
            RecipeType.CRAFTING,
            crafting,
            player.level()
        ).orElse(null) as? CraftingRecipe ?: return

        if (!recipe.canCraftInDimensions(3, 3) && !recipe.canCraftInDimensions(2, 2)) return

        tryQuickCraft(player, crafting, resultStack, recipe)
    }

    private fun tryQuickCraft(
        player: Player,
        crafting: CraftingContainer,
        originalResult: ItemStack,
        recipe: CraftingRecipe
    ) {
        val inventory = player.inventory
        var craftedCount = 0
        val registryAccess = player.level().registryAccess()

        while (recipe.matches(crafting, player.level()) && craftedCount < 64) {
            val remainingStacks = recipe.getRemainingItems(crafting)

            for (i in 0 until crafting.containerSize) {
                val currentStack = crafting.getItem(i)
                val remainingStack = if (i < remainingStacks.size) remainingStacks[i] else ItemStack.EMPTY

                if (!currentStack.isEmpty) {
                    currentStack.shrink(1)
                    if (currentStack.isEmpty && !remainingStack.isEmpty) {
                        crafting.setItem(i, remainingStack)
                    }
                } else if (!remainingStack.isEmpty) {
                    crafting.setItem(i, remainingStack)
                }
            }

            val newResult = recipe.assemble(crafting, registryAccess)
            if (newResult.isEmpty) break

            if (!inventory.add(newResult.copy())) {
                player.drop(newResult, false)
                break
            }

            craftedCount++
        }

        if (craftedCount > 0 && player is ServerPlayer) {
            QLMZombieMod.LOGGER.debug(
                "[FastCraft] 玩家 {} 快速制作 {} x{}",
                player.name.string,
                originalResult.item.descriptionId,
                craftedCount
            )
        }
    }
}

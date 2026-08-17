package com.qlm.zombie.feature

import com.qlm.zombie.QLMZombieMod
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object AlwaysEatFeature {

    @JvmStatic
    @SubscribeEvent
    fun onPlayerRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entity
        val stack = event.itemStack
        val item = stack.item

        val food = item.foodProperties ?: return
        if (player.foodData.foodLevel < 20) return

        event.isCanceled = true

        if (!player.getAbilities().instabuild) {
            stack.shrink(1)
        }

        val foodData = player.foodData
        val newFood = (foodData.foodLevel + food.nutrition).coerceAtMost(20)
        val newSaturation = (foodData.saturationLevel + food.saturationModifier * 2.0f)
            .coerceAtMost(newFood.toFloat())
        
        foodData.setFoodLevel(newFood)
        foodData.setSaturation(newSaturation)

        player.playSound(SoundEvents.GENERIC_EAT, 0.5f, 1.0f)

        QLMZombieMod.LOGGER.debug(
            "[AlwaysEat] 玩家 {} 在饥饿值满时进食 {}, 新饥饿值: {}",
            player.name.string,
            item.descriptionId,
            foodData.foodLevel
        )
    }
}

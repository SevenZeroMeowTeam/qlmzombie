package com.qlm.zombie.craftingdead.item.gun

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

abstract class AbstractGunItem : Item(Item.Properties().stacksTo(1)) {

    abstract val fireRate: Int
    abstract val damage: Float
    abstract val magazineSize: Int

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (level.isClientSide) return InteractionResultHolder.success(stack)

        val tag = stack.orCreateTag

        val currentAmmo = tag.getInt("CurrentAmmo")
        val isReloading = tag.getBoolean("IsReloading")

        if (isReloading) return InteractionResultHolder.consume(stack)

        if (currentAmmo <= 0) {
            val ammoCount = countAmmo(player)
            if (ammoCount > 0) {
                val reloadAmount = minOf(magazineSize, ammoCount)
                tag.putInt("CurrentAmmo", reloadAmount)
                consumeAmmo(player, reloadAmount)
                return InteractionResultHolder.consume(stack)
            }
            return InteractionResultHolder.consume(stack)
        }

        tag.putInt("CurrentAmmo", currentAmmo - 1)
        player.cooldowns.addCooldown(stack.item, fireRate)

        return InteractionResultHolder.consume(stack)
    }

    open fun fire(level: Level, user: LivingEntity, stack: ItemStack): Boolean {
        if (level.isClientSide) return false
        val player = user as? Player ?: return false
        val tag = stack.orCreateTag
        val currentAmmo = tag.getInt("CurrentAmmo")
        val isReloading = tag.getBoolean("IsReloading")
        if (isReloading || currentAmmo <= 0) return false
        tag.putInt("CurrentAmmo", currentAmmo - 1)
        return true
    }

    open fun reload(level: Level, user: LivingEntity, stack: ItemStack) {
        if (level.isClientSide) return
        val player = user as? Player ?: return
        val tag = stack.orCreateTag
        val currentAmmo = tag.getInt("CurrentAmmo")
        if (currentAmmo >= magazineSize) return
        val ammoCount = countAmmo(player)
        if (ammoCount <= 0) return
        val reloadAmount = minOf(magazineSize - currentAmmo, ammoCount)
        tag.putInt("CurrentAmmo", currentAmmo + reloadAmount)
        consumeAmmo(player, reloadAmount)
    }

    private fun countAmmo(player: Player): Int {
        var count = 0
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && isAmmo(stack)) {
                count += stack.count
            }
        }
        return count
    }

    private fun consumeAmmo(player: Player, amount: Int) {
        var remaining = amount
        for (i in 0 until player.inventory.containerSize) {
            if (remaining <= 0) break
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && isAmmo(stack)) {
                val take = minOf(stack.count, remaining)
                stack.shrink(take)
                remaining -= take
            }
        }
    }

    private fun isAmmo(stack: ItemStack): Boolean {
        val itemName = stack.item.toString()
        return itemName.contains("ammo") || itemName.contains("bullet") || itemName.contains("magazine")
    }
}

package com.qlm.zombie.craftingdead.item

import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ArmorMaterial
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

object CDArmorMaterial : ArmorMaterial {

    override fun getDurabilityForType(type: ArmorItem.Type): Int = when (type) {
        ArmorItem.Type.HELMET -> 165
        ArmorItem.Type.CHESTPLATE -> 240
        ArmorItem.Type.LEGGINGS -> 220
        ArmorItem.Type.BOOTS -> 190
        else -> 100
    }

    override fun getDefenseForType(type: ArmorItem.Type): Int = when (type) {
        ArmorItem.Type.HELMET -> 3
        ArmorItem.Type.CHESTPLATE -> 8
        ArmorItem.Type.LEGGINGS -> 6
        ArmorItem.Type.BOOTS -> 3
        else -> 0
    }

    override fun getEnchantmentValue(): Int = 15

    override fun getEquipSound(): SoundEvent = SoundEvents.ARMOR_EQUIP_IRON

    override fun getRepairIngredient(): Ingredient = Ingredient.EMPTY

    override fun getName(): String = "cd_tactical"

    override fun getToughness(): Float = 1.0f

    override fun getKnockbackResistance(): Float = 0.0f
}

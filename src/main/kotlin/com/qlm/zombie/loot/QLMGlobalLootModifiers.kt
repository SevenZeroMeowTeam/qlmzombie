package com.qlm.zombie.loot

import com.mojang.serialization.Codec

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.item.QLMItems
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraftforge.common.loot.IGlobalLootModifier
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

object QLMGlobalLootModifiers {
    private val SERIALIZERS = DeferredRegister.create(
        ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
        QLMZombieMod.MOD_ID
    )

    val CHEST_LOOT_MODIFIER: Codec<QLMChestLootModifier> = Codec.unit(QLMChestLootModifier())

    fun register(eventBus: IEventBus) {
        SERIALIZERS.register("qlm_chest_loot") { CHEST_LOOT_MODIFIER }
        // Note: drop_the_meat codec is registered separately via DropTheMeatLootModifier.registerCodecs()
        SERIALIZERS.register(eventBus)
    }
}

class QLMChestLootModifier : IGlobalLootModifier {
    override fun codec(): Codec<out IGlobalLootModifier> = QLMGlobalLootModifiers.CHEST_LOOT_MODIFIER

    override fun apply(
        generatedLoot: ObjectArrayList<ItemStack>,
        context: LootContext
    ): ObjectArrayList<ItemStack> {
        val level = context.level
        if (level.isClientSide) return generatedLoot

        val lootEntries = listOf(
            QLMItems.ZOMBIE_CORE.get() to 0.10,
            QLMItems.INFECTED_ESSENCE.get() to 0.15,
            QLMItems.SURVIVAL_KIT.get() to 0.08,
            QLMItems.ANTIDOTE.get() to 0.05,
            QLMItems.MEDICAL_SUPPLY.get() to 0.10,
            QLMItems.REINFORCED_PARTS.get() to 0.06,
            QLMItems.BIOHAZARD_SAMPLE.get() to 0.02,
            QLMItems.EMERGENCY_RATION.get() to 0.12,
            QLMItems.TACTICAL_AMMO.get() to 0.08,
            QLMItems.FAKE_PLAYER_SPAWN_EGG.get() to 0.01,
        )

        for ((item, chance) in lootEntries) {
            if (level.random.nextFloat() < chance) {
                generatedLoot.add(ItemStack(item))
            }
        }

        return generatedLoot
    }
}
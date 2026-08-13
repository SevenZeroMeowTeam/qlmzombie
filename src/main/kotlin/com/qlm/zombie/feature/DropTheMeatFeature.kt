package com.qlm.zombie.feature

import com.mojang.serialization.Codec
import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Cow
import net.minecraft.world.entity.animal.Pig
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraftforge.common.loot.IGlobalLootModifier
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.eventbus.api.IEventBus

class DropTheMeatLootModifier : IGlobalLootModifier {
    companion object {
        val CODEC: Codec<DropTheMeatLootModifier> = Codec.unit(DropTheMeatLootModifier())

        private val SERIALIZERS = DeferredRegister.create(
            ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
            QLMZombieMod.MOD_ID
        )

        fun registerCodecs(eventBus: IEventBus) {
            SERIALIZERS.register("drop_the_meat") { CODEC }
            SERIALIZERS.register(eventBus)
        }
    }

    override fun codec(): Codec<out IGlobalLootModifier> = CODEC

    override fun apply(
        generatedLoot: ObjectArrayList<ItemStack>,
        context: LootContext
    ): ObjectArrayList<ItemStack> {
        if (!QLMConfig.enableDropTheMeat) return generatedLoot

        val entity = context.getParamOrNull(LootContextParams.THIS_ENTITY) as? LivingEntity ?: return generatedLoot
        val level = context.level

        for (entry in getMeatDrops(entity)) {
            if (level.random.nextFloat() < entry.chance) {
                generatedLoot.add(entry.stack)
            }
        }

        return generatedLoot
    }

    private data class MeatDrop(val stack: ItemStack, val chance: Float)

    private fun getMeatDrops(entity: LivingEntity): List<MeatDrop> {
        return when (entity) {
            is Zombie -> listOf(MeatDrop(ItemStack(Items.ROTTEN_FLESH, 1), 0.3f))
            is Cow -> listOf(
                MeatDrop(ItemStack(Items.BEEF, 1), 1.0f),
                MeatDrop(ItemStack(Items.LEATHER, 1), 0.5f)
            )
            is Pig -> listOf(MeatDrop(ItemStack(Items.PORKCHOP, 1), 1.0f))
            is net.minecraft.world.entity.animal.Sheep -> listOf(
                MeatDrop(ItemStack(Items.MUTTON, 1), 1.0f)
            )
            is net.minecraft.world.entity.animal.Chicken -> listOf(
                MeatDrop(ItemStack(Items.CHICKEN, 1), 1.0f)
            )
            is net.minecraft.world.entity.animal.Rabbit -> listOf(
                MeatDrop(ItemStack(Items.RABBIT, 1), 1.0f)
            )
            is net.minecraft.world.entity.animal.Fox -> listOf(
                MeatDrop(ItemStack(Items.COD, 1), 0.5f)
            )
            is net.minecraft.world.entity.monster.Drowned -> listOf(
                MeatDrop(ItemStack(Items.ROTTEN_FLESH, 1), 0.3f)
            )
            is net.minecraft.world.entity.monster.Husk -> listOf(
                MeatDrop(ItemStack(Items.ROTTEN_FLESH, 1), 0.3f)
            )
            is net.minecraft.world.entity.monster.ZombieVillager -> listOf(
                MeatDrop(ItemStack(Items.ROTTEN_FLESH, 1), 0.3f)
            )
            else -> emptyList()
        }
    }
}

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object DropTheMeatEventHandler {

    @SubscribeEvent
    fun onLivingDrops(event: LivingDropsEvent) {
        if (!QLMConfig.enableDropTheMeat) return

        val entity = event.entity
        val level = entity.level()
        if (level.isClientSide) return

        val drops = getDropsForEntity(entity)
        for ((stack, chance) in drops) {
            if (level.random.nextFloat() < chance) {
                val itemEntity = net.minecraft.world.entity.item.ItemEntity(
                    level,
                    entity.x, entity.y, entity.z,
                    stack
                )
                event.drops.add(itemEntity)
            }
        }
    }

    private data class DropEntry(val stack: ItemStack, val chance: Float)

    private fun getDropsForEntity(entity: LivingEntity): List<DropEntry> {
        return when (entity) {
            is Zombie -> listOf(DropEntry(ItemStack(Items.ROTTEN_FLESH, 1), 0.3f))
            is Cow -> listOf(
                DropEntry(ItemStack(Items.BEEF, 1), 1.0f),
                DropEntry(ItemStack(Items.LEATHER, 1), 0.5f)
            )
            is Pig -> listOf(DropEntry(ItemStack(Items.PORKCHOP, 1), 1.0f))
            is net.minecraft.world.entity.animal.Sheep -> listOf(
                DropEntry(ItemStack(Items.MUTTON, 1), 1.0f)
            )
            is net.minecraft.world.entity.animal.Chicken -> listOf(
                DropEntry(ItemStack(Items.CHICKEN, 1), 1.0f)
            )
            is net.minecraft.world.entity.animal.Rabbit -> listOf(
                DropEntry(ItemStack(Items.RABBIT, 1), 1.0f)
            )
            is net.minecraft.world.entity.animal.Fox -> listOf(
                DropEntry(ItemStack(Items.COD, 1), 0.5f)
            )
            is net.minecraft.world.entity.monster.Drowned -> listOf(
                DropEntry(ItemStack(Items.ROTTEN_FLESH, 1), 0.3f)
            )
            is net.minecraft.world.entity.monster.Husk -> listOf(
                DropEntry(ItemStack(Items.ROTTEN_FLESH, 1), 0.3f)
            )
            is net.minecraft.world.entity.monster.ZombieVillager -> listOf(
                DropEntry(ItemStack(Items.ROTTEN_FLESH, 1), 0.3f)
            )
            else -> emptyList()
        }
    }
}
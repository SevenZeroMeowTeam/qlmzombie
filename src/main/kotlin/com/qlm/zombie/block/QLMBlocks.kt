package com.qlm.zombie.block

import com.qlm.zombie.QLMZombieMod
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object QLMBlocks {
    private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, QLMZombieMod.MOD_ID)
    private val BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, QLMZombieMod.MOD_ID)

    @JvmField
    val SLEEPING_BAG: RegistryObject<Block> = BLOCKS.register("sleeping_bag") {
        SleepingBagBlock()
    }

    @JvmField
    val SLEEPING_BAG_ITEM: RegistryObject<BlockItem> = BLOCK_ITEMS.register("sleeping_bag") {
        BlockItem(SLEEPING_BAG.get(), Item.Properties().rarity(net.minecraft.world.item.Rarity.COMMON).stacksTo(1))
    }

    fun register(eventBus: IEventBus) {
        BLOCKS.register(eventBus)
        BLOCK_ITEMS.register(eventBus)
    }
}

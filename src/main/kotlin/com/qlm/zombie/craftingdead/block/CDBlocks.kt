package com.qlm.zombie.craftingdead.block

import com.qlm.zombie.QLMZombieMod
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object CDBlocks {
    private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, QLMZombieMod.MOD_ID)
    private val BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, QLMZombieMod.MOD_ID)
    private val BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, QLMZombieMod.MOD_ID)

    @JvmField
    val MEDICAL_SUPPLY_CRATE: RegistryObject<Block> = BLOCKS.register("medical_supply_crate") {
        MedicalSupplyCrateBlock()
    }

    @JvmField
    val AMMO_CRATE: RegistryObject<Block> = BLOCKS.register("ammo_crate") {
        AmmoCrateBlock()
    }

    @JvmField
    val SUPPLY_CRATE: RegistryObject<Block> = BLOCKS.register("supply_crate") {
        SupplyCrateBlock()
    }

    @JvmField
    val SUPPLY_CRATE_ENTITY: RegistryObject<BlockEntityType<SupplyCrateBlockEntity>> = BLOCK_ENTITIES.register("supply_crate") {
        BlockEntityType.Builder.of(::SupplyCrateBlockEntity, SUPPLY_CRATE.get()).build(null)
    }

    @JvmField
    val MEDICAL_SUPPLY_CRATE_ITEM: RegistryObject<BlockItem> = BLOCK_ITEMS.register("medical_supply_crate") {
        BlockItem(MEDICAL_SUPPLY_CRATE.get(), Item.Properties())
    }

    @JvmField
    val AMMO_CRATE_ITEM: RegistryObject<BlockItem> = BLOCK_ITEMS.register("ammo_crate") {
        BlockItem(AMMO_CRATE.get(), Item.Properties())
    }

    @JvmField
    val SUPPLY_CRATE_ITEM: RegistryObject<BlockItem> = BLOCK_ITEMS.register("supply_crate") {
        BlockItem(SUPPLY_CRATE.get(), Item.Properties())
    }

    fun register(eventBus: IEventBus) {
        BLOCKS.register(eventBus)
        BLOCK_ITEMS.register(eventBus)
        BLOCK_ENTITIES.register(eventBus)
    }
}

class SupplyCrateBlock : Block(
    BlockBehaviour.Properties.of()
        .mapColor(MapColor.WOOD)
        .strength(2.0f, 3.0f)
        .sound(SoundType.WOOD)
) {
    companion object
}

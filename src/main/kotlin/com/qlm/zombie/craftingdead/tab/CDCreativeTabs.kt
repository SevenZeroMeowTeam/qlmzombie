package com.qlm.zombie.craftingdead.tab

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.block.CDBlocks
import com.qlm.zombie.craftingdead.item.CDItems
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object CDCreativeTabs {
    private val TABS = DeferredRegister.create<CreativeModeTab>(ResourceLocation.withDefaultNamespace("creative_mode_tab"), QLMZombieMod.MOD_ID)

    val CD_COMBAT: RegistryObject<CreativeModeTab> = TABS.register("cd_combat") {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_combat"))
            .icon { ItemStack(CDItems.CROWBAR.get()) }
            .displayItems { _, output ->
                output.accept(CDItems.CROWBAR.get())
                output.accept(CDItems.COMBAT_KNIFE.get())
                output.accept(CDItems.BOWIE_KNIFE.get())
            }
            .build()
    }

    val CD_EQUIPMENT: RegistryObject<CreativeModeTab> = TABS.register("cd_equipment") {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_equipment"))
            .icon { ItemStack(CDItems.SOLDIER_ZOMBIE_SPAWN_EGG.get()) }
            .displayItems { _, output ->
                output.accept(CDItems.SOLDIER_ZOMBIE_SPAWN_EGG.get())
                output.accept(CDItems.SCIENTIST_ZOMBIE_SPAWN_EGG.get())
                output.accept(CDItems.CIVILIAN_ZOMBIE_SPAWN_EGG.get())
            }
            .build()
    }

    val CD_BLOCKS: RegistryObject<CreativeModeTab> = TABS.register("cd_blocks") {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_blocks"))
            .icon { ItemStack(CDBlocks.MEDICAL_SUPPLY_CRATE_ITEM.get()) }
            .displayItems { _, output ->
                output.accept(CDBlocks.MEDICAL_SUPPLY_CRATE_ITEM.get())
                output.accept(CDBlocks.AMMO_CRATE_ITEM.get())
                output.accept(CDBlocks.SUPPLY_CRATE_ITEM.get())
            }
            .build()
    }

    fun register(eventBus: IEventBus) {
        TABS.register(eventBus)
    }
}
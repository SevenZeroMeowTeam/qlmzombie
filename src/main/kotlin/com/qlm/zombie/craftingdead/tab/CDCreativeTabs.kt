package com.qlm.zombie.craftingdead.tab

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.block.CDBlocks
import com.qlm.zombie.craftingdead.item.CDItems
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object CDCreativeTabs {
    private val TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, QLMZombieMod.MOD_ID)

    val CD_COMBAT: RegistryObject<CreativeModeTab> = TABS.register("cd_combat") {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_combat"))
            .icon { ItemStack(CDItems.AK47.get()) }
            .displayItems { _, output ->
                output.accept(CDItems.AK47.get())
                output.accept(CDItems.M4A1.get())
                output.accept(CDItems.MP5.get())
                output.accept(CDItems.M1014.get())
                output.accept(CDItems.DESERT_EAGLE.get())
                output.accept(CDItems.GLOCK17.get())
                output.accept(CDItems.BARRETT_M82.get())
                output.accept(CDItems.AWM.get())
                output.accept(CDItems.BOWIE_KNIFE.get())
                output.accept(CDItems.COMBAT_KNIFE.get())
                output.accept(CDItems.CROWBAR.get())
                output.accept(CDItems.FRAGMENT_GRENADE.get())
                output.accept(CDItems.FLASHBANG.get())
                output.accept(CDItems.MOLOTOV.get())
                output.accept(CDItems.RIFLE_AMMO.get())
                output.accept(CDItems.PISTOL_AMMO.get())
                output.accept(CDItems.SHOTGUN_SHELL.get())
                output.accept(CDItems.SNIPER_AMMO.get())
            }
            .build()
    }

    val CD_MEDICAL: RegistryObject<CreativeModeTab> = TABS.register("cd_medical") {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_medical"))
            .icon { ItemStack(CDItems.FIRST_AID_KIT.get()) }
            .displayItems { _, output ->
                output.accept(CDItems.BANDAGE.get())
                output.accept(CDItems.FIRST_AID_KIT.get())
                output.accept(CDItems.ADRENALINE_SYRINGE.get())
                output.accept(CDItems.PAINKILLERS.get())
                output.accept(CDItems.TOURNIQUET.get())
                output.accept(CDItems.SALINE_BAG.get())
                output.accept(CDItems.SPLINT.get())
                output.accept(CDItems.SURGICAL_SCISSORS.get())
            }
            .build()
    }

    val CD_EQUIPMENT: RegistryObject<CreativeModeTab> = TABS.register("cd_equipment") {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_equipment"))
            .icon { ItemStack(CDItems.PLATE_CARRIER.get()) }
            .displayItems { _, output ->
                output.accept(CDItems.BALLISTIC_HELMET.get())
                output.accept(CDItems.PLATE_CARRIER.get())
                output.accept(CDItems.TACTICAL_VEST.get())
                output.accept(CDItems.COMBAT_BOOTS.get())
                output.accept(CDItems.RED_DOT_SIGHT.get())
                output.accept(CDItems.HOLOGRAPHIC_SIGHT.get())
                output.accept(CDItems.ACOG_SIGHT.get())
                output.accept(CDItems.SNIPER_SCOPE.get())
                output.accept(CDItems.ANGLED_GRIP.get())
                output.accept(CDItems.VERTICAL_GRIP.get())
                output.accept(CDItems.MACHINE_GRIP.get())
                output.accept(CDItems.SHORT_BARREL.get())
                output.accept(CDItems.LONG_BARREL.get())
                output.accept(CDItems.SUPPRESSOR.get())
                output.accept(CDItems.EXTENDED_MAG.get())
                output.accept(CDItems.FAST_MAG.get())
                output.accept(CDItems.DRUM_MAG.get())
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
            }
            .build()
    }

    fun register(eventBus: IEventBus) {
        TABS.register(eventBus)
    }
}
package com.qlm.zombie.item

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.entity.QLMEntities
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraftforge.common.ForgeSpawnEggItem
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object QLMItems {
    private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, QLMZombieMod.MOD_ID)

    @JvmField
    val ZOMBIE_CORE: RegistryObject<Item> = ITEMS.register("zombie_core") {
        Item(Item.Properties().rarity(Rarity.RARE))
    }

    @JvmField
    val INFECTED_ESSENCE: RegistryObject<Item> = ITEMS.register("infected_essence") {
        Item(Item.Properties().rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val SURVIVAL_KIT: RegistryObject<Item> = ITEMS.register("survival_kit") {
        Item(Item.Properties().rarity(Rarity.COMMON))
    }

    @JvmField
    val EMERGENCY_RATION: RegistryObject<Item> = ITEMS.register("emergency_ration") {
        Item(Item.Properties().rarity(Rarity.COMMON))
    }

    @JvmField
    val MEDICAL_SUPPLY: RegistryObject<Item> = ITEMS.register("medical_supply") {
        Item(Item.Properties().rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val REINFORCED_PARTS: RegistryObject<Item> = ITEMS.register("reinforced_parts") {
        Item(Item.Properties().rarity(Rarity.RARE))
    }

    @JvmField
    val BIOHAZARD_SAMPLE: RegistryObject<Item> = ITEMS.register("biohazard_sample") {
        Item(Item.Properties().rarity(Rarity.EPIC))
    }

    /**
     * 下界星核（神话合成核心）：神话装备合成的必需核心物品。
     * 由下界合金锭 + 钻石 + 金苹果等高阶材料合成，合成神话装备时必须消耗 1 个。
     */
    @JvmField
    val MYTHIC_CORE: RegistryObject<Item> = ITEMS.register("mythic_core") {
        Item(Item.Properties().rarity(Rarity.EPIC).fireResistant())
    }

    @JvmField
    val TACTICAL_AMMO: RegistryObject<Item> = ITEMS.register("tactical_ammo") {
        Item(Item.Properties().rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val ANTIDOTE: RegistryObject<Item> = ITEMS.register("antidote") {
        AntidoteItem()
    }

    @JvmField
    val FAKE_PLAYER_SPAWN_EGG: RegistryObject<Item> = ITEMS.register("fake_player_spawn_egg") {
        ForgeSpawnEggItem({ QLMEntities.FAKE_PLAYER.get() as net.minecraft.world.entity.EntityType<out Mob> }, 0x3B5998, 0xDFE3EE,
            Item.Properties().rarity(Rarity.RARE))
    }

    @JvmField
    val PLANK_AXE: RegistryObject<Item> = ITEMS.register("plank_axe") {
        PlankAxeItem()
    }

    @JvmField
    val PLANK_COLLECTOR: RegistryObject<Item> = ITEMS.register("plank_collector") {
        PlankCollectorItem()
    }

    @JvmField
    val PURIFIED_WATER_BOTTLE: RegistryObject<Item> = ITEMS.register("purified_water_bottle") {
        PurifiedWaterItem(Item.Properties().stacksTo(16))
    }

    @JvmField
    val AI_CALLER: RegistryObject<Item> = ITEMS.register("ai_caller") {
        Item(Item.Properties().rarity(Rarity.RARE))
    }

    @JvmField
    val AI_RECOVER: RegistryObject<Item> = ITEMS.register("ai_recover") {
        Item(Item.Properties().rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val AI_SHIELD: RegistryObject<Item> = ITEMS.register("ai_shield") {
        Item(Item.Properties().rarity(Rarity.RARE))
    }

    @JvmField
    val AI_SPEED_PILL: RegistryObject<Item> = ITEMS.register("ai_speed_pill") {
        Item(Item.Properties().rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val MODE_SWITCH: RegistryObject<Item> = ITEMS.register("mode_switch") {
        Item(Item.Properties().rarity(Rarity.EPIC))
    }

    fun register(eventBus: IEventBus) {
        ITEMS.register(eventBus)
    }
}

package com.qlm.zombie.craftingdead.item

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.craftingdead.effect.CDEffects
import com.qlm.zombie.craftingdead.entity.CDEntities
import com.qlm.zombie.craftingdead.entity.ThrownGrenadeEntity
import com.qlm.zombie.craftingdead.item.gun.AbstractGunItem
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.SwordItem
import net.minecraft.world.item.Tiers
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.level.Level
import net.minecraftforge.common.ForgeSpawnEggItem
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object CDItems {
    private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, QLMZombieMod.MOD_ID)

    // ===== Weapons =====

    @JvmField
    val AK47: RegistryObject<AbstractGunItem> = ITEMS.register("ak47") {
        object : AbstractGunItem() {
            override val fireRate: Int = 8
            override val damage: Float = 10f
            override val magazineSize: Int = 30
        }
    }

    @JvmField
    val M4A1: RegistryObject<AbstractGunItem> = ITEMS.register("m4a1") {
        object : AbstractGunItem() {
            override val fireRate: Int = 6
            override val damage: Float = 9f
            override val magazineSize: Int = 30
        }
    }

    @JvmField
    val MP5: RegistryObject<AbstractGunItem> = ITEMS.register("mp5") {
        object : AbstractGunItem() {
            override val fireRate: Int = 3
            override val damage: Float = 7f
            override val magazineSize: Int = 30
        }
    }

    @JvmField
    val M1014: RegistryObject<AbstractGunItem> = ITEMS.register("m1014") {
        object : AbstractGunItem() {
            override val fireRate: Int = 40
            override val damage: Float = 20f
            override val magazineSize: Int = 8
        }
    }

    @JvmField
    val DESERT_EAGLE: RegistryObject<AbstractGunItem> = ITEMS.register("desert_eagle") {
        object : AbstractGunItem() {
            override val fireRate: Int = 30
            override val damage: Float = 40f
            override val magazineSize: Int = 7
        }
    }

    @JvmField
    val GLOCK17: RegistryObject<AbstractGunItem> = ITEMS.register("glock17") {
        object : AbstractGunItem() {
            override val fireRate: Int = 5
            override val damage: Float = 8f
            override val magazineSize: Int = 17
        }
    }

    @JvmField
    val BARRETT_M82: RegistryObject<AbstractGunItem> = ITEMS.register("barrett_m82") {
        object : AbstractGunItem() {
            override val fireRate: Int = 80
            override val damage: Float = 120f
            override val magazineSize: Int = 5
        }
    }

    @JvmField
    val AWM: RegistryObject<AbstractGunItem> = ITEMS.register("awm") {
        object : AbstractGunItem() {
            override val fireRate: Int = 80
            override val damage: Float = 110f
            override val magazineSize: Int = 5
        }
    }

    // ===== Attachments =====

    @JvmField
    val RED_DOT_SIGHT: RegistryObject<Item> = ITEMS.register("red_dot_sight") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val HOLOGRAPHIC_SIGHT: RegistryObject<Item> = ITEMS.register("holographic_sight") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val ACOG_SIGHT: RegistryObject<Item> = ITEMS.register("acog_sight") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    }

    @JvmField
    val SNIPER_SCOPE: RegistryObject<Item> = ITEMS.register("sniper_scope") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    }

    @JvmField
    val ANGLED_GRIP: RegistryObject<Item> = ITEMS.register("angled_grip") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val VERTICAL_GRIP: RegistryObject<Item> = ITEMS.register("vertical_grip") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val MACHINE_GRIP: RegistryObject<Item> = ITEMS.register("machine_grip") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    }

    @JvmField
    val SHORT_BARREL: RegistryObject<Item> = ITEMS.register("short_barrel") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val LONG_BARREL: RegistryObject<Item> = ITEMS.register("long_barrel") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    }

    @JvmField
    val SUPPRESSOR: RegistryObject<Item> = ITEMS.register("suppressor") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    }

    @JvmField
    val EXTENDED_MAG: RegistryObject<Item> = ITEMS.register("extended_mag") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val FAST_MAG: RegistryObject<Item> = ITEMS.register("fast_mag") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val DRUM_MAG: RegistryObject<Item> = ITEMS.register("drum_mag") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    }

    // ===== Melee =====

    @JvmField
    val BOWIE_KNIFE: RegistryObject<SwordItem> = ITEMS.register("bowie_knife") {
        SwordItem(Tiers.IRON, 6, 1.5f, Item.Properties())
    }

    @JvmField
    val COMBAT_KNIFE: RegistryObject<SwordItem> = ITEMS.register("combat_knife") {
        SwordItem(Tiers.IRON, 4, 2.0f, Item.Properties())
    }

    @JvmField
    val CROWBAR: RegistryObject<SwordItem> = ITEMS.register("crowbar") {
        SwordItem(Tiers.IRON, 5, 1.5f, Item.Properties())
    }

    // ===== Grenades =====

    @JvmField
    val FRAGMENT_GRENADE: RegistryObject<Item> = ITEMS.register("fragment_grenade") {
        object : Item(Item.Properties().stacksTo(16)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                val entity = ThrownGrenadeEntity(CDEntities.THROWN_GRENADE.get(), player, level, ThrownGrenadeEntity.GrenadeType.FRAGMENT)
                entity.shootFromRotation(player, player.xRot, player.yRot, 0.0f, 1.5f, 0.5f)
                level.addFreshEntity(entity)
                if (!player.abilities.instabuild) {
                    stack.shrink(1)
                }
                return InteractionResultHolder.consume(stack)
            }
        }
    }

    @JvmField
    val FLASHBANG: RegistryObject<Item> = ITEMS.register("flashbang") {
        object : Item(Item.Properties().stacksTo(16)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                val entity = ThrownGrenadeEntity(CDEntities.THROWN_GRENADE.get(), player, level, ThrownGrenadeEntity.GrenadeType.FLASHBANG)
                entity.shootFromRotation(player, player.xRot, player.yRot, 0.0f, 1.5f, 0.5f)
                level.addFreshEntity(entity)
                if (!player.abilities.instabuild) {
                    stack.shrink(1)
                }
                return InteractionResultHolder.consume(stack)
            }
        }
    }

    @JvmField
    val MOLOTOV: RegistryObject<Item> = ITEMS.register("molotov") {
        object : Item(Item.Properties().stacksTo(16)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                val entity = ThrownGrenadeEntity(CDEntities.THROWN_GRENADE.get(), player, level, ThrownGrenadeEntity.GrenadeType.MOLOTOV)
                entity.shootFromRotation(player, player.xRot, player.yRot, 0.0f, 1.5f, 0.5f)
                level.addFreshEntity(entity)
                if (!player.abilities.instabuild) {
                    stack.shrink(1)
                }
                return InteractionResultHolder.consume(stack)
            }
        }
    }

    // ===== Medical =====

    @JvmField
    val BANDAGE: RegistryObject<Item> = ITEMS.register("bandage") {
        object : Item(Item.Properties().stacksTo(16)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                if (player.health < player.maxHealth) {
                    player.heal(5f)
                    player.cooldowns.addCooldown(this, 20 * 3)
                    if (!player.abilities.instabuild) {
                        stack.shrink(1)
                    }
                    return InteractionResultHolder.consume(stack)
                }
                return InteractionResultHolder.pass(stack)
            }
        }
    }

    @JvmField
    val FIRST_AID_KIT: RegistryObject<Item> = ITEMS.register("first_aid_kit") {
        object : Item(Item.Properties().stacksTo(4)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                if (player.health < player.maxHealth) {
                    player.heal(20f)
                    player.cooldowns.addCooldown(this, 20 * 10)
                    if (!player.abilities.instabuild) {
                        stack.shrink(1)
                    }
                    return InteractionResultHolder.consume(stack)
                }
                return InteractionResultHolder.pass(stack)
            }
        }
    }

    @JvmField
    val ADRENALINE_SYRINGE: RegistryObject<Item> = ITEMS.register("adrenaline_syringe") {
        object : Item(Item.Properties().stacksTo(8)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 15, 2))
                player.addEffect(MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 15, 1))
                if (!player.abilities.instabuild) {
                    stack.shrink(1)
                }
                return InteractionResultHolder.consume(stack)
            }
        }
    }

    @JvmField
    val PAINKILLERS: RegistryObject<Item> = ITEMS.register("painkillers") {
        object : Item(Item.Properties().stacksTo(16)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                player.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 30, 0))
                if (!player.abilities.instabuild) {
                    stack.shrink(1)
                }
                return InteractionResultHolder.consume(stack)
            }
        }
    }

    @JvmField
    val TOURNIQUET: RegistryObject<Item> = ITEMS.register("tourniquet") {
        object : Item(Item.Properties().stacksTo(8)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                player.removeEffect(CDEffects.BLEEDING.get())
                player.heal(2f)
                if (!player.abilities.instabuild) {
                    stack.shrink(1)
                }
                return InteractionResultHolder.consume(stack)
            }
        }
    }

    @JvmField
    val SALINE_BAG: RegistryObject<Item> = ITEMS.register("saline_bag") {
        object : Item(Item.Properties().stacksTo(4)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                if (player.health < player.maxHealth) {
                    player.heal(10f)
                    if (!player.abilities.instabuild) {
                        stack.shrink(1)
                    }
                    return InteractionResultHolder.consume(stack)
                }
                return InteractionResultHolder.pass(stack)
            }
        }
    }

    @JvmField
    val SPLINT: RegistryObject<Item> = ITEMS.register("splint") {
        object : Item(Item.Properties().stacksTo(8)) {
            override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
                val stack = player.getItemInHand(usedHand)
                if (level.isClientSide) return InteractionResultHolder.success(stack)
                player.removeEffect(CDEffects.FRACTURE.get())
                player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 5, 0))
                if (!player.abilities.instabuild) {
                    stack.shrink(1)
                }
                return InteractionResultHolder.consume(stack)
            }
        }
    }

    @JvmField
    val SURGICAL_SCISSORS: RegistryObject<Item> = ITEMS.register("surgical_scissors") {
        Item(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    // ===== Armor =====

    @JvmField
    val BALLISTIC_HELMET: RegistryObject<ArmorItem> = ITEMS.register("ballistic_helmet") {
        ArmorItem(CDArmorMaterial, ArmorItem.Type.HELMET, Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val PLATE_CARRIER: RegistryObject<ArmorItem> = ITEMS.register("plate_carrier") {
        ArmorItem(CDArmorMaterial, ArmorItem.Type.CHESTPLATE, Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    }

    @JvmField
    val TACTICAL_VEST: RegistryObject<ArmorItem> = ITEMS.register("tactical_vest") {
        ArmorItem(CDArmorMaterial, ArmorItem.Type.CHESTPLATE, Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    @JvmField
    val COMBAT_BOOTS: RegistryObject<ArmorItem> = ITEMS.register("combat_boots") {
        ArmorItem(CDArmorMaterial, ArmorItem.Type.BOOTS, Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    }

    // ===== Ammo =====

    @JvmField
    val RIFLE_AMMO: RegistryObject<Item> = ITEMS.register("rifle_ammo") {
        Item(Item.Properties().stacksTo(64).rarity(Rarity.COMMON))
    }

    @JvmField
    val PISTOL_AMMO: RegistryObject<Item> = ITEMS.register("pistol_ammo") {
        Item(Item.Properties().stacksTo(64).rarity(Rarity.COMMON))
    }

    @JvmField
    val SHOTGUN_SHELL: RegistryObject<Item> = ITEMS.register("shotgun_shell") {
        Item(Item.Properties().stacksTo(32).rarity(Rarity.COMMON))
    }

    @JvmField
    val SNIPER_AMMO: RegistryObject<Item> = ITEMS.register("sniper_ammo") {
        Item(Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))
    }

    // ===== Spawn Eggs =====

    @JvmField
    val SOLDIER_ZOMBIE_SPAWN_EGG: RegistryObject<Item> = ITEMS.register("soldier_zombie_spawn_egg") {
        ForgeSpawnEggItem(
            { CDEntities.SOLDIER_ZOMBIE.get() as EntityType<out Mob> },
            0x8B0000, 0x2F2F2F,
            Item.Properties().rarity(Rarity.RARE)
        )
    }

    @JvmField
    val SCIENTIST_ZOMBIE_SPAWN_EGG: RegistryObject<Item> = ITEMS.register("scientist_zombie_spawn_egg") {
        ForgeSpawnEggItem(
            { CDEntities.SCIENTIST_ZOMBIE.get() as EntityType<out Mob> },
            0x00AA00, 0xFFFFFF,
            Item.Properties().rarity(Rarity.RARE)
        )
    }

    @JvmField
    val CIVILIAN_ZOMBIE_SPAWN_EGG: RegistryObject<Item> = ITEMS.register("civilian_zombie_spawn_egg") {
        ForgeSpawnEggItem(
            { CDEntities.CIVILIAN_ZOMBIE.get() as EntityType<out Mob> },
            0x555555, 0xAAAAAA,
            Item.Properties().rarity(Rarity.UNCOMMON)
        )
    }

    // ===== Java-compatible CD_ prefixed aliases =====

    @JvmField val CD_AMMO_556X45: RegistryObject<Item> = RIFLE_AMMO
    @JvmField val CD_AMMO_762X39: RegistryObject<Item> = RIFLE_AMMO
    @JvmField val CD_AMMO_9X19: RegistryObject<Item> = PISTOL_AMMO
    @JvmField val CD_AMMO_12_GAUGE: RegistryObject<Item> = SHOTGUN_SHELL
    @JvmField val CD_AMMO_45_ACP: RegistryObject<Item> = PISTOL_AMMO
    @JvmField val CD_AMMO_50_BMG: RegistryObject<Item> = SNIPER_AMMO
    @JvmField val CD_AMMO_338_LAPUA: RegistryObject<Item> = SNIPER_AMMO

    @JvmField val CD_BANDAGE: RegistryObject<Item> = BANDAGE
    @JvmField val CD_FIRST_AID_KIT: RegistryObject<Item> = FIRST_AID_KIT
    @JvmField val CD_ADRENALINE_SYRINGE: RegistryObject<Item> = ADRENALINE_SYRINGE
    @JvmField val CD_SPLINT: RegistryObject<Item> = SPLINT
    @JvmField val CD_PAINKILLERS: RegistryObject<Item> = PAINKILLERS
    @JvmField val CD_TOURNIQUET: RegistryObject<Item> = TOURNIQUET
    @JvmField val CD_SALINE_BAG: RegistryObject<Item> = SALINE_BAG
    @JvmField val CD_SURGICAL_SCISSORS: RegistryObject<Item> = SURGICAL_SCISSORS

    @JvmField val CD_FRAGMENT_GRENADE: RegistryObject<Item> = FRAGMENT_GRENADE
    @JvmField val CD_FLASHBANG: RegistryObject<Item> = FLASHBANG
    @JvmField val CD_MOLOTOV: RegistryObject<Item> = MOLOTOV

    @JvmStatic
    fun isQualityItem(item: Item): Boolean {
        // 武器/医疗物品/近战工具/弹药/护甲都受品质系统影响
        if (item is com.qlm.zombie.craftingdead.item.gun.AbstractGunItem) return true
        val cn = item.javaClass.name
        return cn.contains("craftingdead.item")
                && (cn.contains("Grenade") || cn.contains("Bandage") || cn.contains("Kit")
                || cn.contains("Syringe") || cn.contains("Splint") || cn.contains("Tourniquet")
                || cn.contains("Knife") || cn.contains("Crowbar") || cn.contains("Melee")
                || cn.contains("AmmoItem") || cn.contains("Armor") || cn.contains("Helmet")
                || cn.contains("Vest") || cn.contains("Boots"))
    }

    fun register(eventBus: IEventBus) {
        ITEMS.register(eventBus)
    }
}

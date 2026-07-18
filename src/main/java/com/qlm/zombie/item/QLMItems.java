package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.entity.QLMEntities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class QLMItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, QLMZombieMod.MOD_ID);

    public static final RegistryObject<Item> ZOMBIE_CORE = ITEMS.register("zombie_core", 
        () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    
    public static final RegistryObject<Item> INFECTED_ESSENCE = ITEMS.register("infected_essence", 
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    
    public static final RegistryObject<Item> SURVIVAL_KIT = ITEMS.register("survival_kit", 
        () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
    
    public static final RegistryObject<Item> EMERGENCY_RATION = ITEMS.register("emergency_ration", 
        () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
    
    public static final RegistryObject<Item> MEDICAL_SUPPLY = ITEMS.register("medical_supply", 
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    
    public static final RegistryObject<Item> REINFORCED_PARTS = ITEMS.register("reinforced_parts", 
        () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    
    public static final RegistryObject<Item> BIOHAZARD_SAMPLE = ITEMS.register("biohazard_sample", 
        () -> new Item(new Item.Properties().rarity(Rarity.EPIC)));
    
    public static final RegistryObject<Item> TACTICAL_AMMO = ITEMS.register("tactical_ammo", 
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> FAKE_PLAYER_SPAWN_EGG = ITEMS.register("fake_player_spawn_egg",
            () -> new ForgeSpawnEggItem(QLMEntities.FAKE_PLAYER, 0x3B5998, 0xDFE3EE,
                    new Item.Properties().rarity(Rarity.RARE)));
}
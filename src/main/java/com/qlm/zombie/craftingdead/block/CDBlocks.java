package com.qlm.zombie.craftingdead.block;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CDBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, QLMZombieMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, QLMZombieMod.MOD_ID);
    public static final DeferredRegister<Item> BLOCK_ITEM_REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, QLMZombieMod.MOD_ID);

    // ==================== 方块 ====================
    public static final RegistryObject<Block> MEDICAL_SUPPLY_CRATE = BLOCKS.register("cd_medical_supply_crate",
            MedicalSupplyCrateBlock::new);
    public static final RegistryObject<Block> AMMO_CRATE = BLOCKS.register("cd_ammo_crate",
            AmmoCrateBlock::new);

    // ==================== 方块物品（BlockItem） ====================
    public static final RegistryObject<Item> MEDICAL_SUPPLY_CRATE_ITEM = BLOCK_ITEM_REGISTER.register("cd_medical_supply_crate",
            () -> new BlockItem(MEDICAL_SUPPLY_CRATE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> AMMO_CRATE_ITEM = BLOCK_ITEM_REGISTER.register("cd_ammo_crate",
            () -> new BlockItem(AMMO_CRATE.get(), new Item.Properties().stacksTo(16)));

    // ==================== 方块实体 ====================
    public static final RegistryObject<BlockEntityType<SupplyCrateBlockEntity>> SUPPLY_CRATE_ENTITY = BLOCK_ENTITIES.register("cd_supply_crate",
            () -> BlockEntityType.Builder.of(SupplyCrateBlockEntity::new,
                            MEDICAL_SUPPLY_CRATE.get(), AMMO_CRATE.get())
                    .build(null));
}

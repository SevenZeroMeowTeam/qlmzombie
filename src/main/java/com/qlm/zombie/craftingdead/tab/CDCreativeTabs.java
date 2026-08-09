package com.qlm.zombie.craftingdead.tab;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.craftingdead.block.CDBlocks;
import com.qlm.zombie.craftingdead.item.CDItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class CDCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, QLMZombieMod.MOD_ID);

    // 医疗物品标签页
    public static final RegistryObject<CreativeModeTab> CD_MEDICAL_TAB = TABS.register("cd_medical", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_medical"))
            .icon(() -> new ItemStack(CDItems.CD_FIRST_AID_KIT.get()))
            .displayItems((params, output) -> {
                output.accept(CDItems.CD_BANDAGE.get());
                output.accept(CDItems.CD_FIRST_AID_KIT.get());
                output.accept(CDItems.CD_ADRENALINE_SYRINGE.get());
                output.accept(CDItems.CD_PAINKILLERS.get());
                output.accept(CDItems.CD_TOURNIQUET.get());
                output.accept(CDItems.CD_SALINE_BAG.get());
                output.accept(CDItems.CD_SPLINT.get());
                output.accept(CDItems.CD_SURGICAL_SCISSORS.get());
            })
            .build()
    );

    // 战斗物品标签页（枪械/近战/弹药/手雷）
    public static final RegistryObject<CreativeModeTab> CD_COMBAT_TAB = TABS.register("cd_combat", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_combat"))
            .icon(() -> new ItemStack(CDItems.CD_AK47.get()))
            .displayItems((params, output) -> {
                // 枪械
                output.accept(CDItems.CD_AK47.get());
                output.accept(CDItems.CD_M4A1.get());
                output.accept(CDItems.CD_MP5.get());
                output.accept(CDItems.CD_M1014.get());
                output.accept(CDItems.CD_DESERT_EAGLE.get());
                output.accept(CDItems.CD_GLOCK17.get());
                output.accept(CDItems.CD_BARRETT_M82.get());
                output.accept(CDItems.CD_AWM.get());
                // 近战
                output.accept(CDItems.CD_COMBAT_KNIFE.get());
                output.accept(CDItems.CD_BOWIE_KNIFE.get());
                output.accept(CDItems.CD_CROWBAR.get());
                // 弹药
                output.accept(CDItems.CD_AMMO_556X45.get());
                output.accept(CDItems.CD_AMMO_762X39.get());
                output.accept(CDItems.CD_AMMO_9X19.get());
                output.accept(CDItems.CD_AMMO_45_ACP.get());
                output.accept(CDItems.CD_AMMO_12_GAUGE.get());
                output.accept(CDItems.CD_AMMO_50_BMG.get());
                output.accept(CDItems.CD_AMMO_338_LAPUA.get());
                // 手雷
                output.accept(CDItems.CD_FRAGMENT_GRENADE.get());
                output.accept(CDItems.CD_FLASHBANG.get());
                output.accept(CDItems.CD_MOLOTOV.get());
                // 瞄准镜
                output.accept(CDItems.CD_SIGHT_RED_DOT.get());
                output.accept(CDItems.CD_SIGHT_EOTECH.get());
                output.accept(CDItems.CD_SIGHT_ACOG.get());
                output.accept(CDItems.CD_SIGHT_8X.get());
                // 握把/枪管/弹匣附件
                output.accept(CDItems.CD_GRIP_VERTICAL.get());
                output.accept(CDItems.CD_GRIP_ANGLED.get());
                output.accept(CDItems.CD_BIPOD.get());
                output.accept(CDItems.CD_BARREL_SUPPRESSOR.get());
                output.accept(CDItems.CD_BARREL_COMPENSATOR.get());
                output.accept(CDItems.CD_BARREL_EXTENDED.get());
                output.accept(CDItems.CD_MAG_STANDARD.get());
                output.accept(CDItems.CD_MAG_EXTENDED.get());
                output.accept(CDItems.CD_MAG_DRUM.get());
            })
            .build()
    );

    // 装备防具标签页
    public static final RegistryObject<CreativeModeTab> CD_GEAR_TAB = TABS.register("cd_gear", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_gear"))
            .icon(() -> new ItemStack(CDItems.CD_PLATE_CARRIER.get()))
            .displayItems((params, output) -> {
                output.accept(CDItems.CD_BALLISTIC_HELMET.get());
                output.accept(CDItems.CD_PLATE_CARRIER.get());
                output.accept(CDItems.CD_TACTICAL_VEST.get());
                output.accept(CDItems.CD_COMBAT_BOOTS.get());
            })
            .build()
    );

    // 方块标签页
    public static final RegistryObject<CreativeModeTab> CD_BLOCKS_TAB = TABS.register("cd_blocks", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.cd_blocks"))
            .icon(() -> new ItemStack(CDBlocks.MEDICAL_SUPPLY_CRATE.get()))
            .displayItems((params, output) -> {
                output.accept(CDBlocks.MEDICAL_SUPPLY_CRATE_ITEM.get());
                output.accept(CDBlocks.AMMO_CRATE_ITEM.get());
            })
            .build()
    );
}

package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class QLMTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, QLMZombieMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> QLM_ITEMS_TAB = TABS.register("qlm_items", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qlmzombie.qlm_items"))
            .icon(() -> new ItemStack(QLMItems.ZOMBIE_CORE.get()))
            .displayItems((parameters, output) -> {
                output.accept(QLMItems.ZOMBIE_CORE.get());
                output.accept(QLMItems.INFECTED_ESSENCE.get());
                output.accept(QLMItems.BIOHAZARD_SAMPLE.get());
                output.accept(QLMItems.REINFORCED_PARTS.get());
                output.accept(QLMItems.TACTICAL_AMMO.get());
                output.accept(QLMItems.MEDICAL_SUPPLY.get());
                output.accept(QLMItems.EMERGENCY_RATION.get());
                output.accept(QLMItems.SURVIVAL_KIT.get());
                output.accept(QLMItems.ANTIDOTE.get());
                output.accept(QLMItems.FAKE_PLAYER_SPAWN_EGG.get());
                output.accept(QLMItems.PLANK_AXE.get());
                output.accept(QLMItems.PLANK_COLLECTOR.get());
            })
            .build()
    );
}

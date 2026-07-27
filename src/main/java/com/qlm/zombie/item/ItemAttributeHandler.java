package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ItemAttributeHandler {

    private static final UUID IRON_SWORD_DAMAGE_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID IRON_AXE_DAMAGE_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();

        if (stack.getItem() == Items.IRON_SWORD) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(IRON_SWORD_DAMAGE_UUID, "Iron Sword Damage Boost",
                            46.0, AttributeModifier.Operation.ADDITION));
        } else if (stack.getItem() == Items.IRON_AXE) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(IRON_AXE_DAMAGE_UUID, "Iron Axe Damage Boost",
                            19.0, AttributeModifier.Operation.ADDITION));
        }
    }
}
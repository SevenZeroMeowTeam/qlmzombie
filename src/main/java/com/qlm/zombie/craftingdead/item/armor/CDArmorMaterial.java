package com.qlm.zombie.craftingdead.item.armor;

import com.google.common.collect.Maps;
import net.minecraft.Util;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.Map;

public final class CDArmorMaterial {

    private static final EnumMap<ArmorItem.Type, Integer> DEFENSE = Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            map.put(type, 0);
        }
        map.put(ArmorItem.Type.HELMET, 3);
        map.put(ArmorItem.Type.CHESTPLATE, 8);
        map.put(ArmorItem.Type.LEGGINGS, 6);
        map.put(ArmorItem.Type.BOOTS, 3);
    });

    private static final int[] BASE_DURABILITY = {13, 15, 16, 11};
    private static final int DURABILITY_MULTIPLIER = 20;

    public static final ArmorMaterial INSTANCE = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return BASE_DURABILITY[type.getSlot().getIndex()] * DURABILITY_MULTIPLIER;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return DEFENSE.get(type);
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_CHAIN;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.IRON_INGOT);
        }

        @Override
        public String getName() {
            return "cd_armor";
        }

        @Override
        public float getToughness() {
            return 1.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    private CDArmorMaterial() {}
}

package com.qlm.zombie.thirst.foundation.common.damagesource;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

import java.util.Locale;

public class ModDamageSource
{

    public static final ResourceKey<DamageType> DIE_OF_THIRST_KEY = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(QLMZombieMod.MOD_ID, "dehydrate".toLowerCase(Locale.ROOT)));

    public static DamageSource getDamageSource(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), null, null);
    }

}

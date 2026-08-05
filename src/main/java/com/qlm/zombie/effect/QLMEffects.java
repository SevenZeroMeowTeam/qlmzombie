package com.qlm.zombie.effect;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class QLMEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, QLMZombieMod.MOD_ID);

    public static final RegistryObject<MobEffect> INFECTION = MOB_EFFECTS.register("infection",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8B0080) {
                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity.level().isClientSide) return;

                    double healthPercent = entity.getHealth() / entity.getMaxHealth();
                    if (healthPercent <= 0.05) {
                        return;
                    }

                    int damageAmount = 1 + amplifier;
                    entity.hurt(entity.damageSources().magic(), damageAmount);
                }

                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    int interval = Math.max(40, 100 - amplifier * 20);
                    return duration > 0 && duration % interval == 0;
                }
            });
}

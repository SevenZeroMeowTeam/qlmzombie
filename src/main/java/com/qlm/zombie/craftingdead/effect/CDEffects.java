package com.qlm.zombie.craftingdead.effect;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CDEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, QLMZombieMod.MOD_ID);

    // 流血效果：每 interval tick 扣 1+amplifier 血
    public static final RegistryObject<MobEffect> BLEEDING = MOB_EFFECTS.register("cd_bleeding",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8B0000) {
                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity.level().isClientSide) return;
                    int damageAmount = 1 + amplifier;
                    entity.hurt(entity.damageSources().magic(), damageAmount);
                }

                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    int interval = Math.max(30, 80 - amplifier * 15);
                    return duration > 0 && duration % interval == 0;
                }
            });

    // 骨折效果：每 40 tick 附加挖掘缓慢+速度缓慢各 20 tick
    public static final RegistryObject<MobEffect> BROKEN_BONE = MOB_EFFECTS.register("cd_broken_bone",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x666666) {
                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity.level().isClientSide) return;
                    entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 0, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, false));
                }

                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return duration > 0 && duration % 40 == 0;
                }
            });

    // 肾上腺素：每 60 tick 附加再生II + 速度I 各 40 tick
    public static final RegistryObject<MobEffect> ADRENALINE_RUSH = MOB_EFFECTS.register("cd_adrenaline_rush",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFF4500) {
                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity.level().isClientSide) return;
                    entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
                }

                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return duration > 0 && duration % 60 == 0;
                }
            });

    // 止痛：每 100 tick 清除流血/骨折效果，自身持续时间不减少
    public static final RegistryObject<MobEffect> PAIN_SUPPRESSION = MOB_EFFECTS.register("cd_pain_suppression",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x9932CC) {
                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity.level().isClientSide) return;
                    entity.removeEffect(BLEEDING.get());
                    entity.removeEffect(BROKEN_BONE.get());
                }

                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return duration > 0 && duration % 100 == 0;
                }
            });

    // 重度感染：类似普通感染但伤害更快
    public static final RegistryObject<MobEffect> INFECTION_SEVERE = MOB_EFFECTS.register("cd_infection_severe",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x228B22) {
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
                    int interval = Math.max(20, 60 - amplifier * 15);
                    return duration > 0 && duration % interval == 0;
                }
            });
}

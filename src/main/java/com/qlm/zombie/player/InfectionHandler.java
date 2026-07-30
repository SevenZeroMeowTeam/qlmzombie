package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.effect.QLMEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Husk;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class InfectionHandler {

    private static final float INFECTION_CHANCE = 0.25f;
    private static final int INFECTION_DURATION = 20 * 60 * 3;
    private static final int INFECTION_DURATION_EASY = 20 * 60 * 2;
    private static final int INFECTION_DURATION_HARD = 20 * 60 * 5;

    @SubscribeEvent
    public static void onPlayerHurtByZombie(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Entity source = event.getSource().getEntity();
        if (source == null) return;

        if (!isZombieType(source)) return;

        if (player.hasEffect(QLMEffects.INFECTION.get())) return;

        float chance = getInfectionChance(source);
        if (player.getRandom().nextFloat() < chance) {
            int duration = getInfectionDuration(source);
            int amplifier = getInfectionAmplifier(source);

            MobEffectInstance infection = new MobEffectInstance(
                    QLMEffects.INFECTION.get(),
                    duration,
                    amplifier,
                    false,
                    true,
                    true
            );
            player.addEffect(infection);

            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.literal("§c☠ 你被感染了！使用解毒剂(右键)清除感染"),
                        true
                );
            }
        }
    }

    private static boolean isZombieType(Entity entity) {
        return entity instanceof Zombie
                || entity instanceof ZombieVillager
                || entity instanceof Drowned
                || entity instanceof Husk
                || entity instanceof com.qlm.zombie.entity.GiantZombieEntity;
    }

    private static float getInfectionChance(Entity source) {
        if (source instanceof com.qlm.zombie.entity.GiantZombieEntity) {
            return 0.80f;
        }
        if (source instanceof Husk) {
            return 0.50f;
        }
        if (source instanceof Drowned) {
            return 0.30f;
        }
        return INFECTION_CHANCE;
    }

    private static int getInfectionDuration(Entity source) {
        if (source instanceof com.qlm.zombie.entity.GiantZombieEntity) {
            return INFECTION_DURATION_HARD;
        }
        if (source instanceof Husk) {
            return INFECTION_DURATION_HARD;
        }
        if (source instanceof Drowned) {
            return INFECTION_DURATION;
        }
        return INFECTION_DURATION_EASY;
    }

    private static int getInfectionAmplifier(Entity source) {
        if (source instanceof com.qlm.zombie.entity.GiantZombieEntity) {
            return 2;
        }
        if (source instanceof Husk) {
            return 1;
        }
        if (source instanceof Drowned) {
            return 1;
        }
        return 0;
    }
}

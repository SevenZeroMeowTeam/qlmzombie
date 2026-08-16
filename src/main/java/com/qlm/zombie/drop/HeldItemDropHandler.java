package com.qlm.zombie.drop;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 击杀掉落手持物品：
 * - 击杀僵尸/骷髅时，有 1% 概率额外掉落其主手手持物品的复制
 * - 不覆盖原有掉落，仅作为稀有额外奖励
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class HeldItemDropHandler {

    private static final double DROP_CHANCE = 0.01; // 1%

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player)) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob instanceof Zombie) && !(mob instanceof AbstractSkeleton)) return;
        if (mob.level().isClientSide()) return;

        if (mob.getRandom().nextDouble() >= DROP_CHANCE) return;

        ItemStack held = mob.getMainHandItem();
        if (held.isEmpty()) return;

        ItemStack drop = held.copy();
        event.getDrops().add(mob.spawnAtLocation(drop));
        QLMZombieMod.LOGGER.debug("[QLM Zombie] 1% 额外掉落手持物品: {} from {}", 
                drop.getDescriptionId(), mob.getType().getDescriptionId());
    }
}

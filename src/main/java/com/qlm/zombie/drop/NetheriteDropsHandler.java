package com.qlm.zombie.drop;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 击杀敌对生物掉落下界合金锭：
 * - 排除：玩家、村民、铁傀儡
 * - 包含：骷髅、僵尸、苦力怕、蜘蛛、洞穴蜘蛛、女巫、末影人、僵尸猪灵等所有敌对生物
 * - 概率掉落：每个敌对生物约 1% ~ 3% 概率，品质越高掉落越好？ → 直接固定 1.5% 概率
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class NetheriteDropsHandler {

    private static final double DROP_CHANCE = 0.015; // 1.5%

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player)) return; // 只有玩家击杀掉

        if (isExcluded(victim)) return;

        RandomSource random = victim.getRandom();
        if (random.nextDouble() < DROP_CHANCE) {
            ItemStack drop = new ItemStack(Items.NETHERITE_INGOT, 1);
            event.getDrops().add(victim.spawnAtLocation(drop));
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Netherite ingot dropped by {} at {}",
                    ForgeRegistries.ENTITY_TYPES.getKey(victim.getType()), victim.blockPosition());
        }
    }

    /** 排除：玩家、村民、铁傀儡（中立友好生物） */
    private static boolean isExcluded(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        if (type == EntityType.PLAYER) return true;
        if (type == EntityType.VILLAGER) return true;
        if (type == EntityType.IRON_GOLEM) return true;

        // 如果是友好生物，排除；否则保留敌对生物
        MobCategory category = type.getCategory();
        // 如果是 MONSTER = 敌对生物，不排除；非敌对排除
        if (category != MobCategory.MONSTER) {
            // 某些特殊分类也是敌对，但怪物分类已经覆盖绝大多数
            // 特殊检查：猪灵其实也是怪物（MONSTER），所以会保留 → 正确
            if (!isHostileSpecial(type)) {
                return true;
            }
        }
        return false;
    }

    /** 特殊分类但属于敌对生物的 */
    private static boolean isHostileSpecial(EntityType<?> type) {
        return
            type == EntityType.ZOMBIFIED_PIGLIN ||
            type == EntityType.PIGLIN;
    }
}
package com.qlm.zombie.drop;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * 掉落物控制：
 * <ul>
 *   <li>击杀敌对生物时，按配置概率保留掉落物（{@code drops#hostileDropChance}），
 *       减少地面物品堆积导致的服务器卡顿；</li>
 *   <li>每 {@code drops#dropCleanupInterval}（默认 1 分钟）清理一次地面上的部分陈旧
 *       掉落物（超过 {@code drops#dropCleanupMinAge} 的存在时间，按
 *       {@code drops#dropCleanupChance} 概率移除）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class DropControlHandler {

    private DropControlHandler() {
    }

    /** 击杀敌对生物时按配置概率过滤掉落物 */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        double chance = QLMConfig.HOSTILE_DROP_CHANCE.get();
        if (chance >= 1.0) return; // 全部保留

        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) return;
        if (!(victim instanceof Enemy)) return; // 仅控制敌对生物

        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player)) return; // 仅玩家击杀

        var drops = event.getDrops();
        if (drops == null || drops.isEmpty()) return;

        if (chance <= 0.0) {
            drops.clear();
            return;
        }

        var rnd = victim.getRandom();
        drops.removeIf(drop -> drop == null || rnd.nextDouble() >= chance);
        QLMZombieMod.LOGGER.debug("[QLM Zombie] 掉落物按 {} 概率过滤完成", chance);
    }

    /** 每 1 分钟清理一次地面上的部分陈旧掉落物 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!QLMConfig.DROP_CLEANUP_ENABLED.get()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        int interval = Math.max(20, QLMConfig.DROP_CLEANUP_INTERVAL.get());
        long gameTime = overworld.getGameTime();
        if (gameTime % interval != 0) return;

        int minAge = Math.max(0, QLMConfig.DROP_CLEANUP_MIN_AGE.get());
        double chance = QLMConfig.DROP_CLEANUP_CHANCE.get();
        if (chance <= 0.0) return;

        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            AABB worldBounds = new AABB(
                    -30000000.0D, level.getMinBuildHeight(), -30000000.0D,
                    30000000.0D, level.getMaxBuildHeight(), 30000000.0D);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, worldBounds, ItemEntity::isAlive);
            for (ItemEntity item : items) {
                if (item.getAge() < minAge) continue; // 刚掉落的不清理
                if (level.random.nextDouble() >= chance) continue;
                item.discard();
                removed++;
            }
        }
        if (removed > 0) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] 掉落物清理: 移除 {} 个陈旧掉落物", removed);
        }
    }
}

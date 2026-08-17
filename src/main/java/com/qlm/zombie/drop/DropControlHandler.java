package com.qlm.zombie.drop;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 *   <li>击杀敌对生物按受控概率掉落火药（{@code drops#hostileGunpowderChance}）——
 *       苦力怕已被封禁，火药是 TaCZ 弹药合成的唯一来源，概率受控不会满地图都是；</li>
 *   <li>每 {@code drops#dropCleanupInterval}（默认 1 分钟）清理一次地面上的部分陈旧
 *       掉落物（超过 {@code drops#dropCleanupMinAge} 的存在时间，按
 *       {@code drops#dropCleanupChance} 概率移除）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class DropControlHandler {

    /** 火药单次掉落概率上限，防止抢夺附魔叠加后泛滥 */
    private static final double GUNPOWDER_CHANCE_CAP = 0.5;

    private DropControlHandler() {
    }

    /** 击杀敌对生物时：按配置概率过滤掉落物 + 受控概率掉落火药 */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) return;
        if (!(victim instanceof Enemy)) return; // 仅敌对生物

        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player)) return; // 仅玩家击杀

        // 1) 按全局概率过滤原有掉落物（减少满地图堆积）
        double keepChance = QLMConfig.HOSTILE_DROP_CHANCE.get();
        var drops = event.getDrops();
        if (keepChance < 1.0 && drops != null && !drops.isEmpty()) {
            if (keepChance <= 0.0) {
                drops.clear();
            } else {
                var rnd = victim.getRandom();
                drops.removeIf(drop -> drop == null || rnd.nextDouble() >= keepChance);
            }
        }

        // 2) 受控概率掉落火药（苦力怕被封禁后的火药来源，概率受控不过量）
        rollGunpowderDrop(event, victim, (Player) killer);
    }

    /** 火药掉落：基础概率 + 每级抢夺加成（封顶 50%），数量 1-3 + 抢夺小概率+1 */
    private static void rollGunpowderDrop(LivingDropsEvent event, LivingEntity victim, Player killer) {
        double base = QLMConfig.HOSTILE_GUNPOWDER_CHANCE.get();
        if (base <= 0.0) return;

        int looting = killer.getMainHandItem().getEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING);
        double chance = Math.min(GUNPOWDER_CHANCE_CAP,
                base + looting * QLMConfig.HOSTILE_GUNPOWDER_LOOTING_BONUS.get());

        if (victim.getRandom().nextDouble() >= chance) return;

        int count = 1 + victim.getRandom().nextInt(3)
                + (looting > 0 && victim.getRandom().nextDouble() < 0.4 ? 1 : 0);
        ItemStack gp = new ItemStack(Items.GUNPOWDER, count);
        event.getDrops().add(victim.spawnAtLocation(gp));
        QLMZombieMod.LOGGER.debug("[QLM Zombie] 火药 x{} 掉落 by {} at {}",
                count, victim.getType().getDescriptionId(), victim.blockPosition());
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

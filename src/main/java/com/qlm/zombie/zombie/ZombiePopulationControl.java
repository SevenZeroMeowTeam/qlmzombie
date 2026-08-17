package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Comparator;
import java.util.List;

/**
 * 僵尸人口控制：防止主世界僵尸数量无限制增长拖垮服务器 tick。
 *
 * <p>背景：整合包高僵尸生成率 + 出生点区块常驻加载导致僵尸不断累积且不自然清除，
 * 服务器主世界曾堆到 <b>3869 只僵尸</b>（崩溃报告 Level stats: zombie:3869, entities:4128）。
 * 每 tick 全量实体 AI/寻路/移动 + 大量 mod 的实体扫描（enhancedai SearchMountGoal、
 * Sona SoundAttractionGoal 等）使单个 tick 超过 60 秒 → ServerHangWatchdog 反复崩溃
 * （崩溃堆栈位置每次不同：deer tick / SearchMountGoal / 寻路 / 声呐吸引，均为实体 tick）。
 * 4G 内存也无法解决 —— 这是实体数量问题，不是内存问题。
 *
 * <p>机制：每 {@link #CHECK_INTERVAL} tick（5 秒）统计主世界存活僵尸，超过配置上限
 * {@code qlmzombie-common.toml#zombieMaxPopulation}（默认 400，可用命令/配置文件调整）时按
 * "离最近玩家由远到近"排序，先移除远离玩家的僵尸（保留玩家身边 {@link #PLAYER_SAFE_RADIUS}
 * 格内的战斗对象，避免目标突然消失）。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ZombiePopulationControl {

    /** 检查间隔（tick）：100 tick = 5 秒 */
    private static final long CHECK_INTERVAL = 100;
    /** 玩家周边保护半径（格）：此范围内不主动移除 */
    private static final double PLAYER_SAFE_RADIUS = 48.0;

    private static long lastCheckTick;

    private ZombiePopulationControl() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        long gameTime = overworld.getGameTime();
        if (gameTime - lastCheckTick < CHECK_INTERVAL) return;
        lastCheckTick = gameTime;

        // 上限可在 qlmzombie-common.toml 配置（zombieMaxPopulation，默认 400）
        int maxZombies = QLMConfig.ZOMBIE_MAX_POPULATION.get();

        // 覆盖整个世界范围的 AABB（1.20.1 无无 AABB 的 getEntitiesOfClass 重载）
        AABB worldBounds = new AABB(
                -30000000.0D, overworld.getMinBuildHeight(), -30000000.0D,
                30000000.0D, overworld.getMaxBuildHeight(), 30000000.0D);
        List<Zombie> zombies = overworld.getEntitiesOfClass(Zombie.class, worldBounds, Zombie::isAlive);
        if (zombies.size() <= maxZombies) return;

        int excess = zombies.size() - maxZombies;
        List<ServerPlayer> players = overworld.getPlayers(p -> true);

        // 按"离最近玩家的距离"从远到近排序（先移除远离玩家的）
        zombies.sort(Comparator.comparingDouble(z -> -distanceToNearestPlayer(z, players)));

        int removed = 0;
        for (Zombie zombie : zombies) {
            if (removed >= excess) break;
            // 玩家安全半径内的僵尸不主动移除（避免战斗中的目标消失）
            if (distanceToNearestPlayer(zombie, players) < PLAYER_SAFE_RADIUS) continue;
            zombie.discard();
            removed++;
        }

        if (removed > 0) {
            QLMZombieMod.LOGGER.warn(
                    "[QLM Zombie] 僵尸人口控制: 主世界 {} 只僵尸超限，已移除 {} 只（保留 {} 只）",
                    zombies.size(), removed, zombies.size() - removed);
        }
    }

    private static double distanceToNearestPlayer(Zombie zombie, List<ServerPlayer> players) {
        double min = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            double d = zombie.distanceToSqr(player);
            if (d < min) min = d;
        }
        return min;
    }
}

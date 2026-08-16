package com.qlm.zombie.structure;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/**
 * 废弃建筑内刷怪：
 * - 夜晚时，在已生成的废弃建筑（废墟/加油站/学校/军事基地/高楼）内刷新僵尸/骷髅
 * - 每建筑最多 3 只驻守怪（标记 qlm_structure_mob），附近有玩家且怪不足时补充
 * - 建筑内怪比野外怪略强（战利品丰富区域更危险）
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class StructureMobHandler {

    private static final String NBT_STRUCTURE_MOB = "qlm_structure_mob";
    private static final int CHECK_INTERVAL = 100; // 5秒
    private static final int MAX_PER_BUILDING = 3;
    private static final double ALERT_RADIUS = 40.0D;

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        long tod = overworld.getDayTime() % 24000L;
        boolean day = tod < 13000L;

        Collection<BlockPos> centers = com.qlm.zombie.structure.StructureGenSupport.INSTANCE.getBuildingCenters();
        if (centers.isEmpty()) return;

        for (BlockPos center : centers) {
            if (!overworld.isLoaded(center)) continue;

            // 找附近在线玩家（40格内）
            ServerPlayer nearest = null;
            double nearestDist = ALERT_RADIUS * ALERT_RADIUS;
            for (ServerPlayer player : overworld.players()) {
                double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = player;
                }
            }
            if (nearest == null) continue;

            // 白天：建筑内怪减少（只留1只），夜晚：补到最多3只
            int want = day ? 1 : MAX_PER_BUILDING;

            // 统计建筑内已有驻守怪
            int existing = overworld.getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(center).inflate(24.0),
                m -> m.getPersistentData().getBoolean(NBT_STRUCTURE_MOB)).size();
            int need = want - existing;
            if (need <= 0) continue;

            RandomSource rnd = nearest.getRandom();
            for (int i = 0; i < need; i++) {
                // 在建筑中心附近 3-12 格刷怪（尽量在建筑内）
                double ang = rnd.nextDouble() * 2 * Math.PI;
                double dist = 3 + rnd.nextDouble() * 9;
                int x = center.getX() + (int) (Math.cos(ang) * dist);
                int z = center.getZ() + (int) (Math.sin(ang) * dist);
                int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (y <= overworld.getMinBuildHeight() + 1) continue;

                Mob mob;
                if (rnd.nextBoolean()) mob = EntityType.ZOMBIE.create(overworld);
                else mob = EntityType.SKELETON.create(overworld);
                if (mob == null) continue;
                mob.moveTo(x + 0.5, y, z + 0.5, rnd.nextFloat() * 360.0F, 0.0F);
                mob.getPersistentData().putBoolean(NBT_STRUCTURE_MOB, true);
                mob.setPersistenceRequired();
                if (mob instanceof Zombie zombie) {
                    try {
                        var hp = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                        if (hp != null) hp.setBaseValue(hp.getBaseValue() * 1.5);
                        var dmg = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * 1.3);
                        zombie.setHealth(zombie.getMaxHealth());
                    } catch (Exception ignored) {
                    }
                }
                overworld.addFreshEntity(mob);
            }
        }
    }
}

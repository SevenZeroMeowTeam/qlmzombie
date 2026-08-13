package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * 僵尸/骷髅昼夜行为系统：
 * - 白天：不主动攻击，游荡缓慢，僵尸/骷髅不燃烧
 * - 晚上：行动加快，主动攻击
 * - 被攻击后才反击
 * - 骷髅白天停止行动
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class MobBehaviorHandler {

    // 速度修饰符
    private static final UUID DAY_SPEED_UUID = UUID.fromString("d1d1d1d1-0001-4000-8000-000000000001");
    private static final UUID NIGHT_SPEED_UUID = UUID.fromString("d1d1d1d1-0002-4000-8000-000000000002");

    // 白天速度降低
    private static final double DAY_SPEED_PENALTY = -0.15;
    // 夜晚速度提升
    private static final double NIGHT_SPEED_BOOST = 0.10;

    // 标记被攻击的实体（用于反击）
    private static final Set<Integer> attackedEntities = new HashSet<>();
    private static final Map<Integer, Long> attackedTime = new HashMap<>();
    private static final long ATTACKED_DURATION = 200; // 10秒

    @SubscribeEvent
    public static void onMobTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;

            long dayTime = level.getDayTime() % 24000L;
            boolean isDay = dayTime >= 0 && dayTime < 13000;
            boolean isNight = dayTime >= 13000 && dayTime < 24000;

            // 清理过期攻击标记
            long gameTime = level.getGameTime();
            attackedTime.entrySet().removeIf(e -> gameTime - e.getValue() > ATTACKED_DURATION);

            // 处理所有僵尸和骷髅
            for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, 
                new AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                         level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
                z -> z.isAlive())) {

                // 跳过Boss
                if (zombie.getPersistentData().getBoolean("qlm_is_boss")) continue;

                handleDayNightBehavior(zombie, level, isDay, isNight, gameTime);
            }

            for (Skeleton skeleton : level.getEntitiesOfClass(Skeleton.class,
                new AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                         level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
                s -> s.isAlive())) {

                handleDayNightBehavior(skeleton, level, isDay, isNight, gameTime);
            }
        }
    }

    private static void handleDayNightBehavior(Mob mob, ServerLevel level, boolean isDay, boolean isNight, long gameTime) {
        // 处理速度
        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            // 移除所有速度修饰符
            if (speedAttr.getModifier(DAY_SPEED_UUID) != null) {
                speedAttr.removeModifier(DAY_SPEED_UUID);
            }
            if (speedAttr.getModifier(NIGHT_SPEED_UUID) != null) {
                speedAttr.removeModifier(NIGHT_SPEED_UUID);
            }

            if (isDay) {
                // 白天减速
                speedAttr.addTransientModifier(new AttributeModifier(
                    DAY_SPEED_UUID, "Day Speed Penalty",
                    DAY_SPEED_PENALTY, AttributeModifier.Operation.ADDITION));
                // 白天不燃烧（取消火焰伤害）
                mob.setRemainingFireTicks(0);
            } else if (isNight) {
                // 夜晚加速
                speedAttr.addTransientModifier(new AttributeModifier(
                    NIGHT_SPEED_UUID, "Night Speed Boost",
                    NIGHT_SPEED_BOOST, AttributeModifier.Operation.ADDITION));
            }
        }

        // 处理攻击行为
        int entityId = mob.getId();
        boolean wasAttacked = attackedEntities.contains(entityId) && 
            gameTime - attackedTime.getOrDefault(entityId, 0L) < ATTACKED_DURATION;

        if (isDay) {
            if (!wasAttacked) {
                // 白天不主动攻击，清除目标
                if (mob.getTarget() != null) {
                    mob.setTarget(null);
                }
                // 骷髅白天停止行动
                if (mob instanceof Skeleton) {
                    mob.setNoAi(false);
                    mob.getNavigation().stop();
                }
            } else {
                // 被攻击后反击
                if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                    // 找最近的玩家
                    var player = level.getNearestPlayer(mob, 10);
                    if (player != null) {
                        mob.setTarget(player);
                    }
                }
            }
        }
        // 夜晚正常攻击
    }

    /** 记录被攻击的实体 */
    @SubscribeEvent
    public static void onEntityHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Zombie) && !(event.getEntity() instanceof Skeleton)) return;

        if (!(event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player)) return;

        Mob mob = (Mob) event.getEntity();
        int entityId = mob.getId();
        attackedEntities.add(entityId);
        attackedTime.put(entityId, ((ServerLevel) mob.level()).getGameTime());

        // 设置反击目标
        mob.setTarget((LivingEntity) event.getSource().getEntity());
    }
}
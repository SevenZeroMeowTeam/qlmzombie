package com.qlm.zombie.ai;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.*;

/**
 * 昼夜行为 + 追击 + 同伴系统：
 * - 僵尸：白天游荡不燃烧、不主动攻击玩家；夜晚 64 格锁定玩家追击
 * - 骷髅：白天不行动、不燃烧、不主动攻击；被招惹后召唤附近同伴反击并追击
 * - 铁傀儡：AI 增强（更高的索敌范围，被攻击立即锁定攻击者）
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class MobDayNightHandler {

    private static final double NIGHT_LOCK_RADIUS = 64.0D;      // 夜晚锁定追击半径（格）
    private static final double NIGHT_LOCK_RADIUS_SQ = 4096.0D;
    private static final String NBT_SKELETON_ALERT_CD = "qlm_skeleton_alert_cd";
    private static final String NBT_GOLEM_BOOSTED = "qlm_golem_boosted";

    private static boolean isDay(Level level) {
        long time = level.getDayTime() % 24000L;
        return time < 13000L; // 0-13000 视为白天（13000 起进入夜晚）
    }

    // ==================== 通用：昼夜行为 + 夜晚锁定追击 ====================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity.isSpectator()) return;

        boolean day = isDay(level);

        // ---- 僵尸：白天游荡不燃烧不主动攻击；夜晚64格锁定追击 ----
        if (entity instanceof Zombie zombie) {
            if (day) {
                // 白天不燃烧
                if (zombie.isOnFire()) zombie.setRemainingFireTicks(0);
                // 白天不主动攻击玩家（被攻击过仍可反击）
                if (zombie.getTarget() instanceof Player p && zombie.getLastHurtByMob() != p) {
                    zombie.setTarget(null);
                }
            } else {
                // 夜晚：64格内锁定玩家追击
                lockTargetAtNight(zombie, level);
            }
        }

        // ---- 骷髅：白天不行动不燃烧不主动攻击；夜晚正常 ----
        if (entity instanceof AbstractSkeleton skeleton) {
            if (day) {
                if (skeleton.isOnFire()) skeleton.setRemainingFireTicks(0);
                if (skeleton.getTarget() instanceof Player p && skeleton.getLastHurtByMob() != p) {
                    skeleton.setTarget(null);
                    skeleton.getNavigation().stop();
                }
            } else {
                lockTargetAtNight(skeleton, level);
            }
        }
    }

    /** 夜晚：在 64 格内锁定最近的存活玩家并追击 */
    private static void lockTargetAtNight(Mob mob, ServerLevel level) {
        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive() && mob.distanceToSqr(current) < NIGHT_LOCK_RADIUS_SQ) {
            return; // 已有目标且在64格内，保持追击
        }

        Player best = null;
        double bestDist = NIGHT_LOCK_RADIUS_SQ;
        for (Player player : level.players()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            double dist = player.distanceToSqr(mob);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        if (best != null) {
            mob.setTarget(best);
            if (mob.getNavigation() != null) {
                mob.getNavigation().moveTo(best, 1.0D);
            }
        }
    }

    // ==================== 骷髅：被招惹后召唤同伴反击 ====================

    @SubscribeEvent
    public static void onSkeletonHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof AbstractSkeleton skeleton)) return;
        if (skeleton.level().isClientSide()) return;
        if (!(skeleton.level() instanceof ServerLevel level)) return;

        var attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        // 反击：锁定攻击者
        skeleton.setTarget(player);
        skeleton.getNavigation().moveTo(player, 1.2D);
        // 提升追击距离
        var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), 64.0));

        // 冷却（防止无限召唤）
        long cd = skeleton.getPersistentData().getLong(NBT_SKELETON_ALERT_CD);
        long gameTime = level.getGameTime();
        if (gameTime - cd < 300) return; // 15秒冷却
        skeleton.getPersistentData().putLong(NBT_SKELETON_ALERT_CD, gameTime);

        // 召唤附近同伴（2-3只骷髅）反击
        int count = 2 + skeleton.getRandom().nextInt(2);
        int summoned = 0;
        List<AbstractSkeleton> nearby = level.getEntitiesOfClass(AbstractSkeleton.class,
                skeleton.getBoundingBox().inflate(24.0),
                s -> s.isAlive() && s != skeleton);
        // 优先让附近已有骷髅加入追击
        for (AbstractSkeleton ally : nearby) {
            if (summoned >= count) break;
            if (ally.getTarget() == null || !ally.getTarget().isAlive()) {
                ally.setTarget(player);
                ally.getNavigation().moveTo(player, 1.2D);
                var allyFollow = ally.getAttribute(Attributes.FOLLOW_RANGE);
                if (allyFollow != null) allyFollow.setBaseValue(Math.max(allyFollow.getBaseValue(), 64.0));
                summoned++;
            }
        }
        // 不足则现场召唤
        while (summoned < count) {
            AbstractSkeleton ally = null;
            if (skeleton.getRandom().nextBoolean()) {
                ally = net.minecraft.world.entity.EntityType.SKELETON.create(level);
            } else {
                ally = net.minecraft.world.entity.EntityType.STRAY.create(level);
            }
            if (ally == null) break;
            double ang = skeleton.getRandom().nextDouble() * 2 * Math.PI;
            double dist = 3 + skeleton.getRandom().nextDouble() * 3;
            ally.moveTo(skeleton.getX() + Math.cos(ang) * dist, skeleton.getY(), skeleton.getZ() + Math.sin(ang) * dist,
                    skeleton.getRandom().nextFloat() * 360.0F, 0.0F);
            ally.setTarget(player);
            var allyFollow = ally.getAttribute(Attributes.FOLLOW_RANGE);
            if (allyFollow != null) allyFollow.setBaseValue(64.0);
            level.addFreshEntity(ally);
            summoned++;
        }
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c⚠ 骷髅召唤了附近的同伴反击你！"));
        }
        QLMZombieMod.LOGGER.debug("[昼夜AI] 骷髅 {} 被玩家攻击，召唤 {} 只同伴反击", skeleton.getId(), summoned);
    }

    // ==================== 铁傀儡 AI 增强 ====================

    @SubscribeEvent
    public static void onGolemJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        if (event.getLevel().isClientSide()) return;
        if (golem.getPersistentData().getBoolean(NBT_GOLEM_BOOSTED)) return;

        // 提升索敌范围与攻击
        var follow = golem.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), 48.0));
        var atk = golem.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getBaseValue() + 2.0);
        var armor = golem.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(armor.getBaseValue() + 4.0);
        var speed = golem.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.15);
        golem.getPersistentData().putBoolean(NBT_GOLEM_BOOSTED, true);
        QLMZombieMod.LOGGER.debug("[昼夜AI] 铁傀儡 AI 增强 @ {}", golem.blockPosition());
    }

    /** 铁傀儡被攻击：立即锁定攻击者，并让附近铁傀儡协同 */
    @SubscribeEvent
    public static void onGolemHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        if (golem.level().isClientSide()) return;
        if (!(golem.level() instanceof ServerLevel level)) return;

        var attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;

        golem.setTarget(livingAttacker);
        // 附近铁傀儡协同
        for (IronGolem ally : level.getEntitiesOfClass(IronGolem.class,
                golem.getBoundingBox().inflate(20.0),
                g -> g.isAlive() && g != golem && (g.getTarget() == null || !g.getTarget().isAlive()))) {
            ally.setTarget(livingAttacker);
        }
    }
}

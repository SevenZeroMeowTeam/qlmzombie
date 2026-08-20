package com.qlm.zombie.ai;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
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
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 昼夜行为 + 追击 + 同伴系统：
 * - 所有敌对生物（Monster）：白天不主动攻击玩家、四处游荡；被玩家攻击（招惹）后才反击
 * - 僵尸：白天游荡不燃烧、不主动攻击玩家；夜晚 64 格锁定玩家追击
 * - 骷髅：白天不行动、不燃烧、不主动攻击；被招惹后【仅白天】召唤附近同伴反击（每只累计上限 10 只），夜晚不召唤
 * - 铁傀儡：AI 增强（更高的索敌范围，被攻击立即锁定攻击者）
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class MobDayNightHandler {

    private static final double NIGHT_LOCK_RADIUS = 64.0D;      // 夜晚锁定追击半径（格）
    private static final double NIGHT_LOCK_RADIUS_SQ = 4096.0D;
    private static final String NBT_SKELETON_ALERT_CD = "qlm_skeleton_alert_cd";
    private static final String NBT_SKELETON_SUMMONED = "qlm_skeleton_summoned"; // 已召唤同伴累计数（持久化在骷髅 NBT）
    private static final long MAX_SKELETON_SUMMONED = 10L;                        // 每只骷髅累计召唤同伴上限 10 只
    private static final String NBT_GOLEM_BOOSTED = "qlm_golem_boosted";

    /**
     * 白天清目标的冷却（tick）：修复"僵尸双手持物抖动"。
     * 原逻辑每个 tick 都把玩家目标 setTarget(null)，但原版索敌 goal 又会立刻重新
     * 锁定玩家 → isAggressive() 每 tick 在 true/false 间闪烁 → 双手举起的持物姿势
     * 剧烈抖动。改为每 100 tick（5 秒）才清一次，避免高频闪烁。
     */
    private static final long DAY_CLEAR_COOLDOWN = 100;
    private static final Map<Integer, Long> lastDayClear = new HashMap<>();

    private static boolean isDay(Level level) {
        long time = level.getDayTime() % 24000L;
        return time < 13000L; // 0-13000 视为白天（13000 起进入夜晚）
    }

    /**
     * 防御性：识别 player2npc / playerengine 的 AI 机器人实体（AutomatoneEntity 等）。
     * <p>build62 兼容性修复：playerengine 的 Baritone 寻路（PathingBehavior）在 AI 机器人上
     * 计算路径时可能卡死 60 秒导致服务器崩溃（watchdog）。虽然 AutomatoneEntity 直接继承
     * LivingEntity（不是 Monster，不会被本类敌对处理逻辑命中），这里仍显式跳过，
     * 确保 qlmzombie 的任何实体处理都完全不影响 player2npc 的 AI 机器人。</p>
     */
    private static boolean isPlayer2NpcEntity(LivingEntity entity) {
        if (entity == null) return false;
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key != null && key.getNamespace().equals("player2npc")) return true;
        // 兜底：类名包含 Automatone / player2npc
        String name = entity.getClass().getName();
        return name.contains("Automatone") || name.contains("player2npc");
    }

    /** 白天按冷却频率清除玩家目标（防止与索敌 goal 争夺导致抖动） */
    private static boolean tryClearDayTarget(Mob mob, Level level) {
        if (mob.getTarget() == null) return false;
        long now = level.getGameTime();
        Long last = lastDayClear.get(mob.getId());
        if (last != null && now - last < DAY_CLEAR_COOLDOWN) return false;
        mob.setTarget(null);
        lastDayClear.put(mob.getId(), now);
        return true;
    }

    // ==================== 通用：昼夜行为 + 夜晚锁定追击 ====================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity.isSpectator()) return;
        // 跳过 player2npc/playerengine 的 AI 机器人（防御性兼容）
        if (isPlayer2NpcEntity(entity)) return;

        boolean day = isDay(level);

        // ---- 所有敌对生物（Monster）：白天不主动攻击玩家、四处游荡；被招惹（被玩家攻击）才反击 ----
        if (entity instanceof Monster monster) {
            if (day) {
                // 白天不燃烧（僵尸/骷髅）
                if (monster.isOnFire() && (monster instanceof Zombie || monster instanceof AbstractSkeleton)) {
                    monster.setRemainingFireTicks(0);
                }
                // 白天不主动攻击玩家（被攻击过仍可反击）——按冷却频率清除目标，避免与索敌 goal 抖动
                if (monster.getTarget() instanceof Player p && monster.getLastHurtByMob() != p) {
                    if (tryClearDayTarget(monster, level)) {
                        // 骷髅白天停止行动
                        if (monster instanceof AbstractSkeleton) {
                            monster.getNavigation().stop();
                        }
                    }
                }
            } else {
                // 夜晚：僵尸/骷髅保持 64 格锁定追击（其余敌对生物维持原版行为）
                if (monster instanceof Zombie || monster instanceof AbstractSkeleton) {
                    lockTargetAtNight(monster, level);
                }
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

    // ==================== 敌对生物：被招惹后反击 + 骷髅召唤同伴（仅白天，上限10） ====================

    @SubscribeEvent
    public static void onMonsterHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) return;
        // 跳过 player2npc/playerengine 的 AI 机器人（防御性兼容）
        if (isPlayer2NpcEntity(event.getEntity())) return;
        if (monster.level().isClientSide()) return;
        if (!(monster.level() instanceof ServerLevel level)) return;

        var attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        // 反击：锁定攻击者（所有敌对生物被玩家攻击后都立即反击）
        monster.setTarget(player);
        if (monster.getNavigation() != null) {
            monster.getNavigation().moveTo(player, 1.2D);
        }

        // 仅骷髅拥有“召唤同伴”能力
        if (!(monster instanceof AbstractSkeleton skeleton)) return;

        // 提升追击距离
        var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), 64.0));

        // 仅白天召唤同伴；夜晚不召唤（不然打不完）
        if (!isDay(level)) return;

        // 冷却（防止频繁召唤）
        long cd = skeleton.getPersistentData().getLong(NBT_SKELETON_ALERT_CD);
        long gameTime = level.getGameTime();
        if (gameTime - cd < 300) return; // 15秒冷却

        // 累计召唤上限 10 只（每只骷髅 NBT 持久化计数）
        long summonedTotal = skeleton.getPersistentData().getLong(NBT_SKELETON_SUMMONED);
        if (summonedTotal >= MAX_SKELETON_SUMMONED) return;
        skeleton.getPersistentData().putLong(NBT_SKELETON_ALERT_CD, gameTime);

        // 召唤附近同伴（2-3只骷髅）反击，但不超过剩余额度
        int count = (int) Math.min(2 + skeleton.getRandom().nextInt(2), MAX_SKELETON_SUMMONED - summonedTotal);
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
        // 累计已召唤同伴数量
        skeleton.getPersistentData().putLong(NBT_SKELETON_SUMMONED, summonedTotal + summoned);
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c⚠ 骷髅召唤了附近的同伴反击你！"));
        }
        QLMZombieMod.LOGGER.debug("[昼夜AI] 骷髅 {} 被玩家攻击，召唤 {} 只同伴反击（累计 {}/{})", skeleton.getId(), summoned, summonedTotal + summoned, MAX_SKELETON_SUMMONED);
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

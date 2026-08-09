package com.qlm.zombie.cloudai.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 环境扫描工具
 * - 扫描玩家、敌对生物、掉落物、危险
 */
public final class EnvScan {

    private EnvScan() {}

    /** 获取附近玩家（排除自己） */
    public static List<Player> nearbyPlayers(Level level, Entity center, double radius) {
        if (level == null || center == null) return new ArrayList<>();
        AABB box = boxAround(center, radius);
        return level.getEntitiesOfClass(Player.class, box, p -> p != center && p.isAlive());
    }

    /** 获取最近的友方玩家 */
    public static Player nearestAllyPlayer(Level level, Entity center, double radius) {
        List<Player> players = nearbyPlayers(level, center, radius);
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : players) {
            double d = p.distanceToSqr(center);
            if (d < minDist) {
                minDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    /** 获取附近敌对生物（满足 Monster 判定或可攻击型） */
    public static List<LivingEntity> nearbyHostiles(Level level, Entity center, double radius) {
        if (level == null || center == null) return new ArrayList<>();
        AABB box = boxAround(center, radius);
        Predicate<Entity> isHostile = e -> {
            if (!(e instanceof LivingEntity le)) return false;
            if (!le.isAlive()) return false;
            if (e instanceof Player) return false;
            // 简单判定: Mob 类型且非驯服
            if (le instanceof Mob mob) {
                return mob.getTarget() != null || isHostileType(e.getType());
            }
            return false;
        };
        List<LivingEntity> result = new ArrayList<>();
        for (Entity e : level.getEntities(center, box, isHostile)) {
            result.add((LivingEntity) e);
        }
        return result;
    }

    /** 获取最近的敌对目标 */
    public static LivingEntity nearestHostile(Level level, Entity center, double radius) {
        List<LivingEntity> hostiles = nearbyHostiles(level, center, radius);
        LivingEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        for (LivingEntity h : hostiles) {
            double d = h.distanceToSqr(center);
            if (d < minDist) {
                minDist = d;
                nearest = h;
            }
        }
        return nearest;
    }

    /** 获取附近掉落物 */
    public static List<ItemEntity> nearbyDrops(Level level, Entity center, double radius) {
        if (level == null || center == null) return new ArrayList<>();
        AABB box = boxAround(center, radius);
        return level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive);
    }

    /** 检查附近是否有危险（爆炸物、火焰等） */
    public static boolean hasDangerNearby(Level level, Entity center, double radius) {
        if (level == null || center == null) return false;
        AABB box = boxAround(center, radius);
        List<Entity> entities = level.getEntities(center, box, e ->
                e.isAlive() && (
                        e.getType() == EntityType.TNT ||
                        e.getType() == EntityType.CREEPER ||
                        e.getType() == EntityType.END_CRYSTAL ||
                        e.isOnFire()
                )
        );
        return !entities.isEmpty();
    }

    private static AABB boxAround(Entity center, double radius) {
        return new AABB(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius
        );
    }

    // 简化的敌对类型判断
    private static boolean isHostileType(EntityType<?> type) {
        return type == EntityType.ZOMBIE
                || type == EntityType.SKELETON
                || type == EntityType.SPIDER
                || type == EntityType.CAVE_SPIDER
                || type == EntityType.CREEPER
                || type == EntityType.HUSK
                || type == EntityType.DROWNED
                || type == EntityType.STRAY
                || type == EntityType.WITCH
                || type == EntityType.ENDERMAN
                || type == EntityType.SLIME
                || type == EntityType.MAGMA_CUBE
                || type == EntityType.GHAST
                || type == EntityType.BLAZE;
    }
}

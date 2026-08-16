package com.qlm.zombie.villager;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.AABB;

/**
 * 村民守卫增强系统：
 * - 村民有5%概率刷新为守卫
 * - 守卫：100血量，25攻击力，8护甲
 * - 守卫不会逃跑，主动攻击附近敌对生物
 * - 守卫受伤时召唤附近铁傀儡协助反击
 * - 守卫会和铁傀儡协同作战
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class VillagerGuardHandler {

    private static final double GUARD_SPAWN_CHANCE = 0.05;
    private static final double GUARD_HEALTH = 100.0;
    private static final double GUARD_DAMAGE = 25.0;
    private static final double GOLEM_ALERT_RANGE = 25.0;

    public static final String NBT_IS_GUARD = "qlm_villager_guard";

    @SubscribeEvent
    public static void onVillagerSpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Villager villager)) return;

        if (villager.getPersistentData().getBoolean(NBT_IS_GUARD)) return;

        if (villager.getRandom().nextDouble() < GUARD_SPAWN_CHANCE) {
            convertToGuard(villager);
        }
    }

    private static void convertToGuard(Villager villager) {
        CompoundTag tag = villager.getPersistentData();
        tag.putBoolean(NBT_IS_GUARD, true);

        var healthAttr = villager.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(GUARD_HEALTH);
            villager.setHealth((float) GUARD_HEALTH);
        }

        var damageAttr = villager.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) damageAttr.setBaseValue(GUARD_DAMAGE);

        var armorAttr = villager.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(8.0);

        var followAttr = villager.getAttribute(Attributes.FOLLOW_RANGE);
        if (followAttr != null) followAttr.setBaseValue(32.0);

        villager.setCustomName(Component.literal("§c§l村民守卫").withStyle(ChatFormatting.BOLD));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();

        QLMZombieMod.LOGGER.info("[村民守卫] 村民 {} 转化为守卫！位置: {}",
            villager.blockPosition().toShortString(), villager.blockPosition());
    }

    /** 村民守卫不会进行交易：右键拦截交易界面 */
    @SubscribeEvent
    public static void onPlayerInteractGuard(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (!villager.getPersistentData().getBoolean(NBT_IS_GUARD)) return;
        if (villager.level().isClientSide()) return;

        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                Component.literal("§c⚠ 村民守卫不会进行交易！").withStyle(ChatFormatting.RED),
                true
            );
        }
    }

    /** 村民守卫受到攻击时：不逃跑，并召唤铁傀儡协助 */
    @SubscribeEvent
    public static void onGuardHurt(LivingHurtEvent event) {        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!villager.getPersistentData().getBoolean(NBT_IS_GUARD)) return;
        if (villager.level().isClientSide()) return;

        // 不逃跑 - 设置反击目标
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker && livingAttacker != villager) {
            villager.setLastHurtByMob(livingAttacker);
            villager.setTarget(null); // 清除原版逃跑目标，转为攻击

            // 自己主动攻击
            villager.hurt(villager.damageSources().mobAttack(livingAttacker), 0.01f); // 触发反击
        }

        // 召唤附近铁傀儡协助
        if (villager.level() instanceof ServerLevel level) {
            for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class,
                villager.getBoundingBox().inflate(GOLEM_ALERT_RANGE),
                g -> g.isAlive())) {

                if (attacker instanceof LivingEntity livingAttacker) {
                    if (golem.getTarget() == null || !golem.getTarget().isAlive()) {
                        golem.setTarget(livingAttacker);
                        // 铁傀儡通知
                        golem.setCustomName(Component.literal("§7[守卫] 铁傀儡"));
                    }
                }
            }
        }
    }

    /** 村民守卫主动攻击附近的敌对生物（不逃跑） */
    @SubscribeEvent
    public static void onGuardTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.getGameTime() % 20 != 0) continue;

            for (Villager villager : level.getEntitiesOfClass(Villager.class,
                new AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                         level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
                v -> v.isAlive() && v.getPersistentData().getBoolean(NBT_IS_GUARD))) {

                // 守卫不逃跑（清除逃跑行为）
                // 主动搜索附近的敌对生物并设置目标
                if (villager.getTarget() == null || !villager.getTarget().isAlive()) {
                    double range = 16.0;
                    for (Entity entity : level.getEntities(villager, villager.getBoundingBox().inflate(range))) {
                        if (entity instanceof Mob mob && mob.isAlive() && mob instanceof Enemy) {
                            // 守卫主动攻击敌对生物
                            villager.setLastHurtByMob(mob);
                            break;
                        }
                    }
                }
            }
        }
    }

    public static boolean isGuard(Villager villager) {
        return villager.getPersistentData().getBoolean(NBT_IS_GUARD);
    }
}
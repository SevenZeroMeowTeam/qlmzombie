package com.qlm.zombie.projectile;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * 骷髅20%概率发射破甲箭对玩家造成大量伤害。
 * 破甲箭无视护甲（直接伤害），伤害为 20~40 点（10~20心）。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class SkeletonArmorPiercingHandler {

    private static final double ARMOR_PIERCE_CHANCE = 0.20; // 20%
    private static final double MIN_DAMAGE = 20.0; // 10心
    private static final double MAX_DAMAGE = 40.0; // 20心

    @SubscribeEvent
    public static void onArrowImpact(ProjectileImpactEvent event) {
        // 只处理箭矢
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (arrow.level().isClientSide()) return;

        // 只处理命中实体
        if (!(event.getRayTraceResult() instanceof EntityHitResult hitResult)) return;
        Entity hitEntity = hitResult.getEntity();
        if (!(hitEntity instanceof Player target)) return;

        // 只处理骷髅射出的箭
        Entity owner = arrow.getOwner();
        if (!(owner instanceof Skeleton) && !(owner instanceof Stray)) return;

        // 20% 概率触发破甲箭
        Random random = new Random();
        if (random.nextDouble() >= ARMOR_PIERCE_CHANCE) return;

        // 取消原版箭矢伤害（原箭矢仍然命中但伤害被覆盖）
        // 我们直接施加额外伤害
        double damage = MIN_DAMAGE + random.nextDouble() * (MAX_DAMAGE - MIN_DAMAGE);

        // 使用无视护甲的伤害源
        target.hurt(target.damageSources().magic(), (float) damage);

        // 视觉效果：告知玩家
        if (target instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal("§c☠ 破甲箭！骷髅发射了一支破甲箭，对你造成了 §4" + (int) damage + " §c点伤害！").withStyle(net.minecraft.ChatFormatting.RED));
        }

        // 移除原箭矢的伤害（避免重复伤害）
        arrow.discard();

        QLMZombieMod.LOGGER.debug("[QLM Zombie] Armor-piercing arrow hit player {} for {} damage", target.getName().getString(), (int) damage);
    }
}
package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.EntityHitResult;

import java.util.*;

/**
 * 特殊骷髅系统：
 * - 远程：凋零骷髅射手/剧毒骷髅射手/爆破骷髅射手/铁甲骷髅射手
 * - 近战：骷髅剑士/骷髅狂战士/骷髅守卫
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class SpecialSkeletonHandler {

    // NBT标记
    public static final String NBT_SKELETON_TYPE = "qlm_skeleton_type";
    // 远程
    public static final String TYPE_WITHER = "wither";
    public static final String TYPE_POISON = "poison";
    public static final String TYPE_EXPLOSIVE = "explosive";
    public static final String TYPE_ARMORED = "armored";
    // 近战
    public static final String TYPE_SWORDSMAN = "swordsman";   // 骷髅剑士 - 流血
    public static final String TYPE_BERSERKER = "berserker";   // 骷髅狂战士 - 破甲
    public static final String TYPE_GUARD = "guard";           // 骷髅守卫 - 反伤
    // 新增
    public static final String TYPE_SNIPER = "sniper";         // 狙击骷髅 - 超远高伤
    public static final String TYPE_SPEED = "speed";           // 迅捷骷髅 - 低血快速近战

    private static final Random RANDOM = new Random();
    private static final double MIN_DAMAGE = 20.0;
    private static final double MAX_DAMAGE = 40.0;

    // 近战骷髅攻击冷却追踪
    private static final Map<Integer, Long> lastMeleeAttack = new HashMap<>();
    private static final int MELEE_COOLDOWN = 30;

    /** 骷髅生成时转换为特殊类型 */
    @SubscribeEvent
    public static void onSkeletonSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton)) return;
        if (skeleton.level().isClientSide()) return;
        if (!(skeleton.level() instanceof ServerLevel level)) return;
        if (skeleton.getPersistentData().contains(NBT_SKELETON_TYPE)) return;

        long day = level.getDayTime() / 24000L;
        double rand = skeleton.getRandom().nextDouble();

        double multiplier = 1.0;
        if (day >= 25) multiplier = 1.5;
        if (day >= 50) multiplier = 2.0;
        if (day >= 100) multiplier = 3.0;

        double baseChance = 0.02;
        double witherChance = baseChance * multiplier;
        double poisonChance = baseChance * 1.2 * multiplier;
        double explosiveChance = baseChance * 0.8 * multiplier;
        double armoredChance = baseChance * multiplier;
        double swordsmanChance = baseChance * 1.2 * multiplier;
        double berserkerChance = baseChance * multiplier;
        double guardChance = baseChance * multiplier;
        double sniperChance = baseChance * 0.6 * multiplier;
        double speedChance = baseChance * 1.4 * multiplier;

        CompoundTag tag = skeleton.getPersistentData();

        // ====== 远程骷髅 ======
        if (rand < witherChance) {
            applySkeletonType(skeleton, tag, TYPE_WITHER, "§8凋零骷髅射手", 40.0, 12.0, 4.0);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        } else if (rand < witherChance + poisonChance) {
            applySkeletonType(skeleton, tag, TYPE_POISON, "§a剧毒骷髅射手", 35.0, 10.0, 2.0);
        } else if (rand < witherChance + poisonChance + explosiveChance) {
            applySkeletonType(skeleton, tag, TYPE_EXPLOSIVE, "§c爆破骷髅射手", 45.0, 15.0, 4.0);
        } else if (rand < witherChance + poisonChance + explosiveChance + armoredChance) {
            applySkeletonType(skeleton, tag, TYPE_ARMORED, "§7铁甲骷髅射手", 60.0, 10.0, 12.0);
            skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        // ====== 近战骷髅 ======
        } else if (rand < witherChance + poisonChance + explosiveChance + armoredChance + swordsmanChance) {
            applySkeletonType(skeleton, tag, TYPE_SWORDSMAN, "§b骷髅剑士", 50.0, 14.0, 6.0);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        } else if (rand < witherChance + poisonChance + explosiveChance + armoredChance + swordsmanChance + berserkerChance) {
            applySkeletonType(skeleton, tag, TYPE_BERSERKER, "§4骷髅狂战士", 45.0, 18.0, 4.0);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        } else if (rand < witherChance + poisonChance + explosiveChance + armoredChance + swordsmanChance + berserkerChance + guardChance) {
            applySkeletonType(skeleton, tag, TYPE_GUARD, "§e骷髅守卫", 70.0, 8.0, 14.0);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        // ====== 新增：狙击骷髅（超远高伤）/ 迅捷骷髅（低血快速近战） ======
        } else if (rand < witherChance + poisonChance + explosiveChance + armoredChance + swordsmanChance + berserkerChance + guardChance + sniperChance) {
            applySkeletonType(skeleton, tag, TYPE_SNIPER, "§8狙击骷髅", 55.0, 22.0, 4.0);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            var follow = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
            if (follow != null) follow.setBaseValue(48.0);
        } else if (rand < witherChance + poisonChance + explosiveChance + armoredChance + swordsmanChance + berserkerChance + guardChance + sniperChance + speedChance) {
            applySkeletonType(skeleton, tag, TYPE_SPEED, "§f迅捷骷髅", 18.0, 8.0, 2.0);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            var speedAttr = skeleton.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(0.38);
        }
    }

    private static void applySkeletonType(Skeleton skeleton, CompoundTag tag, String type, String name,
                                           double health, double damage, double armor) {
        tag.putString(NBT_SKELETON_TYPE, type);
        var healthAttr = skeleton.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(health);
            skeleton.setHealth((float) health);
        }
        var damageAttr = skeleton.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) damageAttr.setBaseValue(damage);
        var armorAttr = skeleton.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(armor);
        skeleton.setCustomName(Component.literal(name));
        skeleton.setCustomNameVisible(true);
        skeleton.setPersistenceRequired();
        QLMZombieMod.LOGGER.debug("[特殊骷髅] {} 生成于 {}", name, skeleton.blockPosition());
    }

    /** 近战骷髅tick：近战攻击+特效 */
    @SubscribeEvent
    public static void onMeleeSkeletonTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.getGameTime() % 5 != 0) continue;

            for (Skeleton skeleton : level.getEntitiesOfClass(Skeleton.class,
                new AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                         level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
                s -> s.isAlive())) {

                CompoundTag tag = skeleton.getPersistentData();
                String type = tag.getString(NBT_SKELETON_TYPE);

                if (type.isEmpty() || !isMeleeType(type)) continue;
                if (skeleton.getTarget() == null) continue;

                int entityId = skeleton.getId();
                long gameTime = level.getGameTime();
                Long lastAttack = lastMeleeAttack.get(entityId);
                if (lastAttack != null && gameTime - lastAttack < MELEE_COOLDOWN) continue;
                lastMeleeAttack.put(entityId, gameTime);

                LivingEntity target = skeleton.getTarget();
                if (target.distanceToSqr(skeleton) > 16) continue; // 4格内

                // 近战攻击特效
                switch (type) {
                    case TYPE_SWORDSMAN -> {
                        // 骷髅剑士：攻击附带流血效果（瞬间伤害+缓慢）
                        if (RANDOM.nextDouble() < 0.35) {
                            target.hurt(target.damageSources().mobAttack(skeleton), 8.0f);
                            target.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0));
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                            if (target instanceof net.minecraft.server.level.ServerPlayer sp) {
                                sp.sendSystemMessage(Component.literal("§b⚔ 骷髅剑士的剑刃划伤了你！").withStyle(net.minecraft.ChatFormatting.AQUA));
                            }
                        }
                    }
                    case TYPE_BERSERKER -> {
                        // 骷髅狂战士：破甲重击（无视护甲伤害）
                        if (RANDOM.nextDouble() < 0.3) {
                            target.hurt(target.damageSources().magic(), 15.0f);
                            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.5, 0));
                            target.hurtMarked = true;
                            if (target instanceof net.minecraft.server.level.ServerPlayer sp) {
                                sp.sendSystemMessage(Component.literal("§4💥 骷髅狂战士的重击击飞了你！").withStyle(net.minecraft.ChatFormatting.DARK_RED));
                            }
                        }
                    }
                    case TYPE_GUARD -> {
                        // 骷髅守卫：反伤效果
                        if (RANDOM.nextDouble() < 0.4) {
                            skeleton.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1));
                            // 反弹部分伤害给攻击者
                            if (skeleton.getLastHurtByMob() != null) {
                                skeleton.getLastHurtByMob().hurt(skeleton.getLastHurtByMob().damageSources().mobAttack(skeleton), 5.0f);
                                if (skeleton.getLastHurtByMob() instanceof net.minecraft.server.level.ServerPlayer sp) {
                                    sp.sendSystemMessage(Component.literal("§e🛡 骷髅守卫反弹了你的伤害！").withStyle(net.minecraft.ChatFormatting.YELLOW));
                                }
                            }
                        }
                    }
                    case TYPE_SPEED -> {
                        // 迅捷骷髅：连续快速斩击
                        if (RANDOM.nextDouble() < 0.5) {
                            target.hurt(target.damageSources().mobAttack(skeleton), 6.0f);
                            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.2, 0));
                            target.hurtMarked = true;
                        }
                    }
                }
            }
        }
    }

    private static boolean isMeleeType(String type) {
        return TYPE_SWORDSMAN.equals(type) || TYPE_BERSERKER.equals(type) || TYPE_GUARD.equals(type)
            || TYPE_SPEED.equals(type);
    }

    /** 远程骷髅箭矢效果 */
    @SubscribeEvent
    public static void onSkeletonArrowImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (arrow.level().isClientSide()) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hitResult)) return;
        if (!(hitResult.getEntity() instanceof net.minecraft.world.entity.player.Player target)) return;
        if (!(arrow.getOwner() instanceof Skeleton)) return;

        Skeleton skeleton = (Skeleton) arrow.getOwner();
        CompoundTag tag = skeleton.getPersistentData();
        String type = tag.getString(NBT_SKELETON_TYPE);

        // ===== 通用箭效果（所有骷髅，非特殊类型也有）=====
        double roll = skeleton.getRandom().nextDouble();
        if (roll < 0.25D) {
            // 25% 破甲箭：无视护甲造成大量伤害（12-20点）
            double dmg = 12.0D + skeleton.getRandom().nextDouble() * 8.0D;
            target.hurt(target.damageSources().magic(), (float) dmg);
            // 附带随机负面效果（持续5秒=100tick）
            int eff = skeleton.getRandom().nextInt(3);
            if (eff == 0) target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            else if (eff == 1) target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
            else target.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
            if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                sp.sendSystemMessage(Component.literal("§4☠ 破甲箭！无视护甲造成 " + (int) dmg + " 点伤害！").withStyle(net.minecraft.ChatFormatting.DARK_RED));
        } else if (roll < 0.40D) {
            // 15% 随机效果箭：剧毒 / 凋零 / 瞬间伤害（持续5秒），不是所有骷髅都有
            int eff = skeleton.getRandom().nextInt(3);
            if (eff == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                    sp.sendSystemMessage(Component.literal("§a☠ 剧毒箭！持续5秒中毒！").withStyle(net.minecraft.ChatFormatting.GREEN));
            } else if (eff == 1) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                    sp.sendSystemMessage(Component.literal("§8☠ 凋零箭！持续5秒凋零！").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            } else {
                target.hurt(target.damageSources().magic(), 10.0F);
                target.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
                if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                    sp.sendSystemMessage(Component.literal("§c☠ 瞬间伤害箭！").withStyle(net.minecraft.ChatFormatting.RED));
            }
        }

        if (type.isEmpty()) return;

        switch (type) {
            case TYPE_WITHER -> {
                if (RANDOM.nextDouble() < 0.3) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
                    if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                        sp.sendSystemMessage(Component.literal("§8☠ 凋零箭！").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                }
            }
            case TYPE_POISON -> {
                if (RANDOM.nextDouble() < 0.4) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
                    if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                        sp.sendSystemMessage(Component.literal("§a☠ 剧毒箭！").withStyle(net.minecraft.ChatFormatting.GREEN));
                }
            }
            case TYPE_EXPLOSIVE -> {
                if (RANDOM.nextDouble() < 0.25) {
                    arrow.level().explode(arrow, arrow.getX(), arrow.getY(), arrow.getZ(), 2.0f, false, Level.ExplosionInteraction.NONE);
                    if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                        sp.sendSystemMessage(Component.literal("§c§l⚠ 爆破箭！").withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
            case TYPE_ARMORED -> {
                if (RANDOM.nextDouble() < 0.3) {
                    double damage = MIN_DAMAGE + RANDOM.nextDouble() * (MAX_DAMAGE - MIN_DAMAGE);
                    target.hurt(target.damageSources().magic(), (float) damage);
                    target.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                    if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                        sp.sendSystemMessage(Component.literal("§c☠ 破甲箭！造成了 §4" + (int) damage + " §c点伤害！").withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
            case TYPE_SNIPER -> {
                // 狙击骷髅：远程高伤（15-25点），低概率一击必杀效果
                double dmg = 15.0D + RANDOM.nextDouble() * 10.0D;
                target.hurt(target.damageSources().mobAttack(skeleton), (float) dmg);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
                if (target instanceof net.minecraft.server.level.ServerPlayer sp)
                    sp.sendSystemMessage(Component.literal("§8☠ 狙击箭！造成了 §4" + (int) dmg + " §8点伤害！").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
    }

    // ==================== 骷髅破坏/搭建方块（夜间） ====================

    private static final Map<Integer, Long> lastSkeletonBlockAction = new HashMap<>();

    @SubscribeEvent
    public static void onSkeletonBlockTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.getGameTime() % 5 != 0) continue;
            // 仅夜晚（白天骷髅不行动）
            long tod = level.getDayTime() % 24000L;
            if (tod < 13000L) continue;

            for (Skeleton skeleton : level.getEntitiesOfClass(Skeleton.class,
                new AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                         level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
                s -> s.isAlive())) {

                LivingEntity target = skeleton.getTarget();
                if (target == null) continue;
                if (skeleton.distanceToSqr(target) < 4.0D) continue;

                int eid = skeleton.getId();
                Long last = lastSkeletonBlockAction.get(eid);
                long gt = level.getGameTime();
                if (last != null && gt - last < 40) continue;
                lastSkeletonBlockAction.put(eid, gt);

                // 破坏前方阻挡方块
                var dir = target.position().subtract(skeleton.position()).normalize();
                int bx = skeleton.getBlockX() + (int) Math.round(dir.x);
                int bz = skeleton.getBlockZ() + (int) Math.round(dir.z);
                boolean broke = false;
                for (int dy = 0; dy <= 2 && !broke; dy++) {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(bx, skeleton.getBlockY() + dy, bz);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (isBreakableForSkeleton(state)) {
                        level.destroyBlock(pos, false);
                        level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
                        broke = true;
                    }
                }

                // 搭建方块（低概率）：在目标方向搭圆石，靠近追击
                if (!broke && skeleton.getRandom().nextDouble() < 0.25D) {
                    net.minecraft.core.BlockPos front = new net.minecraft.core.BlockPos(
                        skeleton.getBlockX() + (int) Math.signum(dir.x),
                        skeleton.getBlockY(),
                        skeleton.getBlockZ() + (int) Math.signum(dir.z));
                    if (level.getBlockState(front).isAir() && !level.getBlockState(front.below()).isAir()) {
                        level.setBlock(front, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static boolean isBreakableForSkeleton(net.minecraft.world.level.block.state.BlockState state) {
        if (state.isAir()) return false;
        net.minecraft.world.level.block.Block b = state.getBlock();
        if (b == net.minecraft.world.level.block.Blocks.BEDROCK || b == net.minecraft.world.level.block.Blocks.OBSIDIAN
            || b == net.minecraft.world.level.block.Blocks.CHEST || b == net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)
            return false;
        float h;
        try { h = state.getDestroySpeed(null, null); } catch (Exception e) { return false; }
        return h >= 0 && h < 5.0F;
    }
}
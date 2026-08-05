package com.qlm.zombie.ai;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.dayphase.DayPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AIOptimizationHandler {

    private static final String AI_TAG = "qlmzombie.ai_applied";
    private static final UUID FOLLOW_RANGE_UUID = UUID.fromString("8a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e6f");
    private static final UUID MOVEMENT_SPEED_UUID = UUID.fromString("8a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e70");
    private static final String DOOR_PROGRESS_TAG = "qlmzombie.door_progress";
    private static final String DOOR_POS_X = "qlmzombie.door_x";
    private static final String DOOR_POS_Y = "qlmzombie.door_y";
    private static final String DOOR_POS_Z = "qlmzombie.door_z";

    private static final String TYPE_SUICIDE = "qlmzombie.type_suicide";
    private static final String TYPE_BARREL = "qlmzombie.type_barrel";
    private static final String TYPE_GUARDIAN = "qlmzombie.type_guardian";
    private static final String TYPE_TNT = "qlmzombie.type_tnt";
    private static final String TYPE_POTION = "qlmzombie.type_potion";
    private static final String TYPE_SUMMONER = "qlmzombie.type_summoner";
    private static final String SUMMONER_SPAWN_CD = "qlmzombie.summoner_spawn_cd";
    private static final String SUMMONER_SPAWN_COUNT = "qlmzombie.summoner_spawn_count";
    private static final String GUARDIAN_ATTACK_CD = "qlmzombie.guardian_cd";
    private static final String SUICIDE_FUSE = "qlmzombie.suicide_fuse";
    private static final String SUICIDE_TRIGGERED = "qlmzombie.suicide_triggered";
    private static final String BLOCK_BREAK_COOLDOWN = "qlmzombie.bk_cd";
    private static final String BLOCK_PLACE_COOLDOWN = "qlmzombie.bp_cd";
    private static final String TARGET_SEARCH_CD = "qlmzombie.ts_cd";
    private static final String THROW_COOLDOWN = "qlmzombie.throw_cd";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!QLMConfig.ENABLE_AI_OPTIMIZATION.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Entity entity = event.getEntity();

        if (entity instanceof Zombie zombie) {
            optimizeZombie(zombie);
        } else if (entity instanceof AbstractSkeleton skeleton) {
            optimizeSkeleton(skeleton);
        } else if (entity instanceof Villager villager) {
            optimizeVillager(villager);
        } else if (entity instanceof AbstractArrow arrow) {
            Entity owner = arrow.getOwner();
            if (owner instanceof AbstractSkeleton skeleton) {
                double chance = QLMConfig.SKELETON_PERFECT_SHOT_CHANCE.get();
                if (chance <= 0) return;
                DayPhase phase = getCurrentDayPhase(level);
                double effectiveChance = chance;
                if (phase == DayPhase.EXTREME) effectiveChance *= 2.0D;
                else if (phase == DayPhase.HARD) effectiveChance *= 1.5D;
                else if (phase == DayPhase.EASY || phase == DayPhase.SAFE) effectiveChance *= 0.3D;
                if (skeleton.getRandom().nextDouble() < effectiveChance) {
                    arrow.getPersistentData().putBoolean("qlmzombie.perfect_shot", true);
                    arrow.setBaseDamage(arrow.getBaseDamage() * 1.5D);
                    arrow.setSilent(true);
                    LivingEntity target = skeleton.getTarget();
                    if (target != null) {
                        Vec3 vec = target.position().add(0, target.getBbHeight() * 0.5D, 0).subtract(arrow.position());
                        double len = Math.sqrt(vec.x * vec.x + vec.z * vec.z);
                        arrow.setDeltaMovement(vec.x / len * 1.0D,
                                vec.y / Math.max(1.0D, len * 0.025),
                                vec.z / len * 1.0D);
                    }
                    level.playSound(null, skeleton.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.5F, 1.2F);
                }
            }
        }
    }

    private static DayPhase getCurrentDayPhase(ServerLevel level) {
        long currentDay = level.getDayTime() / 57600L;
        return DayPhase.forDay(currentDay);
    }

    private static void optimizeZombie(Zombie zombie) {
        if (zombie.getPersistentData().getBoolean(AI_TAG)) return;
        if (zombie.level().getDifficulty() == Difficulty.PEACEFUL) return;

        zombie.getPersistentData().putBoolean(AI_TAG, true);

        ServerLevel level = getServerLevel(zombie.level());
        if (level == null) return;

        DayPhase phase = getCurrentDayPhase(level);

        applyFollowRangeModifier(zombie, QLMConfig.ZOMBIE_FOLLOW_RANGE_MULTIPLIER.get());
        applyMovementSpeedModifier(zombie, QLMConfig.ZOMBIE_SPEED_MULTIPLIER.get());

        if (QLMConfig.ENHANCED_MOB_AI.get()) {
            applyDifficultyScaling(zombie, phase);
        }

        removeGoalsByType(zombie, MeleeAttackGoal.class);
        zombie.goalSelector.addGoal(2, new OptimizedZombieAttackGoal(zombie, 1.0D, false));

        if (QLMConfig.ZOMBIE_BREAK_DOORS.get() && (phase == DayPhase.NORMAL || phase == DayPhase.HARD || phase == DayPhase.EXTREME)) {
            try {
                GroundPathNavigation navigation = (GroundPathNavigation) zombie.getNavigation();
                navigation.setCanOpenDoors(true);
                navigation.setCanPassDoors(true);
            } catch (Exception ignored) {
            }
        }

        if (phase == DayPhase.HARD || phase == DayPhase.EXTREME) {
            double r = zombie.getRandom().nextDouble();
            double s = QLMConfig.SUICIDE_ZOMBIE_CHANCE.get();
            double b = QLMConfig.BARREL_ZOMBIE_CHANCE.get();
            double t = QLMConfig.TNT_ZOMBIE_CHANCE.get();
            double p = QLMConfig.POTION_ZOMBIE_CHANCE.get();
            if (r < s) {
                zombie.getPersistentData().putBoolean(TYPE_SUICIDE, true);
                zombie.setCustomName(net.minecraft.network.chat.Component.literal("§c自爆僵尸"));
                zombie.setCustomNameVisible(true);
                try {
                    AttributeInstance maxHp = zombie.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHp != null) maxHp.setBaseValue(maxHp.getBaseValue() * 1.3D);
                    zombie.setHealth(zombie.getMaxHealth());
                } catch (Exception ignored) {
                }
            } else if (r < s + b) {
                zombie.getPersistentData().putBoolean(TYPE_BARREL, true);
                zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.BARREL));
                zombie.setCustomName(net.minecraft.network.chat.Component.literal("§6木桶僵尸"));
                zombie.setCustomNameVisible(true);
                try {
                    AttributeInstance maxHp = zombie.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHp != null) maxHp.setBaseValue(maxHp.getBaseValue() * 1.5D);
                    zombie.setHealth(zombie.getMaxHealth());
                } catch (Exception ignored) {
                }
            } else if (r < s + b + t) {
                zombie.getPersistentData().putBoolean(TYPE_TNT, true);
                zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TNT));
                zombie.setCustomName(net.minecraft.network.chat.Component.literal("§e💣 TNT僵尸"));
                zombie.setCustomNameVisible(true);
                try {
                    AttributeInstance maxHp = zombie.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHp != null) maxHp.setBaseValue(maxHp.getBaseValue() * 1.2D);
                    zombie.setHealth(zombie.getMaxHealth());
                } catch (Exception ignored) {
                }
            } else if (r < s + b + t + p) {
                zombie.getPersistentData().putBoolean(TYPE_POTION, true);
                zombie.setCustomName(net.minecraft.network.chat.Component.literal("§d🧪 药水僵尸"));
                zombie.setCustomNameVisible(true);
                try {
                    AttributeInstance maxHp = zombie.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHp != null) maxHp.setBaseValue(maxHp.getBaseValue() * 1.1D);
                    zombie.setHealth(zombie.getMaxHealth());
                } catch (Exception ignored) {
                }
            } else if (r < s + b + t + p + QLMConfig.SUMMONER_ZOMBIE_CHANCE.get()) {
                zombie.getPersistentData().putBoolean(TYPE_SUMMONER, true);
                zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SPAWNER));
                zombie.setCustomName(net.minecraft.network.chat.Component.literal("§5🔮 僵尸召唤师"));
                zombie.setCustomNameVisible(true);
                zombie.getPersistentData().putInt(SUMMONER_SPAWN_COUNT, 0);
                try {
                    AttributeInstance maxHp = zombie.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHp != null) maxHp.setBaseValue(maxHp.getBaseValue() * 1.4D);
                    zombie.setHealth(zombie.getMaxHealth());
                } catch (Exception ignored) {
                }
            }
        }

        try {
            AttributeInstance attackDamage = zombie.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamage != null) {
                double bonus = switch (phase) {
                    case SAFE, EASY -> 0.0;
                    case NORMAL -> 1.0;
                    case HARD -> 2.0;
                    case EXTREME -> 3.0;
                };
                if (bonus > 0) {
                    attackDamage.setBaseValue(attackDamage.getBaseValue() + bonus);
                }
            }
        } catch (Exception ignored) {
        }

        QLMZombieMod.LOGGER.debug("[QLM Zombie] 僵尸AI优化已应用 @ {}", zombie.blockPosition());
    }

    private static void optimizeSkeleton(AbstractSkeleton skeleton) {
        if (skeleton.getPersistentData().getBoolean(AI_TAG)) return;
        if (skeleton.level().getDifficulty() == Difficulty.PEACEFUL) return;

        skeleton.getPersistentData().putBoolean(AI_TAG, true);

        applyFollowRangeModifier(skeleton, QLMConfig.ZOMBIE_FOLLOW_RANGE_MULTIPLIER.get());

        if (QLMConfig.ENHANCED_MOB_AI.get()) {
            ServerLevel level = getServerLevel(skeleton.level());
            if (level != null) {
                DayPhase phase = getCurrentDayPhase(level);
                applyDifficultyScaling(skeleton, phase);
            }
        }

        if (QLMConfig.SKELETON_STRAFE.get()) {
            removeRangedAttackGoals(skeleton);
            skeleton.goalSelector.addGoal(2, new StrafeBowAttackGoal<>(skeleton, 1.0D, 40, 70, 18.0F));
        }

        double accuracy = QLMConfig.SKELETON_ACCURACY_BOOST.get();
        if (accuracy > 0) {
            skeleton.getPersistentData().putDouble("qlmzombie.accuracy_boost", accuracy);
        }

        ServerLevel level = getServerLevel(skeleton.level());
        if (level != null) {
            DayPhase phase = getCurrentDayPhase(level);
            try {
                AttributeInstance attackDamage = skeleton.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attackDamage != null) {
                    double bonus = switch (phase) {
                        case SAFE, EASY -> 0.0;
                        case NORMAL -> 0.5;
                        case HARD -> 1.0;
                        case EXTREME -> 1.5;
                    };
                    if (bonus > 0) {
                        attackDamage.setBaseValue(attackDamage.getBaseValue() + bonus);
                    }
                }
            } catch (Exception ignored) {
            }

            if ((phase == DayPhase.NORMAL || phase == DayPhase.HARD || phase == DayPhase.EXTREME)) {
                double modChance = QLMConfig.SKELETON_MOD_WEAPON_CHANCE.get();
                if (modChance > 0 && skeleton.getRandom().nextDouble() < modChance) {
                    equipSkeletonModWeapon(skeleton);
                }
            }
        }

        QLMZombieMod.LOGGER.debug("[QLM Zombie] 骷髅AI优化已应用 @ {}", skeleton.blockPosition());
    }

    private static void equipSkeletonModWeapon(AbstractSkeleton skeleton) {
        ItemStack modWeapon;
        int variant = skeleton.getRandom().nextInt(4);
        if (variant == 0) {
            modWeapon = new ItemStack(Items.BOW);
            try {
                java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> emap = new java.util.HashMap<>();
                emap.put(net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS, 5);
                    emap.put(net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS, 1);
                emap.put(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(emap, modWeapon);
                modWeapon.setHoverName(net.minecraft.network.chat.Component.literal("§d✦ 亡灵之弓 ✦"));
            } catch (Exception ignored) {}
        } else if (variant == 1) {
            modWeapon = new ItemStack(Items.CROSSBOW);
            try {
                java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> emap = new java.util.HashMap<>();
                emap.put(net.minecraft.world.item.enchantment.Enchantments.PIERCING, 4);
                emap.put(net.minecraft.world.item.enchantment.Enchantments.MULTISHOT, 1);
                emap.put(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(emap, modWeapon);
                modWeapon.setHoverName(net.minecraft.network.chat.Component.literal("§d✦ 亡灵弩 ✦"));
            } catch (Exception ignored) {}
        } else if (variant == 2) {
            modWeapon = new ItemStack(Items.STONE_SWORD);
            try {
                java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> emap = new java.util.HashMap<>();
                emap.put(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 5);
                emap.put(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT, 2);
                emap.put(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(emap, modWeapon);
                modWeapon.setHoverName(net.minecraft.network.chat.Component.literal("§c✦ 骷髅之刃 ✦"));
            } catch (Exception ignored) {}
        } else {
            modWeapon = new ItemStack(Items.IRON_SWORD);
            try {
                java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> emap = new java.util.HashMap<>();
                emap.put(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 4);
                emap.put(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK, 2);
                emap.put(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(emap, modWeapon);
                modWeapon.setHoverName(net.minecraft.network.chat.Component.literal("§c✦ 亡灵之剑 ✦"));
            } catch (Exception ignored) {}
        }
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, modWeapon);
        skeleton.setCustomName(net.minecraft.network.chat.Component.literal("§d⚔ 强化骷髅"));
        skeleton.setCustomNameVisible(true);
    }

    private static void optimizeVillager(Villager villager) {
        if (villager.getPersistentData().getBoolean(AI_TAG)) return;
        villager.getPersistentData().putBoolean(AI_TAG, true);
        villager.getPersistentData().putInt("qlmzombie.flee_radius", QLMConfig.VILLAGER_FLEE_RADIUS.get());
        villager.getPersistentData().putDouble("qlmzombie.panic_boost", QLMConfig.VILLAGER_PANIC_BOOST.get());

        double guardianChance = QLMConfig.VILLAGER_GUARDIAN_CHANCE.get();
        if (guardianChance > 0 && villager.getRandom().nextDouble() < guardianChance) {
            setupVillageGuardian(villager);
        }
        QLMZombieMod.LOGGER.debug("[QLM Zombie] 村民AI优化已应用 @ {}", villager.blockPosition());
    }

    private static void setupVillageGuardian(Villager villager) {
        villager.getPersistentData().putBoolean(TYPE_GUARDIAN, true);

        ServerLevel level = getServerLevel(villager.level());
        if (level == null) return;

        villager.setCustomName(net.minecraft.network.chat.Component.literal("§2🛡 村庄守卫者"));
        villager.setCustomNameVisible(true);

        try {
            AttributeInstance maxHp = villager.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(40.0D);
            AttributeInstance atk = villager.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue(5.0D);
            AttributeInstance follow = villager.getAttribute(Attributes.FOLLOW_RANGE);
            if (follow != null) follow.setBaseValue(24.0D);
            AttributeInstance move = villager.getAttribute(Attributes.MOVEMENT_SPEED);
            if (move != null) move.setBaseValue(0.35D);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Failed to set guardian attributes: {}", e.getMessage());
        }

        villager.setHealth(villager.getMaxHealth());

        ItemStack weapon;
        double modWpChance = QLMConfig.VILLAGER_GUARDIAN_MOD_WEAPON_CHANCE.get();
        if (modWpChance > 0 && villager.getRandom().nextDouble() < modWpChance) {
            weapon = buildModWeapon(villager.getRandom().nextInt(3));
        } else {
            weapon = buildNormalWeapon(villager.getRandom());
        }
        villager.setItemSlot(EquipmentSlot.MAINHAND, weapon);

        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        try { net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(
                new java.util.HashMap<net.minecraft.world.item.enchantment.Enchantment, Integer>(){{
                    put(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 2);
                }}, chest); } catch (Exception e) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Failed to enchant guardian chestplate: {}", e.getMessage());
        }
        villager.setItemSlot(EquipmentSlot.CHEST, chest);

        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        villager.setItemSlot(EquipmentSlot.HEAD, helmet);
    }

    private static ItemStack buildNormalWeapon(net.minecraft.util.RandomSource rnd) {
        ItemStack stack;
        int pick = rnd.nextInt(3);
        if (pick == 0) stack = new ItemStack(Items.IRON_SWORD);
        else if (pick == 1) stack = new ItemStack(Items.STONE_SWORD);
        else stack = new ItemStack(Items.IRON_AXE);
        try {
            java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> emap = new java.util.HashMap<>();
            emap.put(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1 + rnd.nextInt(2));
            net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(emap, stack);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Failed to enchant normal weapon: {}", e.getMessage());
        }
        return stack;
    }

    private static ItemStack buildModWeapon(int variant) {
        ItemStack stack = new ItemStack(Items.NETHERITE_SWORD);
        try {
            java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> emap = new java.util.HashMap<>();
            emap.put(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 3);
            emap.put(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT, 2);
            emap.put(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
            if (variant == 0) emap.put(net.minecraft.world.item.enchantment.Enchantments.SMITE, 4);
            else if (variant == 1) emap.put(net.minecraft.world.item.enchantment.Enchantments.BANE_OF_ARTHROPODS, 4);
            else emap.put(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK, 2);
            net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(emap, stack);
            stack.setHoverName(net.minecraft.network.chat.Component.literal("§d✦ 村庄守护者之剑 ✦"));
        } catch (Exception e) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Failed to enchant mod weapon: {}", e.getMessage());
        }
        return stack;
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!QLMConfig.ENABLE_AI_OPTIMIZATION.get()) return;

        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;

        if (entity instanceof Zombie zombie) {
            tickZombieDoorBreaking(zombie);
            if (zombie.getPersistentData().getBoolean(TYPE_SUICIDE)) {
                tickSuicideZombie(zombie, level);
            }
            if (zombie.getPersistentData().getBoolean(TYPE_TNT)) {
                tickTNTZombie(zombie, level);
            }
            if (zombie.getPersistentData().getBoolean(TYPE_POTION)) {
                tickPotionZombie(zombie, level);
            }
            if (zombie.getPersistentData().getBoolean(TYPE_SUMMONER)) {
                tickSummonerZombie(zombie, level);
            }
            tickBlockBreakAndPlace(zombie, level);
            if (QLMConfig.AGGRESSIVE_TARGETING.get()) {
                tickAggressiveTargeting(zombie, level);
            }
        } else if (entity instanceof AbstractSkeleton skeleton) {
            tickSkeletonInfiniteArrows(skeleton);
            if (QLMConfig.AGGRESSIVE_TARGETING.get()) {
                tickAggressiveTargeting(skeleton, level);
            }
        } else if (entity instanceof Villager villager) {
            if (villager.getPersistentData().getBoolean(TYPE_GUARDIAN)) {
                if (entity.level() instanceof ServerLevel sLevel) tickVillageGuardian(villager, sLevel);
            } else {
                tickVillagerPanic(villager);
            }
        }
    }

    private static void tickSuicideZombie(Zombie zombie, ServerLevel level) {
        LivingEntity target = zombie.getTarget();
        boolean triggered = zombie.getPersistentData().getBoolean(SUICIDE_TRIGGERED);
        int fuse = zombie.getPersistentData().getInt(SUICIDE_FUSE);

        if (target != null && zombie.distanceToSqr(target) < 9.0D) {
            if (!triggered) {
                zombie.getPersistentData().putBoolean(SUICIDE_TRIGGERED, true);
                fuse = 40;
            }
        }

        if (triggered) {
            fuse--;
            zombie.getPersistentData().putInt(SUICIDE_FUSE, fuse);
            if (fuse > 0) {
                if (fuse % 5 == 0) {
                    level.playSound(null, zombie.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
                }
            } else {
                DayPhase phase = getCurrentDayPhase(level);
                float radius = phase == DayPhase.EXTREME ? 5.0F : 3.5F;
                level.explode(zombie, zombie.getX(), zombie.getY(), zombie.getZ(), radius, Level.ExplosionInteraction.MOB);
                zombie.discard();
            }
        }
    }

    private static void tickTNTZombie(Zombie zombie, ServerLevel level) {
        LivingEntity target = zombie.getTarget();
        if (target == null) return;

        double dist = zombie.distanceToSqr(target);
        if (dist < 4.0D || dist > 64.0D) return;

        int throwCd = zombie.getPersistentData().getInt(THROW_COOLDOWN);
        if (throwCd > 0) {
            zombie.getPersistentData().putInt(THROW_COOLDOWN, throwCd - 1);
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(target.getX() - zombie.getX(), target.getZ() - zombie.getZ()));
        zombie.yBodyRot = yaw;

        PrimedTnt tnt = new PrimedTnt(level, zombie.getX(), zombie.getY() + 1.0D, zombie.getZ(), zombie);
        tnt.setFuse(80);

        Vec3 dir = target.position().subtract(zombie.position()).normalize();
        tnt.setDeltaMovement(dir.x * 0.8D, 0.5D + (zombie.getRandom().nextDouble() * 0.3D), dir.z * 0.8D);

        level.addFreshEntity(tnt);
        level.playSound(null, zombie.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);

        zombie.getPersistentData().putInt(THROW_COOLDOWN, 60 + zombie.getRandom().nextInt(40));
    }

    private static void tickPotionZombie(Zombie zombie, ServerLevel level) {
        LivingEntity target = zombie.getTarget();
        if (target == null) return;

        double dist = zombie.distanceToSqr(target);
        if (dist < 4.0D || dist > 49.0D) return;

        int throwCd = zombie.getPersistentData().getInt(THROW_COOLDOWN);
        if (throwCd > 0) {
            zombie.getPersistentData().putInt(THROW_COOLDOWN, throwCd - 1);
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(target.getX() - zombie.getX(), target.getZ() - zombie.getZ()));
        zombie.yBodyRot = yaw;

        ItemStack potionStack = createNegativePotion(zombie.getRandom());

        ThrownPotion projectile = new ThrownPotion(zombie.level(), zombie);
        projectile.setItem(potionStack);
        Vec3 dir = target.position().subtract(zombie.position()).normalize();
        projectile.shoot(dir.x * 0.6D, 0.3D + (zombie.getRandom().nextDouble() * 0.2D), dir.z * 0.6D, 1.2F, 8.0F);

        level.addFreshEntity(projectile);
        level.playSound(null, zombie.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);

        zombie.getPersistentData().putInt(THROW_COOLDOWN, 80 + zombie.getRandom().nextInt(40));
    }

    private static ItemStack createNegativePotion(net.minecraft.util.RandomSource rnd) {
        Potion[] negativePotions = {
                Potions.HARMING,
                Potions.SLOWNESS,
                Potions.POISON,
                Potions.WEAKNESS
        };
        Potion type = negativePotions[rnd.nextInt(negativePotions.length)];
        return PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), type);
    }

    private static void tickSummonerZombie(Zombie zombie, ServerLevel level) {
        LivingEntity target = zombie.getTarget();
        if (target == null) return;

        double dist = zombie.distanceToSqr(target);
        if (dist > 100.0D) return;

        int spawnCount = zombie.getPersistentData().getInt(SUMMONER_SPAWN_COUNT);
        int maxSpawns = QLMConfig.SUMMONER_ZOMBIE_MAX_SUMMONS.get();
        if (spawnCount >= maxSpawns) return;

        int spawnCd = zombie.getPersistentData().getInt(SUMMONER_SPAWN_CD);
        if (spawnCd > 0) {
            zombie.getPersistentData().putInt(SUMMONER_SPAWN_CD, spawnCd - 1);
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(target.getX() - zombie.getX(), target.getZ() - zombie.getZ()));
        zombie.yBodyRot = yaw;

        net.minecraft.world.effect.MobEffect[] buffs = {
                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,
                net.minecraft.world.effect.MobEffects.REGENERATION,
                net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,
                net.minecraft.world.effect.MobEffects.INVISIBILITY
        };
        net.minecraft.world.effect.MobEffect buff = buffs[zombie.getRandom().nextInt(buffs.length)];

        Zombie summoned = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
        summoned.moveTo(zombie.getX() + (zombie.getRandom().nextDouble() - 0.5) * 4.0D, zombie.getY(), zombie.getZ() + (zombie.getRandom().nextDouble() - 0.5) * 4.0D, zombie.getYRot(), zombie.getXRot());
        summoned.addEffect(new net.minecraft.world.effect.MobEffectInstance(buff, 1200, 1));
        summoned.setCustomName(net.minecraft.network.chat.Component.literal("§5🔮 召唤僵尸"));
        summoned.setCustomNameVisible(true);
        summoned.setTarget(target);

        level.addFreshEntity(summoned);
        level.playSound(null, zombie.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 1.0F);

        zombie.getPersistentData().putInt(SUMMONER_SPAWN_COUNT, spawnCount + 1);
        zombie.getPersistentData().putInt(SUMMONER_SPAWN_CD, QLMConfig.SUMMONER_ZOMBIE_SPAWN_INTERVAL.get());
    }

    private static void tickSkeletonInfiniteArrows(AbstractSkeleton skeleton) {
        if (!QLMConfig.SKELETON_INFINITE_ARROWS.get()) return;
        ItemStack handStack = skeleton.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (handStack.is(Items.BOW)) {
            try {
                java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> emap = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(handStack);
                emap.put(net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS, 1);
                net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(emap, handStack);
            } catch (Exception ignored) {}
        }
        ItemStack offhand = skeleton.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.isEmpty()) {
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW));
        }
    }

    private static void tickBlockBreakAndPlace(Zombie zombie, ServerLevel level) {
        DayPhase phase = getCurrentDayPhase(level);
        LivingEntity target = zombie.getTarget();
        if (target == null) return;

        int breakCd = zombie.getPersistentData().getInt(BLOCK_BREAK_COOLDOWN);
        if (breakCd > 0) {
            zombie.getPersistentData().putInt(BLOCK_BREAK_COOLDOWN, breakCd - 1);
        } else {
            if (QLMConfig.ZOMBIE_BREAK_BLOCKS.get() && (phase == DayPhase.NORMAL || phase == DayPhase.HARD || phase == DayPhase.EXTREME)) {
                if (tryBreakBlockingBlock(zombie, target, level, phase)) {
                    zombie.getPersistentData().putInt(BLOCK_BREAK_COOLDOWN, QLMConfig.ZOMBIE_BREAK_INTERVAL.get());
                }
            }
        }

        int placeCd = zombie.getPersistentData().getInt(BLOCK_PLACE_COOLDOWN);
        if (placeCd > 0) {
            zombie.getPersistentData().putInt(BLOCK_PLACE_COOLDOWN, placeCd - 1);
        } else {
            if (QLMConfig.ZOMBIE_PLACE_BLOCKS.get() && phase == DayPhase.EXTREME) {
                if (tryPlaceBlockTowardsTarget(zombie, target, level)) {
                    zombie.getPersistentData().putInt(BLOCK_PLACE_COOLDOWN, QLMConfig.ZOMBIE_PLACE_INTERVAL.get());
                }
            }
        }
    }

    private static boolean tryBreakBlockingBlock(Zombie zombie, LivingEntity target, ServerLevel level, DayPhase phase) {
        if (!zombie.getNavigation().isDone()) return false;
        if (zombie.distanceToSqr(target) < 4.0D) return false;

        Vec3 dir = target.position().subtract(zombie.position()).normalize();
        int bx = zombie.getBlockX() + (int) Math.round(dir.x);
        int bz = zombie.getBlockZ() + (int) Math.round(dir.z);

        int bestY = zombie.getBlockY();
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos pos = new BlockPos(bx, zombie.getBlockY() + dy, bz);
            BlockState state = level.getBlockState(pos);
            if (isBreakable(state)) {
                level.destroyBlock(pos, false);
                level.levelEvent(2001, pos, Block.getId(state));
                return true;
            }
        }
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = new BlockPos(bx, zombie.getBlockY() + dy, bz);
            BlockState state = level.getBlockState(pos);
            if (isBreakable(state)) {
                level.destroyBlock(pos, false);
                level.levelEvent(2001, pos, Block.getId(state));
                return true;
            }
        }
        // Also try diagonal
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos pos = new BlockPos(zombie.getBlockX() + dx, zombie.getBlockY() + dy, zombie.getBlockZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (isBreakable(state) && level.getBlockState(pos.above()).isAir()) {
                        level.destroyBlock(pos, false);
                        level.levelEvent(2001, pos, Block.getId(state));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isBreakable(BlockState state) {
        Block b = state.getBlock();
        if (state.isAir()) return false;
        if (b instanceof DoorBlock) return true;
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BEDROCK || b == Blocks.OBSIDIAN) return false;
        if (b == Blocks.ANVIL || b == Blocks.ENCHANTING_TABLE || b == Blocks.ENDER_CHEST) return false;
        float hardness = -1.0F;
        try { hardness = state.getDestroySpeed(null, null); }
        catch (Exception e) {
            try { hardness = b.defaultDestroyTime(); } catch (Exception e2) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Failed to get block hardness: {}", e2.getMessage());
        }
        }
        if (hardness < 0) return false;
        return hardness < 5.0F;
    }

    private static boolean tryPlaceBlockTowardsTarget(Zombie zombie, LivingEntity target, ServerLevel level) {
        Vec3 dir = target.position().subtract(zombie.position());
        int dx = (int) Math.signum(dir.x);
        int dz = (int) Math.signum(dir.z);
        if (dx == 0 && dz == 0) return false;

        BlockPos zombieFeet = zombie.blockPosition();

        Direction dirX = dx > 0 ? Direction.EAST : Direction.WEST;
        Direction dirZ = dz > 0 ? Direction.SOUTH : Direction.NORTH;

        BlockPos inFront;
        if (dx != 0 && dz != 0) {
            inFront = zombieFeet.relative(dirX, 1).relative(dirZ, 1);
        } else if (dx != 0) {
            inFront = zombieFeet.relative(dirX, 1);
        } else {
            inFront = zombieFeet.relative(dirZ, 1);
        }
        BlockPos belowInFront = inFront.below();
        if (level.getBlockState(belowInFront).isAir() && level.getBlockState(inFront).isAir()) {
            if (canPlaceBlock(level, belowInFront)) {
                level.setBlock(belowInFront, Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.levelEvent(2001, belowInFront, Block.getId(Blocks.COBBLESTONE.defaultBlockState()));
                return true;
            }
        }

        int dy = target.getBlockY() - zombie.getBlockY();
        if (dy >= 1) {
            BlockPos stepPos;
            if (dx != 0 && dz != 0) {
                stepPos = zombieFeet.relative(dirX, 1).relative(dirZ, 1);
            } else if (dx != 0) {
                stepPos = zombieFeet.relative(dirX, 1);
            } else {
                stepPos = zombieFeet.relative(dirZ, 1);
            }
            BlockState stepState = level.getBlockState(stepPos);
            if (stepState.isAir() && canPlaceBlock(level, stepPos)) {
                level.setBlock(stepPos, Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.levelEvent(2001, stepPos, Block.getId(Blocks.COBBLESTONE.defaultBlockState()));
                return true;
            }
            BlockPos nextStep;
            if (dx != 0 && dz != 0) {
                nextStep = stepPos.relative(dirX, 1).relative(dirZ, 1).above();
            } else if (dx != 0) {
                nextStep = stepPos.relative(dirX, 1).above();
            } else {
                nextStep = stepPos.relative(dirZ, 1).above();
            }
            if (level.getBlockState(nextStep).isAir() && canPlaceBlock(level, nextStep)) {
                level.setBlock(nextStep, Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.levelEvent(2001, nextStep, Block.getId(Blocks.COBBLESTONE.defaultBlockState()));
                return true;
            }
        }

        return false;
    }

    private static boolean canPlaceBlock(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() > level.getMaxBuildHeight()) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return false;
        AABB bounds = Blocks.COBBLESTONE.defaultBlockState().getShape(level, pos).bounds();
        if (bounds == null) return true;
        return level.noCollision(null, new AABB(pos.getX() + bounds.minX, pos.getY() + bounds.minY, pos.getZ() + bounds.minZ,
                pos.getX() + bounds.maxX, pos.getY() + bounds.maxY, pos.getZ() + bounds.maxZ));
    }

    private static void tickAggressiveTargeting(Mob mob, ServerLevel level) {
        int cd = mob.getPersistentData().getInt(TARGET_SEARCH_CD);
        if (cd > 0) {
            mob.getPersistentData().putInt(TARGET_SEARCH_CD, cd - 1);
            return;
        }
        mob.getPersistentData().putInt(TARGET_SEARCH_CD, 100);

        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget != null && currentTarget.isAlive() && mob.distanceToSqr(currentTarget) < 1600.0D) {
            return;
        }

        int radius = QLMConfig.AGGRESSIVE_TARGETING_RADIUS.get();
        Player best = null;
        double bestDist = radius * radius;

        for (Player player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;
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

    private static void tickZombieDoorBreaking(Zombie zombie) {
        if (!QLMConfig.ZOMBIE_BREAK_DOORS.get()) return;

        LivingEntity target = zombie.getTarget();
        if (target == null) {
            clearDoorData(zombie);
            return;
        }

        ServerLevel level = getServerLevel(zombie.level());
        if (level == null) return;
        DayPhase phase = getCurrentDayPhase(level);
        if (phase != DayPhase.NORMAL && phase != DayPhase.HARD && phase != DayPhase.EXTREME) return;

        BlockPos doorPos = findNearbyDoor(zombie, target);
        if (doorPos == null) {
            clearDoorData(zombie);
            return;
        }

        int progress = zombie.getPersistentData().getInt(DOOR_PROGRESS_TAG);
        int storedX = zombie.getPersistentData().getInt(DOOR_POS_X);
        int storedY = zombie.getPersistentData().getInt(DOOR_POS_Y);
        int storedZ = zombie.getPersistentData().getInt(DOOR_POS_Z);

        if (storedX != doorPos.getX() || storedY != doorPos.getY() || storedZ != doorPos.getZ()) {
            zombie.getPersistentData().putInt(DOOR_POS_X, doorPos.getX());
            zombie.getPersistentData().putInt(DOOR_POS_Y, doorPos.getY());
            zombie.getPersistentData().putInt(DOOR_POS_Z, doorPos.getZ());
            progress = 0;
        }

        if (zombie.distanceToSqr(Vec3.atCenterOf(doorPos)) > 9.0D) {
            zombie.getNavigation().moveTo(doorPos.getX() + 0.5D, doorPos.getY(), doorPos.getZ() + 0.5D, 1.0D);
            return;
        }

        progress += 1;

        int breakTime = switch (phase) {
            case NORMAL -> 240;
            case HARD -> 160;
            case EXTREME -> 100;
            default -> 99999;
        };

        zombie.getPersistentData().putInt(DOOR_PROGRESS_TAG, progress);

        if (progress % 10 == 0) {
            level.destroyBlockProgress(zombie.getId(), doorPos, Math.min(9, (progress * 9) / breakTime));
        }

        if (progress >= breakTime) {
            BlockState state = level.getBlockState(doorPos);
            level.destroyBlock(doorPos, false);
            level.levelEvent(1019, doorPos, 0);
            clearDoorData(zombie);
        }
    }

    private static BlockPos findNearbyDoor(Zombie zombie, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();
        BlockPos zombiePos = zombie.blockPosition();
        double bestScore = -1.0D;
        BlockPos bestPos = null;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = new BlockPos(zombiePos.getX() + dx, zombiePos.getY() + dy, zombiePos.getZ() + dz);
                    BlockState state = zombie.level().getBlockState(pos);
                    if (state.getBlock() instanceof DoorBlock) {
                        double distToZombie = zombie.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                        double distToTarget = target.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                        double score = 100.0D / (distToZombie + 1.0D) + 50.0D / (distToTarget + 1.0D);
                        if (score > bestScore) {
                            bestScore = score;
                            bestPos = pos;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private static void clearDoorData(Zombie zombie) {
        if (zombie.getPersistentData().contains(DOOR_PROGRESS_TAG)) {
            Level level = zombie.level();
            if (level instanceof ServerLevel sl) {
                int x = zombie.getPersistentData().getInt(DOOR_POS_X);
                int y = zombie.getPersistentData().getInt(DOOR_POS_Y);
                int z = zombie.getPersistentData().getInt(DOOR_POS_Z);
                sl.destroyBlockProgress(zombie.getId(), new BlockPos(x, y, z), -1);
            }
            zombie.getPersistentData().remove(DOOR_PROGRESS_TAG);
            zombie.getPersistentData().remove(DOOR_POS_X);
            zombie.getPersistentData().remove(DOOR_POS_Y);
            zombie.getPersistentData().remove(DOOR_POS_Z);
        }
    }

    private static void tickVillagerPanic(Villager villager) {
        if (!villager.isAlive()) return;
        int radius = villager.getPersistentData().getInt("qlmzombie.flee_radius");
        double panicBoost = villager.getPersistentData().getDouble("qlmzombie.panic_boost");
        if (radius <= 0 || panicBoost <= 1.0) return;

        double radiusSq = radius * radius;
        LivingEntity nearestHostile = null;
        double bestDist = radiusSq;

        for (Entity e : villager.level().getEntitiesOfClass(Entity.class, villager.getBoundingBox().inflate(radius))) {
            if (e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof Creeper
                    || e instanceof net.minecraft.world.entity.monster.Spider) {
                double dist = e.distanceToSqr(villager);
                if (dist < bestDist) {
                    bestDist = dist;
                    nearestHostile = (LivingEntity) e;
                }
            }
        }

        if (nearestHostile != null) {
            AttributeInstance speedAttr = villager.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                if (speedAttr.getModifier(UUID.fromString("8a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e80")) == null) {
                    AttributeModifier boost = new AttributeModifier(
                            UUID.fromString("8a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e80"),
                            "QLM Villager Panic",
                            panicBoost - 1.0D,
                            AttributeModifier.Operation.MULTIPLY_BASE);
                    speedAttr.addTransientModifier(boost);
                }
            }
        } else {
            AttributeInstance speedAttr = villager.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(UUID.fromString("8a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e80"));
            }
        }
    }

    private static final java.util.UUID GUARDIAN_SPEED_UUID = java.util.UUID.fromString("8a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e81");

    private static void tickVillageGuardian(Villager guardian, ServerLevel level) {
        if (!guardian.isAlive()) return;

        int searchRadius = 24;
        double searchRadiusSq = searchRadius * searchRadius;
        LivingEntity target = null;
        double bestDist = searchRadiusSq;
        LivingEntity currentTarget = guardian.getTarget();
        if (currentTarget != null && currentTarget.isAlive() && guardian.distanceToSqr(currentTarget) < searchRadiusSq) {
            target = currentTarget;
        }
        if (target == null) {
            for (Entity e : level.getEntitiesOfClass(Entity.class, guardian.getBoundingBox().inflate(searchRadius))) {
                if (e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof Creeper
                        || e instanceof net.minecraft.world.entity.monster.Spider
                        || e instanceof net.minecraft.world.entity.monster.Husk
                        || e instanceof net.minecraft.world.entity.monster.Drowned
                        || e instanceof ZombifiedPiglin
                        || e instanceof net.minecraft.world.entity.monster.Witch
                        || e instanceof net.minecraft.world.entity.monster.Ravager) {
                    double dist = e.distanceToSqr(guardian);
                    if (dist < bestDist) {
                        bestDist = dist;
                        target = (LivingEntity) e;
                    }
                }
            }
        }

        if (target != null) {
            guardian.setTarget(target);
            guardian.setLastHurtMob(target);
            Vec3 dir = target.position().subtract(guardian.position());
            double hDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (hDist > 0.1D) {
                guardian.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0D / Math.PI) - 90.0F);
                guardian.yBodyRot = guardian.getYRot();
                double speed = 0.35D;
                guardian.setDeltaMovement(dir.x / hDist * speed * 0.35D, guardian.getDeltaMovement().y, dir.z / hDist * speed * 0.35D);
            }
            if (hDist < 2.0D) {
                int cd = guardian.getPersistentData().getInt(GUARDIAN_ATTACK_CD);
                if (cd <= 0) {
                    double atk = 5.0D;
                    try {
                        AttributeInstance atkAttr = guardian.getAttribute(Attributes.ATTACK_DAMAGE);
                        if (atkAttr != null) atk = atkAttr.getValue();
                    } catch (Exception e) {
                        QLMZombieMod.LOGGER.debug("[QLM Zombie] Failed to get guardian attack damage: {}", e.getMessage());
                    }
                    ItemStack weapon = guardian.getMainHandItem();
                    float bonusDmg = 0.0F;
                    try {
                        bonusDmg = net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(weapon, target.getMobType());
                    } catch (Exception e) {
                        QLMZombieMod.LOGGER.debug("[QLM Zombie] Failed to get damage bonus: {}", e.getMessage());
                    }
                    float totalDmg = (float) atk + bonusDmg;
                    boolean success = target.hurt(level.damageSources().mobAttack(guardian), totalDmg);
                    if (success) {
                        double kbX = (target.getX() - guardian.getX()) * 0.12D;
                        double kbZ = (target.getZ() - guardian.getZ()) * 0.12D;
                        target.knockback(0.3D, kbX, kbZ);
                        guardian.setDeltaMovement(guardian.getDeltaMovement().multiply(0.5D, 1.0D, 0.5D));
                        try {
                            weapon.hurtAndBreak(1, guardian, (p_213394_) -> p_213394_.broadcastBreakEvent(EquipmentSlot.MAINHAND));
                        } catch (Exception e) {
                            QLMZombieMod.LOGGER.debug("[QLM Zombie] Failed to damage guardian weapon: {}", e.getMessage());
                        }
                        level.playSound(null, guardian.blockPosition(), SoundEvents.VINDICATOR_CELEBRATE,
                                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                    }
                    guardian.getPersistentData().putInt(GUARDIAN_ATTACK_CD, 30);
                } else {
                    guardian.getPersistentData().putInt(GUARDIAN_ATTACK_CD, cd - 1);
                }
            } else {
                int cd = guardian.getPersistentData().getInt(GUARDIAN_ATTACK_CD);
                if (cd > 0) guardian.getPersistentData().putInt(GUARDIAN_ATTACK_CD, cd - 1);
            }
            AttributeInstance speedAttr = guardian.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null && speedAttr.getModifier(GUARDIAN_SPEED_UUID) == null) {
                AttributeModifier boost = new AttributeModifier(GUARDIAN_SPEED_UUID,
                        "QLM Guardian Combat", 0.2D, AttributeModifier.Operation.MULTIPLY_BASE);
                speedAttr.addTransientModifier(boost);
            }
        } else {
            guardian.setTarget(null);
            AttributeInstance speedAttr = guardian.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(GUARDIAN_SPEED_UUID);
            }
            int cd = guardian.getPersistentData().getInt(GUARDIAN_ATTACK_CD);
            if (cd > 0) guardian.getPersistentData().putInt(GUARDIAN_ATTACK_CD, cd - 1);
        }

        if (guardian.tickCount % 1200 == 0 && guardian.getHealth() < guardian.getMaxHealth() * 0.9F) {
            guardian.heal(4.0F);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!QLMConfig.ENABLE_AI_OPTIMIZATION.get()) return;
        Entity source = event.getSource().getDirectEntity();
        if (!(source instanceof AbstractArrow arrow)) return;
        Entity shooter = arrow.getOwner();
        if (!(shooter instanceof AbstractSkeleton skeleton)) return;

        if (arrow.getPersistentData().getBoolean("qlmzombie.perfect_shot")) {
            float original = event.getAmount();
            float bonus = original * 0.8F;
            event.setAmount(original + bonus);
            LivingEntity victim = event.getEntity();
            if (victim != null) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
            return;
        }

        if (!skeleton.getPersistentData().contains("qlmzombie.accuracy_boost")) return;
        double boost = skeleton.getPersistentData().getDouble("qlmzombie.accuracy_boost");
        if (boost <= 0) return;

        float baseDamage = event.getAmount();
        float bonus = baseDamage * (float) boost * 0.5F;
        event.setAmount(baseDamage + bonus);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!QLMConfig.ENABLE_AI_OPTIMIZATION.get()) return;
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (!(zombie.level() instanceof ServerLevel level)) return;
        if (!zombie.getPersistentData().getBoolean(TYPE_BARREL)) return;

        double r = zombie.getRandom().nextDouble();
        int count;
        if (r < 0.5D) {
            // 50% - small zombie pack
            count = 2 + zombie.getRandom().nextInt(2);
            for (int i = 0; i < count; i++) {
                Zombie newZombie = EntityType.ZOMBIE.create(level);
                if (newZombie != null) {
                    double ox = (zombie.getRandom().nextDouble() - 0.5D) * 1.5D;
                    double oz = (zombie.getRandom().nextDouble() - 0.5D) * 1.5D;
                    newZombie.moveTo(zombie.getX() + ox, zombie.getY(), zombie.getZ() + oz, zombie.getRandom().nextFloat() * 360.0F, 0.0F);
                    try {
                        AttributeInstance hp = newZombie.getAttribute(Attributes.MAX_HEALTH);
                        if (hp != null) hp.setBaseValue(hp.getBaseValue() * 0.7D);
                        newZombie.setHealth(newZombie.getMaxHealth());
                        AttributeInstance dmg = newZombie.getAttribute(Attributes.ATTACK_DAMAGE);
                        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * 0.8D);
                    } catch (Exception ignored) {
                    }
                    level.addFreshEntity(newZombie);
                }
            }
            level.playSound(null, zombie.blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.HOSTILE, 1.0F, 1.0F);
        } else if (r < 0.85D) {
            // 35% - zombie pack
            count = 2 + zombie.getRandom().nextInt(3);
            for (int i = 0; i < count; i++) {
                Zombie newZombie = EntityType.ZOMBIE.create(level);
                if (newZombie != null) {
                    double ox = (zombie.getRandom().nextDouble() - 0.5D) * 2.0D;
                    double oz = (zombie.getRandom().nextDouble() - 0.5D) * 2.0D;
                    newZombie.moveTo(zombie.getX() + ox, zombie.getY(), zombie.getZ() + oz, zombie.getRandom().nextFloat() * 360.0F, 0.0F);
                    level.addFreshEntity(newZombie);
                }
            }
            level.playSound(null, zombie.blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.HOSTILE, 1.0F, 1.0F);
        } else {
            // 15% - skeleton pack
            count = 1 + zombie.getRandom().nextInt(2);
            for (int i = 0; i < count; i++) {
                AbstractSkeleton sk = EntityType.SKELETON.create(level);
                if (sk != null) {
                    double ox = (zombie.getRandom().nextDouble() - 0.5D) * 2.0D;
                    double oz = (zombie.getRandom().nextDouble() - 0.5D) * 2.0D;
                    sk.moveTo(zombie.getX() + ox, zombie.getY(), zombie.getZ() + oz, zombie.getRandom().nextFloat() * 360.0F, 0.0F);
                    sk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    level.addFreshEntity(sk);
                }
            }
            level.playSound(null, zombie.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.0F, 0.8F);
        }
    }

    private static void applyFollowRangeModifier(LivingEntity entity, double multiplier) {
        if (multiplier <= 1.0) return;
        AttributeInstance attribute = entity.getAttribute(Attributes.FOLLOW_RANGE);
        if (attribute == null) return;
        attribute.removeModifier(FOLLOW_RANGE_UUID);
        double bonus = attribute.getBaseValue() * (multiplier - 1.0D);
        attribute.addTransientModifier(new AttributeModifier(
                FOLLOW_RANGE_UUID,
                "QLM AI Follow Range",
                bonus,
                AttributeModifier.Operation.ADDITION));
    }

    private static void applyMovementSpeedModifier(LivingEntity entity, double multiplier) {
        if (multiplier <= 1.0) return;
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(MOVEMENT_SPEED_UUID);
        double bonus = attribute.getBaseValue() * (multiplier - 1.0D);
        attribute.addTransientModifier(new AttributeModifier(
                MOVEMENT_SPEED_UUID,
                "QLM AI Movement Speed",
                bonus,
                AttributeModifier.Operation.ADDITION));
    }

    private static ServerLevel getServerLevel(Level level) {
        if (level instanceof ServerLevel sl) return sl;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && level != null) {
            return server.getLevel(level.dimension());
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void removeGoalsByType(Mob mob, Class<? extends net.minecraft.world.entity.ai.goal.Goal> goalClass) {
        try {
            java.lang.reflect.Field field = findGoalSelectorSetField(mob.goalSelector);
            if (field == null) return;
            field.setAccessible(true);
            Object goals = field.get(mob.goalSelector);
            if (goals instanceof java.util.Collection<?> coll) {
                java.util.Iterator<?> it = coll.iterator();
                while (it.hasNext()) {
                    Object wrapped = it.next();
                    java.lang.reflect.Field goalField = findGoalField(wrapped);
                    if (goalField != null) {
                        goalField.setAccessible(true);
                        Object goalObj = goalField.get(wrapped);
                        if (goalClass.isInstance(goalObj)) {
                            it.remove();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void removeRangedAttackGoals(Mob mob) {
        try {
            java.lang.reflect.Field field = findGoalSelectorSetField(mob.goalSelector);
            if (field == null) return;
            field.setAccessible(true);
            Object goals = field.get(mob.goalSelector);
            if (goals instanceof java.util.Collection<?> coll) {
                java.util.Iterator<?> it = coll.iterator();
                while (it.hasNext()) {
                    Object wrapped = it.next();
                    java.lang.reflect.Field goalField = findGoalField(wrapped);
                    if (goalField != null) {
                        goalField.setAccessible(true);
                        Object goalObj = goalField.get(wrapped);
                        String name = goalObj.getClass().getSimpleName().toLowerCase();
                        if (name.contains("ranged") || name.contains("bow")) {
                            it.remove();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static java.lang.reflect.Field findGoalSelectorSetField(net.minecraft.world.entity.ai.goal.GoalSelector selector) {
        for (java.lang.reflect.Field f : net.minecraft.world.entity.ai.goal.GoalSelector.class.getDeclaredFields()) {
            if (java.util.Set.class.isAssignableFrom(f.getType()) || java.util.Collection.class.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        return null;
    }

    private static java.lang.reflect.Field findGoalField(Object wrappedGoal) {
        for (java.lang.reflect.Field f : wrappedGoal.getClass().getDeclaredFields()) {
            if (net.minecraft.world.entity.ai.goal.Goal.class.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        return null;
    }

    private static final UUID DIFFICULTY_SPEED_UUID = UUID.fromString("9a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e71");
    private static final UUID DIFFICULTY_ARMOR_UUID = UUID.fromString("9a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e72");
    private static final UUID DIFFICULTY_KNOCKBACK_UUID = UUID.fromString("9a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e73");
    private static final UUID DIFFICULTY_DAMAGE_UUID = UUID.fromString("9a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e74");
    private static final UUID DIFFICULTY_HEALTH_UUID = UUID.fromString("9a5e8c9a-2f2b-4a9d-9c1f-1a2b3c4d5e75");

    private static void applyDifficultyScaling(Mob mob, DayPhase phase) {
        if (phase == DayPhase.SAFE || phase == DayPhase.EASY) return;

        double speedBonusPerPhase = QLMConfig.MOB_AI_SPEED_BONUS_PER_PHASE.get();
        double armorPerPhase = QLMConfig.MOB_AI_ARMOR_PER_PHASE.get();
        double kbResistPerPhase = QLMConfig.MOB_AI_KNOCKBACK_RESISTANCE.get();

        int phaseIndex = switch (phase) {
            case SAFE -> 0;
            case EASY -> 0;
            case NORMAL -> 1;
            case HARD -> 2;
            case EXTREME -> 3;
        };

        if (phaseIndex == 0) return;

        try {
            AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                double bonus = speedAttr.getBaseValue() * speedBonusPerPhase * phaseIndex;
                speedAttr.removeModifier(DIFFICULTY_SPEED_UUID);
                speedAttr.addTransientModifier(new AttributeModifier(
                        DIFFICULTY_SPEED_UUID, "QLM Difficulty Speed",
                        bonus, AttributeModifier.Operation.ADDITION));
            }

            AttributeInstance armorAttr = mob.getAttribute(Attributes.ARMOR);
            if (armorAttr != null) {
                double armorBonus = armorPerPhase * phaseIndex;
                armorAttr.removeModifier(DIFFICULTY_ARMOR_UUID);
                armorAttr.addTransientModifier(new AttributeModifier(
                        DIFFICULTY_ARMOR_UUID, "QLM Difficulty Armor",
                        armorBonus, AttributeModifier.Operation.ADDITION));
            }

            AttributeInstance kbAttr = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) {
                double kbBonus = kbResistPerPhase * phaseIndex;
                if (kbBonus > 1.0D) kbBonus = 1.0D;
                kbAttr.removeModifier(DIFFICULTY_KNOCKBACK_UUID);
                kbAttr.addTransientModifier(new AttributeModifier(
                        DIFFICULTY_KNOCKBACK_UUID, "QLM Difficulty KB Resist",
                        kbBonus, AttributeModifier.Operation.ADDITION));
            }

            AttributeInstance dmgAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                double dmgBonus = phaseIndex * 1.5D;
                dmgAttr.removeModifier(DIFFICULTY_DAMAGE_UUID);
                dmgAttr.addTransientModifier(new AttributeModifier(
                        DIFFICULTY_DAMAGE_UUID, "QLM Difficulty Damage",
                        dmgBonus, AttributeModifier.Operation.ADDITION));
            }

            AttributeInstance hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
            if (hpAttr != null) {
                double hpBonus = phaseIndex * 10.0D;
                hpAttr.removeModifier(DIFFICULTY_HEALTH_UUID);
                hpAttr.addTransientModifier(new AttributeModifier(
                        DIFFICULTY_HEALTH_UUID, "QLM Difficulty Health",
                        hpBonus, AttributeModifier.Operation.ADDITION));
                mob.setHealth(mob.getMaxHealth());
            }

            if (mob instanceof Zombie zombie) {
                if (phase == DayPhase.EXTREME) {
                    zombie.getPersistentData().putBoolean("qlmzombie.can_place_blocks", true);
                }
            }
        } catch (Exception e) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] Difficulty scaling failed: {}", e.getMessage());
        }
    }

    public static class StrafeBowAttackGoal<T extends Mob & net.minecraft.world.entity.monster.RangedAttackMob>
            extends net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<T> {

        private final T mob;
        private int attackTime = -1;
        private int seeTime;
        private final double speedModifier;
        private final int attackIntervalMin;
        private final int attackIntervalMax;
        private final float attackRadius;
        private final float attackRadiusSqr;
        private float strafeDir = 0.0F;

        public StrafeBowAttackGoal(T mob, double speed, int minInterval, int maxInterval, float radius) {
            super(mob, speed, minInterval, radius);
            this.mob = mob;
            this.speedModifier = speed;
            this.attackIntervalMin = minInterval;
            this.attackIntervalMax = maxInterval;
            this.attackRadius = radius;
            this.attackRadiusSqr = radius * radius;
        }

        @Override
        public boolean canContinueToUse() {
            return (this.canUse() || !this.mob.getNavigation().isDone());
        }

        @Override
        public void stop() {
            this.seeTime = 0;
            this.attackTime = -1;
            this.mob.stopUsingItem();
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return;

            double distSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            boolean canSee = this.mob.getSensing().hasLineOfSight(target);
            boolean sawBefore = this.seeTime > 0;

            if (canSee != sawBefore) this.seeTime = 0;
            if (canSee) this.seeTime++;
            else this.seeTime--;

            if (distSq > (double) (this.attackRadiusSqr * 0.9D) || this.seeTime < 10) {
                this.mob.getNavigation().moveTo(target, this.speedModifier);
            } else if (distSq < (double) (this.attackRadiusSqr * 0.25D)) {
                this.mob.getNavigation().stop();
                Vec3 away = this.mob.position().subtract(target.position()).normalize().scale(1.5D);
                this.mob.getNavigation().moveTo(this.mob.getX() + away.x, this.mob.getY(), this.mob.getZ() + away.z, this.speedModifier);
            } else {
                this.mob.getNavigation().stop();
            }

            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (--this.attackTime == 0) {
                if (!canSee && this.seeTime < -60) {
                    this.attackTime = this.attackIntervalMin;
                } else if (canSee) {
                    float distFactor = Math.min(1.5F, (float) Math.sqrt(distSq) / this.attackRadius);
                    this.mob.performRangedAttack(target, distFactor);
                    this.attackTime = this.attackIntervalMin + (int) (distFactor * (this.attackIntervalMax - this.attackIntervalMin));
                }
            } else if (this.attackTime < 0) {
                this.attackTime = this.attackIntervalMin;
            }

            if (this.seeTime >= 5 && distSq < (double) this.attackRadiusSqr) {
                if (this.strafeDir == 0.0F) {
                    this.strafeDir = this.mob.getRandom().nextBoolean() ? 0.4F : -0.4F;
                }
                this.mob.setZza(0.0F);
                this.mob.setXxa(this.strafeDir);
                if (this.mob.getRandom().nextInt(50) == 0) {
                    this.strafeDir = -this.strafeDir;
                }
            } else {
                this.mob.setZza(0.0F);
                this.mob.setXxa(0.0F);
                this.strafeDir = 0.0F;
            }
        }
    }

    public static class OptimizedZombieAttackGoal extends MeleeAttackGoal {

        private final Zombie zombie;

        public OptimizedZombieAttackGoal(Zombie zombie, double speed, boolean onlyInSight) {
            super(zombie, speed, onlyInSight);
            this.zombie = zombie;
        }

        @Override
        public void tick() {
            super.tick();
            LivingEntity target = this.zombie.getTarget();
            if (target == null) return;

            if (this.zombie.isInWater()) {
                double dx = target.getX() - this.zombie.getX();
                double dy = (target.getY() - this.zombie.getY()) * 0.5D;
                double dz = target.getZ() - this.zombie.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.1D) {
                    this.zombie.setDeltaMovement(
                            this.zombie.getDeltaMovement().x() + (dx / dist) * 0.06D,
                            this.zombie.getDeltaMovement().y() + 0.03D + dy * 0.01D,
                            this.zombie.getDeltaMovement().z() + (dz / dist) * 0.06D);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            AttributeInstance healthAttr = villager.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null && healthAttr.getBaseValue() != 50.0D) {
                healthAttr.setBaseValue(50.0D);
                villager.setHealth(50.0F);
            }

            AttributeInstance attackAttr = villager.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null && attackAttr.getBaseValue() != 15.0D) {
                attackAttr.setBaseValue(15.0D);
            }
        }
    }
}
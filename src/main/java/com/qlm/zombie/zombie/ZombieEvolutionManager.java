package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 僵尸进化管理器：
 * - 随天数增加，僵尸进化概率提高
 * - 进化后的僵尸获得额外生命值、伤害、速度
 * - 进化僵尸有特殊名称和掉落加成
 * - 天数越高，血量越厚
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ZombieEvolutionManager {

    // 进化概率随天数增加
    private static final double[] EVOLUTION_CHANCE_BY_DAY = {
        0.00,  // Day 0-24:   0%
        0.10,  // Day 25-49: 10%
        0.25,  // Day 50-99: 25%
        0.50,  // Day 100-149: 50%
        0.75   // Day 150+:   75%
    };

    // 进化血量加成倍率
    private static final double[] EVOLUTION_HEALTH_MULTIPLIER = {
        0.0,   // Day 0-24:  无进化
        1.5,   // Day 25-49: 1.5x 血量
        2.0,   // Day 50-99: 2.0x 血量
        3.0,   // Day 100-149: 3.0x 血量
        5.0    // Day 150+:  5.0x 血量
    };

    private static final UUID EVOLUTION_HEALTH_UUID = UUID.fromString("e1e2e3e4-e5e6-4e7e-8e9e-0e1e2e3e4e5e");
    private static final UUID EVOLUTION_DAMAGE_UUID = UUID.fromString("f1f2f3f4-f5f6-4f7f-8f9f-0f1f2f3f4f5f");
    private static final UUID EVOLUTION_SPEED_UUID = UUID.fromString("a1a2a3a4-a5a6-4a7a-8a9a-0a1a2a3a4a5a");

    private static final String NBT_EVOLVED = "qlm_evolved";
    private static final String NBT_EVOLUTION_TIER = "qlm_evolution_tier";

    @SubscribeEvent
    public static void onZombieSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob)) return;
        if (entity.level().isClientSide()) return;

        // 只处理敌对生物
        if (!isEvolvableMob(mob)) return;

        ServerLevel level = (ServerLevel) mob.level();
        long day = level.getDayTime() / 24000L;

        // 获取进化等级
        int tier = getEvolutionTier(day);
        if (tier <= 0) return;

        // 掷骰决定是否进化
        RandomSource random = mob.getRandom();
        if (random.nextDouble() >= EVOLUTION_CHANCE_BY_DAY[tier]) return;

        // 应用进化
        applyEvolution(mob, tier, day);
    }

    private static boolean isEvolvableMob(Mob mob) {
        return mob instanceof Zombie || mob instanceof Skeleton ||
               mob instanceof Creeper || mob instanceof Spider ||
               mob instanceof ZombifiedPiglin;
    }

    private static int getEvolutionTier(long day) {
        if (day >= 150) return 4;
        if (day >= 100) return 3;
        if (day >= 50)  return 2;
        if (day >= 25)  return 1;
        return 0;
    }

    private static void applyEvolution(Mob mob, int tier, long day) {
        // 标记为已进化
        CompoundTag persistentData = mob.getPersistentData();
        persistentData.putBoolean(NBT_EVOLVED, true);
        persistentData.putInt(NBT_EVOLUTION_TIER, tier);

        // 血量加成
        AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double baseHealth = healthAttr.getBaseValue();
            double bonusHealth = baseHealth * (EVOLUTION_HEALTH_MULTIPLIER[tier] - 1.0);
            if (bonusHealth > 0) {
                healthAttr.removeModifier(EVOLUTION_HEALTH_UUID);
                healthAttr.addPermanentModifier(new AttributeModifier(
                    EVOLUTION_HEALTH_UUID, "Evolution Health Bonus",
                    bonusHealth, AttributeModifier.Operation.ADDITION
                ));
            }
        }

        // 伤害加成
        AttributeInstance damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            double baseDamage = damageAttr.getBaseValue();
            double bonusDamage = baseDamage * (0.5 * tier);
            if (bonusDamage > 0) {
                damageAttr.removeModifier(EVOLUTION_DAMAGE_UUID);
                damageAttr.addPermanentModifier(new AttributeModifier(
                    EVOLUTION_DAMAGE_UUID, "Evolution Damage Bonus",
                    bonusDamage, AttributeModifier.Operation.ADDITION
                ));
            }
        }

        // 速度加成（仅 tier 3+）
        if (tier >= 3) {
            AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                double speedBonus = 0.03 * (tier - 2);
                speedAttr.removeModifier(EVOLUTION_SPEED_UUID);
                speedAttr.addPermanentModifier(new AttributeModifier(
                    EVOLUTION_SPEED_UUID, "Evolution Speed Bonus",
                    speedBonus, AttributeModifier.Operation.ADDITION
                ));
            }
        }

        // 设置进化名称
        String[] tierNames = {"", "§7[进化] ", "§a[进化] ", "§e[进化] ", "§c[进化] "};
        String name = tierNames[tier] + mob.getName().getString();
        mob.setCustomName(Component.literal(name).withStyle(ChatFormatting.BOLD));
        mob.setCustomNameVisible(true);

        // 满血（Mob 继承 LivingEntity）
        mob.setHealth(mob.getMaxHealth());

        QLMZombieMod.LOGGER.debug("[僵尸进化] {} 已进化 (Tier {}, Day {})，血量: {}，伤害: {}",
            mob.getType().getDescriptionId(), tier, day,
            mob.getAttributeValue(Attributes.MAX_HEALTH),
            mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }
}
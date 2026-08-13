package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * 击杀敌对生物获得永久随机生命上限 / 攻击力 系统。
 *
 *  - 排除：玩家、村民、铁傀儡
 *  - 敌对生物：实现 Enemy 接口的怪物（僵尸、骷髅、爬行者等），以及其他非被动生物
 *  - 每次击杀：0.8概率触发；随机生命上限 [0.5, 2.0]，随机攻击力 [0.2, 1.5]
 *  - 神话级装备时：概率提升至 100%，且加成翻倍
 *  - 持久化到玩家 NBT：qlm_kill_health_total, qlm_kill_attack_total
 *  - 使用 transient AttributeModifier，每次登录/重算时重新注入
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class PermanentKillStats {

    // ======== NBT ========
    public static final String PERSISTENT_TAG = "qlm_kill_stats_v1";
    public static final String NBT_HEALTH_TOTAL = "health_total";
    public static final String NBT_ATTACK_TOTAL = "attack_total";
    public static final String NBT_KILL_COUNT = "kill_count";

    // ======== UUIDs ========
    private static final UUID PERM_HEALTH_UUID = UUID.fromString("a7f1c2e8-9b0d-4e3a-8c5f-1d2e3a4b5c6d");
    private static final UUID PERM_ATTACK_UUID = UUID.fromString("b8e2d3f9-ac1e-5f4b-9d6a-2e3f4b5c6d7e");

    // ======== 参数 ========
    private static final double BASE_CHANCE       = 0.80;
    private static final double MIN_HEALTH_BONUS  = 0.5;
    private static final double MAX_HEALTH_BONUS  = 2.0;
    private static final double MIN_ATTACK_BONUS  = 0.2;
    private static final double MAX_ATTACK_BONUS  = 1.5;

    // 防止 Tick 事件重复应用修饰符
    private static final ThreadLocal<Long> LAST_RECALC_TICK = ThreadLocal.withInitial(() -> 0L);

    // ==================== 判定 ====================

    /** 是否为允许统计的击杀目标（排除玩家、村民、铁傀儡） */
    public static boolean isAllowedTarget(LivingEntity entity) {
        if (entity == null) return false;
        if (entity instanceof Player) return false;
        if (entity instanceof Villager) return false;
        if (entity instanceof IronGolem) return false;
        // 仅允许敌对生物 (Enemy) 或被标记为 Monster 的实体
        if (entity instanceof Enemy) return true;
        // 某些 boss 或扩展模组可能未实现 Enemy 接口：再用 entity type 做一次兜底
        EntityType<?> type = entity.getType();
        String id = EntityType.getKey(type).toString();
        return id.contains("zombie")    || id.contains("skeleton")
            || id.contains("creeper")    || id.contains("spider")
            || id.contains("slime")      || id.contains("magma")
            || id.contains("ghast")      || id.contains("blaze")
            || id.contains("witch")      || id.contains("phantom")
            || id.contains("husk")       || id.contains("drowned")
            || id.contains("stray")      || id.contains("wither")
            || id.contains("dragon")     || id.contains("raider")
            || id.contains("illager")    || id.contains("pillager")
            || id.contains("evoker")     || id.contains("vindicator");
    }

    /** 玩家是否穿着/持有神话级装备 */
    public static boolean hasMythic(Player player) {
        EquipmentQuality mainH = EquipmentQuality.fromStack(player.getMainHandItem());
        if (mainH != null && mainH.isIndestructible()) return true;
        for (var armor : player.getArmorSlots()) {
            EquipmentQuality q = EquipmentQuality.fromStack(armor);
            if (q != null && q.isIndestructible()) return true;
        }
        return false;
    }

    // ==================== 主事件：死亡 ====================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null) return;
        if (!isAllowedTarget(victim)) return;

        DamageSource src = event.getSource();
        if (src == null) return;
        if (!(src.getEntity() instanceof Player p)) return;
        if (p.level().isClientSide()) return;
        if (!(p instanceof ServerPlayer sp)) return;

        RandomSource rnd = RandomSource.create();
        double chance = BASE_CHANCE;
        double multiplier = 1.0;
        if (hasMythic(sp)) { chance = 1.0; multiplier = 2.0; }

        if (rnd.nextDouble() > chance) {
            // 未触发加成：只增加击杀计数
            incrementKillCount(sp);
            return;
        }

        double deltaHealth = MIN_HEALTH_BONUS + rnd.nextDouble() * (MAX_HEALTH_BONUS - MIN_HEALTH_BONUS);
        double deltaAttack = MIN_ATTACK_BONUS + rnd.nextDouble() * (MAX_ATTACK_BONUS - MIN_ATTACK_BONUS);
        deltaHealth *= multiplier;
        deltaAttack *= multiplier;

        applyBonuses(sp, deltaHealth, deltaAttack);
        incrementKillCount(sp);

        // 通知玩家
        sp.sendSystemMessage(Component.empty()
                .append(Component.literal("[击杀奖励] ").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal("❤ +" + String.format("%.1f", deltaHealth) + "  ").withStyle(ChatFormatting.RED))
                .append(Component.literal("⚔ +" + String.format("%.1f", deltaAttack)).withStyle(ChatFormatting.GOLD)));
    }

    // ==================== NBT 持久化 ====================

    private static CompoundTag getTag(Player p) {
        var root = p.getPersistentData();
        if (!root.contains(PERSISTENT_TAG)) {
            root.put(PERSISTENT_TAG, new CompoundTag());
        }
        return root.getCompound(PERSISTENT_TAG);
    }

    private static void applyBonuses(Player p, double dH, double dA) {
        CompoundTag tag = getTag(p);
        double h = tag.getDouble(NBT_HEALTH_TOTAL) + dH;
        double a = tag.getDouble(NBT_ATTACK_TOTAL) + dA;
        tag.putDouble(NBT_HEALTH_TOTAL, h);
        tag.putDouble(NBT_ATTACK_TOTAL, a);
        refreshPlayerModifiers(p);
    }

    private static void incrementKillCount(Player p) {
        CompoundTag tag = getTag(p);
        tag.putInt(NBT_KILL_COUNT, tag.getInt(NBT_KILL_COUNT) + 1);
    }

    public static double getHealthTotal(Player p) { return getTag(p).getDouble(NBT_HEALTH_TOTAL); }
    public static double getAttackTotal(Player p) { return getTag(p).getDouble(NBT_ATTACK_TOTAL); }
    public static int    getKillCount(Player p)   { return getTag(p).getInt(NBT_KILL_COUNT); }

    // ==================== Attribute Modifiers 刷新 ====================

    /** 重新计算并注入永久加成 AttributeModifier（调用方保证在服务端） */
    public static void refreshPlayerModifiers(Player p) {
        double hT = getHealthTotal(p);
        double aT = getAttackTotal(p);

        AttributeInstance hAttr = p.getAttribute(Attributes.MAX_HEALTH);
        if (hAttr != null) {
            hAttr.removeModifier(PERM_HEALTH_UUID);
            if (hT > 0) {
                hAttr.addTransientModifier(new AttributeModifier(PERM_HEALTH_UUID,
                        "QLM KillPerm Health", hT, AttributeModifier.Operation.ADDITION));
            }
            double curMx = hAttr.getValue();
            if (p.getHealth() > curMx) p.setHealth((float) curMx);
        }

        AttributeInstance aAttr = p.getAttribute(Attributes.ATTACK_DAMAGE);
        if (aAttr != null) {
            aAttr.removeModifier(PERM_ATTACK_UUID);
            if (aT > 0) {
                aAttr.addTransientModifier(new AttributeModifier(PERM_ATTACK_UUID,
                        "QLM KillPerm Attack", aT, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) refreshPlayerModifiers(sp);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) refreshPlayerModifiers(sp);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) refreshPlayerModifiers(sp);
    }

    // 每秒刷新一次（防止被其他 mod 清除 transient modifier）
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.side.isClient()) return;
        Player p = e.player;
        long now = p.level().getGameTime();
        if ((now & 0x13) == 0) { // 约 10 Hz
            Long last = LAST_RECALC_TICK.get();
            if (last == null || now - last > 19) { // 每 20 tick 重算一次
                LAST_RECALC_TICK.set(now);
                try {
                    var hAttr = p.getAttribute(Attributes.MAX_HEALTH);
                    boolean missingHealth = hAttr != null && hAttr.getModifier(PERM_HEALTH_UUID) == null && getHealthTotal(p) > 0;
                    var aAttr = p.getAttribute(Attributes.ATTACK_DAMAGE);
                    boolean missingAttack = aAttr != null && aAttr.getModifier(PERM_ATTACK_UUID) == null && getAttackTotal(p) > 0;
                    if (missingHealth || missingAttack) refreshPlayerModifiers(p);
                } catch (Throwable ignore) {}
            }
        }
    }

    // ==================== 命令：/qlm stats (扩展) ====================
    public static List<Component> formatStats(Player p) {
        var c1 = Component.literal("=== QLM 击杀永久属性 ===").withStyle(ChatFormatting.GOLD);
        var c2 = Component.literal("  击杀计数: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("" + getKillCount(p)).withStyle(ChatFormatting.YELLOW));
        var c3 = Component.literal("  永久生命上限: +").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.1f", getHealthTotal(p))).withStyle(ChatFormatting.RED));
        var c4 = Component.literal("  永久攻击力: +").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.1f", getAttackTotal(p))).withStyle(ChatFormatting.GOLD));
        return List.of(c1, c2, c3, c4);
    }
}

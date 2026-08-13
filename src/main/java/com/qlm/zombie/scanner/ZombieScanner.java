package com.qlm.zombie.scanner;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 自动扫描系统：
 * - 扫描附近20格内的僵尸类怪物
 * - 根据僵尸强度显示不同颜色的发光效果
 * - 强度判定：普通 < 精英 < 进化 < 小Boss < 大Boss
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ZombieScanner {

    private static final int SCAN_RANGE = 20;
    private static final int SCAN_INTERVAL = 40; // 每2秒扫描一次
    private static final int GLOW_DURATION = 60; // 3秒发光

    // 发光颜色（通过发光效果+不同颜色代码实现）
    private static final int COLOR_WEAK = 0x00FF00;    // 绿色 - 普通僵尸
    private static final int COLOR_NORMAL = 0xFFFF00;  // 黄色 - 中等强度
    private static final int COLOR_STRONG = 0xFF6600;  // 橙色 - 精英
    private static final int COLOR_ELITE = 0xFF0000;   // 红色 - 进化
    private static final int COLOR_BOSS = 0x9900FF;    // 紫色 - Boss

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < SCAN_INTERVAL) return;
        tickCounter = 0;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.level().dimension() != Level.OVERWORLD) continue;

            scanAndHighlight(player, (ServerLevel) player.level());
        }
    }

    private static void scanAndHighlight(ServerPlayer player, ServerLevel level) {
        Vec3 pos = player.position();
        AABB scanArea = new AABB(
            pos.x - SCAN_RANGE, pos.y - SCAN_RANGE, pos.z - SCAN_RANGE,
            pos.x + SCAN_RANGE, pos.y + SCAN_RANGE, pos.z + SCAN_RANGE
        );

        // 扫描所有敌对生物
        List<Mob> nearbyHostiles = level.getEntitiesOfClass(Mob.class, scanArea, 
            mob -> mob instanceof Enemy && mob.isAlive()
        );

        if (nearbyHostiles.isEmpty()) return;

        // 通知玩家扫描结果
        if (nearbyHostiles.size() > 5) {
            player.sendSystemMessage(Component.literal("§c§l⚠ 扫描到 " + nearbyHostiles.size() + " 个敌对生物在附近！"));
        }

        for (Mob mob : nearbyHostiles) {
            int color = getMobColor(mob);
            // 应用发光效果
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION, 0, true, false));

            // 通过粒子效果和名称显示强度
            String colorCode = switch (color) {
                case 0x00FF00 -> "§a";
                case 0xFFFF00 -> "§e";
                case 0xFF6600 -> "§6";
                case 0xFF0000 -> "§c";
                case 0x9900FF -> "§5";
                default -> "§7";
            };

            // 为高强度僵尸添加标记名称
            if (color >= COLOR_STRONG) {
                String label = switch (color) {
                    case 0xFF6600 -> "§6[精英] ";
                    case 0xFF0000 -> "§c[进化] ";
                    case 0x9900FF -> "§5[Boss] ";
                    default -> "";
                };
                String existingName = mob.getCustomName() != null ? 
                    ChatFormatting.stripFormatting(mob.getCustomName().getString()) : 
                    mob.getType().getDescription().getString();
                // 只设置一次名称，避免频繁刷新
                if (mob.getCustomName() == null || !mob.getCustomName().getString().contains(label)) {
                    mob.setCustomName(Component.literal(label + existingName));
                    mob.setCustomNameVisible(true);
                }
            }
        }
    }

    private static int getMobColor(Mob mob) {
        double health = mob.getAttributeValue(Attributes.MAX_HEALTH);
        double damage = mob.getAttributeValue(Attributes.ATTACK_DAMAGE);

        // 检查NBT标记
        CompoundTag tag = mob.getPersistentData();

        // Boss级
        if (tag.getBoolean("qlm_is_boss")) return COLOR_BOSS;

        // 进化僵尸
        if (tag.getBoolean("qlm_evolved")) return COLOR_ELITE;

        // 根据血量和伤害判断强度
        if (health > 40 || damage > 15) return COLOR_STRONG;
        if (health > 20 || damage > 8) return COLOR_NORMAL;

        return COLOR_WEAK;
    }
}
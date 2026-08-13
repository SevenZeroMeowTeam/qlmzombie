package com.qlm.zombie.achievement;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.skill.SkillPointHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 成就系统 - 完成任务解锁成就，不可作弊获取
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AchievementManager {

    private static final String NBT_ACHIEVEMENTS = "qlm_achievements";

    // 定义成就列表
    public static final Achievement ACH_FIRST_BLOOD = new Achievement(
        "first_blood", "§c初出茅庐", "击杀第一个敌对生物",
        "🔥");
    public static final Achievement ACH_ZOMBIE_SLAYER = new Achievement(
        "zombie_slayer", "§a僵尸猎人", "累计击杀100只僵尸",
        "⚔️");
    public static final Achievement ACH_ZOMBIE_MASTER = new Achievement(
        "zombie_master", "§b僵尸大师", "累计击杀500只僵尸",
        "🏆");
    public static final Achievement ACH_SURVIVOR_25 = new Achievement(
        "survivor_25", "§e生存者", "存活25天",
        "☀️");
    public static final Achievement ACH_SURVIVOR_100 = new Achievement(
        "survivor_100", "§d老手", "存活100天",
        "⭐");
    public static final Achievement ACH_BOSS_KILLER = new Achievement(
        "boss_killer", "§c§lBoss杀手", "击杀Boss",
        "💀");
    public static final Achievement ACH_HORDE_SURVIVOR = new Achievement(
        "horde_survivor", "§5§l僵尸潮幸存者", "成功存活过一次僵尸潮",
        "🛡️");
    public static final Achievement ACH_NETHERITE_HUNTER = new Achievement(
        "netherite_hunter", "§8下界合金猎手", "获得下界合金锭",
        "⛏️");
    public static final Achievement ACH_MYTHIC_CRAFTER = new Achievement(
        "mythic_crafter", "§6§l神话工匠", "获得神话品质装备",
        "✨");
    public static final Achievement ACH_EVOLUTION_WATCHER = new Achievement(
        "evolution_watcher", "§a进化见证者", "见证10只僵尸进化",
        "🧬");

    public static final List<Achievement> ALL_ACHIEVEMENTS = List.of(
        ACH_FIRST_BLOOD, ACH_ZOMBIE_SLAYER, ACH_ZOMBIE_MASTER,
        ACH_SURVIVOR_25, ACH_SURVIVOR_100,
        ACH_BOSS_KILLER, ACH_HORDE_SURVIVOR,
        ACH_NETHERITE_HUNTER, ACH_MYTHIC_CRAFTER,
        ACH_EVOLUTION_WATCHER
    );

    // 玩家数据缓存
    private static final Map<UUID, PlayerAchievementData> playerData = new HashMap<>();

    // ========== 事件监听 ==========

    /** 玩家登录时加载成就数据 */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        loadData(player);
    }

    /** 玩家击杀事件（仅统计敌对生物） */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            LivingEntity victim = event.getEntity();

            // 只统计敌对生物（Monster 类），击杀中立/被动生物无效
            if (!(victim instanceof Monster)) return;

            PlayerAchievementData data = playerData.get(killer.getUUID());
            if (data == null) return;

            // 击杀计数
            data.totalKills++;

            // 检查 First Blood
            if (!data.isUnlocked(ACH_FIRST_BLOOD)) {
                unlockAchievement(killer, ACH_FIRST_BLOOD, data);
            }

            // 僵尸击杀计数
            if (event.getEntity() instanceof Zombie zombie) {
                data.zombieKills++;

                // 检查 Zombie Slayer (100)
                if (data.zombieKills >= 100 && !data.isUnlocked(ACH_ZOMBIE_SLAYER)) {
                    unlockAchievement(killer, ACH_ZOMBIE_SLAYER, data);
                }
                // 检查 Zombie Master (500)
                if (data.zombieKills >= 500 && !data.isUnlocked(ACH_ZOMBIE_MASTER)) {
                    unlockAchievement(killer, ACH_ZOMBIE_MASTER, data);
                }

                // 检查进化见证者
                CompoundTag tag = zombie.getPersistentData();
                if (tag.getBoolean("qlm_evolved")) {
                    data.evolutionCount++;
                    if (data.evolutionCount >= 10 && !data.isUnlocked(ACH_EVOLUTION_WATCHER)) {
                        unlockAchievement(killer, ACH_EVOLUTION_WATCHER, data);
                    }
                }
            }

            saveData(killer, data);
        }
    }

    /** 每 tick 检查存活天数成就 */
    public static void tickDayCheck(ServerPlayer player, long currentDay) {
        PlayerAchievementData data = playerData.get(player.getUUID());
        if (data == null) return;

        boolean changed = false;

        if (currentDay >= 25 && !data.isUnlocked(ACH_SURVIVOR_25)) {
            unlockAchievement(player, ACH_SURVIVOR_25, data);
            changed = true;
        }
        if (currentDay >= 100 && !data.isUnlocked(ACH_SURVIVOR_100)) {
            unlockAchievement(player, ACH_SURVIVOR_100, data);
            changed = true;
        }

        if (changed) saveData(player, data);
    }

    /** 解锁成就（外部调用） */
    public static void unlockAchievement(ServerPlayer player, Achievement achievement) {
        PlayerAchievementData data = playerData.get(player.getUUID());
        if (data == null) return;
        unlockAchievement(player, achievement, data);
    }

    private static void unlockAchievement(ServerPlayer player, Achievement achievement, PlayerAchievementData data) {
        if (data.isUnlocked(achievement)) return;
        data.unlock(achievement);
        saveData(player, data);

        // 全服广播
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().forEach(p -> {
            p.sendSystemMessage(Component.literal(""));
            p.sendSystemMessage(Component.literal("§6§l✦✦✦ 成就解锁 ✦✦✦"));
            p.sendSystemMessage(Component.literal("§e" + player.getName().getString() + " §7解锁了成就: " + achievement.icon + " " + achievement.name));
            p.sendSystemMessage(Component.literal("§7" + achievement.description));
            p.sendSystemMessage(Component.literal("§6§l✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦"));
            p.sendSystemMessage(Component.literal(""));
        });

        // 给予技能点奖励
        int skillPoints = getAchievementReward(achievement);
        if (skillPoints > 0) {
            SkillPointHandler.addPoints(player, skillPoints);
            player.sendSystemMessage(Component.literal("§a✔ 获得 " + skillPoints + " 技能点奖励！"));
        }

        QLMZombieMod.LOGGER.info("[成就] 玩家 {} 解锁成就: {} ({})", player.getName().getString(), achievement.id, achievement.name);
    }

    private static int getAchievementReward(Achievement achievement) {
        if (achievement == ACH_FIRST_BLOOD) return 1;
        if (achievement == ACH_ZOMBIE_SLAYER || achievement == ACH_SURVIVOR_25) return 2;
        if (achievement == ACH_ZOMBIE_MASTER || achievement == ACH_SURVIVOR_100) return 3;
        if (achievement == ACH_BOSS_KILLER || achievement == ACH_HORDE_SURVIVOR) return 5;
        if (achievement == ACH_NETHERITE_HUNTER || achievement == ACH_MYTHIC_CRAFTER) return 5;
        if (achievement == ACH_EVOLUTION_WATCHER) return 2;
        return 1;
    }

    // ========== 数据管理 ==========

    private static void loadData(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag tag = persistent.getCompound(NBT_ACHIEVEMENTS);
        PlayerAchievementData data = new PlayerAchievementData();
        data.totalKills = tag.getInt("totalKills");
        data.zombieKills = tag.getInt("zombieKills");
        data.evolutionCount = tag.getInt("evolutionCount");
        data.bossKills = tag.getInt("bossKills");
        CompoundTag unlocked = tag.getCompound("unlocked");
        for (String key : unlocked.getAllKeys()) {
            data.unlocked.add(key);
        }
        playerData.put(player.getUUID(), data);
    }

    private static void saveData(ServerPlayer player, PlayerAchievementData data) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("totalKills", data.totalKills);
        tag.putInt("zombieKills", data.zombieKills);
        tag.putInt("evolutionCount", data.evolutionCount);
        tag.putInt("bossKills", data.bossKills);
        CompoundTag unlocked = new CompoundTag();
        for (String key : data.unlocked) {
            unlocked.putBoolean(key, true);
        }
        tag.put("unlocked", unlocked);
        player.getPersistentData().put(NBT_ACHIEVEMENTS, tag);
    }

    /** 查看玩家成就列表 */
    public static void showAchievements(ServerPlayer player) {
        PlayerAchievementData data = playerData.get(player.getUUID());
        if (data == null) return;

        player.sendSystemMessage(Component.literal("§6§l===== 成就列表 (" + data.unlocked.size() + "/" + ALL_ACHIEVEMENTS.size() + ") ====="));
        player.sendSystemMessage(Component.literal("§7总击杀: " + data.totalKills + " | 僵尸击杀: " + data.zombieKills + " | 进化见证: " + data.evolutionCount));

        for (Achievement ach : ALL_ACHIEVEMENTS) {
            boolean unlocked = data.isUnlocked(ach);
            String status = unlocked ? "§a✔" : "§7✘";
            String name = unlocked ? ach.name : "§7" + ChatFormatting.stripFormatting(ach.name);
            player.sendSystemMessage(Component.literal(status + " " + ach.icon + " " + name + " §7- " + ach.description));
        }
    }

    // ========== 内部类 ==========

    public record Achievement(String id, String name, String description, String icon) {}

    private static class PlayerAchievementData {
        int totalKills = 0;
        int zombieKills = 0;
        int evolutionCount = 0;
        int bossKills = 0;
        final Set<String> unlocked = new HashSet<>();

        boolean isUnlocked(Achievement achievement) {
            return unlocked.contains(achievement.id());
        }

        void unlock(Achievement achievement) {
            unlocked.add(achievement.id());
        }
    }
}
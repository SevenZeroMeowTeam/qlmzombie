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

    // ===== 新增大量成就（需完成任务解锁，非进入游戏即得） =====
    public static final Achievement ACH_SKELETON_SLAYER = new Achievement(
        "skeleton_slayer", "§f骷髅猎手", "累计击杀100只骷髅",
        "🏹");
    public static final Achievement ACH_GIANT_KILLER = new Achievement(
        "giant_killer", "§c§l巨人屠夫", "击杀巨人僵尸",
        "🗿");
    public static final Achievement ACH_BARREL_POPPER = new Achievement(
        "barrel_popper", "§6拆桶专家", "击杀木桶僵尸",
        "🛢️");
    public static final Achievement ACH_ARCHER_HUNTER = new Achievement(
        "archer_hunter", "§e弓手克星", "击杀弓箭手僵尸",
        "🎯");
    public static final Achievement ACH_SUICIDE_SURVIVOR = new Achievement(
        "suicide_survivor", "§4自爆克星", "击杀自爆僵尸",
        "💥");
    public static final Achievement ACH_QUALITY_BEGINNER = new Achievement(
        "quality_beginner", "§a品质初体验", "合成第一件带品质的装备",
        "🎨");
    public static final Achievement ACH_QUALITY_EXPERT = new Achievement(
        "quality_expert", "§b品质大师", "合成5件优秀(4级)以上品质装备",
        "💎");
    public static final Achievement ACH_BEDROCK_BREAKER = new Achievement(
        "bedrock_breaker", "§5§l基岩破坏者", "破坏基岩",
        "⛏️");
    public static final Achievement ACH_HORDE_WINNER = new Achievement(
        "horde_winner", "§4§l僵尸潮征服者", "成功打完5波僵尸潮并获得丰厚奖励",
        "🏆");
    public static final Achievement ACH_BLOOD_MOON_SURVIVOR = new Achievement(
        "blood_moon_survivor", "§4血月勇者", "存活过血月之夜",
        "🌕");
    public static final Achievement ACH_NIGHT_HUNTER = new Achievement(
        "night_hunter", "§3夜行者", "夜间击杀50只敌对生物",
        "🌙");
    public static final Achievement ACH_SURVIVOR_365 = new Achievement(
        "survivor_365", "§6§l末日幸存者", "存活365天",
        "👑");

    public static final List<Achievement> ALL_ACHIEVEMENTS = List.of(
        ACH_FIRST_BLOOD, ACH_ZOMBIE_SLAYER, ACH_ZOMBIE_MASTER,
        ACH_SURVIVOR_25, ACH_SURVIVOR_100,
        ACH_BOSS_KILLER, ACH_HORDE_SURVIVOR,
        ACH_NETHERITE_HUNTER, ACH_MYTHIC_CRAFTER,
        ACH_EVOLUTION_WATCHER,
        ACH_SKELETON_SLAYER, ACH_GIANT_KILLER, ACH_BARREL_POPPER,
        ACH_ARCHER_HUNTER, ACH_SUICIDE_SURVIVOR,
        ACH_QUALITY_BEGINNER, ACH_QUALITY_EXPERT, ACH_BEDROCK_BREAKER,
        ACH_HORDE_WINNER, ACH_BLOOD_MOON_SURVIVOR, ACH_NIGHT_HUNTER,
        ACH_SURVIVOR_365
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

    /** 每 5 秒检查存活天数成就 */
    private static int dayTickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (++dayTickCounter < 100) return;
        dayTickCounter = 0;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long currentDay = (player.serverLevel().getDayTime() / 24000L) + 1;
            tickDayCheck(player, currentDay);
        }
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

            // 夜间击杀计数（13000-24000 tick）
            long tod = victim.level().getDayTime() % 24000L;
            if (tod >= 13000L) {
                data.nightKills++;
                if (data.nightKills >= 50 && !data.isUnlocked(ACH_NIGHT_HUNTER)) {
                    unlockAchievement(killer, ACH_NIGHT_HUNTER, data);
                }
            }

            // 检查 First Blood
            if (!data.isUnlocked(ACH_FIRST_BLOOD)) {
                unlockAchievement(killer, ACH_FIRST_BLOOD, data);
            }

            // 僵尸击杀计数 + 特殊僵尸
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

                // 特殊僵尸成就（两套 NBT 系统兼容）
                String specialType = tag.getString("qlm_special_zombie_type");
                boolean isBarrel = "barrel".equals(specialType) || tag.getBoolean("qlmzombie.type_barrel");
                boolean isGiant = "giant".equals(specialType);
                boolean isArcher = "archer".equals(specialType);
                boolean isSuicide = "suicide".equals(specialType) || tag.getBoolean("qlmzombie.type_suicide");

                if (isGiant && !data.isUnlocked(ACH_GIANT_KILLER)) {
                    data.giantKills++;
                    unlockAchievement(killer, ACH_GIANT_KILLER, data);
                }
                if (isBarrel && !data.isUnlocked(ACH_BARREL_POPPER)) {
                    data.barrelKills++;
                    unlockAchievement(killer, ACH_BARREL_POPPER, data);
                }
                if (isArcher && !data.isUnlocked(ACH_ARCHER_HUNTER)) {
                    data.archerKills++;
                    unlockAchievement(killer, ACH_ARCHER_HUNTER, data);
                }
                if (isSuicide && !data.isUnlocked(ACH_SUICIDE_SURVIVOR)) {
                    data.suicideKills++;
                    unlockAchievement(killer, ACH_SUICIDE_SURVIVOR, data);
                }

                // Boss 击杀
                if (tag.getBoolean("qlm_is_boss")) {
                    data.bossKills++;
                    if (!data.isUnlocked(ACH_BOSS_KILLER)) {
                        unlockAchievement(killer, ACH_BOSS_KILLER, data);
                    }
                }
            }

            // 骷髅击杀计数
            if (victim instanceof net.minecraft.world.entity.monster.AbstractSkeleton) {
                data.skeletonKills++;
                if (data.skeletonKills >= 100 && !data.isUnlocked(ACH_SKELETON_SLAYER)) {
                    unlockAchievement(killer, ACH_SKELETON_SLAYER, data);
                }
            }

            saveData(killer, data);
        }
    }

    /** 合成品质装备 / 神话装备成就 */
    @SubscribeEvent
    public static void onItemCrafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        net.minecraft.world.item.ItemStack stack = event.getCrafting();
        if (stack == null || stack.isEmpty()) return;

        PlayerAchievementData data = playerData.get(player.getUUID());
        if (data == null) return;

        com.qlm.zombie.item.EquipmentQuality q = com.qlm.zombie.item.EquipmentQuality.fromStack(stack);
        if (q != null) {
            data.qualityCrafted++;
            if (!data.isUnlocked(ACH_QUALITY_BEGINNER)) {
                unlockAchievement(player, ACH_QUALITY_BEGINNER, data);
            }
            if (q.getId() >= 4) {
                data.qualityExpertCount++;
                if (data.qualityExpertCount >= 5 && !data.isUnlocked(ACH_QUALITY_EXPERT)) {
                    unlockAchievement(player, ACH_QUALITY_EXPERT, data);
                }
            }
            if (q == com.qlm.zombie.item.EquipmentQuality.MYTHIC && !data.isUnlocked(ACH_MYTHIC_CRAFTER)) {
                unlockAchievement(player, ACH_MYTHIC_CRAFTER, data);
            }
            saveData(player, data);
        }
    }

    /** 破坏基岩成就 */
    @SubscribeEvent
    public static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.getState().is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
            PlayerAchievementData data = playerData.get(player.getUUID());
            if (data == null) return;
            if (!data.isUnlocked(ACH_BEDROCK_BREAKER)) {
                data.bedrockBroken++;
                unlockAchievement(player, ACH_BEDROCK_BREAKER, data);
                saveData(player, data);
            }
        }
    }

    /** 外部钩子：僵尸潮成功打完5波（由 ZombieHordeHandler 调用） */
    public static void unlockHordeWin(ServerPlayer player) {
        PlayerAchievementData data = playerData.get(player.getUUID());
        if (data == null) return;
        if (!data.isUnlocked(ACH_HORDE_WINNER)) {
            unlockAchievement(player, ACH_HORDE_WINNER, data);
            saveData(player, data);
        }
        // 同时解锁僵尸潮幸存者
        if (!data.isUnlocked(ACH_HORDE_SURVIVOR)) {
            unlockAchievement(player, ACH_HORDE_SURVIVOR, data);
            saveData(player, data);
        }
    }

    /** 外部钩子：存活过血月（由血月事件调用） */
    public static void unlockBloodMoon(ServerPlayer player) {
        PlayerAchievementData data = playerData.get(player.getUUID());
        if (data == null) return;
        if (!data.isUnlocked(ACH_BLOOD_MOON_SURVIVOR)) {
            unlockAchievement(player, ACH_BLOOD_MOON_SURVIVOR, data);
            saveData(player, data);
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
        if (currentDay >= 365 && !data.isUnlocked(ACH_SURVIVOR_365)) {
            unlockAchievement(player, ACH_SURVIVOR_365, data);
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
        if (achievement == ACH_FIRST_BLOOD || achievement == ACH_QUALITY_BEGINNER) return 1;
        if (achievement == ACH_ZOMBIE_SLAYER || achievement == ACH_SURVIVOR_25) return 2;
        if (achievement == ACH_ZOMBIE_MASTER || achievement == ACH_SURVIVOR_100) return 3;
        if (achievement == ACH_BOSS_KILLER || achievement == ACH_HORDE_SURVIVOR) return 5;
        if (achievement == ACH_NETHERITE_HUNTER || achievement == ACH_MYTHIC_CRAFTER) return 5;
        if (achievement == ACH_EVOLUTION_WATCHER) return 2;
        // 新增成就奖励
        if (achievement == ACH_SKELETON_SLAYER) return 3;
        if (achievement == ACH_GIANT_KILLER || achievement == ACH_BARREL_POPPER
            || achievement == ACH_ARCHER_HUNTER || achievement == ACH_SUICIDE_SURVIVOR) return 2;
        if (achievement == ACH_QUALITY_EXPERT) return 3;
        if (achievement == ACH_BEDROCK_BREAKER || achievement == ACH_HORDE_WINNER) return 8;
        if (achievement == ACH_BLOOD_MOON_SURVIVOR || achievement == ACH_NIGHT_HUNTER) return 3;
        if (achievement == ACH_SURVIVOR_365) return 10;
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
        data.skeletonKills = tag.getInt("skeletonKills");
        data.nightKills = tag.getInt("nightKills");
        data.qualityCrafted = tag.getInt("qualityCrafted");
        data.qualityExpertCount = tag.getInt("qualityExpertCount");
        data.bedrockBroken = tag.getInt("bedrockBroken");
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
        tag.putInt("skeletonKills", data.skeletonKills);
        tag.putInt("nightKills", data.nightKills);
        tag.putInt("qualityCrafted", data.qualityCrafted);
        tag.putInt("qualityExpertCount", data.qualityExpertCount);
        tag.putInt("bedrockBroken", data.bedrockBroken);
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
        player.sendSystemMessage(Component.literal("§7总击杀: " + data.totalKills + " | 僵尸: " + data.zombieKills + " | 骷髅: " + data.skeletonKills + " | 进化见证: " + data.evolutionCount));

        for (Achievement ach : ALL_ACHIEVEMENTS) {
            boolean unlocked = data.isUnlocked(ach);
            String status = unlocked ? "§a✔" : "§7✘";
            String name = unlocked ? ach.name : "§7" + ChatFormatting.stripFormatting(ach.name);
            player.sendSystemMessage(Component.literal(status + " " + ach.icon + " " + name + " §7- " + ach.description));
        }
    }

    /** 获取玩家已解锁成就数量（供计分板显示） */
    public static int getUnlockedCount(ServerPlayer player) {
        PlayerAchievementData data = playerData.get(player.getUUID());
        return data == null ? 0 : data.unlocked.size();
    }

    // ========== 内部类 ==========

    public record Achievement(String id, String name, String description, String icon) {}

    private static class PlayerAchievementData {
        int totalKills = 0;
        int zombieKills = 0;
        int skeletonKills = 0;
        int evolutionCount = 0;
        int bossKills = 0;
        int nightKills = 0;
        int giantKills = 0;
        int barrelKills = 0;
        int archerKills = 0;
        int suicideKills = 0;
        int qualityCrafted = 0;
        int qualityExpertCount = 0;
        int bedrockBroken = 0;
        final Set<String> unlocked = new HashSet<>();

        boolean isUnlocked(Achievement achievement) {
            return unlocked.contains(achievement.id());
        }

        void unlock(Achievement achievement) {
            unlocked.add(achievement.id());
        }
    }
}
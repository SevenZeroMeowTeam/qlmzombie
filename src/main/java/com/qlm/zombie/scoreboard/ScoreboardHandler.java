package com.qlm.zombie.scoreboard;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.dayphase.DayPhase;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.item.PermanentKillStats;
import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * 计分板同步：
 * - 每秒（20 tick）实时更新
 * - 按玩家个性化显示：生命上限加成 / 攻击上限加成
 * - 全局信息：天数 / 时间 / 安全日 / 月相 / 阶段
 *
 * 布局（从上到下，slot 从大到小）：
 *   slot 7  ☀ 天数
 *   slot 6  ☘ 安全日
 *   slot 5  ⌚ 游戏内时间
 *   slot 4  ☾ 月相
 *   slot 3  ⚔ 难度阶段
 *   slot 2  （间隔空）
 *   slot 1  ❤ 生命上限加成
 *   slot 0  ⚔ 攻击上限加成
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ScoreboardHandler {

    private static final String OBJECTIVE_NAME = "qlm_survival";
    private static final String TEAM_PREFIX      = "qlm_sb_";
    private static final long   DAY_LENGTH       = 24000L;
    private static final long   SAFE_DAY_TOTAL   = 25L;

    private static final int SLOT_DAY          = 7;
    private static final int SLOT_SAFE_DAY     = 6;
    private static final int SLOT_TIME         = 5;
    private static final int SLOT_MOON         = 4;
    private static final int SLOT_PHASE        = 3;
    private static final int SLOT_SEP          = 2;
    private static final int SLOT_HP_BONUS     = 1;
    private static final int SLOT_ATK_BONUS    = 0;
    // 25 天后新增的 emoji 行（新手期后显示）
    private static final int SLOT_ENEMY        = 8;
    private static final int SLOT_ACHIEVEMENT  = 9;

    private static final String[] COLOR_PREFIXES = new String[] {
            "\u00a70", "\u00a71", "\u00a72", "\u00a73", "\u00a74",
            "\u00a75", "\u00a76", "\u00a77", "\u00a78", "\u00a79",
            "\u00a7a", "\u00a7b", "\u00a7c", "\u00a7d", "\u00a7e", "\u00a7f"
    };

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ensureScoreboard(sp);
            updateForPlayer(sp);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ensureScoreboard(sp);
            updateForPlayer(sp);
        }
    }

    // ==================== 计分板创建 ====================
    private static void ensureScoreboard(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
        if (existing != null) return;

        Objective objective = scoreboard.addObjective(
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal(""),
                ObjectiveCriteria.RenderType.INTEGER
        );
        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
    }

    // ==================== 每玩家更新 ====================
    private static void updateForPlayer(ServerPlayer player) {
        ServerLevel overworld = player.serverLevel();
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) return;

        // ----- 全局信息 -----
        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / DAY_LENGTH;
        long displayDay = currentDay + 1;

        long timeOfDay = dayTime % DAY_LENGTH;
        long hours24 = (timeOfDay / 2400L + 6) % 24;
        long minutes = ((timeOfDay % 2400L) * 60L) / 2400L;
        long hours12 = hours24 % 12;
        if (hours12 == 0) hours12 = 12;
        String ampm = (hours24 >= 12) ? "下午" : "上午";

        String daySegment;
        if (timeOfDay < 2000L)        daySegment = "清晨";
        else if (timeOfDay < 12000L)  daySegment = "白天";
        else if (timeOfDay < 14000L)  daySegment = "黄昏";
        else if (timeOfDay < 22000L)  daySegment = "夜晚";
        else                          daySegment = "黎明";

        String timeText = String.format("%02d:%02d %s [%s]", hours12, minutes, ampm, daySegment);

        // 安全日剩余
        long safeRemain = Math.max(0L, SAFE_DAY_TOTAL - displayDay);
        String safeText = safeRemain > 0
                ? String.format("剩 %d 天", safeRemain)
                : "已结束";

        // 阶段
        DayPhase phase = DayPhaseManager.getCurrentPhase();
        String phaseName = phase.displayName() + (phase.isLocked() ? " [锁定]" : "");
        ChatFormatting phaseColor = getPhaseColor(phase);

        // 月相
        boolean isBloodMoon   = MoonHelper.isBloodMoon(overworld);
        boolean isLuckyMoon   = MoonHelper.isLuckyMoon(overworld);
        boolean isHarvestMoon = MoonHelper.isHarvestMoon(overworld);
        String moonState = isBloodMoon ? "血月 ☠"
                : isLuckyMoon   ? "幸运之月 🍀"
                : isHarvestMoon ? "丰收之月 🌾"
                : getMoonPhaseName(timeOfDay);
        ChatFormatting moonColor = isBloodMoon ? ChatFormatting.RED
                : isLuckyMoon   ? ChatFormatting.GREEN
                : isHarvestMoon ? ChatFormatting.GOLD
                :                 ChatFormatting.GRAY;

        // ----- 玩家个性化信息 -----
        // 生命上限加成（来自击杀奖励）
        double hpBonus = PermanentKillStats.getHealthTotal(player);
        AttributeInstance maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        double totalMaxHp = (maxHpAttr == null) ? 20.0 : maxHpAttr.getValue();

        // 攻击上限加成（来自击杀奖励）
        double atkBonus = PermanentKillStats.getAttackTotal(player);
        AttributeInstance atkAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        double totalAtk = (atkAttr == null) ? 1.0 : atkAttr.getValue();

        // ===== 写入各 slot =====
        // 1. 天数
        setEntry(scoreboard, objective, SLOT_DAY,
                Component.literal("☀ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("第 ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(displayDay)).withStyle(ChatFormatting.GREEN).withStyle(s -> s.withBold(true)))
                        .append(Component.literal(" 天").withStyle(ChatFormatting.GRAY)));

        // 2. 安全日 / 新手期后更新为 emoji 丰富内容
        boolean afterNewbie = safeRemain <= 0;
        if (afterNewbie) {
            // 25 天后：安全日行 → 🧟 在线玩家
            int online = ServerLifecycleHooks.getCurrentServer() != null
                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerCount() : 0;
            int max = ServerLifecycleHooks.getCurrentServer() != null
                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getMaxPlayers() : 0;
            setEntry(scoreboard, objective, SLOT_SAFE_DAY,
                    Component.literal("🧟 ").withStyle(ChatFormatting.DARK_GREEN)
                            .append(Component.literal("在线: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(online + "/" + max).withStyle(ChatFormatting.GREEN).withStyle(s -> s.withBold(true))));

            // 顶部新增：☠ 附近敌对生物数量（MC 可识别 emoji）
            int nearbyEnemies = overworld.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                    player.getBoundingBox().inflate(32.0D), m -> m.isAlive()).size();
            setEntry(scoreboard, objective, SLOT_ENEMY,
                    Component.literal("☠ ").withStyle(ChatFormatting.RED)
                            .append(Component.literal("附近敌人: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.valueOf(nearbyEnemies)).withStyle(ChatFormatting.RED).withStyle(s -> s.withBold(true))));

            // 顶部新增：🏆 已解锁成就数
            int achCount = com.qlm.zombie.achievement.AchievementManager.getUnlockedCount(player);
            setEntry(scoreboard, objective, SLOT_ACHIEVEMENT,
                    Component.literal("🏆 ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("成就: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(achCount + "/22").withStyle(ChatFormatting.GOLD).withStyle(s -> s.withBold(true))));
        } else {
            setEntry(scoreboard, objective, SLOT_SAFE_DAY,
                    Component.literal("☘ ").withStyle(ChatFormatting.DARK_GREEN)
                            .append(Component.literal("安全日: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(safeText).withStyle(safeRemain > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }

        // 3. 时间
        setEntry(scoreboard, objective, SLOT_TIME,
                Component.literal("⌚ ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(timeText).withStyle(ChatFormatting.WHITE)));

        // 4. 月相
        setEntry(scoreboard, objective, SLOT_MOON,
                Component.literal("☾ ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(moonState).withStyle(moonColor)));

        // 5. 阶段
        setEntry(scoreboard, objective, SLOT_PHASE,
                Component.literal("⚔ ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("阶段: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(phaseName).withStyle(phaseColor)));

        // 6. 间隔（分隔线）
        setEntry(scoreboard, objective, SLOT_SEP,
                Component.literal("§7§m----------------").withStyle(ChatFormatting.GRAY));

        // 7. 生命上限（自动检测真实数值）
        setEntry(scoreboard, objective, SLOT_HP_BONUS,
                Component.literal("❤ ").withStyle(ChatFormatting.RED)
                        .append(Component.literal("生命: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format("%.0f", totalMaxHp)).withStyle(ChatFormatting.RED).withStyle(s -> s.withBold(true)))
                        .append(Component.literal(String.format("  (+%.1f)", hpBonus)).withStyle(ChatFormatting.DARK_GREEN)));

        // 8. 攻击上限（自动检测真实数值）
        setEntry(scoreboard, objective, SLOT_ATK_BONUS,
                Component.literal("⚔ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal("攻击: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format("%.1f", totalAtk)).withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(s -> s.withBold(true)))
                        .append(Component.literal(String.format("  (+%.1f)", atkBonus)).withStyle(ChatFormatting.DARK_AQUA)));
    }

    private static ChatFormatting getPhaseColor(DayPhase phase) {
        if (phase == DayPhase.PEACE)   return ChatFormatting.GREEN;
        if (phase == DayPhase.EASY)    return ChatFormatting.YELLOW;
        if (phase == DayPhase.NORMAL)  return ChatFormatting.GOLD;
        if (phase == DayPhase.HARD)    return ChatFormatting.RED;
        return ChatFormatting.DARK_RED;
    }

    private static String getMoonPhaseName(long timeOfDay) {
        // 基于游戏日计算月相（每8天一循环）
        long day = (timeOfDay / DAY_LENGTH) % 8;
        return switch ((int) day) {
            case 0 -> "新月 🌑";
            case 1 -> "蛾眉月 🌒";
            case 2 -> "上弦月 🌓";
            case 3 -> "盈凸月 🌔";
            case 4 -> "满月 🌕";
            case 5 -> "亏凸月 🌖";
            case 6 -> "下弦月 🌗";
            case 7 -> "残月 🌘";
            default -> "普通月";
        };
    }

    // ==================== 工具方法 ====================
    private static void setEntry(Scoreboard scoreboard, Objective objective, int scoreValue, Component displayText) {
        int colorIdx = Math.max(0, Math.min(COLOR_PREFIXES.length - 1, 15 - scoreValue));
        String entryName = COLOR_PREFIXES[colorIdx] + "\u00a7r";

        String teamName = TEAM_PREFIX + scoreValue;
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) team = scoreboard.addPlayerTeam(teamName);

        team.setPlayerPrefix(displayText);
        team.setPlayerSuffix(Component.literal(""));

        if (!team.getPlayers().contains(entryName)) {
            scoreboard.addPlayerToTeam(entryName, team);
        }

        Score score = scoreboard.getOrCreatePlayerScore(entryName, objective);
        score.setScore(scoreValue);
    }
}
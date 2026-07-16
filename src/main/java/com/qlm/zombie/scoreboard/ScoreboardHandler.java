package com.qlm.zombie.scoreboard;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.dayphase.DayPhase;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ScoreboardHandler {

    private static final String OBJECTIVE_NAME = "qlm_survival";
    private static final String TEAM_PREFIX = "qlm_sb_";

    // 槽位（数字越大越靠上，最大为 SIDEBAR 显示上限）
    private static final int SLOT_TOP = 15;
    private static final int SLOT_DAY = 14;
    private static final int SLOT_TIME = 13;
    private static final int SLOT_PHASE = 12;
    private static final int SLOT_MOON = 11;
    private static final int SLOT_BOTTOM = 10;

    // 用作 entry（"玩家名"）占位符的前缀，使用颜色控制字符避免显示成有效玩家名
    private static final String[] COLOR_PREFIXES = new String[]{
            "\u00a70", "\u00a71", "\u00a72", "\u00a73", "\u00a74", "\u00a75", "\u00a76", "\u00a77",
            "\u00a78", "\u00a79", "\u00a7a", "\u00a7b", "\u00a7c", "\u00a7d", "\u00a7e", "\u00a7f"
    };

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ensureScoreboard();
        updateScoreboard();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        ensureScoreboard();
        updateScoreboard();
    }

    private static void ensureScoreboard() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);

        if (existing == null) {
            Objective objective = scoreboard.addObjective(
                    OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal(""),
                    ObjectiveCriteria.RenderType.HEARTS
            );
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
        }
    }

    private static void updateScoreboard() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) return;

        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / 57600L;
        long displayDay = currentDay + 1; // 向玩家显示时使用 1-indexed（第 1 天，而不是"第 0 天"）

        // 游戏内时间：2400 tick = 1 小时；tick 0 对应早晨 6:00
        long timeOfDay = dayTime % 57600L;
        long hours24 = (timeOfDay / 2400L + 6) % 24;
        long minutes = ((timeOfDay % 2400L) * 60L) / 2400L;
        long hours12 = hours24 % 12;
        if (hours12 == 0) hours12 = 12;
        String ampm = (hours24 >= 12) ? "PM" : "AM";

        String daySegment;
        if (timeOfDay < 2400L) daySegment = "清晨";
        else if (timeOfDay < 26400L) daySegment = "白天";
        else if (timeOfDay < 31200L) daySegment = "黄昏";
        else if (timeOfDay < 55200L) daySegment = "夜晚";
        else daySegment = "黎明";

        String timeText = String.format("%02d:%02d %s [%s]", hours12, minutes, ampm, daySegment);

        // 阶段
        DayPhase phase = DayPhaseManager.getCurrentPhase();
        String phaseName = phase.displayName() + (phase.isLocked() ? " [锁定]" : "");

        // 月相
        boolean isBloodMoon = MoonHelper.isBloodMoon(overworld);
        boolean isLuckyMoon = MoonHelper.isLuckyMoon(overworld);
        boolean isHarvestMoon = MoonHelper.isHarvestMoon(overworld);
        String moonState = isBloodMoon ? "血月"
                : isLuckyMoon ? "幸运之月"
                : isHarvestMoon ? "丰收之月"
                : "普通";

        // ====== 写入各条目 ======
        // 顶部装饰线
        setEntry(scoreboard, objective, SLOT_TOP,
                Component.literal("===== 生存HUD =====").withStyle(ChatFormatting.DARK_GRAY));

        // 天数
        setEntry(scoreboard, objective, SLOT_DAY,
                Component.literal("[D] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("第 ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(displayDay)).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" 天").withStyle(ChatFormatting.GRAY)));

        // 时间
        setEntry(scoreboard, objective, SLOT_TIME,
                Component.literal("[T] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(timeText).withStyle(ChatFormatting.WHITE)));

        // 阶段（根据难度配色）
        ChatFormatting phaseColor;
        if (phase == DayPhase.SAFE) phaseColor = ChatFormatting.GREEN;
        else if (phase == DayPhase.EASY) phaseColor = ChatFormatting.YELLOW;
        else if (phase == DayPhase.NORMAL) phaseColor = ChatFormatting.GOLD;
        else if (phase == DayPhase.HARD) phaseColor = ChatFormatting.RED;
        else phaseColor = ChatFormatting.DARK_RED;
        setEntry(scoreboard, objective, SLOT_PHASE,
                Component.literal("[P] ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(phaseName).withStyle(phaseColor)));

        // 月相
        ChatFormatting moonColor = isBloodMoon ? ChatFormatting.RED
                : isLuckyMoon ? ChatFormatting.GREEN
                : isHarvestMoon ? ChatFormatting.GOLD
                : ChatFormatting.GRAY;
        setEntry(scoreboard, objective, SLOT_MOON,
                Component.literal("[M] ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(moonState).withStyle(moonColor)));

        // 底部装饰线
        setEntry(scoreboard, objective, SLOT_BOTTOM,
                Component.literal("=======================").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 通过 PlayerTeam.prefix 机制在某个"分数槽位"上显示彩色文本。<br/>
     * 1. 以 scoreValue 作为槽位号，并使用颜色控制字符作为 entryName（完全不可见，避免显示 "_数字" 占位符）；<br/>
     * 2. 创建/获取 PlayerTeam，将 Team 的 prefix 设置为要显示的彩色 Component；<br/>
     * 3. 把 entryName 加入该 Team；<br/>
     * 4. score 固定为 0（HEARTS 下 0 不显示心/数字）。
     */
    private static void setEntry(Scoreboard scoreboard, Objective objective, int scoreValue, Component displayText) {
        // 使用纯颜色代码序列作为 entryName（完全不可见）
        // scoreValue 越大越靠上 → COLOR_PREFIXES 中字母序靠前
        // 注意：SIDEBAR 按 score 降序显示条目；scoreValue 作为槽位号，对应一条固定的条目
        int colorIdx = Math.max(0, Math.min(COLOR_PREFIXES.length - 1, 15 - scoreValue));
        String entryName = COLOR_PREFIXES[colorIdx] + "\u00a7r";

        // 获取或创建 PlayerTeam
        String teamName = TEAM_PREFIX + scoreValue;
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        // 把显示文本放在 team prefix 中（前面加一个空格，避免贴在左边缘）
        team.setPlayerPrefix(Component.literal(" ").append(displayText));
        // 确保 suffix 为空，避免意外显示多余内容
        team.setPlayerSuffix(Component.literal(""));

        // 将当前 entry 加入 team（若尚未加入）
        if (!team.getPlayers().contains(entryName)) {
            scoreboard.addPlayerToTeam(entryName, team);
        }

        // score = 0：HEARTS 下不显示心/数字；这里仍写入以保证条目存在
        Score score = scoreboard.getOrCreatePlayerScore(entryName, objective);
        score.setScore(0);
    }
}
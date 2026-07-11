package com.qlm.zombie.scoreboard;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.dayphase.DayPhase;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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

@Mod.EventBusSubscriber(modid = QLMZombieMod.MODID)
public class ScoreboardHandler {

    private static final String OBJECTIVE_NAME = "qlm_survival";
    private static final String TEAM_PREFIX = "qlm_sb_";

    // 槽位（数字越大越靠上，最大为 SIDEBAR 显示上限）
    private static final int SLOT_SEP_TOP = 15;
    private static final int SLOT_DAY = 14;
    private static final int SLOT_TIME = 13;
    private static final int SLOT_PHASE = 12;
    private static final int SLOT_MOON = 11;
    private static final int SLOT_SEP_BOTTOM = 10;
    private static final int SLOT_FOOTER = 9;

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
        // 每 20 tick (1秒) 更新一次
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
            MutableComponent titleComponent = Component.literal("僵尸末日生存")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));

            Objective objective = scoreboard.addObjective(
                    OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    titleComponent,
                    ObjectiveCriteria.RenderType.HEARTS
            );

            // DISPLAY_SLOT_SIDEBAR = 1 = 左侧显示槽
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
        long currentDay = dayTime / 24000L;

        // 游戏内时间：MC 的 1000 tick = 1 小时；tick 0 对应早晨 6:00
        long timeOfDay = dayTime % 24000L;
        long hours24 = (timeOfDay / 1000L + 6) % 24;
        long minutes = ((timeOfDay % 1000L) * 60L) / 1000L;
        long hours12 = hours24 % 12;
        if (hours12 == 0) hours12 = 12;
        String ampm = (hours24 >= 12) ? "PM" : "AM";
        String timeText = String.format("%02d:%02d %s (%02d:%02d)", hours12, minutes, ampm, hours24, minutes);

        // 时间段提示
        String daySegment;
        if (timeOfDay < 1000) daySegment = "清晨";
        else if (timeOfDay < 11000) daySegment = "白天";
        else if (timeOfDay < 13000) daySegment = "黄昏";
        else if (timeOfDay < 23000) daySegment = "夜晚";
        else daySegment = "黎明";

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
        // 顶部横线（分隔符，中划线样式）
        setEntry(scoreboard, objective, SLOT_SEP_TOP,
                Component.literal("=========")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)
                                .applyFormat(ChatFormatting.STRIKETHROUGH)));

        // 天数（标签+数值同一行）
        setEntry(scoreboard, objective, SLOT_DAY,
                Component.literal("天数: ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("第 " + currentDay + " 天").withStyle(ChatFormatting.GREEN)));

        // 游戏时间（标签+数值同一行）
        setEntry(scoreboard, objective, SLOT_TIME,
                Component.literal("时间: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(timeText).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" [" + daySegment + "]").withStyle(ChatFormatting.GRAY)));

        // 当前阶段（标签+数值同一行）
        setEntry(scoreboard, objective, SLOT_PHASE,
                Component.literal("阶段: ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(phaseName)
                                .withStyle(phase.isLocked() ? ChatFormatting.RED : ChatFormatting.BLUE)));

        // 月相（标签+数值同一行）
        ChatFormatting moonColor = isBloodMoon ? ChatFormatting.RED
                : isLuckyMoon ? ChatFormatting.GREEN
                : isHarvestMoon ? ChatFormatting.GOLD
                : ChatFormatting.GRAY;
        setEntry(scoreboard, objective, SLOT_MOON,
                Component.literal("月相: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(moonState).withStyle(moonColor)));

        // 底部
        setEntry(scoreboard, objective, SLOT_SEP_BOTTOM,
                Component.literal("=========")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)
                                .applyFormat(ChatFormatting.STRIKETHROUGH)));
        setEntry(scoreboard, objective, SLOT_FOOTER,
                Component.literal("七零喵团队 (SevenZeroMeowTeam)").withStyle(ChatFormatting.WHITE));
    }

    /**
     * 通过 PlayerTeam.prefix 机制在某个"分数槽位"上显示彩色文本：<br/>
     * 1. 以 scoreValue 作为槽位号，并生成对应的占位玩家名 entryName（不被直接显示）；<br/>
     * 2. 创建/获取 PlayerTeam，将 Team 的 prefix 设置为要显示的彩色 Component；<br/>
     * 3. 把 entryName 加入该 Team；<br/>
     * 4. 把 entryName 在 objective 上的分数设为 scoreValue（作为排序键）。<br/>
     * 由于 SIDEBAR 按 score 降序显示，所以 score 大的条目位于顶部。
     */
    private static void setEntry(Scoreboard scoreboard, Objective objective, int scoreValue, Component displayText) {
        // 使用纯颜色代码序列作为 entryName（完全不可见），避免显示 "_数字" 占位符
        // scoreValue 越大越靠上，使用 COLOR_PREFIXES[15 - scoreValue] 保证字母升序：
        // scoreValue=15 → COLOR_PREFIXES[0]="§0"（字母序第1，在SIDEBAR顶部）
        // scoreValue=3  → COLOR_PREFIXES[12]="§c"（字母序第13，在底部）
        String entryName = COLOR_PREFIXES[15 - scoreValue] + "§r";

        // 获取或创建 PlayerTeam
        String teamName = TEAM_PREFIX + scoreValue;
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        // 把 prefix 设置为要显示的内容（prefix + 不可见的 entryName）
        // 前面添加若干空格，让文本向右偏移，避免紧贴左边缘遮挡游戏画面
        Component indentedText = Component.literal(" ").append(displayText);
        team.setPlayerPrefix(indentedText);

        // 将该 entry 加入 Team（若尚未加入）
        if (!team.getPlayers().contains(entryName)) {
            scoreboard.addPlayerToTeam(entryName, team);
        }

        // HEARTS renderType 下 score=0 不显示任何心或数字
        Score score = scoreboard.getOrCreatePlayerScore(entryName, objective);
        score.setScore(0);
    }
}
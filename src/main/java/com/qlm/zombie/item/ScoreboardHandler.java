package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 计分板系统：在玩家侧边栏显示游戏信息。
 *
 * 显示内容（使用 MC 可识别的 Unicode 符号）：
 *   ✦ 七零喵末日 ✦       (标题)
 *   ☀ 天数: 42           (当前游戏天数)
 *   ☘ 安全日: 剩 14 天    (PEACE 阶段剩余天数)
 *   ⌚ 时间: 06:30        (游戏内时间 HH:MM)
 *   ☾ 月相: 血月          (当前月相状态)
 *
 * 每 20 tick (1 秒) 更新一次。
 * 使用 team prefix 显示文本，fake player 名称使用不可见格式化代码。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ScoreboardHandler {

    private static final String OBJECTIVE_NAME = "qlm_info";
    private static final String TITLE = "✦ 七零喵末日 ✦";

    // 不可见的 fake player 名称（使用 §-格式化代码，在计分板中不显示可见字符）
    // 每行使用不同的格式化代码保证唯一性
    private static final String[] FAKE_PLAYERS = {
        "\u00A70",  // §0
        "\u00A71",  // §1
        "\u00A72",  // §2
        "\u00A73",  // §3
        "\u00A74",  // §4
        "\u00A75",  // §5
        "\u00A76",  // §6
        "\u00A77",  // §7
    };

    private static final String[] TEAM_NAMES = {
        "qlm_sb_0", "qlm_sb_1", "qlm_sb_2", "qlm_sb_3",
        "qlm_sb_4", "qlm_sb_5", "qlm_sb_6", "qlm_sb_7",
    };

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter < 20) return; // 每秒更新一次
        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        updateScoreboard(server, overworld);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // 登录时立即更新一次
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        updateScoreboard(server, overworld);
    }

    private static void updateScoreboard(MinecraftServer server, ServerLevel overworld) {
        Scoreboard board = server.getScoreboard();

        // 创建或获取 Objective
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            try {
                objective = board.addObjective(
                    OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal(TITLE).withStyle(ChatFormatting.GOLD),
                    ObjectiveCriteria.RenderType.INTEGER
                );
            } catch (Exception e) {
                QLMZombieMod.LOGGER.warn("[QLM Scoreboard] 创建 objective 失败: {}", e.getMessage());
                return;
            }
        }

        // 设置显示槽位为侧边栏
        board.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);

        // 计算显示数据
        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;
        String timeStr = formatTime(timeOfDay);

        // 安全日：PEACE 阶段 (day 0-24) 剩余天数
        int safeDays;
        String safeStr;
        ChatFormatting safeColor;
        if (day < 25) {
            safeDays = (int) (25 - day);
            safeStr = "剩 " + safeDays + " 天";
            safeColor = ChatFormatting.GREEN;
        } else {
            safeDays = 0;
            safeStr = "已结束";
            safeColor = ChatFormatting.RED;
        }

        // 月相
        String moonName;
        ChatFormatting moonColor;
        if (MoonHelper.isBloodMoon(overworld)) {
            moonName = "☠ 血月";
            moonColor = ChatFormatting.DARK_RED;
        } else if (MoonHelper.isLuckyMoon(overworld)) {
            moonName = "★ 幸运之月";
            moonColor = ChatFormatting.GOLD;
        } else if (MoonHelper.isHarvestMoon(overworld)) {
            moonName = "✿ 丰收之月";
            moonColor = ChatFormatting.YELLOW;
        } else {
            // 原版月相
            int phase = overworld.getMoonPhase();
            String[] phaseNames = {
                "新月", "蛾眉月", "上弦月", "盈凸月",
                "满月", "亏凸月", "下弦月", "残月"
            };
            moonName = phaseNames[phase % 8];
            moonColor = ChatFormatting.LIGHT_PURPLE;
        }

        // 白天/黑夜
        boolean isNight = overworld.isNight();
        String dayNightIcon = isNight ? "☾" : "☀";
        String dayNightStr = isNight ? "夜晚" : "白天";
        ChatFormatting dayNightColor = isNight ? ChatFormatting.DARK_BLUE : ChatFormatting.YELLOW;

        // 写入各行（score 越高越靠上）
        setLine(board, objective, 0, 7, Component.empty()
                .append(Component.literal("☀ 天数: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(day)).withStyle(ChatFormatting.YELLOW)));

        setLine(board, objective, 1, 6, Component.empty()
                .append(Component.literal("☘ 安全日: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(safeStr).withStyle(safeColor)));

        setLine(board, objective, 2, 5, Component.empty()
                .append(Component.literal("⌚ 时间: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(timeStr + " ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(dayNightStr).withStyle(dayNightColor)));

        setLine(board, objective, 3, 4, Component.empty()
                .append(Component.literal("☾ 月相: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(moonName).withStyle(moonColor)));

        // 空行分隔
        setLine(board, objective, 4, 3, Component.literal("§r"));

        // 阶段信息
        String phaseName;
        ChatFormatting phaseColor;
        try {
            var phase = DayPhaseManager.getCurrentPhase();
            phaseName = phase.displayName();
            phaseColor = switch (phase.name()) {
                case "PEACE" -> ChatFormatting.GREEN;
                case "EASY" -> ChatFormatting.YELLOW;
                case "NORMAL" -> ChatFormatting.GOLD;
                case "HARD" -> ChatFormatting.RED;
                case "EXTREME" -> ChatFormatting.DARK_RED;
                default -> ChatFormatting.GRAY;
            };
        } catch (Throwable t) {
            phaseName = "未知";
            phaseColor = ChatFormatting.GRAY;
        }

        setLine(board, objective, 5, 2, Component.empty()
                .append(Component.literal("⚔ 阶段: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(phaseName).withStyle(phaseColor)));

        // 击杀永久属性
        try {
            var players = server.getPlayerList().getPlayers();
            if (!players.isEmpty()) {
                var p = players.get(0);
                double hTotal = PermanentKillStats.getHealthTotal(p);
                double aTotal = PermanentKillStats.getAttackTotal(p);
                int kills = PermanentKillStats.getKillCount(p);
                setLine(board, objective, 6, 1, Component.empty()
                        .append(Component.literal("☠ 击杀: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(kills)).withStyle(ChatFormatting.RED))
                        .append(Component.literal(" ❤+").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format("%.1f", hTotal)).withStyle(ChatFormatting.RED))
                        .append(Component.literal(" ⚔+").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format("%.1f", aTotal)).withStyle(ChatFormatting.GOLD)));
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 设置计分板一行。
     * 使用 team prefix 显示文本，fake player 名称为不可见的格式化代码。
     */
    private static void setLine(Scoreboard board, Objective objective,
                                 int lineIdx, int score, Component displayText) {
        if (lineIdx >= FAKE_PLAYERS.length) return;
        String fakePlayer = FAKE_PLAYERS[lineIdx];
        String teamName = TEAM_NAMES[lineIdx];

        // 获取或创建 Team
        PlayerTeam team = board.getPlayerTeam(teamName);
        if (team == null) {
            team = board.addPlayerTeam(teamName);
        }

        // 设置 prefix 为显示文本
        team.setPlayerPrefix(displayText);
        team.setColor(ChatFormatting.RESET);
        team.setAllowFriendlyFire(false);
        team.setSeeFriendlyInvisibles(false);

        // 将 fake player 加入 team（如果尚未加入）
        try {
            // 检查是否已在某个 team 中
            PlayerTeam existing = board.getPlayersTeam(fakePlayer);
            if (existing != null && existing != team) {
                board.removePlayerFromTeam(fakePlayer, existing);
                board.addPlayerToTeam(fakePlayer, team);
            } else if (existing == null) {
                board.addPlayerToTeam(fakePlayer, team);
            }
        } catch (Throwable ignored) {}

        // 设置 score 控制排序（score 越高越靠上）
        try {
            var scoreObj = board.getOrCreatePlayerScore(fakePlayer, objective);
            scoreObj.setScore(score);
        } catch (Throwable ignored) {}
    }

    /**
     * 将 MC 时间转换为 HH:MM 格式。
     * MC 时间：0 = 6:00, 6000 = 12:00, 12000 = 18:00, 18000 = 0:00
     */
    private static String formatTime(long timeOfDay) {
        int hours = (int) ((timeOfDay / 1000 + 6) % 24);
        int minutes = (int) ((timeOfDay % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }
}

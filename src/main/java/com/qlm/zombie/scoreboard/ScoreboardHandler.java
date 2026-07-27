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

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ScoreboardHandler {

    private static final String OBJECTIVE_NAME = "qlm_survival";
    private static final String TEAM_PREFIX = "qlm_sb_";

    private static final int SLOT_DAY = 4;
    private static final int SLOT_TIME = 3;
    private static final int SLOT_PHASE = 2;
    private static final int SLOT_MOON = 1;

    private static final String[] COLOR_PREFIXES = new String[] {
            "\u00a70", "\u00a71", "\u00a72", "\u00a73", "\u00a74", "\u00a75", "\u00a76", "\u00a77",
            "\u00a78", "\u00a79", "\u00a7a", "\u00a7b", "\u00a7c", "\u00a7d", "\u00a7e", "\u00a7f"
    };

    private static int tickCounter = 0;

    private static final Component[] lastDisplayTexts = new Component[16];
    private static final Set<String> createdEntries = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ensureScoreboard();
        updateScoreboard();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (++tickCounter < 20)
            return;
        tickCounter = 0;

        ensureScoreboard();
        updateScoreboard();
    }

    private static void ensureScoreboard() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null)
            return;

        Scoreboard scoreboard = server.getScoreboard();
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);

        if (existing == null) {
            Objective objective = scoreboard.addObjective(
                    OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal(""),
                    ObjectiveCriteria.RenderType.INTEGER);
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
        }
    }

    private static void updateScoreboard() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null)
            return;

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null)
            return;

        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / 57600L;
        long displayDay = currentDay + 1;

        long timeOfDay = dayTime % 57600L;
        long hours24 = (timeOfDay / 2400L + 6) % 24;
        long minutes = ((timeOfDay % 2400L) * 60L) / 2400L;
        long hours12 = hours24 % 12;
        if (hours12 == 0)
            hours12 = 12;
        String ampm = (hours24 >= 12) ? "PM" : "AM";

        String daySegment;
        if (timeOfDay < 2400L)
            daySegment = "清晨";
        else if (timeOfDay < 26400L)
            daySegment = "白天";
        else if (timeOfDay < 31200L)
            daySegment = "黄昏";
        else if (timeOfDay < 55200L)
            daySegment = "夜晚";
        else
            daySegment = "黎明";

        String timeText = String.format("%02d:%02d %s [%s]", hours12, minutes, ampm, daySegment);

        DayPhase phase = DayPhaseManager.getCurrentPhase();
        String phaseName = phase.displayName() + (phase.isLocked() ? " [锁定]" : "");

        boolean isBloodMoon = MoonHelper.isBloodMoon(overworld);
        boolean isLuckyMoon = MoonHelper.isLuckyMoon(overworld);
        boolean isHarvestMoon = MoonHelper.isHarvestMoon(overworld);
        String moonState = isBloodMoon ? "血月"
                : isLuckyMoon ? "幸运之月"
                        : isHarvestMoon ? "丰收之月"
                                : "普通";

        setEntry(scoreboard, objective, SLOT_DAY,
                Component.literal("[D] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("第 ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(displayDay)).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" 天").withStyle(ChatFormatting.GRAY)));

        setEntry(scoreboard, objective, SLOT_TIME,
                Component.literal("[T] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(timeText).withStyle(ChatFormatting.WHITE)));

        ChatFormatting phaseColor;
        if (phase == DayPhase.SAFE)
            phaseColor = ChatFormatting.GREEN;
        else if (phase == DayPhase.EASY)
            phaseColor = ChatFormatting.YELLOW;
        else if (phase == DayPhase.NORMAL)
            phaseColor = ChatFormatting.GOLD;
        else if (phase == DayPhase.HARD)
            phaseColor = ChatFormatting.RED;
        else
            phaseColor = ChatFormatting.DARK_RED;
        setEntry(scoreboard, objective, SLOT_PHASE,
                Component.literal("[P] ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(phaseName).withStyle(phaseColor)));

        ChatFormatting moonColor = isBloodMoon ? ChatFormatting.RED
                : isLuckyMoon ? ChatFormatting.GREEN
                        : isHarvestMoon ? ChatFormatting.GOLD
                                : ChatFormatting.GRAY;
        setEntry(scoreboard, objective, SLOT_MOON,
                Component.literal("[M] ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(moonState).withStyle(moonColor)));

        cleanupOldEntries(scoreboard, objective);
    }

    private static void cleanupOldEntries(Scoreboard scoreboard, Objective objective) {
        Set<String> entriesToRemove = new HashSet<>();
        for (String entryName : createdEntries) {
            int scoreValue = -1;
            if (entryName.contains(COLOR_PREFIXES[15 - SLOT_DAY]))
                scoreValue = SLOT_DAY;
            else if (entryName.contains(COLOR_PREFIXES[15 - SLOT_TIME]))
                scoreValue = SLOT_TIME;
            else if (entryName.contains(COLOR_PREFIXES[15 - SLOT_PHASE]))
                scoreValue = SLOT_PHASE;
            else if (entryName.contains(COLOR_PREFIXES[15 - SLOT_MOON]))
                scoreValue = SLOT_MOON;

            if (scoreValue != SLOT_DAY && scoreValue != SLOT_TIME &&
                    scoreValue != SLOT_PHASE && scoreValue != SLOT_MOON) {
                entriesToRemove.add(entryName);
                for (PlayerTeam team : scoreboard.getPlayerTeams()) {
                    if (team.getPlayers().contains(entryName)) {
                        scoreboard.removePlayerFromTeam(entryName, team);
                    }
                }
            }
        }
        createdEntries.removeAll(entriesToRemove);
    }

    private static void setEntry(Scoreboard scoreboard, Objective objective, int scoreValue, Component displayText) {
        int colorIdx = Math.max(0, Math.min(COLOR_PREFIXES.length - 1, 15 - scoreValue));
        String entryName = COLOR_PREFIXES[colorIdx] + "\u00a7r";

        String teamName = TEAM_PREFIX + scoreValue;
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        Component cached = lastDisplayTexts[scoreValue];
        if (cached != null && cached.equals(displayText)) {
            if (!team.getPlayers().contains(entryName)) {
                scoreboard.addPlayerToTeam(entryName, team);
            }
            Score score = scoreboard.getOrCreatePlayerScore(entryName, objective);
            score.setScore(scoreValue);
            createdEntries.add(entryName);
            return;
        }
        lastDisplayTexts[scoreValue] = displayText;

        team.setPlayerPrefix(displayText);
        team.setPlayerSuffix(Component.literal(""));

        if (!team.getPlayers().contains(entryName)) {
            scoreboard.addPlayerToTeam(entryName, team);
        }

        Score score = scoreboard.getOrCreatePlayerScore(entryName, objective);
        score.setScore(scoreValue);
        createdEntries.add(entryName);
    }
}

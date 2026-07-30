package com.qlm.zombie.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.dayphase.DayPhase;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.dependency.ModDependencyHandler;
import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class QLMCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("qlm")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("day")
                .executes(ctx -> {
                    long day = DayPhaseManager.getCurrentDay();
                    ctx.getSource().sendSuccess(() -> Component.literal("§e当前天数: §a第 " + day + " 天"), false);
                    return (int) day;
                })
                .then(Commands.argument("day", IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        int targetDay = IntegerArgumentType.getInteger(ctx, "day");
                        ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                        if (overworld != null) {
                            overworld.setDayTime((targetDay - 1) * 24000L);
                            ctx.getSource().sendSuccess(() -> Component.literal("§a天数已设置为: 第 " + targetDay + " 天"), true);
                        }
                        return 1;
                    })
                )
            )
            .then(Commands.literal("phase")
                .executes(ctx -> {
                    DayPhase phase = DayPhaseManager.getCurrentPhase();
                    long day = DayPhaseManager.getCurrentDay();
                    int minDay = phase.minDay();
                    int maxDay = phase.maxDay();
                    String range = maxDay == Integer.MAX_VALUE ? minDay + "+" : minDay + "-" + maxDay;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e当前阶段: §b" + phase.displayName() + 
                        " §7(第" + day + "天, 范围: " + range + "天, 难度: " + phase.difficulty().getKey() + ")" +
                        (phase.isLocked() ? " §c[锁定]" : "")
                    ), false);
                    return 1;
                })
            )
            .then(Commands.literal("difficulty")
                .executes(ctx -> {
                    ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                    if (overworld == null) return 0;
                    Difficulty diff = overworld.getDifficulty();
                    DayPhase phase = DayPhaseManager.getCurrentPhase();
                    Component msg = Component.literal("§e当前难度: §c" + diff.getKey() + " §7(阶段: " + phase.displayName() + 
                        ", 锁定: " + (phase.isLocked() ? "是" : "否") + ")");
                    ctx.getSource().sendSuccess(() -> msg, false);
                    return 1;
                })
            )
            .then(Commands.literal("info")
                .executes(ctx -> {
                    ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                    if (overworld == null) return 0;
                    long day = DayPhaseManager.getCurrentDay();
                    DayPhase phase = DayPhaseManager.getCurrentPhase();
                    Difficulty diff = overworld.getDifficulty();
                    boolean isBloodMoon = MoonHelper.isBloodMoon(overworld);
                    boolean isLuckyMoon = MoonHelper.isLuckyMoon(overworld);
                    boolean isHarvestMoon = MoonHelper.isHarvestMoon(overworld);

                    ctx.getSource().sendSuccess(() -> Component.literal("§6===== QLM僵尸末日生存mod 状态 ====="), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§e天数: §a第 " + day + " 天"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§e阶段: §b" + phase.displayName() + 
                        " §7(" + phase.minDay() + "-" + (phase.maxDay() == Integer.MAX_VALUE ? "∞" : String.valueOf(phase.maxDay())) + "天)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§e难度: §c" + diff.getKey() + 
                        " §7(锁定: " + (phase.isLocked() ? "§c是" : "§a否") + "§7)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§e配置: §7安全日=" + QLMConfig.PEACEFUL_DAYS.get() + 
                        " 简单截止=" + QLMConfig.NORMAL_DAYS.get() + 
                        " 普通截止=" + QLMConfig.HARD_DAYS.get() + 
                        " 困难截止=" + QLMConfig.EXTREME_DAYS.get()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§e月相: " + 
                        (isBloodMoon ? "§4血月 " : "") +
                        (isLuckyMoon ? "§a幸运之月 " : "") +
                        (isHarvestMoon ? "§6丰收之月 " : "") +
                        (isBloodMoon || isLuckyMoon || isHarvestMoon ? "" : "§7普通")), false);

                    // 依赖释放状态
                    int total = ModDependencyHandler.getTotalLibsCount();
                    int released = ModDependencyHandler.getReleasedCount();
                    boolean hasConflicts = ModDependencyHandler.hasConflicts();
                    boolean hasDups = ModDependencyHandler.hasDuplicates();
                    java.util.List<String> disabled = ModDependencyHandler.getDisabledMods();
                    java.util.List<String> deleted = ModDependencyHandler.getDeletedDuplicates();

                    if (total > 0) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§e依赖: §a已扫描 " + total + " 个内部 mod，释放 " + released + " 个"
                        ), false);
                    }
                    if (hasConflicts) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6⚠ 冲突: 检测到冲突组，已禁用 " + disabled.size() + " 个 mod"
                        ), false);
                        for (String c : ModDependencyHandler.getDetectedConflicts()) {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "  §7- " + c
                            ), false);
                        }
                    }
                    if (hasDups) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6⚠ 重复: 检测到 " + deleted.size() + " 个重复 mod，已自动删除（仅保留一个）"
                        ), false);
                        for (String d : deleted) {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "  §7- 已删除: " + d
                            ), false);
                        }
                    }
                    if (QLMZombieMod.needsRestart) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§c⚠ 需要重启游戏以加载新安装/禁用/删除的 mod"
                        ), false);
                    }

                    ctx.getSource().sendSuccess(() -> Component.literal("§6================================"), false);
                    return 1;
                })
            )
            .then(Commands.literal("mods")
                .executes(ctx -> {
                    java.util.List<String> internalLibs = ModDependencyHandler.scanInternalLibs();
                    Path modsDir = net.minecraftforge.fml.loading.FMLPaths.MODSDIR.get();
                    java.util.Set<String> existingMods = ModDependencyHandler.getExistingModsIn(modsDir);

                    ctx.getSource().sendSuccess(() -> Component.literal("§6===== 内部Mod列表 §7(§f" + internalLibs.size() + "个§7) ====="), false);
                    int installed = 0;
                    int missing = 0;
                    for (String lib : internalLibs) {
                        boolean exists = existingMods.contains(lib);
                        if (exists) installed++;
                        else missing++;
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            (exists ? "  §a✔ " : "  §e○ ") + lib
                        ), false);
                    }
                    final int installedCount = installed;
                    final int missingCount = missing;
                    ctx.getSource().sendSuccess(() -> Component.literal("§6已安装: §a" + installedCount + " §6待释放: §e" + missingCount), false);

                    boolean hasConflicts = ModDependencyHandler.hasConflicts();
                    if (hasConflicts) {
                        java.util.List<String> conflicts = ModDependencyHandler.getDetectedConflicts();
                        java.util.List<String> disabled = ModDependencyHandler.getDisabledMods();
                        ctx.getSource().sendSuccess(() -> Component.literal("§6===== 冲突组 §7(§f" + conflicts.size() + "组§7) ====="), false);
                        for (String c : conflicts) {
                            ctx.getSource().sendSuccess(() -> Component.literal("  §6⚠ " + c), false);
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("§c已禁用 " + disabled.size() + " 个冲突mod"), false);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("§7使用 §f/qlm download §7重新释放所有内部mod"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§6========================================"), false);
                    return 1;
                })
            )
            .then(Commands.literal("download")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    source.sendSuccess(() -> Component.literal("§6===== 重新释放内部Mod ====="), false);

                    java.util.List<String> internalLibs = ModDependencyHandler.scanInternalLibs();
                    Path modsDir = net.minecraftforge.fml.loading.FMLPaths.MODSDIR.get();
                    java.util.Set<String> existingMods = ModDependencyHandler.getExistingModsIn(modsDir);

                    final java.util.List<String> released = new java.util.ArrayList<>();
                    final java.util.List<String> skipped = new java.util.ArrayList<>();
                    final java.util.List<String> failed = new java.util.ArrayList<>();

                    for (String libFileName : internalLibs) {
                        if (existingMods.contains(libFileName)) {
                            skipped.add(libFileName);
                            continue;
                        }
                        try {
                            boolean ok = ModDependencyHandler.extractModFromJar(libFileName, modsDir);
                            if (ok) {
                                released.add(libFileName);
                            } else {
                                failed.add(libFileName);
                            }
                        } catch (Exception e) {
                            failed.add(libFileName);
                            QLMZombieMod.LOGGER.warn("[QLM Zombie] 释放失败: " + libFileName, e);
                        }
                    }

                    source.sendSuccess(() -> Component.literal("§7已存在: §f" + skipped.size() + "个 §7已释放: §a" + released.size() + "个 §7失败: §c" + failed.size() + "个"), false);
                    if (!released.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§a已释放: " + String.join(", ", released)), false);
                    }
                    if (!failed.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§c失败: " + String.join(", ", failed)), false);
                    }

                    // 重新做冲突检测
                    ModDependencyHandler.detectAndResolveConflicts(modsDir, internalLibs);
                    java.util.List<String> conflicts = ModDependencyHandler.getDetectedConflicts();
                    java.util.List<String> disabled = ModDependencyHandler.getDisabledMods();
                    if (!conflicts.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§6===== 冲突检测结果 ====="), false);
                        for (String c : conflicts) {
                            source.sendSuccess(() -> Component.literal("  §6⚠ " + c), false);
                        }
                        source.sendSuccess(() -> Component.literal("§c已禁用 " + disabled.size() + " 个冲突mod"), false);
                    }

                    source.sendSuccess(() -> Component.literal("§6===== 处理结果 ====="), false);
                    if (!released.isEmpty() || !disabled.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§e⚠ 请重启游戏以加载新安装/禁用的mod！"), true);
                        QLMZombieMod.needsRestart = true;
                    } else if (!skipped.isEmpty() && released.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§a所有mod已安装"), false);
                    }
                    source.sendSuccess(() -> Component.literal("§6================================"), false);
                    return 1;
                })
            )
            .then(Commands.literal("phases")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6===== 难度阶段一览 ====="), false);
                    for (DayPhase p : DayPhase.values()) {
                        String range = p.maxDay() == Integer.MAX_VALUE ? p.minDay() + "+" : p.minDay() + "-" + p.maxDay();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§b" + p.displayName() + " §7: 第" + range + "天, 难度§c" + p.difficulty().getKey() + 
                            (p.isLocked() ? " §c[锁定]" : "")
                        ), false);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("§6=========================="), false);
                    return 1;
                })
            )
            .then(Commands.literal("aiplayer")
                .then(Commands.literal("spawn")
                    .executes(ctx -> QLMAIPlayerCommands.spawnAIPlayer(ctx.getSource(), "AI_Player", ""))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> QLMAIPlayerCommands.spawnAIPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name"), ""))
                        .then(Commands.argument("skinUrl", StringArgumentType.greedyString())
                            .executes(ctx -> QLMAIPlayerCommands.spawnAIPlayer(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "skinUrl")))
                        )
                    )
                )
                .then(Commands.literal("skin")
                    .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> QLMAIPlayerCommands.setAIPlayerSkin(ctx.getSource(), StringArgumentType.getString(ctx, "url")))
                    )
                )
                .then(Commands.literal("tame")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> QLMAIPlayerCommands.tameAIPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                    )
                )
                .then(Commands.literal("list")
                    .executes(ctx -> QLMAIPlayerCommands.listAIPlayers(ctx.getSource()))
                )
                .then(Commands.literal("tp")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> QLMAIPlayerCommands.tpToAIPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                    )
                )
                .then(Commands.literal("kill")
                    .executes(ctx -> QLMAIPlayerCommands.killAIPlayer(ctx.getSource()))
                )
            )
        );
    }
}
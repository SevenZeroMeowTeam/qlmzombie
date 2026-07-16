package com.qlm.zombie.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.dayphase.DayPhase;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.dependency.ModDependencyHandler;
import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.entity.QLMEntities;
import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

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
                            overworld.setDayTime(targetDay * 57600L);
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
                    .executes(ctx -> spawnAIPlayer(ctx.getSource(), "AI_Player", ""))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> spawnAIPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name"), ""))
                        .then(Commands.argument("skinUrl", StringArgumentType.greedyString())
                            .executes(ctx -> spawnAIPlayer(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "skinUrl")))
                        )
                    )
                )
                .then(Commands.literal("skin")
                    .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> setAIPlayerSkin(ctx.getSource(), StringArgumentType.getString(ctx, "url")))
                    )
                )
                .then(Commands.literal("tame")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> tameAIPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                    )
                )
                .then(Commands.literal("list")
                    .executes(ctx -> listAIPlayers(ctx.getSource()))
                )
                .then(Commands.literal("tp")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> tpToAIPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                    )
                )
                .then(Commands.literal("kill")
                    .executes(ctx -> killAIPlayer(ctx.getSource()))
                )
            )
        );
    }

    private static int spawnAIPlayer(CommandSourceStack source, String name, String skinUrl) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        FakePlayerEntity ai = QLMEntities.FAKE_PLAYER.get().create(level);
        if (ai == null) return 0;

        ai.setPos(pos.x, pos.y, pos.z);
        ai.setCustomNameStr(name);
        ai.setPlayerUUID(UUID.randomUUID());

        if (!skinUrl.isEmpty()) {
            ai.setSkinURL(skinUrl);
        }

        if (ai.getRandom().nextFloat() < 0.25F) {
            ai.giveRandomWeapon();
        }

        level.addFreshEntity(ai);
        source.sendSuccess(() -> Component.literal("§a已生成 AI 玩家: §e" + name), true);
        return 1;
    }

    private static int setAIPlayerSkin(CommandSourceStack source, String url) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        FakePlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity ai) {
                double dist = entity.distanceToSqr(pos);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = ai;
                }
            }
        }

        if (nearest == null) {
            source.sendFailure(Component.literal("§c附近没有找到 AI 玩家"));
            return 0;
        }

        final FakePlayerEntity target = nearest;
        target.setSkinURL(url);
        source.sendSuccess(() -> Component.literal("§a已设置 " + target.getCustomNameStr() + " 的皮肤"), true);
        return 1;
    }

    private static int tameAIPlayer(CommandSourceStack source, ServerPlayer player) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        FakePlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity ai) {
                double dist = entity.distanceToSqr(pos);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = ai;
                }
            }
        }

        if (nearest == null) {
            source.sendFailure(Component.literal("§c附近没有找到 AI 玩家"));
            return 0;
        }

        final FakePlayerEntity target2 = nearest;
        target2.tame(player);
        source.sendSuccess(() -> Component.literal("§a已将 " + target2.getCustomNameStr() + " 驯服，主人: §e" + player.getName().getString()), true);
        return 1;
    }

    private static int listAIPlayers(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int count = 0;
        source.sendSuccess(() -> Component.literal("§6===== AI 玩家列表 ====="), false);

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity ai) {
                count++;
                String owner = ai.getOwnerUUID().map(uuid -> {
                    Entity e = level.getEntity(uuid);
                    return e != null ? e.getName().getString() : "未知";
                }).orElse("无");
                String status = ai.isSitting() ? "§7[蹲坐]" : "§a[活动]";
                String tamed = ai.isTamed() ? "§a已驯服" : "§e未驯服";
                source.sendSuccess(() -> Component.literal(
                        "  §b" + ai.getCustomNameStr() + " §7| " + tamed +
                                " §7| 主人: §f" + owner + " §7| " + status +
                                " §7| 食物: §f" + ai.getFoodLevel() + "/20 §7| HP: §f" + (int)ai.getHealth() + "/" + (int)ai.getMaxHealth()
                ), false);
            }
        }

        if (count == 0) {
            source.sendSuccess(() -> Component.literal("  §7没有 AI 玩家"), false);
        }
        final int total = count;
        source.sendSuccess(() -> Component.literal("§6共 " + total + " 个 AI 玩家"), false);
        return count;
    }

    private static int tpToAIPlayer(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        Entity exec = source.getEntity();
        if (!(exec instanceof ServerPlayer player)) return 0;

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity ai) {
                if (ai.getCustomNameStr().toLowerCase().contains(name.toLowerCase())) {
                    player.teleportTo(ai.getX(), ai.getY(), ai.getZ());
                    source.sendSuccess(() -> Component.literal("§a已传送到 AI 玩家: §e" + ai.getCustomNameStr()), true);
                    return 1;
                }
            }
        }

        source.sendFailure(Component.literal("§c找不到名为 '" + name + "' 的 AI 玩家"));
        return 0;
    }

    private static int killAIPlayer(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        FakePlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity ai) {
                double dist = entity.distanceToSqr(pos);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = ai;
                }
            }
        }

        if (nearest == null) {
            source.sendFailure(Component.literal("§c附近没有找到 AI 玩家"));
            return 0;
        }

        String name = nearest.getCustomNameStr();
        nearest.discard();
        source.sendSuccess(() -> Component.literal("§a已移除 AI 玩家: §e" + name), true);
        return 1;
    }
}
package com.qlm.zombie.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.ai.Player2APIService;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.dayphase.DayPhase;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.dependency.ModDependencyHandler;
import com.qlm.zombie.item.EquipmentQuality;
import com.qlm.zombie.item.PermanentKillStats;
import com.qlm.zombie.item.StarterKitHandler;
import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

        // ============ 玩家命令（默认权限 0，所有玩家可用） ============
        dispatcher.register(Commands.literal("qlm")
            .executes(ctx -> {
                showHelp(ctx.getSource());
                return 1;
            })
            // /qlm help
            .then(Commands.literal("help")
                .executes(ctx -> {
                    showHelp(ctx.getSource());
                    return 1;
                })
            )
            // /qlm stats - 查看永久击杀属性
            .then(Commands.literal("stats")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
                        double h = PermanentKillStats.getHealthTotal(p);
                        double a = PermanentKillStats.getAttackTotal(p);
                        int k = PermanentKillStats.getKillCount(p);
                        ctx.getSource().sendSuccess(() -> Component.empty()
                                .append(Component.literal("§6===== 永久属性统计 =====").withStyle(ChatFormatting.GOLD))
                                .append("\n")
                                .append(Component.literal("☠ 击杀总数: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(String.valueOf(k)).withStyle(ChatFormatting.RED))
                                .append("\n")
                                .append(Component.literal("❤ 永久生命上限: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(String.format("+%.1f", h)).withStyle(ChatFormatting.GREEN))
                                .append("\n")
                                .append(Component.literal("⚔ 永久攻击上限: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(String.format("+%.1f", a)).withStyle(ChatFormatting.GOLD))
                                .append("\n")
                                .append(Component.literal("§6========================").withStyle(ChatFormatting.GOLD)),
                            false);
                    } else {
                        ctx.getSource().sendSuccess(() -> Component.literal("§c✘ 该命令仅玩家可用"), false);
                    }
                    return 1;
                })
            )
            // /qlm quality - 查看手中物品的品质
            .then(Commands.literal("quality")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
                        ItemStack held = p.getMainHandItem();
                        if (held.isEmpty()) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§c✘ 请先手持一件物品"), false);
                            return 1;
                        }
                        EquipmentQuality q = EquipmentQuality.fromStack(held);
                        if (q == null) {
                            ctx.getSource().sendSuccess(() -> Component.literal("§7该物品暂无品质属性"), false);
                            return 1;
                        }
                        CompoundTag tag = held.getTag();
                        float ba = tag != null ? tag.getFloat(EquipmentQuality.NBT_ATTACK) : 0;
                        float bh = tag != null ? tag.getFloat(EquipmentQuality.NBT_HEALTH) : 0;
                        float bAr = tag != null ? tag.getFloat(EquipmentQuality.NBT_ARMOR) : 0;
                        double rd = tag != null ? tag.getDouble(EquipmentQuality.NBT_RANDOM_DMG) : 0;

                        ctx.getSource().sendSuccess(() -> Component.empty()
                                .append(Component.literal("§6===== 装备品质 =====").withStyle(ChatFormatting.GOLD))
                                .append("\n")
                                .append(Component.literal("✦ 品质等级: ").withStyle(ChatFormatting.GRAY))
                                .append(q.getDisplayComponent())
                                .append(" (ID ").append(Component.literal(String.valueOf(q.getId())).withStyle(ChatFormatting.GRAY))
                                .append(")\n")
                                .append(Component.literal("攻击倍率: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(String.format("%.1fx", q.getAttackMultiplier())).withStyle(ChatFormatting.RED))
                                .append("\n")
                                .append(ba > 0 ? Component.literal("⚔ 攻击加成: ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(String.format("+%.0f", ba)).withStyle(ChatFormatting.RED)).append("\n")
                                        : Component.empty())
                                .append(bh > 0 ? Component.literal("❤ 生命加成: ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(String.format("+%.0f", bh)).withStyle(ChatFormatting.GREEN)).append("\n")
                                        : Component.empty())
                                .append(bAr > 0 ? Component.literal("🛡 护甲加成: ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(String.format("+%.0f", bAr)).withStyle(ChatFormatting.BLUE)).append("\n")
                                        : Component.empty())
                                .append(rd > 0 && q != EquipmentQuality.MYTHIC ? Component.literal("☄ 随机伤害: ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(String.format("+%.1f", rd)).withStyle(ChatFormatting.GOLD)).append("\n")
                                        : Component.empty())
                                .append(q.isIndestructible() ? Component.literal("✦ 神话级：耐久无限+破坏基岩+全套盔甲虚空免伤\n").withStyle(ChatFormatting.DARK_PURPLE) : Component.empty())
                                .append(Component.literal("§6=====================").withStyle(ChatFormatting.GOLD)),
                            false);
                    } else {
                        ctx.getSource().sendSuccess(() -> Component.literal("§c✘ 该命令仅玩家可用"), false);
                    }
                    return 1;
                })
            )
            // /qlm moon - 查看当前月相
            .then(Commands.literal("moon")
                .executes(ctx -> {
                    ServerLevel ow = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                    if (ow == null) return 0;
                    boolean blood = MoonHelper.isBloodMoon(ow);
                    boolean lucky = MoonHelper.isLuckyMoon(ow);
                    boolean harvest = MoonHelper.isHarvestMoon(ow);
                    boolean night = MoonHelper.isNight(ow);
                    int vanillaPhase = ow.getMoonPhase();
                    String[] vanillaNames = {"新月", "蛾眉月", "上弦月", "盈凸月", "满月", "亏凸月", "下弦月", "残月"};

                    String moonName;
                    ChatFormatting color;
                    if (blood) { moonName = "☠ 血月"; color = ChatFormatting.DARK_RED; }
                    else if (lucky) { moonName = "★ 幸运之月"; color = ChatFormatting.GOLD; }
                    else if (harvest) { moonName = "✿ 丰收之月"; color = ChatFormatting.YELLOW; }
                    else { moonName = vanillaNames[vanillaPhase % 8]; color = ChatFormatting.LIGHT_PURPLE; }

                    ctx.getSource().sendSuccess(() -> Component.empty()
                            .append(Component.literal("§6===== 月相状态 =====").withStyle(ChatFormatting.GOLD))
                            .append("\n")
                            .append(Component.literal("☾ 当前月相: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(moonName).withStyle(color))
                            .append("\n")
                            .append(Component.literal("☀ 状态: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(night ? "夜晚" : "白天").withStyle(night ? ChatFormatting.DARK_BLUE : ChatFormatting.YELLOW))
                            .append("\n")
                            .append(lucky ? Component.literal("★ 幸运之月：合成高品质概率 +40%\n").withStyle(ChatFormatting.GOLD) : Component.empty())
                            .append(blood ? Component.literal("☠ 血月：合成高品质概率 +25%\n").withStyle(ChatFormatting.DARK_RED) : Component.empty())
                            .append(harvest ? Component.literal("✿ 丰收之月：合成高品质概率 +10%\n").withStyle(ChatFormatting.YELLOW) : Component.empty())
                            .append(Component.literal("§6====================").withStyle(ChatFormatting.GOLD)),
                        false);
                    return 1;
                })
                // /qlm moon force <blood|lucky|harvest>
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("force")
                    .then(Commands.literal("blood")
                        .executes(ctx -> {
                            ServerLevel ow = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                            if (ow == null) return 0;
                            boolean ok = MoonHelper.forceBloodMoon(ow);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                ok ? "§a✔ 已强制触发血月" : "§c✘ 触发失败（EnhancedCelestials 未加载?）"), true);
                            return 1;
                        })
                    )
                    .then(Commands.literal("lucky")
                        .executes(ctx -> {
                            ServerLevel ow = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                            if (ow == null) return 0;
                            boolean ok = MoonHelper.forceLuckyMoon(ow);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                ok ? "§a✔ 已强制触发幸运之月" : "§c✘ 触发失败（EnhancedCelestials 未加载?）"), true);
                            return 1;
                        })
                    )
                    .then(Commands.literal("harvest")
                        .executes(ctx -> {
                            ServerLevel ow = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                            if (ow == null) return 0;
                            boolean ok = MoonHelper.forceHarvestMoon(ow);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                ok ? "§a✔ 已强制触发丰收之月" : "§c✘ 触发失败（EnhancedCelestials 未加载?）"), true);
                            return 1;
                        })
                    )
                )
            )
            // /qlm backpack - 打开AI玩家背包
            .then(Commands.literal("backpack")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        com.qlm.zombie.player.AIPlayerBackpack.openBackpack(player);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§c只有玩家可以使用此命令"));
                    }
                    return 1;
                })
            )
            // /qlm skill - 查看技能点
            .then(Commands.literal("skill")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        com.qlm.zombie.skill.SkillPointHandler.showSkillInfo(player);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§c只有玩家可以使用此命令"));
                    }
                    return 1;
                })
            )
            // /qlm achievement - 查看成就
            .then(Commands.literal("achievement")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        com.qlm.zombie.achievement.AchievementManager.showAchievements(player);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§c只有玩家可以使用此命令"));
                    }
                    return 1;
                })
            )
            // /qlm starter - 重新发放初始装备（需权限 2）
            .then(Commands.literal("starter")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        Player target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                        if (target instanceof ServerPlayer) {
                            target.getPersistentData().remove(StarterKitHandler.NBT_RECEIVED);
                            ctx.getSource().sendSuccess(() -> Component.literal("§a✔ 已重置玩家 " + name + " 的初始装备标记（下次登录重发）"), true);
                        } else {
                            ctx.getSource().sendSuccess(() -> Component.literal("§c✘ 玩家 " + name + " 未在线"), false);
                        }
                        return 1;
                    })
                )
            )
        );

        // ============ OP 命令（权限 2+） ============
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
                    int minDay = phase.getMinDay();
                    int maxDay = phase.getMaxDay();
                    String range = maxDay == Integer.MAX_VALUE ? minDay + "+" : minDay + "-" + maxDay;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e当前阶段: §b" + phase.displayName() +
                        " §7(第" + day + "天, 范围: " + range + "天, 难度乘数: " + phase.getDifficultyMultiplier() + "x)" +
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
                .then(Commands.literal("set")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> {
                            // 100 天+ 锁定困难，无法更改
                            if (DayPhaseManager.blockDifficultyChange()) {
                                ctx.getSource().sendFailure(Component.literal("§4无法更改难度：服务器已进入锁定困难阶段（第 100 天+），难度锁定为困难！"));
                                return 0;
                            }
                            ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
                            if (overworld == null) return 0;
                            String value = StringArgumentType.getString(ctx, "value").toLowerCase(java.util.Locale.ROOT);
                            Difficulty target = switch (value) {
                                case "peaceful" -> Difficulty.PEACEFUL;
                                case "easy" -> Difficulty.EASY;
                                case "normal" -> Difficulty.NORMAL;
                                case "hard" -> Difficulty.HARD;
                                default -> null;
                            };
                            if (target == null) {
                                ctx.getSource().sendFailure(Component.literal("§c无效难度: " + value + " (可选: peaceful/easy/normal/hard)"));
                                return 0;
                            }
                            ctx.getSource().getServer().setDifficulty(target, true);
                            ctx.getSource().sendSuccess(() -> Component.literal("§a难度已设置为: §c" + target.getKey()), true);
                            return 1;
                        })
                    )
                )
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
                        " §7(" + phase.getMinDay() + "-" + (phase.getMaxDay() == Integer.MAX_VALUE ? "∞" : String.valueOf(phase.getMaxDay())) + "天)"), false);
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
                        String range = p.getMaxDay() == Integer.MAX_VALUE ? p.getMinDay() + "+" : p.getMinDay() + "-" + p.getMaxDay();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§b" + p.displayName() + " §7: 第" + range + "天, 难度乘数§c" + p.getDifficultyMultiplier() + "x" +
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
                    .then(Commands.argument("name", StringArgumentType.string())
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
                    .executes(ctx -> QLMAIPlayerCommands.tameNearestAI(ctx.getSource()))
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> QLMAIPlayerCommands.tameAIPlayerByName(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                    )
                )
                .then(Commands.literal("list")
                    .executes(ctx -> QLMAIPlayerCommands.listAIPlayers(ctx.getSource()))
                )
                .then(Commands.literal("tp")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> QLMAIPlayerCommands.tpToAIPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                    )
                )
                .then(Commands.literal("kill")
                    .executes(ctx -> QLMAIPlayerCommands.killAIPlayer(ctx.getSource()))
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> QLMAIPlayerCommands.killAIPlayerByName(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                    )
                )
            )
            .then(Commands.literal("mcp")
                .executes(ctx -> {
                    String url = QLMConfig.PLAYER2_MCP_URL.get();
                    String apiKey = QLMConfig.PLAYER2_MCP_API_KEY.get();
                    boolean enabled = QLMConfig.ENABLE_PLAYER2_MCP.get();
                    int timeout = QLMConfig.PLAYER2_MCP_TIMEOUT.get();
                    boolean available = Player2APIService.isPlayer2Available();

                    ctx.getSource().sendSuccess(() -> Component.literal("§6===== Player2 MCP 服务器集成 ====="), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e启用: " + (enabled ? "§a✔ 已启用" : "§c✘ 已禁用") +
                        " §7| 在线: " + (available ? "§a✔ 在线" : "§c✘ 离线")
                    ), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e服务器地址: §f" + url
                    ), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§eAPI密钥: §f" + (apiKey.length() > 8 ? apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4) : apiKey)
                    ), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e请求超时: §f" + timeout + "ms"
                    ), false);

                    if (enabled) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§7----- MCP 配置 (复制到你的MCP客户端) -----"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e{"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e  §7\"mcpServers\": §e{"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e    §7\"player2\": §e{"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e      §7\"url\": §f\"" + url + "\""), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e      §7\"headers\": §e{"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e        §7\"Authorization\": §f\"Bearer " + apiKey + "\""), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e      §e}"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e    §e}"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e  §e}"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e}"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§7----- 将此配置粘贴到 Claude Desktop / Cursor 等 AI 编码助手 -----"), false);
                    }

                    ctx.getSource().sendSuccess(() -> Component.literal("§6===================================="), false);
                    return 1;
                })
            )
        );
    }

    // ==================== 帮助菜单 ====================
    private static void showHelp(CommandSourceStack source) {
        boolean op = source.hasPermission(2);
        source.sendSuccess(() -> Component.literal("§6====== 七零喵僵尸末日 命令帮助 ======").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("§6------- 玩家命令 -------").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("§b/qlm help§7 - 显示此帮助菜单"), false);
        source.sendSuccess(() -> Component.literal("§b/qlm stats§7 - 查看永久击杀属性（生命/攻击加成）"), false);
        source.sendSuccess(() -> Component.literal("§b/qlm quality§7 - 查看手中物品的品质详情"), false);
        source.sendSuccess(() -> Component.literal("§b/qlm moon§7 - 查看当前月相状态与合成加成"), false);

        if (op) {
            source.sendSuccess(() -> Component.literal("§6------- 管理员命令 -------").withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal("§b/qlm day§7 - 查看/设置当前天数"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm day <天数>§7 - 设置天数"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm phase§7 - 查看当前难度阶段"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm difficulty [set <peaceful|easy|normal|hard>]§7 - 查看/设置难度(100天+锁定困难无法更改)"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm info§7 - 查看模组完整状态"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm phases§7 - 查看所有难度阶段一览"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm mods§7 - 列出内部Mod及安装状态"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm download§7 - 重新释放内部Mod"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm starter <玩家名>§7 - 重置玩家初始装备标记"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm moon force <blood|lucky|harvest>§7 - 强制触发月相"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm aiplayer spawn§7 - 生成AI玩家"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm aiplayer list§7 - 列出AI玩家"), false);
            source.sendSuccess(() -> Component.literal("§b/qlm mcp§7 - Player2 MCP 集成信息"), false);
        }

        source.sendSuccess(() -> Component.literal("§6提示：§7计分板已常驻侧边栏，输入§b /qlm stats §7查看永久属性详情")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("§6===================================").withStyle(ChatFormatting.GOLD), false);
    }
}
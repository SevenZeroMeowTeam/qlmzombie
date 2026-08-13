package com.qlm.zombie.command;

import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.entity.QLMEntities;
import com.qlm.zombie.player.AIPlayerSkinManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class QLMAIPlayerCommands {

    static int spawnAIPlayer(CommandSourceStack source, String name, String skinUrl) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        FakePlayerEntity ai = QLMEntities.FAKE_PLAYER.get().create(level);
        if (ai == null) return 0;

        ai.setPos(pos.x, pos.y, pos.z);
        ai.setCustomNameStr(name);
        ai.setPlayerUUID(UUID.randomUUID());

        if (!skinUrl.isEmpty()) {
            ai.setSkinURL(skinUrl);
        } else {
            // 玩家未指定皮肤时，随机分配 LittleSkin 形象（异步）
            AIPlayerSkinManager.assignRandomSkin(ai, level);
        }

        if (ai.getRandom().nextFloat() < 0.25F) {
            ai.giveRandomWeapon();
        }

        level.addFreshEntity(ai);
        source.sendSuccess(() -> Component.literal("§a已生成 AI 玩家: §e" + name), true);
        return 1;
    }

    static int setAIPlayerSkin(CommandSourceStack source, String url) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        FakePlayerEntity nearest = findNearestAIPlayer(level, pos);

        if (nearest == null) {
            source.sendFailure(Component.literal("§c附近没有找到 AI 玩家"));
            return 0;
        }

        nearest.setSkinURL(url);
        source.sendSuccess(() -> Component.literal("§a已设置 " + nearest.getCustomNameStr() + " 的皮肤"), true);
        return 1;
    }

    static int tameAIPlayer(CommandSourceStack source, ServerPlayer player) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        FakePlayerEntity nearest = findNearestAIPlayer(level, pos);

        if (nearest == null) {
            source.sendFailure(Component.literal("§c附近没有找到 AI 玩家"));
            return 0;
        }

        nearest.tame(player);
        source.sendSuccess(() -> Component.literal("§a已将 " + nearest.getCustomNameStr() + " 驯服，主人: §e" + player.getName().getString()), true);
        return 1;
    }

    static int tameNearestAI(CommandSourceStack source) {
        Entity exec = source.getEntity();
        if (!(exec instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c只有玩家才能使用此命令"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        FakePlayerEntity nearest = findNearestAIPlayer(level, pos);

        if (nearest == null) {
            source.sendFailure(Component.literal("§c附近没有找到 AI 玩家"));
            return 0;
        }

        final ServerPlayer finalPlayer = player;
        final FakePlayerEntity finalAI = nearest;
        nearest.tame(player);
        source.sendSuccess(() -> Component.literal("§a已将 " + finalAI.getCustomNameStr() + " 驯服，主人: §e" + finalPlayer.getName().getString()), true);
        return 1;
    }

    static int tameAIPlayerByName(CommandSourceStack source, String aiName) {
        Entity exec = source.getEntity();
        if (!(exec instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c只有玩家才能使用此命令"));
            return 0;
        }
        ServerLevel level = source.getLevel();

        FakePlayerEntity target = null;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity ai && ai.getCustomNameStr().equalsIgnoreCase(aiName)) {
                target = ai;
                break;
            }
        }

        if (target == null) {
            source.sendFailure(Component.literal("§c找不到名为 '" + aiName + "' 的 AI 玩家"));
            return 0;
        }

        final ServerPlayer finalPlayer = player;
        final FakePlayerEntity finalAI = target;
        target.tame(player);
        source.sendSuccess(() -> Component.literal("§a已将 " + finalAI.getCustomNameStr() + " 驯服，主人: §e" + finalPlayer.getName().getString()), true);
        return 1;
    }

    static int listAIPlayers(CommandSourceStack source) {
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

    static int tpToAIPlayer(CommandSourceStack source, String name) {
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

    static int killAIPlayer(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        FakePlayerEntity nearest = findNearestAIPlayer(level, pos);

        if (nearest == null) {
            source.sendFailure(Component.literal("§c附近没有找到 AI 玩家"));
            return 0;
        }

        String name = nearest.getCustomNameStr();
        nearest.discard();
        source.sendSuccess(() -> Component.literal("§a已移除 AI 玩家: §e" + name), true);
        return 1;
    }

    static int killAIPlayerByName(CommandSourceStack source, String aiName) {
        ServerLevel level = source.getLevel();

        FakePlayerEntity target = null;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity ai && ai.getCustomNameStr().equalsIgnoreCase(aiName)) {
                target = ai;
                break;
            }
        }

        if (target == null) {
            source.sendFailure(Component.literal("§c找不到名为 '" + aiName + "' 的 AI 玩家"));
            return 0;
        }

        final String finalName = target.getCustomNameStr();
        target.discard();
        source.sendSuccess(() -> Component.literal("§a已移除 AI 玩家: §e" + finalName), true);
        return 1;
    }

    private static FakePlayerEntity findNearestAIPlayer(ServerLevel level, Vec3 pos) {
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
        return nearest;
    }
}
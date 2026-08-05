/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * This file is part of QLM Zombie Mod.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * ----------------------------------------------------------------------------
 * Open Source Attribution:
 *
 * This class is an ORIGINAL implementation inspired by the design patterns of:
 *   - Player2NPC (https://github.com/Goodbird-git/Player2NPC)
 *     Copyright (c) Goodbird-git
 *     Licensed under MIT License
 *   - PlayerEngine (https://github.com/Goodbird-git/PlayerEngine)
 *     AI control loop and command parsing pattern
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements.
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.player;

import com.qlm.zombie.ai.LLMBridge;
import com.qlm.zombie.ai.Player2APIService;
import com.qlm.zombie.ai.Player2APIService.AIResponse;
import com.qlm.zombie.ai.task.Task;
import com.qlm.zombie.ai.task.TaskCatalogue;
import com.qlm.zombie.ai.task.TaskRunner;
import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.QLMZombieMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 玩家聊天指令处理器 — 参考 Player2NPC 的 AI 控制循环
 *
 * 控制流程:
 * 1. 玩家在聊天中发送指令 → onServerChat
 * 2. 解析指令（Player2 API 或本地解析） → AIResponse
 * 3. TaskCatalogue 将 AIResponse 映射为 Task 实例
 * 4. TaskRunner 管理 Task 生命周期（start → tick → stop）
 *
 * 任务执行由 FakePlayerEntity.tick() → TaskRunner.tick() 驱动
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AIPlayerChatHandler {

    /** 兼容层：保留静态任务映射供 FakePlayerEntity 旧代码查询 */
    private static final ConcurrentHashMap<String, String> AI_TASKS = new ConcurrentHashMap<>();

    /**
     * 获取指定AI当前任务类型（兼容层，优先使用 FakePlayerEntity.getCurrentTaskName()）
     */
    public static String getTask(String uuid) {
        return AI_TASKS.get(uuid);
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().getString().trim();
        Level level = player.level();
        BlockPos playerPos = player.blockPosition();

        QLMZombieMod.LOGGER.info("[AI玩家] 收到聊天消息: '{}' from {}", message, player.getName().getString());

        if (message.isEmpty()) return;

        // 搜索附近所有本模组 AI（扩大到32格）
        List<FakePlayerEntity> nearbyAI = level.getEntitiesOfClass(FakePlayerEntity.class,
                new AABB(
                        playerPos.getX() - 32, playerPos.getY() - 16, playerPos.getZ() - 32,
                        playerPos.getX() + 32, playerPos.getY() + 16, playerPos.getZ() + 32));

        if (nearbyAI.isEmpty()) {
            QLMZombieMod.LOGGER.info("[AI玩家] 附近32格内没有AI玩家");
            return; // 不发消息，避免干扰其他模组
        }

        QLMZombieMod.LOGGER.info("[AI玩家] 附近找到 {} 个AI玩家", nearbyAI.size());

        FakePlayerEntity targetAI = null;
        String extractedAiName = null;

        // 步骤1：尝试从消息中匹配 AI 名字
        // 支持: "@AI名字 指令"、"AI名字 指令"、"AI名字指令"（无空格）
        String msgForMatch = message.startsWith("@") ? message.substring(1).trim() : message;

        for (FakePlayerEntity ai : nearbyAI) {
            String aiName = ai.getCustomNameStr();
            if (aiName == null || aiName.isEmpty()) continue;

            // 精确前缀匹配：消息以AI名字开头（支持无空格）
            if (msgForMatch.toLowerCase().startsWith(aiName.toLowerCase())) {
                targetAI = ai;
                extractedAiName = aiName;
                QLMZombieMod.LOGGER.info("[AI玩家] 匹配到AI: {} (前缀匹配)", aiName);
                break;
            }
        }

        // 步骤2：如果消息中没有AI名字，尝试 G键选中的AI
        if (targetAI == null) {
            targetAI = AISelectionHandler.findSelectedAI(player, playerPos, 32);
            if (targetAI != null) {
                QLMZombieMod.LOGGER.info("[AI玩家] 使用G键选中的AI: {}", targetAI.getCustomNameStr());
            }
        }

        // 没有匹配到本模组的 AI，静默返回
        if (targetAI == null) {
            QLMZombieMod.LOGGER.info("[AI玩家] 消息未匹配到任何AI，忽略");
            return;
        }

        // 冷却检查（每个 AI 独立冷却）
        String playerUUID = player.getUUID().toString();
        String aiUUID = targetAI.getUUID().toString();
        if (!TaskRunner.checkCooldown(playerUUID, aiUUID)) {
            QLMZombieMod.LOGGER.info("[AI玩家] AI {} 冷却中", targetAI.getCustomNameStr());
            player.sendSystemMessage(Component.literal("§7[AI玩家] §e" + targetAI.getCustomNameStr() + " 指令冷却中，请稍等 2 秒"));
            return;
        }

        if (!targetAI.isTamed()) {
            QLMZombieMod.LOGGER.info("[AI玩家] AI {} 未驯服", targetAI.getCustomNameStr());
            player.sendSystemMessage(Component.literal("§c[AI玩家] §7" + targetAI.getCustomNameStr() + " 还未被驯服，无法交流。请使用 §f/qlm aiplayer tame §e驯服"));
            return;
        }

        // 提取指令内容：从消息中移除 AI 名字
        String task;
        if (extractedAiName != null) {
            // 移除 @ 前缀和 AI 名字
            task = message.replaceFirst("@?" + java.util.regex.Pattern.quote(extractedAiName), "").trim();
        } else {
            // G键选中模式：整个消息作为指令
            task = message;
        }

        if (task.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7[AI玩家] §e指令为空，请输入具体任务"));
            return;
        }

        QLMZombieMod.LOGGER.info("[AI玩家] 解析指令: '{}' for AI: {}", task, targetAI.getCustomNameStr());
        player.sendSystemMessage(Component.literal("§6[AI玩家] §f" + targetAI.getCustomNameStr() + " §7-> §e" + task));

        // AI 算法模式切换指令（优先处理，不进入 Task 系统）
        if (handleAlgorithmCommand(targetAI, player, task)) {
            return;
        }

        processPlayerCommand(targetAI, player, task);
    }

    /**
     * 处理 AI 算法模式切换指令
     * 支持的指令:
     *   "算法自动" / "algorithm auto"     → AUTO 自动选择
     *   "算法行为树" / "algorithm bt"     → BEHAVIOR_TREE
     *   "算法状态机" / "algorithm fsm"    → FSM
     *   "算法强化学习" / "algorithm rl"   → Q_LEARNING
     *   "算法效用" / "algorithm utility"  → UTILITY
     *   "算法模糊" / "algorithm fuzzy"    → FUZZY
     *   "算法禁用" / "algorithm off"      → DISABLED
     *   "算法状态" / "algorithm status"   → 查询当前模式和 Q-Learning 统计
     */
    private static boolean handleAlgorithmCommand(FakePlayerEntity ai, Player player, String command) {
        String lower = command.toLowerCase().trim();
        if (!lower.contains("算法") && !lower.contains("algorithm")) {
            return false;
        }

        com.qlm.zombie.ai.algorithm.AIAlgorithmManager mgr = ai.getAlgorithmManager();

        if (lower.contains("状态") || lower.contains("status")) {
            com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode mode = mgr.getMode();
            com.qlm.zombie.ai.algorithm.qlearning.QLearningAgent ql = mgr.getQLearning();
            String info = "§b当前算法模式: §e" + mode;
            if (ql != null) {
                info += "\n§bQ-Learning: §e步数=" + ql.getTotalSteps()
                        + " §eε=" + String.format("%.3f", ql.getEpsilon())
                        + " §e平均奖励=" + String.format("%.2f", ql.getAverageReward())
                        + " §e状态数=" + ql.getQTable().getStateCount();
            }
            player.sendSystemMessage(Component.literal("§6[" + ai.getCustomNameStr() + "] §f" + info));
            return true;
        }

        com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode newMode = null;
        if (lower.contains("自动") || lower.contains("auto")) newMode = com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.AUTO;
        else if (lower.contains("行为树") || lower.contains("bt") || lower.contains("behavior")) newMode = com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.BEHAVIOR_TREE;
        else if (lower.contains("状态机") || lower.contains("fsm")) newMode = com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.FSM;
        else if (lower.contains("强化") || lower.contains("rl") || lower.contains("qlearning") || lower.contains("q-learning")) newMode = com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.Q_LEARNING;
        else if (lower.contains("效用") || lower.contains("utility")) newMode = com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.UTILITY;
        else if (lower.contains("模糊") || lower.contains("fuzzy")) newMode = com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.FUZZY;
        else if (lower.contains("禁用") || lower.contains("off") || lower.contains("disable")) newMode = com.qlm.zombie.ai.algorithm.AIAlgorithmManager.AlgorithmMode.DISABLED;

        if (newMode != null) {
            mgr.setMode(newMode);
            player.sendSystemMessage(Component.literal("§6[" + ai.getCustomNameStr() + "] §f已切换算法模式为 §e" + newMode));
            return true;
        }

        // 列出可用模式
        player.sendSystemMessage(Component.literal("§6[" + ai.getCustomNameStr() + "] §f可用算法模式: §e自动/行为树/状态机/强化学习/效用/模糊/禁用/状态"));
        return true;
    }

    private static void processPlayerCommand(FakePlayerEntity ai, Player player, String command) {
        boolean apiAvailable = Player2APIService.isPlayer2Available();

        if (apiAvailable) {
            // ★ 使用 qlm_ 前缀避免与 Player2NPC 原版的 character_id 冲突
            String characterId = "qlm_" + ai.getCustomNameStr();
            QLMZombieMod.LOGGER.info("[AI玩家] Player2 API 可用，发送指令到API: characterId={}", characterId);

            // 设置3秒超时，超时后直接使用本地解析
            Player2APIService.sendTask(characterId, command)
                    .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .thenAccept(response -> {
                        AIResponse aiResponse;
                        if (response != null && !response.equals(command)) {
                            aiResponse = Player2APIService.parseAIResponse(response);
                        } else {
                            // API 返回原始指令（未处理），走本地/LLM 解析
                            aiResponse = null;
                        }
                        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                        if (server != null) {
                            if (aiResponse != null) {
                                final AIResponse finalResponse = aiResponse;
                                server.execute(() -> executeAIResponse(ai, player, finalResponse));
                            } else {
                                server.execute(() -> tryLocalOrLLM(ai, player, command));
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        QLMZombieMod.LOGGER.warn("[AI玩家] Player2 API 调用失败/超时，使用本地/LLM解析: {}", ex.getMessage());
                        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                        if (server != null) {
                            server.execute(() -> tryLocalOrLLM(ai, player, command));
                        }
                        return null;
                    });
        } else {
            // API 不可用，直接使用本地解析或 LLM
            QLMZombieMod.LOGGER.info("[AI玩家] Player2 API 不可用，使用本地/LLM解析指令: '{}'", command);
            tryLocalOrLLM(ai, player, command);
        }
    }

    /**
     * 尝试本地解析，若无法理解则使用 LLM 大模型翻译
     * 必须在主线程（server thread）调用
     */
    private static void tryLocalOrLLM(FakePlayerEntity ai, Player player, String command) {
        AIResponse aiResponse = Player2APIService.parseSimpleResponse(command);

        // 本地解析未能理解指令（返回 chat 类型）且 LLM 已启用 → 使用大模型翻译
        if ("chat".equals(aiResponse.action()) && LLMBridge.isEnabled()) {
            processLLMCommand(ai, player, command);
        } else {
            executeAIResponse(ai, player, aiResponse);
        }
    }

    /**
     * 使用 LLM 大模型将自然语言指令翻译成任务链
     * 异步调用 LLM API，不阻塞游戏线程
     */
    private static void processLLMCommand(FakePlayerEntity ai, Player player, String command) {
        player.sendSystemMessage(Component.literal("§6[" + ai.getCustomNameStr() + "] §7正在用大模型分析指令..."));

        LLMBridge.planTask(command, ai).thenAccept(responses -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            server.execute(() -> {
                if (responses.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§6[" + ai.getCustomNameStr() + "] §f我不太明白你的意思"));
                    return;
                }

                // 将 AIResponse 列表转换为 Task 列表
                List<Task> tasks = new ArrayList<>();
                for (AIResponse resp : responses) {
                    Task task = TaskCatalogue.createTask(ai, player, resp);
                    if (task != null) {
                        tasks.add(task);
                    }
                }

                if (tasks.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§6[" + ai.getCustomNameStr() + "] §f无法执行该指令"));
                    return;
                }

                // 启动任务链串行执行
                ai.getTaskRunner().startTaskChain(tasks);

                String taskSummary = responses.stream()
                        .map(AIResponse::action)
                        .reduce((a, b) -> a + " → " + b)
                        .orElse("");
                player.sendSystemMessage(Component.literal(
                        "§6[" + ai.getCustomNameStr() + "] §f已规划 " + tasks.size() + " 个任务: §e" + taskSummary));
            });
        });
    }

    /**
     * 执行 AI 响应：通过 TaskCatalogue 创建 Task，由 TaskRunner 启动
     */
    private static void executeAIResponse(FakePlayerEntity ai, Player player, AIResponse response) {
        String aiName = ai.getCustomNameStr();

        // 显示 AI 回复消息
        if (response.message() != null && !response.message().isEmpty()) {
            player.sendSystemMessage(Component.literal("§6[" + aiName + "] §f" + response.message()));
        }

        // 通过 TaskCatalogue 创建任务
        Task task = TaskCatalogue.createTask(ai, player, response);

        if (task != null) {
            // 更新兼容层映射
            AI_TASKS.put(ai.getUUID().toString(), task.getName());
            // 通过 TaskRunner 启动任务
            ai.getTaskRunner().startTask(task);
        } else if (response.action().equals("chat")) {
            if (response.message() == null) {
                player.sendSystemMessage(Component.literal("§6[" + aiName + "] §f嗯，我明白了"));
            }
        } else {
            player.sendSystemMessage(Component.literal("§6[" + aiName + "] §f我不太明白你的意思"));
        }
    }

    /** 查找最近的 AI 玩家 */
    public static FakePlayerEntity findNearestAIPlayer(Level level, BlockPos pos, double range) {
        FakePlayerEntity nearest = null;
        double nearestDist = range * range;
        List<FakePlayerEntity> aiPlayers = level.getEntitiesOfClass(FakePlayerEntity.class,
                new AABB(
                        pos.getX() - range, pos.getY() - range, pos.getZ() - range,
                        pos.getX() + range, pos.getY() + range, pos.getZ() + range));
        for (FakePlayerEntity ai : aiPlayers) {
            double dist = ai.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = ai;
            }
        }
        return nearest;
    }

    /** 按名字查找 AI 玩家（支持部分匹配和模糊匹配） */
    public static FakePlayerEntity findAIByName(Level level, BlockPos pos, double range, String nameQuery) {
        if (nameQuery == null || nameQuery.isEmpty()) return null;

        List<FakePlayerEntity> aiPlayers = level.getEntitiesOfClass(FakePlayerEntity.class,
                new AABB(
                        pos.getX() - range, pos.getY() - range, pos.getZ() - range,
                        pos.getX() + range, pos.getY() + range, pos.getZ() + range));

        String queryLower = nameQuery.toLowerCase();

        // 精确匹配
        for (FakePlayerEntity ai : aiPlayers) {
            if (ai.getCustomNameStr().equalsIgnoreCase(nameQuery)) {
                return ai;
            }
        }

        // 模糊匹配（包含关系）
        for (FakePlayerEntity ai : aiPlayers) {
            if (ai.getCustomNameStr().toLowerCase().contains(queryLower) ||
                queryLower.contains(ai.getCustomNameStr().toLowerCase())) {
                return ai;
            }
        }

        return null;
    }
}

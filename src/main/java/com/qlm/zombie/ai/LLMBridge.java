/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * ----------------------------------------------------------------------------
 * LLM 大模型桥接层（Mod 内部 AI 版）
 *
 * 职责: 将玩家的自然语言指令翻译成 AI 可执行的任务序列。
 * LLM 只负责「规划」，不直接输出 MC 动作。规划结果交给 TaskRunner 串行执行。
 *
 * 架构:
 *   玩家: "帮我建一座房子"
 *     → LLMBridge.planTask() 异步调用大模型
 *     → 大模型输出 JSON 任务数组
 *     → TaskRunner.startTaskChain(tasks) 串行执行
 *
 * 支持的 LLM 后端（均使用 OpenAI 兼容的 /v1/chat/completions 接口）:
 *   - Ollama (本地，默认): http://localhost:11434/v1/chat/completions
 *   - OpenAI / 兼容服务
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai;

import com.google.gson.*;
import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 大模型桥接 — 自然语言 → AIResponse 任务列表
 *
 * 线程安全: 所有方法均为静态方法，无共享可变状态。
 * 异步调用: planTask() 返回 CompletableFuture，不阻塞游戏线程。
 */
public class LLMBridge {

    private static final Gson GSON = new Gson();

    /** TaskCatalogue 支持的合法 action 集合 */
    private static final Set<String> VALID_ACTIONS = Set.of(
            "mine", "chop", "gather", "collect_planks",
            "build", "craft", "give", "drop",
            "attack", "guard", "explore", "heal", "farm",
            "follow", "wait", "come", "stop"
    );

    private LLMBridge() {}

    /** 是否启用 LLM */
    public static boolean isEnabled() {
        return QLMConfig.ENABLE_LLM.get();
    }

    /**
     * 将自然语言指令翻译成任务列表
     * @param naturalLanguage 玩家输入的自然语言
     * @param ai 目标 AI 实体（用于提供上下文：位置/背包/血量）
     * @return CompletableFuture<List<AIResponse>> 异步返回任务列表
     */
    public static CompletableFuture<List<Player2APIService.AIResponse>> planTask(
            String naturalLanguage, FakePlayerEntity ai) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String systemPrompt = buildSystemPrompt(ai);
                String userPrompt = naturalLanguage;

                QLMZombieMod.LOGGER.info("[LLM] 规划指令: \"{}\"", naturalLanguage);

                String rawResponse = callLLM(systemPrompt, userPrompt);
                List<Player2APIService.AIResponse> tasks = parseTasks(rawResponse);

                if (tasks.isEmpty()) {
                    QLMZombieMod.LOGGER.warn("[LLM] 未返回有效任务");
                    return Collections.emptyList();
                }

                QLMZombieMod.LOGGER.info("[LLM] 规划完成: {} 个任务 → {}",
                        tasks.size(),
                        tasks.stream().map(Player2APIService.AIResponse::action)
                                .reduce((a, b) -> a + " → " + b).orElse(""));
                return tasks;
            } catch (Exception e) {
                QLMZombieMod.LOGGER.error("[LLM] 规划失败: {}", e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    /**
     * 构建系统提示词（包含可用任务类型和 AI 当前状态）
     */
    private static String buildSystemPrompt(FakePlayerEntity ai) {
        // 上下文信息
        BlockPos pos = ai.blockPosition();
        StringBuilder context = new StringBuilder();
        context.append(String.format("当前坐标: (%d, %d, %d)\n", pos.getX(), pos.getY(), pos.getZ()));
        context.append(String.format("生命值: %.0f/100, 食物值: %d/20\n",
                ai.getHealth(), ai.getFoodLevel()));

        // 背包摘要
        List<String> invItems = new ArrayList<>();
        for (int i = 0; i < ai.getInventory().getContainerSize(); i++) {
            ItemStack stack = ai.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) {
                    invItems.add(stack.getCount() + "x" + id.getPath());
                }
            }
        }
        if (!invItems.isEmpty()) {
            context.append("背包物品: ").append(String.join(", ", invItems)).append("\n");
        }

        return """
                你是 Minecraft AI 任务规划器。你的职责是将玩家的自然语言指令翻译成 AI 可执行的任务序列。

                ## 支持的任务类型（action 字段）
                - mine: 挖矿/采集方块 → {"action":"mine","targetItem":"iron_ore","targetCount":1}
                - chop: 砍树/伐木 → {"action":"chop","targetItem":null,"targetCount":1}
                - gather: 收集掉落物 → {"action":"gather","targetItem":"oak_log","targetCount":5}
                - collect_planks: 收集木板 → {"action":"collect_planks","targetItem":null,"targetCount":1}
                - build: 建造房屋 → {"action":"build","targetItem":null,"targetCount":1}
                - craft: 合成物品 → {"action":"craft","targetItem":"oak_planks","targetCount":4}
                - give: 给主人物品 → {"action":"give","targetItem":"iron_ingot","targetCount":5}
                - drop: 丢弃物品 → {"action":"drop","targetItem":"dirt","targetCount":1}
                - attack: 攻击附近怪物 → {"action":"attack","targetItem":null,"targetCount":1}
                - guard: 守卫主人 → {"action":"guard","targetItem":null,"targetCount":1}
                - explore: 探索附近区域 → {"action":"explore","targetItem":null,"targetCount":1}
                - heal: 治疗/进食 → {"action":"heal","targetItem":null,"targetCount":1}
                - farm: 种田/农业 → {"action":"farm","targetItem":null,"targetCount":1}
                - follow: 跟随主人 → {"action":"follow","targetItem":null,"targetCount":1}
                - wait: 原地等待 → {"action":"wait","targetItem":null,"targetCount":1}
                - come: 过来主人身边 → {"action":"come","targetItem":null,"targetCount":1}
                - stop: 停止任务 → {"action":"stop","targetItem":null,"targetCount":1}

                ## 输出规则
                1. 只输出一个 JSON 数组，不要输出任何其他文字、解释或 markdown。
                2. targetItem 为 null 时填 null，targetCount 默认为 1。
                3. 物品名使用 minecraft 标识符（如 oak_log, iron_ore, cobblestone）。
                4. 合成配方遵循原版: oak_log → 4 oak_planks, 2 planks → 4 sticks 等。
                5. 如需采集材料再合成，先 mine/gather 再 craft。
                6. 建造任务用 build 类型。
                7. 任务按执行顺序排列，保持简单可执行。

                ## 输出格式示例
                [{"action":"mine","targetItem":"oak_log","targetCount":5},{"action":"craft","targetItem":"oak_planks","targetCount":4},{"action":"craft","targetItem":"crafting_table","targetCount":1}]

                ## 当前 AI 状态
                """ + context;
    }

    /**
     * 调用 LLM API（OpenAI 兼容格式）
     */
    private static String callLLM(String systemPrompt, String userPrompt) throws Exception {
        String apiUrl = QLMConfig.LLM_API_URL.get();
        String apiKey = QLMConfig.LLM_API_KEY.get();
        String model = QLMConfig.LLM_MODEL.get();
        double temperature = QLMConfig.LLM_TEMPERATURE.get();
        int timeout = QLMConfig.LLM_TIMEOUT.get();

        // 构建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        // 发送 HTTP 请求
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("User-Agent", "QLMZombie/" + QLMZombieMod.MOD_VERSION);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(GSON.toJson(requestBody).getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        InputStream is;
        if (responseCode >= 200 && responseCode < 300) {
            is = conn.getInputStream();
        } else {
            is = conn.getErrorStream();
            String errorBody = readStream(is);
            QLMZombieMod.LOGGER.error("[LLM] API 返回 {}: {}", responseCode,
                    errorBody.substring(0, Math.min(200, errorBody.length())));
            throw new RuntimeException("LLM API 返回 " + responseCode);
        }

        String responseBody = readStream(is);
        conn.disconnect();

        // 解析 OpenAI 格式响应: choices[0].message.content
        JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
        if (json.has("choices")) {
            JsonArray choices = json.getAsJsonArray("choices");
            if (!choices.isEmpty()) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                if (firstChoice.has("message")) {
                    JsonObject message = firstChoice.getAsJsonObject("message");
                    if (message.has("content")) {
                        return message.get("content").getAsString();
                    }
                }
            }
        }

        throw new RuntimeException("LLM 返回内容为空");
    }

    /**
     * 从 LLM 返回文本中解析任务列表
     * 处理多种格式: 纯 JSON / markdown 代码块 / 带包装文字
     */
    private static List<Player2APIService.AIResponse> parseTasks(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();

        String jsonStr = raw.trim();

        // 去除 markdown 代码块标记
        int codeStart = jsonStr.indexOf("```");
        if (codeStart != -1) {
            int contentStart = jsonStr.indexOf('\n', codeStart);
            if (contentStart != -1) {
                int codeEnd = jsonStr.indexOf("```", contentStart);
                if (codeEnd != -1) {
                    jsonStr = jsonStr.substring(contentStart + 1, codeEnd).trim();
                }
            }
        }

        JsonElement parsed = null;
        // 1. 尝试直接解析
        try {
            parsed = GSON.fromJson(jsonStr, JsonElement.class);
        } catch (Exception e) {
            // 2. 尝试提取 [ 到 ] 之间
            int start = jsonStr.indexOf('[');
            int end = jsonStr.lastIndexOf(']');
            if (start != -1 && end != -1 && end > start) {
                try {
                    parsed = GSON.fromJson(jsonStr.substring(start, end + 1), JsonElement.class);
                } catch (Exception e2) {
                    parsed = null;
                }
            }
        }

        if (parsed == null || !parsed.isJsonArray()) {
            QLMZombieMod.LOGGER.warn("[LLM] 无法解析为 JSON 数组: {}",
                    raw.substring(0, Math.min(200, raw.length())));
            return Collections.emptyList();
        }

        List<Player2APIService.AIResponse> tasks = new ArrayList<>();
        for (JsonElement elem : parsed.getAsJsonArray()) {
            if (!elem.isJsonObject()) continue;
            JsonObject obj = elem.getAsJsonObject();
            if (!obj.has("action")) continue;

            String action = obj.get("action").getAsString();
            if (!VALID_ACTIONS.contains(action)) {
                QLMZombieMod.LOGGER.debug("[LLM] 忽略未知任务类型: {}", action);
                continue;
            }

            String targetItem = null;
            if (obj.has("targetItem") && !obj.get("targetItem").isJsonNull()) {
                targetItem = obj.get("targetItem").getAsString();
            }

            int targetCount = 1;
            if (obj.has("targetCount") && !obj.get("targetCount").isJsonNull()) {
                try {
                    targetCount = obj.get("targetCount").getAsInt();
                } catch (Exception ignored) {}
            }

            tasks.add(new Player2APIService.AIResponse(action, targetItem, targetCount, null));
        }

        return tasks;
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}

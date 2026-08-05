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
 *     MCP API communication pattern
 *
 * This is NOT a direct copy. The implementation is original Forge 1.20.1 code
 * adapted to the qlmzombie mod's specific requirements.
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai;

import com.google.gson.*;
import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Player2APIService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static long lastHeartbeatTime = 0;
    private static final long HEARTBEAT_INTERVAL = 60000;
    private static boolean availableCache = false;
    private static long availableCacheTime = 0;

    private Player2APIService() {}

    private static String getBaseUrl() {
        return QLMConfig.PLAYER2_MCP_URL.get();
    }

    private static String getApiKey() {
        return QLMConfig.PLAYER2_MCP_API_KEY.get();
    }

    private static int getTimeout() {
        return QLMConfig.PLAYER2_MCP_TIMEOUT.get();
    }

    private static boolean isEnabled() {
        return QLMConfig.ENABLE_PLAYER2_MCP.get();
    }

    public static boolean isPlayer2Available() {
        if (!isEnabled()) return false;
        long now = System.currentTimeMillis();
        if (now - availableCacheTime < 10000) return availableCache;

        availableCacheTime = now;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(getBaseUrl() + "/health").openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Authorization", "Bearer " + getApiKey());
            connection.setRequestProperty("User-Agent", "QLMZombie/" + QLMZombieMod.MOD_VERSION);
            connection.setRequestProperty("Content-Type", "application/json");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            availableCache = responseCode == 200;
            return availableCache;
        } catch (Exception e) {
            availableCache = false;
            return false;
        }
    }

    public static void sendHeartbeat() {
        long now = System.nanoTime();
        if (now - lastHeartbeatTime < HEARTBEAT_INTERVAL * 1000000L) return;
        lastHeartbeatTime = now;

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, String> resp = sendRequest("/health", false, null);
                QLMZombieMod.LOGGER.debug("Player2 MCP heartbeat: {}", resp);
            } catch (Exception e) {
                QLMZombieMod.LOGGER.debug("Player2 MCP heartbeat failed: {}", e.getMessage());
                availableCache = false;
            }
        });
    }

    public static CompletableFuture<String> sendChatMessage(String characterId, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("character_id", characterId);
                request.addProperty("message", message);

                Map<String, String> resp = sendRequest("/chat", true, request);
                if (resp.containsKey("response")) {
                    return resp.get("response");
                }
                return null;
            } catch (Exception e) {
                QLMZombieMod.LOGGER.warn("Failed to send chat to Player2 MCP: {}", e.getMessage());
                return null;
            }
        });
    }

    public static CompletableFuture<String> sendTask(String characterId, String task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("character_id", characterId);
                request.addProperty("task", task);

                Map<String, String> resp = sendRequest("/task", true, request);
                if (resp.containsKey("action")) {
                    String action = resp.get("action");
                    QLMZombieMod.LOGGER.info("Player2 MCP returned action: {} for task: {}", action, task);
                    return action;
                }
                if (resp.containsKey("response")) {
                    QLMZombieMod.LOGGER.info("Player2 MCP returned response: {} for task: {}", resp.get("response"), task);
                    return resp.get("response");
                }
                // API 返回 200 但无可解析内容 — fallback 到本地解析
                QLMZombieMod.LOGGER.warn("Player2 MCP returned 200 but no action/response for: {} - falling back to local parsing", task);
                return task;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                QLMZombieMod.LOGGER.warn("Failed to send task to Player2 MCP: {}", errorMsg);

                // HTTP 错误或解析失败 — fallback 到本地解析
                QLMZombieMod.LOGGER.warn("Player2 MCP error - falling back to local parsing for: {}", task);
                return task;
            }
        });
    }

    public static CompletableFuture<List<String>> getAvailableCharacters() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> resp = sendRequestWithObject("/characters", false, null);
                if (resp.containsKey("characters")) {
                    List<?> chars = (List<?>) resp.get("characters");
                    List<String> result = new ArrayList<>();
                    for (Object c : chars) {
                        if (c instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) c;
                            if (map.containsKey("id")) {
                                result.add(map.get("id").toString());
                            }
                        } else {
                            result.add(c.toString());
                        }
                    }
                    return result;
                }
                return Collections.emptyList();
            } catch (Exception e) {
                QLMZombieMod.LOGGER.warn("Failed to get characters from Player2 MCP: {}", e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    public static CompletableFuture<String> getCharacterInfo(String characterId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> resp = sendRequest("/character/" + characterId, false, null);
                if (resp.containsKey("name")) {
                    return resp.get("name");
                }
                return null;
            } catch (Exception e) {
                QLMZombieMod.LOGGER.warn("Failed to get character info from Player2 MCP: {}", e.getMessage());
                return null;
            }
        });
    }

    public static AIResponse parseAIResponse(String response) {
        if (response == null || response.isEmpty()) {
            return parseSimpleResponse(response);
        }

        // 先尝试解析为 JSON（标准 API 格式）
        try {
            JsonObject json = GSON.fromJson(response, JsonObject.class);

            String action = json.has("action") ? json.get("action").getAsString() : "none";
            String targetItem = null;
            int targetCount = 1;
            String message = json.has("message") ? json.get("message").getAsString() : null;

            if (json.has("target")) {
                JsonObject target = json.get("target").getAsJsonObject();
                if (target.has("item")) {
                    targetItem = target.get("item").getAsString();
                }
                if (target.has("count")) {
                    targetCount = target.get("count").getAsInt();
                }
            }

            // 如果 action 不是 "none"，直接返回
            if (!"none".equals(action)) {
                return new AIResponse(action, targetItem, targetCount, message != null ? message : response);
            }

            // action 为 none，检查 response 字段
            if (json.has("response")) {
                String respText = json.get("response").getAsString();
                // 用 response 文本进行简单解析
                AIResponse parsed = parseSimpleResponse(respText);
                // 如果解析结果是 chat 类型，保留原始 response 作为 message
                if ("chat".equals(parsed.action())) {
                    return new AIResponse("chat", null, 1, respText);
                }
                return parsed;
            }

            // 尝试从 JSON 的 message 字段解析
            if (message != null) {
                return parseSimpleResponse(message);
            }

            // 无可用字段，用原始 response 解析
            return parseSimpleResponse(response);
        } catch (Exception e) {
            // 不是 JSON — 当作纯文本响应处理
            // 先过滤错误信息（防止泄漏到游戏）
            String lower = response.toLowerCase();
            if (lower.contains("invalid response format") || lower.contains("error") ||
                lower.contains("exception") || lower.contains("traceback")) {
                QLMZombieMod.LOGGER.warn("Player2 MCP returned error response, falling back to local parsing: {}", response.substring(0, Math.min(200, response.length())));
                return parseSimpleResponse(response);
            }

            // 纯文本 LLM 响应（如"好的，我来挖矿"）
            return parseSimpleResponse(response);
        }
    }

    public static AIResponse parseSimpleResponse(String response) {
        if (response == null) {
            return new AIResponse("none", null, 1, null);
        }

        String lower = response.toLowerCase();

        // 检查否定/失败响应（如"做不到"、"不能"、"不会"、"无法"）
        if (lower.contains("做不到") || lower.contains("不能") || lower.contains("不会") || 
            lower.contains("无法") || lower.contains("不行") || lower.contains("没办法")) {
            return new AIResponse("chat", null, 1, response);
        }

        // 按特异性从高到低匹配，避免宽泛单字（如"来"/"打"）截胡更具体的指令
        // 1. 带参数的工作类指令（最具体）
        // 收集木板 — 专门任务，优先于通用"收集"
        if (lower.contains("收集木板") || lower.contains("捡木板") || lower.contains("收木板")) {
            return new AIResponse("collect_planks", null, 1, response);
        }
        if (lower.contains("gather") || lower.contains("收集") || lower.contains("获取")) {
            String item = extractItemName(response);
            int count = extractCount(response);
            return new AIResponse("gather", item, count, response);
        }
        if (lower.contains("craft") || lower.contains("制作") || lower.contains("合成")) {
            String item = extractItemName(response);
            int count = extractCount(response);
            return new AIResponse("craft", item, count, response);
        }
        if (lower.contains("give") || lower.contains("给我") || lower.contains("给你") || lower.contains("交换")) {
            String item = extractItemName(response);
            int count = extractCount(response);
            return new AIResponse("give", item, count, response);
        }
        if (lower.contains("drop") || lower.contains("丢弃") || lower.contains("扔掉") || lower.contains("丢掉")) {
            String item = extractItemName(response);
            int count = extractCount(response);
            return new AIResponse("drop", item, count, response);
        }
        if (lower.contains("mine") || lower.contains("挖矿") || lower.contains("采矿") || lower.contains("挖掘")) {
            String item = extractItemName(response);
            return new AIResponse("mine", item, 1, response);
        }
        if (lower.contains("chop") || lower.contains("砍树") || lower.contains("伐木") || lower.contains("砍柴")) {
            String item = extractItemName(response);
            return new AIResponse("chop", item, 1, response);
        }

        // 2. 无参数的工作类指令
        if (lower.contains("build") || lower.contains("house") || lower.contains("建造") || lower.contains("搭房子") || lower.contains("建房") || lower.contains("盖房")) {
            return new AIResponse("build", null, 1, response);
        }
        if (lower.contains("farm") || lower.contains("种田") || lower.contains("种植") || lower.contains("农业") || lower.contains("种地")) {
            return new AIResponse("farm", null, 1, response);
        }
        if (lower.contains("explore") || lower.contains("探索") || lower.contains("探险")) {
            return new AIResponse("explore", null, 1, response);
        }
        if (lower.contains("heal") || lower.contains("治疗") || lower.contains("恢复") || lower.contains("吃东西") || lower.contains("进食")) {
            return new AIResponse("heal", null, 1, response);
        }
        if (lower.contains("guard") || lower.contains("守卫") || lower.contains("保护") || lower.contains("守护")) {
            return new AIResponse("guard", null, 1, response);
        }
        if (lower.contains("attack") || lower.contains("攻击") || lower.contains("打怪") || lower.contains("打敌") || lower.contains("战斗")) {
            return new AIResponse("attack", null, 1, response);
        }
        if (lower.contains("stop") || lower.contains("停止") || lower.contains("休息") || lower.contains("停下")) {
            return new AIResponse("stop", null, 1, response);
        }
        if (lower.contains("wait") || lower.contains("等待") || lower.contains("原地") || lower.contains("待命")) {
            return new AIResponse("wait", null, 1, response);
        }

        // 3. 移动类指令（较宽泛，放后面）
        if (lower.contains("follow") || lower.contains("跟随") || lower.contains("跟我") || lower.contains("跟着")) {
            return new AIResponse("follow", null, 1, response);
        }
        if (lower.contains("come") || lower.contains("过来") || lower.contains("来这") || lower.contains("过来这")) {
            return new AIResponse("come", null, 1, response);
        }

        // 4. 单字"拿"放最后（太宽泛，可能是"拿铁"等误触发）
        if (lower.contains("拿") && !lower.contains("拿手") && !lower.contains("拿铁")) {
            String item = extractItemName(response);
            int count = extractCount(response);
            return new AIResponse("gather", item, count, response);
        }
        // 单字"丢"放最后
        if (lower.contains("丢") && !lower.contains("丢失")) {
            String item = extractItemName(response);
            int count = extractCount(response);
            return new AIResponse("drop", item, count, response);
        }
        // 单字"放"放最后（太宽泛，可能是"放置"等）
        if (lower.contains("放下") || lower.contains("放掉")) {
            String item = extractItemName(response);
            int count = extractCount(response);
            return new AIResponse("drop", item, count, response);
        }

        return new AIResponse("chat", null, 1, response);
    }

    private static String extractItemName(String message) {
        String[] keywords = {
            "wood", "木头", "原木", "木板", "plank", "log",
            "stone", "石头", "stone", "cobblestone",
            "iron", "铁", "iron_ingot", "iron_ore",
            "gold", "金", "gold_ingot", "gold_ore",
            "diamond", "钻石", "diamond",
            "coal", "煤", "coal",
            "food", "食物", "面包", "bread", "meat", "肉",
            "sword", "剑", "sword",
            "pickaxe", "镐", "pickaxe",
            "axe", "斧头", "axe",
            "shovel", "铲子", "shovel",
            "bow", "弓", "bow",
            "arrow", "箭", "arrow",
            "leather", "皮革", "leather",
            "wool", "羊毛", "wool",
            "glass", "玻璃", "glass",
            "brick", "砖", "brick",
            "obsidian", "黑曜石", "obsidian",
            "netherite", "下界合金", "netherite",
            "emerald", "绿宝石", "emerald",
            "ruby", "红宝石", "ruby",
            "lapislazuli", "青金石", "lapis",
            "redstone", "红石", "redstone",
            "torch", "火把", "torch",
            "lantern", "灯笼", "lantern",
            "bed", "床", "bed",
            "chest", "箱子", "chest",
            "furnace", "熔炉", "furnace",
            "table", "工作台", "crafting_table",
            "enchanting", "附魔台", "enchanting_table",
            "book", "书", "book",
            "paper", "纸", "paper",
            "ink", "墨", "ink_sac",
            "feather", "羽毛", "feather",
            "egg", "蛋", "egg",
            "milk", "牛奶", "milk_bucket",
            "water", "水", "water_bucket",
            "lava", "岩浆", "lava_bucket",
            "bucket", "桶", "bucket",
            "seed", "种子", "seed",
            "wheat", "小麦", "wheat",
            "carrot", "胡萝卜", "carrot",
            "potato", "土豆", "potato",
            "beetroot", "甜菜", "beetroot",
            "melon", "西瓜", "melon",
            "pumpkin", "南瓜", "pumpkin",
            "sugar_cane", "甘蔗", "sugar_cane",
            "cactus", "仙人掌", "cactus",
            "bamboo", "竹子", "bamboo",
            "bone", "骨头", "bone",
            "string", "线", "string",
            "slime", "粘液球", "slime_ball",
            "gunpowder", "火药", "gunpowder",
            "ender_pearl", "末影珍珠", "ender_pearl",
            "experience", "经验", "experience_bottle",
            "coin", "硬币", "coin"
        };

        for (String keyword : keywords) {
            if (message.toLowerCase().contains(keyword.toLowerCase())) {
                return keyword;
            }
        }
        return null;
    }

    private static int extractCount(String message) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\s*(个|块|份|组|个?)");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        pattern = java.util.regex.Pattern.compile("(\\d+)");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return 1;
    }

    private static String executeHttpRequest(String endpoint, boolean post, JsonObject body) throws Exception {
        URL url = new URL(getBaseUrl() + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(getTimeout());
        connection.setReadTimeout(getTimeout());
        connection.setRequestProperty("User-Agent", "QLMZombie/" + QLMZombieMod.MOD_VERSION);
        connection.setRequestProperty("Authorization", "Bearer " + getApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        if (post) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            if (body != null) {
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
                }
            }
        } else {
            connection.setRequestMethod("GET");
        }

        int responseCode = connection.getResponseCode();
        InputStream is = null;
        try {
            if (responseCode >= 200 && responseCode < 300) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
                String errorBody = readStream(is);
                QLMZombieMod.LOGGER.warn("Player2 MCP API error {}: {}", responseCode, errorBody);
                throw new RuntimeException("HTTP " + responseCode + ": " + errorBody);
            }

            return readStream(is);
        } finally {
            if (is != null) {
                is.close();
            }
            connection.disconnect();
        }
    }

    private static Map<String, String> sendRequest(String endpoint, boolean post, JsonObject body) throws Exception {
        String responseBody = executeHttpRequest(endpoint, post, body);
        if (responseBody.isEmpty()) {
            return new HashMap<>();
        }

        JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
        Map<String, String> result = new HashMap<>();

        // 处理 choices[0].message.content 格式（LLM API 标准响应）
        if (json.has("choices")) {
            JsonElement choicesEl = json.get("choices");
            if (choicesEl.isJsonArray() && !choicesEl.getAsJsonArray().isEmpty()) {
                JsonElement firstChoice = choicesEl.getAsJsonArray().get(0);
                if (firstChoice.isJsonObject()) {
                    JsonObject choiceObj = firstChoice.getAsJsonObject();
                    if (choiceObj.has("message")) {
                        JsonElement msgEl = choiceObj.get("message");
                        if (msgEl.isJsonObject()) {
                            JsonObject msgObj = msgEl.getAsJsonObject();
                            if (msgObj.has("content")) {
                                String content = msgObj.get("content").getAsString();
                                result.put("response", content);
                                result.put("action", content);
                                QLMZombieMod.LOGGER.info("Player2 MCP extracted content from choices format: {}", content.substring(0, Math.min(100, content.length())));
                                return result;
                            }
                        }
                    }
                }
            }
        }

        // 标准格式：提取顶层 key-value
        for (String key : json.keySet()) {
            JsonElement element = json.get(key);
            if (element != null && !element.isJsonNull()) {
                if (element.isJsonPrimitive()) {
                    result.put(key, element.getAsString());
                } else {
                    result.put(key, element.toString());
                }
            }
        }
        return result;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sendRequestWithObject(String endpoint, boolean post, JsonObject body) throws Exception {
        String responseBody = executeHttpRequest(endpoint, post, body);
        if (responseBody.isEmpty()) {
            return new HashMap<>();
        }
        return GSON.fromJson(responseBody, Map.class);
    }

    public record AIResponse(String action, String targetItem, int targetCount, String message) {}
}

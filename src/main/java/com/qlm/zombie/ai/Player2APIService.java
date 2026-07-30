package com.qlm.zombie.ai;

import com.google.gson.*;
import com.qlm.zombie.QLMZombieMod;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Player2APIService {

    private static final String BASE_URL = "http://localhost:4315";
    private static final String GAME_KEY = "qlmzombie";
    private static final int TIMEOUT = 15000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static long lastHeartbeatTime = 0;
    private static final long HEARTBEAT_INTERVAL = 60000;

    private Player2APIService() {}

    public static boolean isPlayer2Available() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + "/v1/health").openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static void sendHeartbeat() {
        long now = System.nanoTime();
        if (now - lastHeartbeatTime < HEARTBEAT_INTERVAL * 1000000) return;
        lastHeartbeatTime = now;

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, String> resp = sendRequest("/v1/health", false, null);
                if (resp.containsKey("client_version")) {
                    QLMZombieMod.LOGGER.debug("Player2 heartbeat successful");
                }
            } catch (Exception e) {
                QLMZombieMod.LOGGER.debug("Player2 heartbeat failed: {}", e.getMessage());
            }
        });
    }

    public static CompletableFuture<String> sendChatMessage(String characterId, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("character_id", characterId);
                request.addProperty("message", message);

                Map<String, String> resp = sendRequest("/v1/chat", true, request);
                if (resp.containsKey("response")) {
                    return resp.get("response");
                }
                return null;
            } catch (Exception e) {
                QLMZombieMod.LOGGER.warn("Failed to send chat to Player2: {}", e.getMessage());
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

                Map<String, String> resp = sendRequest("/v1/task", true, request);
                if (resp.containsKey("action")) {
                    return resp.get("action");
                }
                return null;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                QLMZombieMod.LOGGER.warn("Failed to send task to Player2: {}", errorMsg);

                if (errorMsg != null && errorMsg.contains("HTTP 400")) {
                    QLMZombieMod.LOGGER.warn("Player2 API returned 400 - falling back to local parsing for: {}", task);
                    return task;
                }
                return null;
            }
        });
    }

    public static CompletableFuture<List<String>> getAvailableCharacters() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> resp = sendRequestWithObject("/v1/characters", false, null);
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
                QLMZombieMod.LOGGER.warn("Failed to get characters from Player2: {}", e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    public static CompletableFuture<String> getCharacterInfo(String characterId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> resp = sendRequest("/v1/character/" + characterId, false, null);
                if (resp.containsKey("name")) {
                    return resp.get("name");
                }
                return null;
            } catch (Exception e) {
                QLMZombieMod.LOGGER.warn("Failed to get character info: {}", e.getMessage());
                return null;
            }
        });
    }

    public static AIResponse parseAIResponse(String response) {
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

            return new AIResponse(action, targetItem, targetCount, message);
        } catch (Exception e) {
            return parseSimpleResponse(response);
        }
    }

    public static AIResponse parseSimpleResponse(String response) {
        if (response == null) {
            return new AIResponse("none", null, 1, null);
        }

        String lower = response.toLowerCase();
        
        if (lower.contains("follow") || lower.contains("跟随")) {
            return new AIResponse("follow", null, 1, response);
        }
        if (lower.contains("wait") || lower.contains("等待") || lower.contains("原地")) {
            return new AIResponse("wait", null, 1, response);
        }
        if (lower.contains("come") || lower.contains("来") || lower.contains("过来")) {
            return new AIResponse("come", null, 1, response);
        }
        if (lower.contains("gather") || lower.contains("收集") || lower.contains("获取") || lower.contains("拿")) {
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
        if (lower.contains("attack") || lower.contains("攻击") || lower.contains("打")) {
            return new AIResponse("attack", null, 1, response);
        }
        if (lower.contains("mine") || lower.contains("挖矿")) {
            String item = extractItemName(response);
            return new AIResponse("mine", item, 1, response);
        }
        if (lower.contains("chop") || lower.contains("砍树") || lower.contains("伐木")) {
            String item = extractItemName(response);
            return new AIResponse("chop", item, 1, response);
        }
        if (lower.contains("build") || lower.contains("house") || lower.contains("建造") || lower.contains("搭房子") || lower.contains("建房")) {
            return new AIResponse("build", null, 1, response);
        }
        if (lower.contains("stop") || lower.contains("停止") || lower.contains("休息")) {
            return new AIResponse("stop", null, 1, response);
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
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\s*(个|块|个?|份|组)");
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

    private static Map<String, String> sendRequest(String endpoint, boolean post, JsonObject body) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestProperty("User-Agent", "QLMZombie/" + QLMZombieMod.MOD_VERSION);
        connection.setRequestProperty("player2-game-key", GAME_KEY);
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
        }

        int responseCode = connection.getResponseCode();
        InputStream is = null;
        try {
            if (responseCode >= 200 && responseCode < 300) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
                String errorBody = readStream(is);
                QLMZombieMod.LOGGER.warn("Player2 API error {}: {}", responseCode, errorBody);
                throw new RuntimeException("HTTP " + responseCode + ": " + errorBody);
            }

            String responseBody = readStream(is);
            if (responseBody.isEmpty()) {
                return new HashMap<>();
            }

            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            Map<String, String> result = new HashMap<>();
            for (String key : json.keySet()) {
                result.put(key, json.get(key).getAsString());
            }
            return result;
        } finally {
            if (is != null) {
                is.close();
            }
            connection.disconnect();
        }
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
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestProperty("User-Agent", "QLMZombie/" + QLMZombieMod.MOD_VERSION);
        connection.setRequestProperty("player2-game-key", GAME_KEY);
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
        }

        int responseCode = connection.getResponseCode();
        InputStream is = null;
        try {
            if (responseCode >= 200 && responseCode < 300) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
                String errorBody = readStream(is);
                QLMZombieMod.LOGGER.warn("Player2 API error {}: {}", responseCode, errorBody);
                throw new RuntimeException("HTTP " + responseCode + ": " + errorBody);
            }

            String responseBody = readStream(is);
            if (responseBody.isEmpty()) {
                return new HashMap<>();
            }
            return GSON.fromJson(responseBody, Map.class);
        } finally {
            if (is != null) {
                is.close();
            }
            connection.disconnect();
        }
    }

    public record AIResponse(String action, String targetItem, int targetCount, String message) {}
}
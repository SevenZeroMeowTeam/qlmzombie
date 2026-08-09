package com.qlm.zombie.cloudai.util;

import com.qlm.zombie.cloudai.core.CloudAiConstants;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 指令缓存
 * 缓存 FORWARD/STOP/JUMP/ATTACK 等指令，TTL 3 秒
 * 用于 WS 消息 -> AI 执行 的异步解耦
 */
public final class CmdCache {

    private CmdCache() {}

    public static final String CMD_FORWARD = "FORWARD";
    public static final String CMD_STOP = "STOP";
    public static final String CMD_JUMP = "JUMP";
    public static final String CMD_ATTACK = "ATTACK";
    public static final String CMD_LEFT = "LEFT";
    public static final String CMD_RIGHT = "RIGHT";

    private static final ConcurrentHashMap<String, CachedCmd> CACHE = new ConcurrentHashMap<>();

    /** 存入指令（默认 TTL） */
    public static void put(String aiId, String command) {
        put(aiId, command, CloudAiConstants.CMD_CACHE_TTL_SEC);
    }

    /** 存入指令（自定义 TTL，秒） */
    public static void put(String aiId, String command, int ttlSec) {
        if (aiId == null || command == null) return;
        String key = key(aiId, command);
        long expireAt = System.currentTimeMillis() + (long) ttlSec * 1000L;
        CACHE.put(key, new CachedCmd(command, expireAt));
    }

    /** 检查指令是否有效（未过期），消费一次 */
    public static boolean consume(String aiId, String command) {
        if (aiId == null || command == null) return false;
        String key = key(aiId, command);
        CachedCmd cached = CACHE.get(key);
        if (cached == null) return false;
        if (System.currentTimeMillis() > cached.expireAtMillis.get()) {
            CACHE.remove(key);
            return false;
        }
        // 单次消费
        CACHE.remove(key);
        return true;
    }

    /** 仅查看指令是否有效（不消费） */
    public static boolean peek(String aiId, String command) {
        if (aiId == null || command == null) return false;
        String key = key(aiId, command);
        CachedCmd cached = CACHE.get(key);
        if (cached == null) return false;
        if (System.currentTimeMillis() > cached.expireAtMillis.get()) {
            CACHE.remove(key);
            return false;
        }
        return true;
    }

    /** 清除指定 AI 的全部缓存指令 */
    public static void clearFor(String aiId) {
        if (aiId == null) return;
        String prefix = aiId + ":";
        CACHE.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 清理过期缓存（建议在 tick 中周期性调用） */
    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(e -> now > e.getValue().expireAtMillis.get());
    }

    private static String key(String aiId, String command) {
        return aiId + ":" + command;
    }

    private static final class CachedCmd {
        final String command;
        final AtomicLong expireAtMillis;

        CachedCmd(String command, long expireAtMillis) {
            this.command = command;
            this.expireAtMillis = new AtomicLong(expireAtMillis);
        }
    }
}

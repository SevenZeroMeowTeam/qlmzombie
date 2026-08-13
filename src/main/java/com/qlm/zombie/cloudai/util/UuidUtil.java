package com.qlm.zombie.cloudai.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * UUID 工具
 * - offlineUuid: 基于玩家名生成离线 UUID（与原版一致）
 * - randomUuid: 生成随机 UUID（用于 FakePlayer）
 */
public final class UuidUtil {

    private UuidUtil() {}

    /**
     * 根据玩家名生成离线模式 UUID（与 Minecraft 原版算法一致）
     * 参考: net.minecraft.world.entity.player.ProfileKey.offlinePlayerUUID
     */
    public static UUID offlineUuid(String playerName) {
        if (playerName == null) playerName = "CloudAIFollower";
        byte[] nameBytes = ("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(nameBytes);
    }

    /** 生成随机 UUID */
    public static UUID randomUuid() {
        return UUID.randomUUID();
    }
}

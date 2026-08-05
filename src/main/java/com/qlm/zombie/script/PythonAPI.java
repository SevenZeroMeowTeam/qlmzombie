/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * 功能：暴露给 Python 脚本调用的 Java API
 * Python 脚本通过 qlm 对象访问 Minecraft/Forge 功能
 *
 * API 分类：
 *   1. 服务器/玩家    getServer, getPlayer, sendMessage, broadcast
 *   2. 物品          giveItem
 *   3. 方块          getBlock, setBlock, breakBlock, placeBlock
 *   4. 实体          spawnEntity, getNearbyEntities
 *   5. 事件          onBlockBreak, onEntityDeath, onEvent
 *   6. 工具          getGameDay, getPlayerCount, getOnlinePlayerUUIDs
 *   7. 日志          log, warn, error
 */
package com.qlm.zombie.script;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Python 脚本可调用的 Java API。
 * <p>
 * 在 Python 中使用：
 * <pre>
 * # 方块操作
 * qlm.setBlock("minecraft:stone", 0, 64, 0)         # 在坐标放置方块
 * block_id = qlm.getBlock(0, 64, 0)                   # 获取方块 ID
 * qlm.breakBlock(0, 64, 0)                            # 破坏方块
 *
 * # 实体生成
 * qlm.spawnEntity("minecraft:zombie", 0, 64, 0)      # 生成僵尸
 *
 * # 事件监听
 * qlm.onBlockBreak(lambda pos, block, player: ...)
 * qlm.onEntityDeath(lambda entity, source: ...)
 * </pre>
 */
public class PythonAPI {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final IEventBus eventBus;

    // 事件回调注册表
    private final ConcurrentHashMap<String, Consumer<Object>> callbacks = new ConcurrentHashMap<>();
    private Consumer<Object> blockBreakCallback;
    private Consumer<Object> entityDeathCallback;

    public PythonAPI(IEventBus eventBus) {
        this.eventBus = eventBus;
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. 服务器 & 玩家
    // ═══════════════════════════════════════════════════════════════

    /** 获取当前 Minecraft 服务器实例 */
    public MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** 获取模组版本号 */
    public String getModVersion() {
        return QLMZombieMod.MOD_VERSION;
    }

    /** 根据 UUID 获取玩家 */
    public Player getPlayer(String uuid) {
        MinecraftServer server = getServer();
        if (server == null) return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (Exception e) {
            return null;
        }
    }

    /** 给玩家发送聊天消息 */
    public void sendMessage(String uuid, String message) {
        Player player = getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    /** 给玩家发送全局广播 */
    public void broadcast(String message) {
        MinecraftServer server = getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(message), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. 物品
    // ═══════════════════════════════════════════════════════════════

    /** 给玩家物品 (格式: "minecraft:diamond") */
    public void giveItem(String uuid, String itemId, int count) {
        Player player = getPlayer(uuid);
        if (player == null) return;

        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item == null) {
            LOGGER.warn("[Python API] 物品不存在: {}", itemId);
            return;
        }

        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. 方块操作
    // ═══════════════════════════════════════════════════════════════

    /**
     * 获取指定坐标的方块 ID (格式: "minecraft:stone")。
     * @param x, y, z  方块坐标
     * @return 方块资源 ID，如 "minecraft:stone"；无效坐标返回 "minecraft:air"
     */
    public String getBlock(int x, int y, int z) {
        ServerLevel level = getOverworld();
        if (level == null) return "minecraft:air";
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
    }

    /**
     * 在指定坐标放置/设置方块。
     * @param blockId  方块 ID (格式: "minecraft:stone")
     * @param x, y, z  方块坐标
     * @return true=成功, false=方块ID无效
     */
    public boolean setBlock(String blockId, int x, int y, int z) {
        ServerLevel level = getOverworld();
        if (level == null) return false;

        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(blockId));
        if (block == null) {
            LOGGER.warn("[Python API] 方块不存在: {}", blockId);
            return false;
        }

        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
        return true;
    }

    /**
     * 破坏指定坐标的方块（模拟玩家破坏，产生掉落物）。
     * @param x, y, z  方块坐标
     * @return true=成功
     */
    public boolean breakBlock(int x, int y, int z) {
        ServerLevel level = getOverworld();
        if (level == null) return false;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        level.destroyBlock(pos, true); // true = 产生掉落物
        return true;
    }

    /**
     * 在指定坐标放置方块（等同于 setBlock，语义化命名）。
     * @return true=成功
     */
    public boolean placeBlock(String blockId, int x, int y, int z) {
        return setBlock(blockId, x, y, z);
    }

    /**
     * 获取指定区域内所有方块的 ID 列表。
     * @return 坐标到方块ID的映射 Map
     */
    public Map<String, String> getBlockRange(int x1, int y1, int z1, int x2, int y2, int z2) {
        Map<String, String> result = new HashMap<>();
        ServerLevel level = getOverworld();
        if (level == null) return result;

        BlockPos.betweenClosedStream(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        ).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
            result.put(pos.getX() + "," + pos.getY() + "," + pos.getZ(), blockId);
        });
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. 实体操作
    // ═══════════════════════════════════════════════════════════════

    /**
     * 在指定坐标生成实体。
     * @param entityTypeId  实体类型 ID (格式: "minecraft:zombie")
     * @param x, y, z       生成坐标
     * @return 实体 UUID 字符串；失败返回 null
     */
    public String spawnEntity(String entityTypeId, double x, double y, double z) {
        ServerLevel level = getOverworld();
        if (level == null) return null;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.parse(entityTypeId));
        if (type == null) {
            LOGGER.warn("[Python API] 实体类型不存在: {}", entityTypeId);
            return null;
        }

        try {
            Entity entity = type.create(level);
            if (entity == null) return null;
            entity.moveTo(x, y, z, 0.0F, 0.0F);
            level.addFreshEntity(entity);
            return entity.getUUID().toString();
        } catch (Exception e) {
            LOGGER.error("[Python API] 生成实体失败: {} - {}", entityTypeId, e.getMessage());
            return null;
        }
    }

    /**
     * 在指定坐标附近生成多个实体。
     * @param count  生成数量
     * @return 实际生成数量
     */
    public int spawnEntityBatch(String entityTypeId, double x, double y, double z, int count) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double ox = x + (Math.random() - 0.5) * 4;
            double oz = z + (Math.random() - 0.5) * 4;
            if (spawnEntity(entityTypeId, ox, y, oz) != null) spawned++;
        }
        return spawned;
    }

    /**
     * 获取指定坐标附近的实体列表。
     * @param radius  搜索半径（格）
     * @return 实体信息列表，每项为 Map{uuid, type, x, y, z, name}
     */
    public List<Map<String, Object>> getNearbyEntities(double x, double y, double z, double radius) {
        List<Map<String, Object>> result = new ArrayList<>();
        ServerLevel level = getOverworld();
        if (level == null) return result;

        AABB box = new AABB(x - radius, y - radius, z - radius,
                            x + radius, y + radius, z + radius);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
            Map<String, Object> info = new HashMap<>();
            info.put("uuid", entity.getUUID().toString());
            info.put("type", ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString());
            info.put("x", entity.getX());
            info.put("y", entity.getY());
            info.put("z", entity.getZ());
            info.put("name", entity.getName().getString());
            result.add(info);
        }
        return result;
    }

    /**
     * 根据 UUID 移除/击杀实体。
     * @return true=成功
     */
    public boolean removeEntity(String uuid) {
        ServerLevel level = getOverworld();
        if (level == null) return false;
        try {
            Entity entity = level.getEntity(UUID.fromString(uuid));
            if (entity != null) {
                entity.discard();
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("[Python API] 移除实体失败: {}", uuid);
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. 事件监听
    // ═══════════════════════════════════════════════════════════════

    /**
     * 注册方块破坏事件回调。
     * Python 回调接收参数：pos(Map{x,y,z}), blockId(String), playerUuid(String)
     * <pre>
     * qlm.onBlockBreak(lambda pos, blockId, playerUuid:
     *     qlm.log(playerUuid + " broke " + blockId + " at " + str(pos)))
     * </pre>
     */
    public void onBlockBreak(Consumer<Object> callback) {
        LOGGER.info("[Python API] 注册方块破坏事件回调");
        blockBreakCallback = callback;
    }

    /**
     * 注册实体死亡事件回调。
     * Python 回调接收参数：entityType(String), entityUuid(String), sourceName(String), x, y, z
     * <pre>
     * qlm.onEntityDeath(lambda entityType, entityUuid, sourceName, x, y, z:
     *     qlm.log(entityType + " killed by " + sourceName))
     * </pre>
     */
    public void onEntityDeath(Consumer<Object> callback) {
        LOGGER.info("[Python API] 注册实体死亡事件回调");
        entityDeathCallback = callback;
    }

    /**
     * 通用事件注册（供 Java 侧调用分发）。
     */
    public void onEvent(String eventName, Consumer<Object> callback) {
        LOGGER.info("[Python API] 注册事件: {}", eventName);
        callbacks.put(eventName, callback);
    }

    /** 触发方块破坏回调（供 Java 侧 PythonEventBridge 调用） */
    public void fireBlockBreak(int x, int y, int z, String blockId, String playerUuid) {
        if (blockBreakCallback != null) {
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("x", x);
                data.put("y", y);
                data.put("z", z);
                data.put("blockId", blockId);
                data.put("playerUuid", playerUuid);
                blockBreakCallback.accept(data);
            } catch (Exception e) {
                LOGGER.error("[Python API] 方块破坏回调异常: {}", e.getMessage());
            }
        }
    }

    /** 触发实体死亡回调（供 Java 侧 PythonEventBridge 调用） */
    public void fireEntityDeath(String entityType, String entityUuid, String sourceName,
                                 double x, double y, double z) {
        if (entityDeathCallback != null) {
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("entityType", entityType);
                data.put("entityUuid", entityUuid);
                data.put("sourceName", sourceName);
                data.put("x", x);
                data.put("y", y);
                data.put("z", z);
                entityDeathCallback.accept(data);
            } catch (Exception e) {
                LOGGER.error("[Python API] 实体死亡回调异常: {}", e.getMessage());
            }
        }
    }

    /** 触发已注册的事件回调（供 Java 侧调用） */
    public void fireEvent(String eventName, Object event) {
        Consumer<Object> callback = callbacks.get(eventName);
        if (callback != null) {
            try {
                callback.accept(event);
            } catch (Exception e) {
                LOGGER.error("[Python API] 事件回调异常: {} - {}", eventName, e.getMessage());
            }
        }
    }

    /** 检查是否有方块破坏回调注册 */
    public boolean hasBlockBreakCallback() {
        return blockBreakCallback != null;
    }

    /** 检查是否有实体死亡回调注册 */
    public boolean hasEntityDeathCallback() {
        return entityDeathCallback != null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. 日志
    // ═══════════════════════════════════════════════════════════════

    public void log(String message) { LOGGER.info("[Python] {}", message); }
    public void warn(String message) { LOGGER.warn("[Python] {}", message); }
    public void error(String message) { LOGGER.error("[Python] {}", message); }

    // ═══════════════════════════════════════════════════════════════
    //  7. 工具方法
    // ═══════════════════════════════════════════════════════════════

    /** 获取当前游戏天数 */
    public long getGameDay() {
        ServerLevel overworld = getOverworld();
        return overworld != null ? overworld.getDayTime() / 24000L : 0;
    }

    /** 获取在线玩家数量 */
    public int getPlayerCount() {
        MinecraftServer server = getServer();
        return server != null ? server.getPlayerCount() : 0;
    }

    /** 获取在线玩家 UUID 列表 */
    public String[] getOnlinePlayerUUIDs() {
        MinecraftServer server = getServer();
        if (server == null) return new String[0];
        return server.getPlayerList().getPlayers().stream()
                .map(p -> p.getUUID().toString())
                .toArray(String[]::new);
    }

    /** 获取指定维度（格式: "minecraft:overworld" / "minecraft:the_nether" / "minecraft:the_end"） */
    public ServerLevel getLevel(String dimensionId) {
        MinecraftServer server = getServer();
        if (server == null) return null;
        // 映射常用维度 ID 到 Level 常量
        if (dimensionId.equals("minecraft:overworld") || dimensionId.equals("overworld")) {
            return server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        } else if (dimensionId.equals("minecraft:the_nether") || dimensionId.equals("the_nether")) {
            return server.getLevel(net.minecraft.world.level.Level.NETHER);
        } else if (dimensionId.equals("minecraft:the_end") || dimensionId.equals("the_end")) {
            return server.getLevel(net.minecraft.world.level.Level.END);
        }
        return null;
    }

    // ── 内部工具 ──

    private ServerLevel getOverworld() {
        MinecraftServer server = getServer();
        return server != null ? server.getLevel(net.minecraft.world.level.Level.OVERWORLD) : null;
    }
}

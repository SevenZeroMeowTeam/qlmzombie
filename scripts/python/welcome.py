# -*- coding: utf-8 -*-
"""
七零喵僵尸末日 - Python 脚本示例
此文件会在游戏启动时自动加载执行

API 文档:
  方块操作:
    qlm.getBlock(x, y, z)                    -> "minecraft:stone"
    qlm.setBlock(blockId, x, y, z)           -> True/False
    qlm.placeBlock(blockId, x, y, z)          -> True/False
    qlm.breakBlock(x, y, z)                   -> True/False
    qlm.getBlockRange(x1,y1,z1, x2,y2,z2)    -> {"x,y,z": "minecraft:stone", ...}

  实体操作:
    qlm.spawnEntity(entityTypeId, x, y, z)   -> "uuid" / None
    qlm.spawnEntityBatch(typeId, x, y, z, n) -> int
    qlm.getNearbyEntities(x, y, z, radius)   -> [{uuid, type, x, y, z, name}, ...]
    qlm.removeEntity(uuid)                    -> True/False

  事件监听:
    qlm.onBlockBreak(callback)     # callback(data: {x, y, z, blockId, playerUuid})
    qlm.onEntityDeath(callback)    # callback(data: {entityType, entityUuid, sourceName, x, y, z})
    qlm.onEvent(name, callback)

  玩家:
    qlm.getServer()                -> MinecraftServer
    qlm.getPlayer(uuid)            -> Player
    qlm.sendMessage(uuid, msg)
    qlm.broadcast(msg)
    qlm.giveItem(uuid, itemId, count)
    qlm.getOnlinePlayerUUIDs()     -> ["uuid1", "uuid2"]
    qlm.getPlayerCount()           -> int

  其他:
    qlm.getModVersion()            -> "2.10.0..."
    qlm.getGameDay()               -> int
    qlm.getLevel(dimensionId)      -> ServerLevel
    qlm.log(msg) / qlm.warn(msg) / qlm.error(msg)
"""

# ========== 初始化 ==========
qlm.log("Python 脚本引擎已启动: " + qlm.getModVersion())

# ========== 方块破坏监听 ==========
def on_block_break(data):
    """玩家破坏方块时触发"""
    x = data.get("x", 0)
    y = data.get("y", 0)
    z = data.get("z", 0)
    block_id = data.get("blockId", "?")
    player_uuid = data.get("playerUuid", "?")
    qlm.log("[方块破坏] " + str(x) + "," + str(y) + "," + str(z)
            + " " + block_id + " by " + player_uuid[:8])

    # 示例：破坏钻石矿时广播
    if block_id == "minecraft:diamond_ore":
        qlm.broadcast("§b[Python] §e有人挖到了钻石！")

qlm.onBlockBreak(on_block_break)

# ========== 实体死亡监听 ==========
def on_entity_death(data):
    """实体死亡时触发"""
    entity_type = data.get("entityType", "?")
    source_name = data.get("sourceName", "?")
    x = data.get("x", 0)
    y = data.get("y", 0)
    z = data.get("z", 0)
    qlm.log("[实体死亡] " + entity_type + " killed by " + source_name
            + " at " + str(x) + "," + str(y) + "," + str(z))

    # 示例：击杀僵尸时 10% 概率生成额外僵尸
    if entity_type == "minecraft:zombie":
        import random
        if random.random() < 0.1:
            qlm.spawnEntity("minecraft:zombie", x, y, z)
            qlm.broadcast("§c[Python] §4僵尸增援来了！")

qlm.onEntityDeath(on_entity_death)

# ========== 玩家加入事件 ==========
def on_player_join(event_data):
    """玩家加入游戏时触发"""
    qlm.log("有玩家加入了游戏！")
    count = qlm.getPlayerCount()
    qlm.broadcast("§a[Python] §b欢迎新玩家来到七零喵僵尸末日世界！当前在线: " + str(count) + " 人")

qlm.onEvent("PlayerLoggedInEvent", on_player_join)

# ========== 工具函数 ==========
def give_starter_kit(uuid):
    """给玩家发放新手礼包"""
    qlm.giveItem(uuid, "minecraft:stone_sword", 1)
    qlm.giveItem(uuid, "minecraft:bread", 5)
    qlm.giveItem(uuid, "minecraft:torch", 16)
    qlm.sendMessage(uuid, "§a[Python] §e你收到了新手礼包！")

def spawn_mob_wave(entity_type, x, y, z, count):
    """在指定位置生成一波怪物"""
    spawned = qlm.spawnEntityBatch(entity_type, x, y, z, count)
    qlm.broadcast("§c[Python] §4生成了 " + str(spawned) + " 只 " + entity_type)
    return spawned

# ========== 建筑示例 ==========
def build_small_shelter(x, y, z):
    """在指定坐标建造一个小避难所 (5x5x3)"""
    # 地面
    for dx in range(5):
        for dz in range(5):
            qlm.setBlock("minecraft:cobblestone", x + dx, y, z + dz)
    # 墙壁
    for dy in range(1, 4):
        for dx in range(5):
            qlm.setBlock("minecraft:cobblestone", x + dx, y + dy, z)
            qlm.setBlock("minecraft:cobblestone", x + dx, y + dy, z + 4)
        for dz in range(1, 4):
            qlm.setBlock("minecraft:cobblestone", x, y + dy, z + dz)
            qlm.setBlock("minecraft:cobblestone", x + 4, y + dy, z + dz)
    # 屋顶
    for dx in range(5):
        for dz in range(5):
            qlm.setBlock("minecraft:oak_planks", x + dx, y + 4, z + dz)
    # 门
    qlm.setBlock("minecraft:air", x + 2, y + 1, z)
    qlm.setBlock("minecraft:air", x + 2, y + 2, z)
    # 火把
    qlm.setBlock("minecraft:torch", x + 1, y + 3, z + 1)
    qlm.setBlock("minecraft:torch", x + 3, y + 3, z + 3)
    qlm.log("避难所已建造: " + str(x) + "," + str(y) + "," + str(z))

qlm.log("Python 脚本加载完成！")
qlm.log("已注册事件: onBlockBreak, onEntityDeath, PlayerLoggedInEvent")
qlm.log("可用函数: give_starter_kit(uuid), spawn_mob_wave(type, x, y, z, n), build_small_shelter(x, y, z)")

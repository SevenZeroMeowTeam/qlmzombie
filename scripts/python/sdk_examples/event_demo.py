# -*- coding: utf-8 -*-
"""
QLM SDK 事件系统示例
演示如何监听游戏事件：方块破坏、实体死亡、玩家加入等
"""
import qlm

# ========== 事件监听 ==========

def on_block_break(event):
    """方块被破坏时触发"""
    # event 是一个 Map，包含 pos{x,y,z}, blockId, playerUuid
    pos = event.get("pos") if "pos" in event else {}
    block_id = event.get("blockId", "unknown")
    player = event.get("playerUuid", "unknown")
    qlm.log("[事件] 玩家 %s 破坏了 %s" % (player, block_id))

    # 示例：破坏石头时生成粒子效果
    if "stone" in str(block_id):
        x = event.get("x", 0)
        y = event.get("y", 0)
        z = event.get("z", 0)
        qlm.spawnParticle("happy_villager", x, y + 1, z, 0.5, 0.5, 0.5, 5)

def on_entity_death(event):
    """实体死亡时触发"""
    entity_type = event.get("entityType", "unknown")
    source = event.get("sourceName", "unknown")
    qlm.log("[事件] %s 被 %s 击杀" % (entity_type, source))

def on_player_join(event):
    """玩家加入服务器"""
    player_name = event.get("playerName", "unknown")
    qlm.log("[事件] 玩家 %s 加入了游戏" % player_name)
    qlm.broadcast("a欢迎 %s 加入服务器！" % player_name)

def on_player_chat(event):
    """玩家聊天"""
    player = event.get("playerName", "?")
    message = event.get("message", "")
    qlm.log("[聊天] %s: %s" % (player, message))

# ========== 注册事件 ==========

qlm.on("block_break", on_block_break)
qlm.on("entity_death", on_entity_death)
qlm.on("player_join", on_player_join)
qlm.on("player_chat", on_player_chat)

# ========== 主动触发自定义事件 ==========

def trigger_custom_event():
    """演示主动触发事件"""
    qlm.emit("my_custom_event", {"time": qlm.getGameDay(), "msg": "自定义事件测试"})

# 注册一个定时任务，每60秒触发一次自定义事件
qlm.runTimer(0, 1200, trigger_custom_event)

qlm.log("[SDK示例] 事件系统示例已加载")

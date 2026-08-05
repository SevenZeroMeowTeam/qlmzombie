# -*- coding: utf-8 -*-
"""
QLM SDK 综合示例：抽奖系统
组合使用事件、特效、任务、物品系统，实现一个完整的抽奖功能
当玩家输入 "抽奖" 时，延迟3秒后执行抽奖，根据结果播放不同特效
"""
import qlm
import random

# 抽奖冷却（玩家UUID -> 上次抽奖时间）
cooldowns = {}

def on_player_chat(event):
    """监听玩家聊天，检测 '抽奖' 关键词"""
    player_name = event.get("playerName", "")
    message = event.get("message", "").strip()

    if message == "抽奖":
        # 获取玩家UUID
        uuids = qlm.getOnlinePlayerUUIDs()
        player_uuid = None
        for uid in uuids:
            p = qlm.getPlayer(uid)
            if p is not None and p.getName().getString() == player_name:
                player_uuid = uid
                break

        if player_uuid is None:
            return

        # 冷却检查（60秒）
        import time
        now = int(time.time())
        last = cooldowns.get(player_uuid, 0)
        if now - last < 60:
            remaining = 60 - (now - last)
            qlm.sendMessage(player_uuid, "c[抽奖] 冷却中，还需 %d 秒" % remaining)
            return

        cooldowns[player_uuid] = now
        qlm.sendMessage(player_uuid, "e[抽奖] 正在抽奖...3秒后揭晓！")
        qlm.playSoundToPlayer(player_uuid, "block.note_block.harp", 1.0, 0.8)

        # 3秒后执行抽奖（60tick）
        qlm.runLater(60, lambda: do_roll(player_uuid))

def do_roll(player_uuid):
    """执行抽奖"""
    player = qlm.getPlayer(player_uuid)
    if player is None:
        return

    x = player.getX()
    y = player.getY()
    z = player.getZ()

    roll = random.random()

    if roll < 0.01:  # 1% 史诗
        qlm.sendMessage(player_uuid, "d[抽奖] ★★★ 恭喜获得史诗奖品！传说核心！")
        qlm.giveItem(player_uuid, "minecraft:nether_star", 1)
        qlm.playSoundToPlayer(player_uuid, "random.levelup", 1.0, 1.5)
        qlm.spawnParticle("end_rod", x, y + 1, z, 1.0, 2.0, 1.0, 20)
        qlm.spawnParticle("totem_of_undying", x, y + 1, z, 2.0, 2.0, 2.0, 15)
        qlm.broadcast("d[全服] %s 抽到了史诗奖品！" % player.getName().getString())

    elif roll < 0.10:  # 9% 稀有
        qlm.sendMessage(player_uuid, "b[抽奖] ★ 恭喜获得稀有奖品！钻石x3！")
        qlm.giveItem(player_uuid, "minecraft:diamond", 3)
        qlm.playSoundToPlayer(player_uuid, "random.levelup", 0.8, 1.2)
        qlm.spawnParticle("happy_villager", x, y + 1, z, 1.0, 1.0, 1.0, 10)

    elif roll < 0.40:  # 30% 普通
        qlm.sendMessage(player_uuid, "a[抽奖] 获得普通奖品！铁锭x5！")
        qlm.giveItem(player_uuid, "minecraft:iron_ingot", 5)
        qlm.playSoundToPlayer(player_uuid, "block.note_block.harp", 0.6, 1.0)
        qlm.spawnParticle("heart", x, y + 1, z, 0.5, 0.5, 0.5, 5)

    else:  # 60% 安慰
        qlm.sendMessage(player_uuid, "7[抽奖] 获得安慰奖：圆石x10")
        qlm.giveItem(player_uuid, "minecraft:cobblestone", 10)
        qlm.playSoundToPlayer(player_uuid, "block.stone.break", 0.5, 1.0)

# 注册事件
qlm.on("player_chat", on_player_chat)

qlm.log("[SDK示例] 抽奖系统已加载，玩家发送 '抽奖' 即可参与")

# -*- coding: utf-8 -*-
"""
QLM SDK 特效系统示例
演示粒子效果、音效播放、爆炸特效
"""
import qlm

# ========== 粒子效果 ==========

def flame_column(x, y, z, height=5):
    """在某坐标生成火焰柱"""
    for i in range(height):
        qlm.spawnParticle("flame", x, y + i, z)
    qlm.spawnParticle("smoke", x, y + height, z, 0.2, 0.5, 0.2, 10)

def heart_burst(x, y, z):
    """爱心爆发效果"""
    qlm.spawnParticle("heart", x, y + 1, z, 1.0, 1.0, 1.0, 8)
    qlm.spawnParticle("happy_villager", x, y, z, 0.8, 0.8, 0.8, 6)

def portal_effect(x, y, z):
    """传送门粒子"""
    for i in range(10):
        qlm.spawnParticle("portal", x, y + 1, z, 2.0, 2.0, 2.0, 1)

# ========== 音效 ==========

def play_level_up(player_uuid):
    """对玩家播放升级音效"""
    qlm.playSoundToPlayer(player_uuid, "random.levelup", 1.0, 1.0)

def play_thunder(x, y, z):
    """在坐标播放雷声"""
    qlm.playSound("ambient.weather.thunder", x, y, z, 1.0, 1.0)

def play_global_chest_open():
    """全场播放箱子打开音效"""
    qlm.playSoundGlobal("block.chest.open", 0.8, 1.2)

# ========== 组合特效 ==========

def explosion_party(x, y, z):
    """爆炸派对：爆炸+音效+粒子"""
    qlm.spawnExplosionEffect(x, y, z, False)
    qlm.playSound("entity.generic.explode", x, y, z, 1.5, 1.0)
    qlm.spawnParticle("large_smoke", x, y, z, 1.0, 1.0, 1.0, 15)
    qlm.spawnParticle("flame", x, y, z, 0.5, 0.5, 0.5, 10)

# ========== 定时特效演示 ==========

def periodic_effect():
    """每30秒在所有在线玩家位置生成特效"""
    uuids = qlm.getOnlinePlayerUUIDs()
    for uuid in uuids:
        player = qlm.getPlayer(uuid)
        if player is not None:
            x = player.getX()
            y = player.getY()
            z = player.getZ()
            # 在玩家头顶生成爱心
            heart_burst(x, y + 2, z)
            qlm.log("[特效] 在 %s 头顶生成爱心" % player.getName().getString())

# 每600tick（30秒）执行一次
qlm.runTimer(200, 600, periodic_effect)

qlm.log("[SDK示例] 特效系统示例已加载")
qlm.log("[SDK示例] 可用粒子: flame/smoke/portal/heart/lava/redstone/cloud/end_rod")
qlm.log("[SDK示例] 可用音效: block.stone.break/entity.player.hurt/ambient.weather.thunder/random.levelup")

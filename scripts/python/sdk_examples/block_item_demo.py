# -*- coding: utf-8 -*-
"""
QLM SDK 方块/物品注册示例
演示如何注册自定义方块和物品
注意：注册必须在 mod 加载阶段完成，脚本会在启动时自动加载
"""
import qlm

# ========== 注册自定义方块 ==========

# 注册一个发光矿石
qlm.registerBlock("glow_ore", {
    "hardness": 3.0,
    "resistance": 15.0,
    "lightLevel": 1.0,       # 满亮度发光
    "dropItem": "minecraft:glowstone_dust"
})

# 注册一个坚硬的装饰石
qlm.registerBlock("reinforced_stone", {
    "hardness": 10.0,
    "resistance": 50.0,
    "lightLevel": 0.0
})

# ========== 注册自定义物品 ==========

# 注册一个稀有物品
qlm.registerItem("magic_essence", {
    "maxStackSize": 16,
    "rarity": "RARE"
})

# 注册一个普通食物
qlm.registerItem("monster_jerky", {
    "maxStackSize": 64,
    "rarity": "COMMON",
    "isFood": True,
    "nutrition": 6,
    "saturation": 0.8
})

# 注册一个史诗级工具材料
qlm.registerItem("legendary_core", {
    "maxStackSize": 1,
    "rarity": "EPIC"
})

# ========== 查询已注册内容 ==========

qlm.log("[SDK示例] === 注册完成 ===")
qlm.log("[SDK示例] 已注册方块: %s" % str(qlm.getRegisteredBlocks()))
qlm.log("[SDK示例] 已注册物品: %s" % str(qlm.getRegisteredItems()))
qlm.log("[SDK示例] SDK版本: %s" % qlm.getSDKVersion())
qlm.log("[SDK示例] SDK就绪: %s" % qlm.isSDKReady())

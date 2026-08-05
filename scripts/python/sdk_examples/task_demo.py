# -*- coding: utf-8 -*-
"""
QLM SDK 任务调度示例
演示延迟任务、重复任务、任务取消
"""
import qlm

# ========== 延迟任务 ==========

def delayed_greeting():
    """5秒后执行一次"""
    qlm.broadcast("a[系统] 5秒倒计时结束！")
    qlm.playSoundGlobal("block.note_block.harp", 1.0, 1.5)

# 100tick = 5秒后执行
qlm.runLater(100, delayed_greeting)

# ========== 重复任务 ==========

task_id = None

def periodic_broadcast():
    """每10秒广播一次在线人数"""
    count = qlm.getPlayerCount()
    qlm.broadcast("7[系统] 当前在线: %d 人" % count)

# 每200tick（10秒）执行一次
task_id = qlm.runTimer(0, 200, periodic_broadcast)
qlm.log("[SDK示例] 广播任务已启动, taskId=%d" % task_id)

# ========== 任务取消 ==========

def cancel_after_one_minute():
    """60秒后取消广播任务"""
    if task_id is not None and task_id >= 0:
        qlm.cancelTask(task_id)
        qlm.broadcast("e[系统] 自动广播已停止")
        qlm.log("[SDK示例] 任务 %d 已取消" % task_id)

# 1200tick = 60秒后执行取消
qlm.runLater(1200, cancel_after_one_minute)

# ========== 链式任务 ==========

def step1():
    qlm.broadcast("b[任务] 第1步：准备开始...")
    qlm.runLater(60, step2)  # 3秒后执行第2步

def step2():
    qlm.broadcast("b[任务] 第2步：生成怪物！")
    # 在出生点附近生成3只僵尸
    for i in range(3):
        qlm.spawnEntity("minecraft:zombie", 0.5 + i, 64, 0.5)
    qlm.runLater(80, step3)  # 4秒后执行第3步

def step3():
    qlm.broadcast("b[任务] 第3步：播放胜利音效！")
    qlm.playSoundGlobal("random.levelup", 1.0, 1.0)
    qlm.broadcast("a[任务] 链式任务完成！")

# 启动链式任务
qlm.runLater(20, step1)

qlm.log("[SDK示例] 任务调度示例已加载")

# QLM ModSDK — 类似网易我的世界的开发框架

基于 Minecraft Forge 1.20.1 的模组开发 SDK，提供 Python + Java 双层 API，支持事件系统、方块/物品/实体注册、UI 界面、特效音效、任务调度、命令系统。

## 架构

```
┌─────────────────────────────────────────────────┐
│            Python 脚本层 (scripts/python/)        │
│  qlm.on() / qlm.spawnParticle() / qlm.runLater() │
├─────────────────────────────────────────────────┤
│          PythonAPI 桥接层 (Java)                  │
│     Jython / GraalPy / Jep → Java 方法调用        │
├─────────────────────────────────────────────────┤
│              Java SDK 层 (com.qlm.zombie.sdk)     │
│  Event / Registry / UI / Effect / Task / Command  │
├─────────────────────────────────────────────────┤
│              Forge 1.20.1 (MojMaps)               │
└─────────────────────────────────────────────────┘
```

## 核心模块

### 1. 事件系统 (`sdk.event`)
- `SDKEventBus` — 事件总线，registerListener / post
- `SDKEvent` — 事件基类 + 11 个子类
- `ForgeEventBridge` — Forge 事件 → SDK 事件自动桥接
- 支持事件：block_break / block_place / entity_death / entity_hurt / player_join / player_quit / player_chat / player_tick / world_tick / server_start / server_stop

### 2. 注册系统 (`sdk.registry`)
- `SDKRegistry` — 统一注册表（DeferredRegister）
- `CustomBlock.builder(id).hardness(3).resistance(15).lightLevel(1).build()`
- `CustomItem.builder(id).maxStackSize(64).rarity(RARE).food(true).nutrition(6).build()`
- `CustomEntity.builder(id).health(20).speed(0.3).hostile(true).build()`

### 3. UI 界面 (`sdk.ui`)
- `SDKScreen` — GUI 屏幕，继承 Minecraft Screen
- `SDKButton` / `SDKLabel` / `SDKTextField` / `SDKProgressBar`
- `SDKComponent` — 组件基类，支持位置/大小/可见性

### 4. 特效系统 (`sdk.effect`)
- `ParticleAPI` — 18 种粒子（flame/smoke/portal/heart/lava/cloud/end_rod…）
- `SoundAPI` — 10 种音效（block.stone.break/random.levelup/ambient.weather.thunder…）
- `RenderAPI` — 8 种绘制方法（drawText/drawRect/drawTexture/drawLine…）

### 5. 任务调度 (`sdk.task`)
- `TaskScheduler` — runTask / runTaskLater / runTaskTimer / cancelTask
- `ScheduledTask` — 任务数据类

### 6. 命令系统 (`sdk.command`)
- `CommandBuilder.create("mycmd").then("arg", STRING).executes(ctx -> {...}).register()`
- 8 种参数类型：STRING / INTEGER / DOUBLE / BOOLEAN / PLAYER / POSITION / BLOCK_POS / ITEM_STACK

## Python API 速查

```python
import qlm

# 事件
qlm.on("block_break", lambda e: qlm.log("破坏: " + str(e)))
qlm.on("player_join", lambda e: qlm.broadcast("欢迎！"))
qlm.emit("my_event", {"key": "value"})

# 特效
qlm.spawnParticle("flame", x, y, z)
qlm.spawnParticle("heart", x, y, z, 1.0, 1.0, 1.0, 5)
qlm.playSound("random.levelup", x, y, z, 1.0, 1.0)
qlm.playSoundToPlayer(player_uuid, "block.note_block.harp", 1.0, 1.5)
qlm.playSoundGlobal("ambient.weather.thunder", 1.0, 1.0)
qlm.spawnExplosionEffect(x, y, z, False)

# 任务
task_id = qlm.runLater(100, lambda: qlm.log("5秒后执行"))
qlm.runTimer(0, 200, lambda: qlm.log("每10秒重复"))
qlm.cancelTask(task_id)

# 注册（仅 mod 加载阶段）
qlm.registerBlock("my_ore", {"hardness": 3.0, "resistance": 15.0, "lightLevel": 1.0})
qlm.registerItem("magic_gem", {"maxStackSize": 16, "rarity": "RARE"})
qlm.registerItem("jerky", {"isFood": True, "nutrition": 6, "saturation": 0.8})

# 查询
qlm.getRegisteredBlocks()
qlm.getRegisteredItems()
qlm.getSDKVersion()
qlm.isSDKReady()

# 原有 API 仍然可用
qlm.getBlock(x, y, z)
qlm.setBlock("minecraft:stone", x, y, z)
qlm.spawnEntity("minecraft:zombie", x, y, z)
qlm.giveItem(player_uuid, "minecraft:diamond", 3)
qlm.getGameDay()
qlm.getOnlinePlayerUUIDs()
```

## 示例脚本

| 脚本 | 说明 |
|---|---|
| `event_demo.py` | 事件监听：方块破坏/实体死亡/玩家加入/聊天 |
| `effect_demo.py` | 特效：火焰柱/爱心爆发/爆炸派对/定时特效 |
| `task_demo.py` | 任务调度：延迟/重复/取消/链式任务 |
| `block_item_demo.py` | 注册：自定义方块/物品/食物 |
| `combined_demo.py` | 综合：抽奖系统（事件+特效+任务+物品） |

## 文件结构

```
src/main/java/com/qlm/zombie/sdk/
├── QLMModSDK.java              # SDK 入口
├── ForgeEventBridge.java       # Forge→SDK 事件桥接
├── event/
│   ├── SDKEvent.java           # 事件基类 + 11个子类
│   ├── SDKEventBus.java        # 事件总线
│   └── SDKEventListener.java   # 监听器接口
├── registry/
│   ├── SDKRegistry.java        # 注册表
│   ├── CustomBlock.java        # 方块 Builder
│   ├── CustomItem.java         # 物品 Builder
│   └── CustomEntity.java       # 实体 Builder
├── ui/
│   ├── SDKScreen.java          # GUI 屏幕
│   ├── SDKComponent.java       # 组件基类
│   ├── SDKButton.java          # 按钮
│   ├── SDKTextField.java       # 文本框
│   ├── SDKLabel.java           # 标签
│   └── SDKProgressBar.java     # 进度条
├── effect/
│   ├── ParticleAPI.java        # 粒子 API
│   ├── SoundAPI.java           # 音效 API
│   └── RenderAPI.java          # 渲染辅助
├── task/
│   ├── TaskScheduler.java      # 任务调度器
│   └── ScheduledTask.java      # 任务数据类
└── command/
    └── CommandBuilder.java     # 命令构建器
```

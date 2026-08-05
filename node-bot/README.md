# QLM AI Bot — 外部 Node.js AI 玩家

基于 **mineflayer + mineflayer-pathfinder** 的多层架构 Minecraft AI 机器人。
支持三种决策引擎：FSM / 行为树 / GOAP。

## 架构

```
┌─────────────────────────────────────┐
│  Brain (FSM / BehaviorTree / GOAP)  │  决策层
├─────────────────────────────────────┤
│  TaskSystem                         │  任务系统（串行队列）
├─────────────────────────────────────┤
│  Actions (+ ActionLock)             │  行为层（高级动作封装）
├─────────────────────────────────────┤
│  InventoryManager                   │  背包管理
├─────────────────────────────────────┤
│  Navigator (mineflayer-pathfinder)  │  执行层（A* 寻路）
├─────────────────────────────────────┤
│  Sensor ← Memory                    │  感知 + 记忆
├─────────────────────────────────────┤
│  mineflayer Bot                     │  网络层
└─────────────────────────────────────┘
```

## 快速开始

### 1. 安装依赖

```bash
cd node-bot
npm install
```

### 2. 配置

编辑 [config.json](config.json)：

```json
{
  "host": "127.0.0.1",
  "port": 25565,
  "username": "QLM_AI_Bot",
  "version": "1.20.1",
  "auth": "offline",
  "brain": { "type": "fsm" }
}
```

如需在线模式（正版账号），把 `auth` 改为 `"microsoft"`。

### 3. 启动

```bash
# 默认 FSM 大脑
npm start

# 指定大脑
npm run start:bt      # 行为树
npm run start:goap    # GOAP
```

或在游戏内通过聊天切换：

```
!brain bt
!brain fsm
!brain goap
```

## 聊天指令

在游戏内对 bot 发送：

| 指令 | 说明 |
|---|---|
| `!help` | 列出所有指令 |
| `!status` | 查看 bot 状态 |
| `!mine <方块> <数量>` | 入队挖矿任务，例：`!mine iron_ore 10` |
| `!goto <x> <y> <z>` | 移动到坐标 |
| `!follow` | 跟随发送者 |
| `!stop` | 停止所有任务 |
| `!sethome` | 设置当前位置为家 |
| `!home` | 回家 |
| `!brain <fsm\|bt\|goap>` | 切换决策大脑 |
| `!task <preset>` | 入队预设任务链（`wooden_pickaxe` / `cobblestone <count>`） |
| `!inventory` | 查看背包 |

## 三种决策大脑对比

| 大脑 | 适用场景 | 优点 | 缺点 |
|---|---|---|---|
| **FSM** | 简单任务（挖矿/打怪/逃跑） | 简单好调试 | 复杂任务 if-else 多 |
| **行为树** | 完整 AI 玩家 | 模块化、可复用、可视化 | 节点过多时维护麻烦 |
| **GOAP** | 高度自主机器人 | 自动规划动作链、有目标导向 | 实现难度最大、状态空间爆炸 |

## 关键设计

### 动作锁（ActionLock）
所有动作返回 `Promise`，受 `ActionLock` 保护，避免同一动作被并发调用：
- 挖方块需要时间，攻击有冷却，不能疯狂循环调用 `dig`/`attack`
- 一个动作没完成不下达新指令

### 感知节流
- 默认 20 tick (1秒) 扫描一次环境，不是每帧扫描
- 方块感知最大半径 16，防止同步 IO 卡顿

### 寻路配置
[pathfinder Movements](src/executor/Navigator.js) 配置：
- 允许疾跑、跳跃、爬梯
- 禁止踩岩浆、火、仙人掌等危险方块
- 最大路径长度 256 节点，搜索半径 64

### 背包管理
- [InventoryManager](src/inventory/InventoryManager.js) 自动切换最佳工具（依据方块类型）
- 工具耐久 < 5% 自动切换同类工具
- 可配置丢弃垃圾物品（泥土、圆石、花岗岩等）

## 避坑清单

- **不要高频发包**：MC 服务器 tick 20 次/秒，AI 逻辑 3-10 tick 执行一次足够
- **不要图像识别**：性能极差，优先游戏协议
- **不要在公共服务器运行**：反作弊可能 ban
- **远距离任务会失效**：协议只能获取已加载区块，未加载方块看不到
- **动作时序**：每个行为返回 Promise，等待完成再执行下一个

## 持久化
- 家坐标、已访问区块保存在 `data/memory.json`
- 重启后自动加载

## 目录结构

```
node-bot/
├── config.json               # 配置
├── package.json
├── src/
│   ├── index.js              # Bot 入口
│   ├── utils/
│   │   ├── config.js         # 配置加载
│   │   └── logger.js         # 日志
│   ├── sensor/
│   │   └── Sensor.js         # 感知层
│   ├── memory/
│   │   └── Memory.js         # 记忆层
│   ├── executor/
│   │   └── Navigator.js      # pathfinder 封装
│   ├── inventory/
│   │   └── InventoryManager.js
│   ├── action/
│   │   ├── ActionLock.js     # 动作锁
│   │   └── Actions.js        # 行为层
│   ├── brain/
│   │   ├── fsm/
│   │   │   └── FSMBrain.js
│   │   ├── behavior/
│   │   │   ├── BehaviorTree.js
│   │   │   └── BTBrain.js
│   │   └── goap/
│   │       ├── GoapPlanner.js
│   │       └── GoapBrain.js
│   └── task/
│       └── TaskSystem.js
└── data/                     # 持久化数据
    └── memory.json
```

## License
Apache-2.0

'use strict';

/**
 * QLM AI Bot 入口
 *
 * 架构分层:
 *   ┌─────────────────────────────────┐
 *   │   Brain (FSM / BT / GOAP)       │  决策层
 *   ├─────────────────────────────────┤
 *   │   TaskSystem                    │  任务系统
 *   ├─────────────────────────────────┤
 *   │   Actions (含 ActionLock)        │  行为层
 *   ├─────────────────────────────────┤
 *   │   InventoryManager              │  背包管理
 *   ├─────────────────────────────────┤
 *   │   Navigator (mineflayer-pathfinder) │ 执行层
 *   ├─────────────────────────────────┤
 *   │   Sensor ← Memory               │  感知+记忆
 *   ├─────────────────────────────────┤
 *   │   mineflayer Bot                │  网络层
 *   └─────────────────────────────────┘
 *
 * 主循环: bot 物理 tick → Sensor 扫描 → Memory 更新 → Brain 决策 → Actions 执行
 */
const mineflayer = require('mineflayer');
const { loadConfig } = require('./utils/config');
const { getLogger } = require('./utils/logger');
const { Sensor } = require('./sensor/Sensor');
const { Memory } = require('./memory/Memory');
const { Navigator } = require('./executor/Navigator');
const { InventoryManager } = require('./inventory/InventoryManager');
const { Actions } = require('./action/Actions');
const { FSMBrain } = require('./brain/fsm/FSMBrain');
const { BTBrain } = require('./brain/behavior/BTBrain');
const { GoapBrain } = require('./brain/goap/GoapBrain');
const { TaskSystem, TaskPresets } = require('./task/TaskSystem');
const { LLMBridge } = require('./llm/LLMBridge');
const { ForgeHandshake } = require('./forge/ForgeHandshake');

const path = require('path');
const fs = require('fs');

class QLMAIBot {
  constructor(config) {
    this.config = config;
    this.log = getLogger(config).child({ module: 'Main' });
    this.bot = null;
    this.brain = null;
    this.sensor = null;
    this.memory = null;
    this.navigator = null;
    this.inventory = null;
    this.actions = null;
    this.tasks = null;
    this.llm = null;
    this.running = false;
    this.lastTickAt = 0;
    this.lastSensorScanAt = 0;
    this.brainType = (config.brain && config.brain.type) || 'fsm';
  }

  /** 创建并连接 bot */
  async start() {
    this.log.info(`启动 QLM AI Bot [${this.config.username}] → ${this.config.host}:${this.config.port} (brain=${this.brainType})`);

    this.bot = mineflayer.createBot({
      host: this.config.host,
      port: this.config.port,
      username: this.config.username,
      version: this.config.version,
      auth: this.config.auth
    });

    // 初始化 Forge 握手处理器（让 bot 能连接 Forge 服务器）
    new ForgeHandshake(this.bot, this.log);

    // 捕获 minecraft-protocol 客户端的 error 事件
    // PartialReadError (declare_commands 解析失败) 不会导致 Bot 崩溃，
    // 但如果 error 事件没有被监听，Node.js 会抛出未处理异常，中断 TCP 回调，
    // 导致 keepalive 包无法处理 → 30秒后超时断开
    this.bot._client.on('error', (err) => {
      const errName = err.constructor.name;
      if (errName === 'PartialReadError') {
        this.log.warn(`协议解析错误 (已忽略): ${err.message}`);
        return;
      }
      this.log.error(`客户端错误 [${errName}]: ${err.message}`);
    });

    this.attachEventHandlers();
  }

  /** 注册 mineflayer 事件 */
  attachEventHandlers() {
    const bot = this.bot;

    bot.once('spawn', () => this.onSpawn());

    bot.on('chat', (username, message) => this.onChat(username, message));

    bot.on('health', () => {
      if (this.memory && this.memory.selfState) {
        this.memory.selfState.health = bot.health;
        this.memory.selfState.food = bot.food;
      }
      if (bot.health <= 0) {
        this.log.warn('Bot 死亡');
      }
    });

    bot.on('entityHurt', (entity) => {
      // 自己受伤
      if (entity === bot.entity && this.memory) {
        // 找出攻击者
        const attacker = bot.nearestEntity(e =>
          e.position.distanceTo(bot.entity.position) < 5 &&
          e !== bot.entity
        );
        if (attacker) {
          this.memory.recordAttacker(attacker);
          this.log.warn(`受到 ${attacker.name || attacker.username} 攻击`);
        }
      }
    });

    bot.on('playerCollect', () => {
      if (this.inventory) this.inventory.sync();
    });

    bot.on('kicked', (reason) => {
      this.log.error(`被踢出: ${reason}`);
    });

    bot.on('error', (err) => {
      // 连接重置/服务器未启动 → 自动重连
      const msg = err.message || String(err);
      if (msg.includes('ECONNRESET') || msg.includes('ECONNREFUSED') || msg.includes('ETIMEDOUT')) {
        this.log.warn(`连接失败 (${msg.substring(0, 50)})，3秒后重试...`);
        setTimeout(() => {
          this.log.info('尝试重连...');
          try {
            this.bot = mineflayer.createBot({
              host: this.config.host,
              port: this.config.port,
              username: this.config.username,
              version: this.config.version,
              auth: this.config.auth
            });
            // 重新初始化 Forge 握手处理器
            new ForgeHandshake(this.bot, this.log);
            // 重新绑定客户端 error 处理
            this.bot._client.on('error', (err) => {
              const errName = err.constructor.name;
              if (errName === 'PartialReadError') {
                this.log.warn(`协议解析错误 (已忽略): ${err.message}`);
                return;
              }
              this.log.error(`客户端错误 [${errName}]: ${err.message}`);
            });
            // 重新绑定事件
            this.attachEventHandlers();
            this.bot.once('spawn', () => this.onSpawn());
          } catch (e) {
            this.log.error(`重连失败: ${e.message}`);
          }
        }, 3000);
      } else {
        this.log.error(`Bot 错误: ${msg}`);
      }
    });

    bot.on('end', () => {
      this.log.info('连接结束');
      this.running = false;
      if (this.memory) this.memory.persist();
    });

    // 物理 tick: 每 50ms 一次
    bot.on('physicsTick', () => this.onPhysicsTick());

    // 进程退出清理
    process.on('SIGINT', () => this.shutdown());
    process.on('SIGTERM', () => this.shutdown());
  }

  /** spawn 后初始化各组件 */
  onSpawn() {
    this.log.info('已进入世界，初始化组件...');

    this.sensor = new Sensor(this.bot, this.config.sensor);
    this.memory = new Memory(this.config);
    this.memory.enablePersistence(path.resolve(__dirname, '../data/memory.json'));
    this.navigator = new Navigator(this.bot, this.config);
    this.inventory = new InventoryManager(this.bot, this.config, this.memory);
    this.actions = new Actions(this.bot, {
      navigator: this.navigator,
      inventoryManager: this.inventory,
      sensor: this.sensor,
      memory: this.memory,
      config: this.config
    });
    this.tasks = new TaskSystem({
      memory: this.memory,
      config: this.config,
      onTaskComplete: (t, r) => this.log.info(`✓ ${this.tasks.describe(t)}`),
      onTaskFailed: (t, reason) => this.log.warn(`✗ ${this.tasks.describe(t)}: ${reason}`)
    });

    // 初始化 LLM 桥接（自然语言 → 任务规划）
    this.llm = new LLMBridge(this.config.llm || {});
    if (this.llm.enabled) {
      this.log.info(`LLM 已启用: ${this.llm.provider} / ${this.llm.model}`);
    }

    // 创建大脑
    const deps = {
      actions: this.actions,
      sensor: this.sensor,
      memory: this.memory,
      inventory: this.inventory,
      config: this.config
    };
    switch (this.brainType) {
      case 'behavior-tree':
      case 'bt':
        this.brain = new BTBrain(deps);
        this.log.info('使用行为树 (BehaviorTree) 大脑');
        break;
      case 'goap':
        this.brain = new GoapBrain(deps);
        this.log.info('使用 GOAP 大脑');
        break;
      case 'fsm':
      default:
        this.brain = new FSMBrain(deps);
        this.log.info('使用 FSM 大脑');
        break;
    }

    // 初始扫描
    this.sensor.scan();
    this.memory.updateFromSensor(this.sensor.getCached());
    this.memory.updateInventory(this.bot);
    this.inventory.sync();

    // 演示任务: 自动挖矿+合成木镐（可通过 chat 命令重新触发）
    // this.tasks.enqueueChain(TaskPresets.craftWoodenPickaxe());

    this.running = true;
    this.log.info('组件初始化完成，AI 大脑已启动');
    this.bot.chat('大家好！我已上线，使用 !help 查看指令');
  }

  /** 物理 tick 回调 */
  async onPhysicsTick() {
    if (!this.running || !this.brain) return;
    const now = Date.now();
    this.lastTickAt = now;

    // 周期性感知扫描（默认 20 tick = 1 秒）
    const refreshInterval = (this.config.sensor && this.config.sensor.refreshInterval) || 20;
    if (now - this.lastSensorScanAt > refreshInterval * 50) {
      this.lastSensorScanAt = now;
      const snapshot = this.sensor.scan();
      this.memory.updateFromSensor(snapshot);
      // 背包每隔几秒同步一次
      if (Math.random() < 0.2) this.inventory.sync();
    }

    // 大脑 tick（异步）
    try {
      await this.brain.tick();
    } catch (e) {
      this.log.error(`大脑 tick 异常: ${e.message}`);
    }
  }

  /** 聊天指令处理 */
  onChat(username, message) {
    if (username === this.bot.username) return;
    const msg = message.trim();
    if (!msg.startsWith('!') && !msg.startsWith(this.bot.username)) return;

    const cmd = msg.replace(/^!\s*/, '').replace(new RegExp('^' + this.bot.username + '\\s*'), '');
    this.log.info(`[${username}] ${cmd}`);

    const parts = cmd.split(/\s+/);
    const action = parts[0].toLowerCase();

    switch (action) {
      case 'help':
        this.bot.chat('指令: !help | !status | !ai <自然语言> | !mine <方块> <数量> | !goto <x> <y> <z> | !follow | !stop | !home | !sethome | !brain <fsm|bt|goap> | !task <wooden_pickaxe|cobblestone> | !inventory');
        break;
      case 'status':
        this.reportStatus();
        break;
      case 'mine': {
        const block = parts[1] || 'iron_ore';
        const count = parseInt(parts[2] || '5', 10);
        this.tasks.enqueue({
          type: 'mine',
          targetBlocks: [block],
          count
        });
        this.bot.chat(`已入队挖矿任务: ${block} × ${count}`);
        break;
      }
      case 'goto': {
        const x = parseInt(parts[1], 10);
        const y = parseInt(parts[2], 10);
        const z = parseInt(parts[3], 10);
        if (isNaN(x) || isNaN(y) || isNaN(z)) {
          this.bot.chat('用法: !goto <x> <y> <z>');
          return;
        }
        this.tasks.enqueue({ type: 'goto', x, y, z });
        this.bot.chat(`已入队移动任务: (${x},${y},${z})`);
        break;
      }
      case 'follow':
        // 跟随发送指令者
        {
          const player = this.bot.players[username];
          if (player && player.entity) {
            const p = player.entity.position;
            this.tasks.enqueue({ type: 'follow', target: { x: p.x, y: p.y, z: p.z } });
            this.bot.chat(`好的，我跟随着你`);
          } else {
            this.bot.chat('看不到你，无法跟随');
          }
        }
        break;
      case 'stop':
        this.tasks.clear();
        this.actions.stopMoving();
        this.bot.chat('已停止所有任务');
        break;
      case 'sethome':
        if (this.memory) {
          const p = this.bot.entity.position;
          this.memory.setHome({ x: p.x, y: p.y, z: p.z });
          this.bot.chat(`家已设置: (${Math.floor(p.x)},${Math.floor(p.y)},${Math.floor(p.z)})`);
        }
        break;
      case 'home':
        if (this.memory && this.memory.homePos) {
          const h = this.memory.homePos;
          this.tasks.enqueue({ type: 'goto', x: h.x, y: h.y, z: h.z });
          this.bot.chat(`回家: (${h.x},${h.y},${h.z})`);
        } else {
          this.bot.chat('未设置家，使用 !sethome 设置');
        }
        break;
      case 'brain':
        {
          const newBrain = parts[1];
          if (['fsm', 'bt', 'behavior-tree', 'goap'].includes(newBrain)) {
            this.switchBrain(newBrain);
            this.bot.chat(`大脑已切换: ${newBrain}`);
          } else {
            this.bot.chat('可用大脑: fsm | bt | goap');
          }
        }
        break;
      case 'task':
        {
          const preset = parts[1];
          if (preset === 'wooden_pickaxe') {
            this.tasks.enqueueChain(TaskPresets.craftWoodenPickaxe());
            this.bot.chat('已入队: 制作木镐链');
          } else if (preset === 'cobblestone') {
            const n = parseInt(parts[2] || '20', 10);
            this.tasks.enqueueChain(TaskPresets.gatherCobblestone(n));
            this.bot.chat(`已入队: 挖 ${n} 个圆石`);
          } else {
            this.bot.chat('可用任务: wooden_pickaxe | cobblestone <count>');
          }
        }
        break;
      case 'inventory':
        {
          const items = this.bot.inventory.items();
          if (items.length === 0) {
            this.bot.chat('背包为空');
          } else {
            const summary = items.slice(0, 10)
              .map(i => `${i.count}x${i.name}`)
              .join(', ');
            this.bot.chat(`背包: ${summary}${items.length > 10 ? '...' : ''}`);
          }
        }
        break;
      case 'ai': {
        const prompt = parts.slice(1).join(' ');
        if (!prompt) {
          this.bot.chat('用法: !ai <自然语言指令>，例如: !ai 帮我建一座房子');
          return;
        }
        if (!this.llm || !this.llm.enabled) {
          this.bot.chat('LLM 未启用，请在 config.json 中配置 llm');
          return;
        }
        this.bot.chat(`正在规划: ${prompt}...`);
        this.handleAICommand(prompt).catch(e => {
          this.log.error(`AI 规划失败: ${e.message}`);
          this.bot.chat(`规划失败: ${e.message}`);
        });
        break;
      }
      default:
        this.bot.chat(`未知指令: ${action}。输入 !help 查看`);
    }
  }

  /**
   * 处理 !ai 自然语言指令
   * 流程: 收集上下文 → 调用 LLM 规划任务 → 入队 TaskSystem 串行执行
   */
  async handleAICommand(prompt) {
    const context = {
      position: this.bot.entity.position,
      inventory: this.bot.inventory.items().map(i => `${i.count}x${i.name}`),
      health: this.bot.health,
      food: this.bot.food
    };
    const tasks = await this.llm.planTask(prompt, context);
    this.tasks.enqueueChain(tasks);
    this.bot.chat(`已规划 ${tasks.length} 个任务: ${tasks.map(t => t.type).join(' → ')}`);
  }

  /** 切换大脑类型 */
  switchBrain(type) {
    const deps = {
      actions: this.actions,
      sensor: this.sensor,
      memory: this.memory,
      inventory: this.inventory,
      config: this.config
    };
    switch (type) {
      case 'bt':
      case 'behavior-tree':
        this.brain = new BTBrain(deps);
        break;
      case 'goap':
        this.brain = new GoapBrain(deps);
        break;
      case 'fsm':
      default:
        this.brain = new FSMBrain(deps);
        break;
    }
    this.brainType = type;
    this.log.info(`大脑切换为: ${type}`);
  }

  /** 上报状态 */
  reportStatus() {
    const s = this.memory ? this.memory.snapshot() : {};
    const brainName = this.brain ? this.brain.constructor.name : 'none';
    const state = this.brain && this.brain.getState ? this.brain.getState() : '-';
    const pos = s.position || { x: 0, y: 0, z: 0 };
    this.bot.chat(
      `[${brainName}:${state}] HP=${s.health || 0}/${20} Food=${s.food || 0} ` +
      `Pos=(${Math.floor(pos.x)},${Math.floor(pos.y)},${Math.floor(pos.z)}) ` +
      `敌=${s.hostiles || 0} 危险度=${s.dangerLevel || 0}/10 ` +
      `任务=${s.taskQueueLength || 0}`
    );
  }

  /** 优雅关闭 */
  async shutdown() {
    this.log.info('正在关闭...');
    this.running = false;
    if (this.memory) this.memory.persist();
    if (this.actions) this.actions.stopMoving();
    try { this.bot.quit('Bye'); } catch (e) {}
    setTimeout(() => process.exit(0), 1000);
  }
}

// === 启动入口 ===
function main() {
  const config = loadConfig(path.resolve(__dirname, '../'));
  const bot = new QLMAIBot(config);
  bot.start().catch(err => {
    console.error('启动失败:', err);
    process.exit(1);
  });
}

if (require.main === module) {
  main();
}

module.exports = { QLMAIBot };

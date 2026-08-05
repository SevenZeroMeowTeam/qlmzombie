'use strict';

/**
 * 记忆层 Memory
 *
 * 维护 bot 的内部记忆，避免每帧扫描:
 *  - inventory: 背包缓存（物品名 → 总数量）
 *  - hostileMobs: 附近怪物列表
 *  - passiveMobs: 附近动物列表
 *  - nearbyPlayers: 附近玩家
 *  - selfState: 自身状态快照
 *  - homePos: 家坐标（持久化）
 *  - mineTarget: 当前挖矿目标
 *  - taskQueue: 任务队列
 *  - visitedChunks: 已探索区块（轻量探索热力图）
 *  - lastAttackedBy: 上次攻击 bot 的实体
 *  - worldTime: 游戏时间缓存
 */
const fs = require('fs');
const path = require('path');

class Memory {
  constructor(config = {}) {
    this.config = config;
    this.inventory = new Map();        // itemName → count
    this.hostileMobs = [];
    this.passiveMobs = [];
    this.nearbyPlayers = [];
    this.selfState = null;
    this.homePos = config.brain && config.brain.homePos
      ? { ...config.brain.homePos }
      : null;
    this.mineTarget = null;            // { name, position, distance }
    this.taskQueue = [];               // Task[]
    this.visitedChunks = new Set();    // "x,z"
    this.lastAttackedBy = null;        // { name, uuid, timestamp }
    this.worldTime = 0;
    this.isDaytime = true;
    this.lastScanAt = 0;
    this.lastBlockScanAt = 0;
    this.dangerLevel = 0;              // 0-10，根据附近怪物数量+血量
    this.persistPath = null;
  }

  /** 启用持久化（保存到 JSON 文件） */
  enablePersistence(filePath) {
    this.persistPath = filePath;
    this.load();
  }

  /** 从 Sensor 快照更新记忆 */
  updateFromSensor(snapshot) {
    if (!snapshot) return;
    this.lastScanAt = snapshot.timestamp;
    this.selfState = snapshot.self;
    this.hostileMobs = snapshot.hostiles || [];
    this.passiveMobs = snapshot.passives || [];
    this.nearbyPlayers = snapshot.players || [];

    // 危险度计算: 怪物数量 + 平均距离反比
    const mobs = this.hostileMobs;
    let danger = 0;
    for (const m of mobs) {
      // 8格内每个怪物 +2 危险度；16格内 +1
      if (m.distance < 8) danger += 2;
      else if (m.distance < 16) danger += 1;
    }
    if (this.selfState && this.selfState.health < 10) danger += 3;
    this.dangerLevel = Math.min(10, danger);

    if (this.selfState) {
      // 记录已访问区块
      const p = this.selfState.position;
      if (p) {
        const cx = Math.floor(p.x / 16);
        const cz = Math.floor(p.z / 16);
        this.visitedChunks.add(`${cx},${cz}`);
      }
    }
  }

  /** 从 bot 事件更新背包缓存 */
  updateInventory(bot) {
    this.inventory.clear();
    if (!bot.inventory) return;
    for (const item of bot.inventory.items()) {
      const name = item.name;
      this.inventory.set(name, (this.inventory.get(name) || 0) + (item.count || 1));
    }
  }

  /** 查询物品数量 */
  itemCount(name) {
    return this.inventory.get(name) || 0;
  }

  /** 是否拥有至少 N 个某物品 */
  hasItem(name, count = 1) {
    return this.itemCount(name) >= count;
  }

  /** 设置家坐标 */
  setHome(pos) {
    this.homePos = { x: pos.x, y: pos.y, z: pos.z };
    this.persist();
  }

  /** 设置挖矿目标 */
  setMineTarget(target) {
    this.mineTarget = target;
  }

  /** 清除挖矿目标 */
  clearMineTarget() {
    this.mineTarget = null;
  }

  /** 添加任务到队列 */
  enqueueTask(task) {
    this.taskQueue.push(task);
  }

  /** 取出下一个任务 */
  dequeueTask() {
    return this.taskQueue.shift() || null;
  }

  /** 查看当前任务（不出队） */
  peekTask() {
    return this.taskQueue.length > 0 ? this.taskQueue[0] : null;
  }

  /** 清空任务队列 */
  clearTasks() {
    this.taskQueue = [];
  }

  /** 记录攻击者 */
  recordAttacker(entity) {
    if (!entity) return;
    this.lastAttackedBy = {
      name: entity.name || entity.username || 'unknown',
      uuid: entity.uuid,
      timestamp: Date.now()
    };
  }

  /** 判断最近 30 秒是否被攻击 */
  wasRecentlyAttacked(msWindow = 30000) {
    return this.lastAttackedBy &&
      (Date.now() - this.lastAttackedBy.timestamp) < msWindow;
  }

  /** 持久化记忆到文件 */
  persist() {
    if (!this.persistPath) return;
    try {
      const data = {
        homePos: this.homePos,
        visitedChunks: Array.from(this.visitedChunks),
        savedAt: Date.now()
      };
      const dir = path.dirname(this.persistPath);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(this.persistPath, JSON.stringify(data, null, 2));
    } catch (e) {
      // 持久化失败不影响运行
    }
  }

  /** 从文件加载记忆 */
  load() {
    if (!this.persistPath || !fs.existsSync(this.persistPath)) return;
    try {
      const data = JSON.parse(fs.readFileSync(this.persistPath, 'utf8'));
      if (data.homePos) this.homePos = data.homePos;
      if (Array.isArray(data.visitedChunks)) {
        this.visitedChunks = new Set(data.visitedChunks);
      }
    } catch (e) {
      // 加载失败使用默认值
    }
  }

  /** 生成快照用于日志/调试 */
  snapshot() {
    return {
      health: this.selfState ? this.selfState.health : 0,
      food: this.selfState ? this.selfState.food : 0,
      position: this.selfState ? this.selfState.position : null,
      hostiles: this.hostileMobs.length,
      passives: this.passiveMobs.length,
      players: this.nearbyPlayers.length,
      dangerLevel: this.dangerLevel,
      homePos: this.homePos,
      mineTarget: this.mineTarget ? this.mineTarget.name : null,
      taskQueueLength: this.taskQueue.length,
      visitedChunks: this.visitedChunks.size,
      inventorySize: this.inventory.size
    };
  }
}

module.exports = { Memory };

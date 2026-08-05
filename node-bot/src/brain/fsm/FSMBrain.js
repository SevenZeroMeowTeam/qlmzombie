'use strict';

/**
 * 有限状态机 FSM Brain
 *
 * 状态列表:
 *   IDLE     空闲闲逛
 *   MINE     挖矿
 *   FIGHT    战斗
 *   FLEE     逃跑
 *   GOTO     移动到指定位置
 *   COLLECT  拾取物品
 *   EAT      进食
 *   FOLLOW   跟随主人
 *
 * 状态切换逻辑（优先级从高到低）:
 *   1. 血量过低 → FLEE
 *   2. 附近有怪物 → FIGHT
 *   3. 饥饿值低 → EAT
 *   4. 有挖矿任务 → MINE
 *   5. 有拾取任务 → COLLECT
 *   6. 有移动任务 → GOTO
 *   7. 默认 → IDLE 闲逛
 *
 * 每个时刻只能处于一个状态。
 */
const { getLogger } = require('../../utils/logger');

const STATES = {
  IDLE: 'IDLE',
  MINE: 'MINE',
  FIGHT: 'FIGHT',
  FLEE: 'FLEE',
  GOTO: 'GOTO',
  COLLECT: 'COLLECT',
  EAT: 'EAT',
  FOLLOW: 'FOLLOW'
};

class FSMBrain {
  /**
   * @param {object} deps { actions, sensor, memory, inventory, config }
   */
  constructor(deps = {}) {
    this.actions = deps.actions;
    this.sensor = deps.sensor;
    this.memory = deps.memory;
    this.inventory = deps.inventory;
    this.config = deps.config || {};
    this.log = getLogger(this.config).child({ module: 'FSM' });

    this.state = STATES.IDLE;
    this.lastTransitionAt = Date.now();
    this.minStateDurationMs = 500; // 状态最小持续时间，防抖
    this.tickInterval = (this.config.brain && this.config.brain.tickInterval) || 5;
    this.tickCount = 0;
    this.lastExploreAt = 0;

    // 状态处理函数映射
    this.handlers = {
      [STATES.IDLE]: this.handleIdle.bind(this),
      [STATES.MINE]: this.handleMine.bind(this),
      [STATES.FIGHT]: this.handleFight.bind(this),
      [STATES.FLEE]: this.handleFlee.bind(this),
      [STATES.GOTO]: this.handleGoto.bind(this),
      [STATES.COLLECT]: this.handleCollect.bind(this),
      [STATES.EAT]: this.handleEat.bind(this),
      [STATES.FOLLOW]: this.handleFollow.bind(this)
    };
  }

  /** 每 tick 调用 */
  async tick() {
    this.tickCount++;
    if (this.tickCount % this.tickInterval !== 0) return;
    if (this.actions.isBusy()) return; // 有动作在执行，等待

    // 1. 优先级评估，决定是否切换状态
    const desired = this.evaluateState();

    if (desired !== this.state) {
      if (Date.now() - this.lastTransitionAt < this.minStateDurationMs) {
        return; // 状态过短，跳过
      }
      this.transitionTo(desired);
    }

    // 2. 执行当前状态
    const handler = this.handlers[this.state];
    if (handler) {
      try {
        await handler();
      } catch (e) {
        this.log.error(`状态 ${this.state} 处理异常: ${e.message}`);
      }
    }
  }

  /** 评估应当进入的状态 */
  evaluateState() {
    const self = this.memory.selfState;
    if (!self) return STATES.IDLE;

    // 血量过低 → FLEE
    if (self.health <= (this.config.combat.fleeHealthThreshold || 8)) {
      return STATES.FLEE;
    }

    // 附近有怪物 → FIGHT（16 格内）
    const hostiles = this.memory.hostileMobs || [];
    if (hostiles.length > 0 && hostiles[0].distance < 16) {
      return STATES.FIGHT;
    }

    // 饥饿值低 → EAT
    if (self.food < 10) {
      return STATES.EAT;
    }

    // 有挖矿任务 → MINE
    const task = this.memory.peekTask();
    if (task && task.type === 'mine') {
      return STATES.MINE;
    }
    if (this.memory.mineTarget) {
      return STATES.MINE;
    }

    // 有拾取任务 → COLLECT
    if (task && task.type === 'collect') {
      return STATES.COLLECT;
    }

    // 有移动任务 → GOTO
    if (task && task.type === 'goto') {
      return STATES.GOTO;
    }

    // 有跟随任务 → FOLLOW
    if (task && task.type === 'follow') {
      return STATES.FOLLOW;
    }

    return STATES.IDLE;
  }

  /** 状态切换 */
  transitionTo(newState) {
    const old = this.state;
    this.state = newState;
    this.lastTransitionAt = Date.now();
    this.log.info(`状态切换: ${old} → ${newState}`);
  }

  // === 状态处理函数 ===

  async handleIdle() {
    // 偶尔随机走动
    const now = Date.now();
    if (now - this.lastExploreAt > 8000) {
      this.lastExploreAt = now;
      const myPos = this.memory.selfState.position;
      if (!myPos) return;
      const angle = Math.random() * Math.PI * 2;
      const dist = 3 + Math.random() * 5;
      const target = {
        x: Math.floor(myPos.x + Math.cos(angle) * dist),
        y: Math.floor(myPos.y),
        z: Math.floor(myPos.z + Math.sin(angle) * dist)
      };
      const r = await this.actions.goToBlock(target.x, target.y, target.z, { timeout: 6000 });
      if (!r.ok) {
        // 走动失败，尝试执行脱困动作
        await this.actions.unstick();
      }
    }
  }

  async handleMine() {
    const task = this.memory.peekTask();
    if (task && task.type === 'mine') {
      // 找方块
      const myPos = this.memory.selfState.position;
      const blocks = this.sensor.findBlocks(
        myPos,
        this.config.mining.range || 16,
        task.targetBlocks || this.config.mining.preferredOres || ['iron_ore', 'coal_ore'],
        1
      );
      if (blocks.length === 0) {
        this.log.info('未找到目标方块，任务完成');
        this.memory.dequeueTask();
        return;
      }
      const target = blocks[0];
      this.memory.setMineTarget(target);
      const r = await this.actions.mineBlockAt(target.position, { timeout: 15000 });
      if (r.ok) {
        if (task.count && task.count > 1) {
          task.count--;
          this.log.info(`挖矿任务进度: 剩余 ${task.count}`);
        } else {
          this.memory.dequeueTask();
        }
      } else if (r.reason === 'cannot-reach' || r.reason === 'dig-failed') {
        // 无法到达或挖掘失败，尝试脱困
        this.log.warn(`挖矿失败 (${r.reason})，尝试脱困`);
        await this.actions.unstick();
      }
      this.memory.clearMineTarget();
    } else if (this.memory.mineTarget) {
      // 直接挖指定方块
      const r = await this.actions.mineBlockAt(this.memory.mineTarget.position);
      if (r.ok || r.reason === 'cannot-reach') {
        this.memory.clearMineTarget();
      } else if (r.reason === 'dig-failed') {
        await this.actions.unstick();
        this.memory.clearMineTarget();
      }
    }
  }

  async handleFight() {
    const hostile = this.memory.hostileMobs[0];
    if (!hostile) return;
    // 找到对应的实体
    const entity = this.botEntityByUuid(hostile.uuid);
    if (!entity) {
      this.log.debug('怪物实体已消失');
      return;
    }
    const r = await this.actions.fightMob(entity, { timeout: 10000 });
    if (r.reason === 'low-health') {
      // 触发 FLEE
      this.transitionTo(STATES.FLEE);
    }
  }

  async handleFlee() {
    const hostile = this.memory.hostileMobs[0];
    if (!hostile) {
      // 安全了，回到 IDLE
      return;
    }
    const entity = this.botEntityByUuid(hostile.uuid);
    if (entity) {
      await this.actions.fleeFrom(entity, this.config.combat.safeDistance || 24);
    }
  }

  async handleGoto() {
    const task = this.memory.peekTask();
    if (!task || task.type !== 'goto') {
      this.memory.dequeueTask();
      return;
    }
    const r = await this.actions.goToBlock(task.x, task.y, task.z, { timeout: 20000 });
    if (r.ok || r.reason === 'timeout' || r.reason === 'no-path') {
      this.memory.dequeueTask();
    }
  }

  async handleCollect() {
    const task = this.memory.peekTask();
    if (!task || task.type !== 'collect') {
      this.memory.dequeueTask();
      return;
    }
    // 查找附近的掉落物
    const item = this.bot && this.bot.nearestEntity(e =>
      e.entityType === 'item' || e.name === 'item'
    );
    if (!item) {
      this.memory.dequeueTask();
      return;
    }
    await this.actions.collectItem(item);
    if (task.count && task.count > 1) {
      task.count--;
    } else {
      this.memory.dequeueTask();
    }
  }

  async handleEat() {
    const r = await this.actions.eat();
    if (!r) {
      // 没食物，回到 IDLE
      this.log.warn('没有可吃食物');
    }
  }

  async handleFollow() {
    const task = this.memory.peekTask();
    if (!task || task.type !== 'follow') {
      this.memory.dequeueTask();
      return;
    }
    const target = task.target;
    if (!target) {
      this.memory.dequeueTask();
      return;
    }
    await this.actions.goToBlock(
      Math.floor(target.x),
      Math.floor(target.y),
      Math.floor(target.z),
      { timeout: 8000, range: 3 }
    );
  }

  /** 通过 UUID 查找 bot 内部实体 */
  botEntityByUuid(uuid) {
    if (!this.actions.bot.entities) return null;
    for (const id of Object.keys(this.actions.bot.entities)) {
      const e = this.actions.bot.entities[id];
      if (e.uuid === uuid) return e;
    }
    return null;
  }

  /** 获取当前状态 */
  getState() {
    return this.state;
  }

  /** 强制重置到 IDLE */
  reset() {
    this.state = STATES.IDLE;
    this.lastTransitionAt = Date.now();
  }
}

module.exports = { FSMBrain, STATES };

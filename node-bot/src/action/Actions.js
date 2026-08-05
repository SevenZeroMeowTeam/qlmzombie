'use strict';

/**
 * 行为层 Actions
 *
 * 封装高级动作，所有动作返回 Promise，受 ActionLock 保护:
 *  - goToPos(targetPos)        使用 pathfinder 寻路走到坐标
 *  - mineBlockAt(pos)          走到方块，挖掘，等待掉落物，拾取
 *  - fightMob(entity)          锁定怪物，走位攻击，血量低就退出
 *  - build(pos, blockId)       移动，放置方块
 *  - collectItem(itemEntity)   走向掉落物并拾取
 *  - eat()                     自动进食
 *  - fleeFrom(entity, dist)    远离指定实体
 *
 * 设计原则: 不在大脑直接调用 dig/attack，封装为高级函数。
 */
const { Vec3 } = require('vec3');
const { ActionLock } = require('./ActionLock');
const { getLogger } = require('../utils/logger');

class Actions {
  /**
   * @param {import('mineflayer').Bot} bot
   * @param {object} deps { navigator, inventoryManager, sensor, memory, config }
   */
  constructor(bot, deps = {}) {
    this.bot = bot;
    this.navigator = deps.navigator;
    this.inventory = deps.inventoryManager;
    this.sensor = deps.sensor;
    this.memory = deps.memory;
    this.config = deps.config || {};
    this.log = getLogger(this.config).child({ module: 'Actions' });
    this.lock = new ActionLock();

    // 战斗参数
    this.combat = this.config.combat || {
      attackRange: 4.0,
      attackCooldownMs: 600,
      fleeHealthThreshold: 8,
      safeDistance: 24
    };

    // 拾取参数
    this.pickupTimeout = 8000; // 拾取最长 8 秒
    this.pickupReach = 1.5;
  }

  /** 走到指定坐标 */
  async goToPos(target, options = {}) {
    const pos = target.position ? target.position : target;
    return this.lock.run('move', async () => {
      this.log.debug(`寻路到 (${pos.x}, ${pos.y}, ${pos.z})`);
      const result = await this.navigator.goToNear(
        Math.floor(pos.x),
        Math.floor(pos.y),
        Math.floor(pos.z),
        options.range || 2,
        options
      );
      if (!result.ok) {
        this.log.warn(`寻路失败: ${result.reason}, 尝试脱困...`);
        // 如果寻路失败（如卡住），尝试执行脱困动作
        if (result.reason === 'stopped' || result.reason === 'no-path') {
          await this.unstick();
        }
      }
      return result;
    }, options.timeout || 30000);
  }

  /** 走到指定方块中心 */
  async goToBlock(x, y, z, options = {}) {
    return this.lock.run('move', async () => {
      const result = await this.navigator.goToNear(x, y, z, options.range || 2, options);
      if (!result.ok && (result.reason === 'stopped' || result.reason === 'no-path')) {
        this.log.warn(`寻路失败到 (${x},${y},${z}): ${result.reason}, 尝试脱困...`);
        await this.unstick();
      }
      return result;
    }, options.timeout || 30000);
  }

  /** 停止移动 */
  stopMoving() {
    this.navigator.stop();
    this.lock.release('move');
  }

  /**
   * 挖掘指定方块
   * 流程: 装备工具 → 寻路靠近 → dig → 等待掉落物
   * @returns {Promise<{ok: boolean, block: string, dropped: boolean}>}
   */
  async mineBlockAt(pos, options = {}) {
    const blockPos = pos.position ? pos.position : pos;
    const block = pos.block || this.bot.blockAt(blockPos);
    if (!block) {
      return { ok: false, reason: 'block-not-found' };
    }

    return this.lock.run('mine', async () => {
      this.log.info(`挖掘 ${block.name} @ (${blockPos.x},${blockPos.y},${blockPos.z})`);

      // 1. 装备工具
      if (this.inventory) {
        await this.inventory.equipToolForBlock(block.name);
      }

      // 2. 走到方块附近（如果距离够远）
      const myPos = this.bot.entity.position;
      const dist = myPos.distanceTo(blockPos);
      if (dist > 5) {
        const r = await this.navigator.goToNear(
          Math.floor(blockPos.x),
          Math.floor(blockPos.y),
          Math.floor(blockPos.z),
          3,
          { timeout: 15000 }
        );
        if (!r.ok) {
          // 寻路失败，若目标在高处则搭建方块柱上去
          const yDiff = blockPos.y - this.bot.entity.position.y;
          if (yDiff > 2) {
            this.log.info(`目标方块在高处 (Δy=${yDiff.toFixed(1)})，搭建方块上去挖掘`);
            // 先水平走到目标正下方
            await this.navigator.goToXZ(Math.floor(blockPos.x), Math.floor(blockPos.z), { timeout: 8000 });
            await this.buildPillarUp(blockPos.y - 1, { maxBlocks: Math.ceil(yDiff) + 2 });
          } else {
            return { ok: false, reason: 'cannot-reach', block: block.name };
          }
        }
      }

      // 3. 看向方块
      try {
        await this.bot.lookAt(blockPos);
      } catch (e) {}

      // 4. 挖掘
      try {
        await this.bot.dig(block, options.ignoreGravity !== false);
      } catch (e) {
        this.log.warn(`挖掘失败: ${e.message}`);
        return { ok: false, reason: 'dig-failed', error: e, block: block.name };
      }

      // 5. 工具切换检查
      if (this.inventory) {
        await this.inventory.switchToBetterToolIfLow();
      }

      // 6. 等待掉落物（可选）
      let dropped = false;
      if (options.waitForDrop !== false) {
        dropped = await this.waitForPickup(blockPos, options.dropTimeout || 3000);
      }

      // 7. 同步背包
      if (this.inventory) this.inventory.sync();

      return { ok: true, block: block.name, dropped };
    }, options.timeout || 20000);
  }

  /**
   * 攻击指定实体
   * 流程: 装备武器 → 走近 → 攻击 → 循环直到目标死亡或自身血量低
   */
  async fightMob(targetEntity, options = {}) {
    return this.lock.run('attack', async () => {
      if (!targetEntity || !targetEntity.isValid) {
        return { ok: false, reason: 'invalid-target' };
      }

      this.log.info(`攻击 ${targetEntity.name || targetEntity.username}`);

      // 装备武器
      if (this.inventory) {
        await this.inventory.equipWeapon();
      }

      const maxDuration = options.timeout || 30000;
      const startTime = Date.now();
      let lastAttack = 0;

      while (Date.now() - startTime < maxDuration) {
        // 目标失效 / 死亡
        if (!targetEntity.isValid || targetEntity.health <= 0) {
          return { ok: true, reason: 'target-dead' };
        }
        // 自身血量过低 → 撤退
        if (this.bot.health <= this.combat.fleeHealthThreshold) {
          this.log.warn('血量过低，撤退');
          return { ok: false, reason: 'low-health' };
        }

        const target = targetEntity.position;
        const myPos = this.bot.entity.position;
        const dist = myPos.distanceTo(target);

        // 视线 + 距离判断
        if (dist > this.combat.attackRange) {
          // 走近
          try {
            await this.navigator.goToNear(
              Math.floor(target.x),
              Math.floor(target.y),
              Math.floor(target.z),
              Math.max(1, this.combat.attackRange - 1),
              { timeout: 2000 }
            );
          } catch (e) {}
        } else {
          // 攻击（受冷却）
          const now = Date.now();
          if (now - lastAttack >= this.combat.attackCooldownMs) {
            try {
              await this.bot.lookAt(target);
              await this.bot.attack(targetEntity);
              lastAttack = now;
            } catch (e) {
              this.log.debug(`攻击失败: ${e.message}`);
            }
          }
        }

        // 攻击间隙让出控制
        await sleep(100);
      }

      return { ok: false, reason: 'timeout' };
    }, options.timeout || 35000);
  }

  /**
   * 在指定位置放置方块
   */
  async build(targetPos, blockName, options = {}) {
    return this.lock.run('build', async () => {
      if (!this.inventory || !this.inventory.hasItem(blockName, 1)) {
        return { ok: false, reason: 'no-block' };
      }

      // 装备方块到主手
      const item = this.inventory.findItem(blockName);
      await this.inventory.equip(item);

      // 走到目标附近
      const r = await this.navigator.goToNear(
        Math.floor(targetPos.x),
        Math.floor(targetPos.y),
        Math.floor(targetPos.z),
        3,
        { timeout: 10000 }
      );
      if (!r.ok) return { ok: false, reason: 'cannot-reach' };

      // 找到放置面（参考方块）
      const refBlock = this.bot.blockAt(targetPos);
      if (!refBlock) return { ok: false, reason: 'no-ref' };

      try {
        await this.bot.placeBlock(refBlock, new Vec3(0, 1, 0));
        return { ok: true };
      } catch (e) {
        return { ok: false, reason: 'place-failed', error: e };
      }
    }, options.timeout || 15000);
  }

  /** 从背包中查找可用于搭建的方块（按优先级） */
  findBuildBlock() {
    const buildBlocks = [
      'dirt', 'grass_block', 'cobblestone', 'stone',
      'netherrack', 'oak_planks', 'spruce_planks', 'birch_planks',
      'andesite', 'diorite', 'granite', 'deepslate',
      'cobbled_deepslate', 'tuff', 'sandstone', 'terracotta'
    ];
    for (const name of buildBlocks) {
      if (this.inventory && this.inventory.hasItem(name, 1)) {
        return name;
      }
    }
    return null;
  }

  /**
   * 搭建方块柱向上爬（用于收集高处的物品/方块）
   * 流程: 循环「跳起→脚下放置方块→落地」直到到达目标高度
   * @param {number} targetY 目标 Y 高度（到达此高度即停）
   * @param {object} options { maxBlocks, blockName, timeout }
   * @returns {Promise<{ok: boolean, placed: number, currentY: number}>}
   */
  async buildPillarUp(targetY, options = {}) {
    return this.lock.run('build', async () => {
      const maxBlocks = options.maxBlocks || 32;
      let placed = 0;
      let consecFails = 0;

      while (this.bot.entity.position.y < targetY - 0.5 && placed < maxBlocks) {
        // 1. 找到可搭建方块并装备
        const blockName = options.blockName || this.findBuildBlock();
        if (!blockName) {
          this.log.warn('背包中没有可搭建方块，停止搭建');
          break;
        }
        const item = this.inventory ? this.inventory.findItem(blockName) : null;
        if (!item) break;
        await this.inventory.equip(item);

        // 2. 看向脚下方块（参考方块）
        const feet = this.bot.entity.position.floored();
        const refBlock = this.bot.blockAt(feet.offset(0, -1, 0));
        if (!refBlock || refBlock.name === 'air') {
          this.log.warn('脚下无方块，无法搭建');
          break;
        }
        try {
          await this.bot.lookAt(refBlock.position);
        } catch (e) {}

        // 3. 跳起并在脚下放置方块
        try {
          await this.bot.setControlState('jump', true);
          await sleep(150);
          await this.bot.placeBlock(refBlock, new Vec3(0, 1, 0));
          placed++;
          consecFails = 0;
        } catch (e) {
          consecFails++;
          this.log.debug(`搭建方块失败 (${consecFails}): ${e.message}`);
          if (consecFails >= 3) {
            this.log.warn('连续搭建失败 3 次，停止');
            break;
          }
        } finally {
          await this.bot.setControlState('jump', false);
        }
        // 4. 等待落地
        await sleep(350);
      }

      const reached = this.bot.entity.position.y >= targetY - 0.5;
      this.log.info(`搭建完成: 已放置 ${placed} 个方块, 当前高度 ${this.bot.entity.position.y.toFixed(1)}, 目标 ${targetY}, 到达=${reached}`);
      return { ok: reached, placed, currentY: this.bot.entity.position.y };
    }, options.timeout || 30000);
  }

  /**
   * 拾取附近的掉落物
   * 若物品在高处（比自身高 2 格以上），会自动搭建方块柱上去拾取
   */
  async collectItem(itemEntity, options = {}) {
    return this.lock.run('pickup', async () => {
      if (!itemEntity || !itemEntity.isValid) {
        return { ok: false, reason: 'invalid-item' };
      }
      const pos = itemEntity.position;
      this.log.debug(`拾取 ${itemEntity.name} @ ${pos}`);

      // 检测目标是否在高处
      const myPos = this.bot.entity.position;
      const yDiff = pos.y - myPos.y;
      if (yDiff > 2) {
        this.log.info(`目标在高处 (Δy=${yDiff.toFixed(1)})，搭建方块上去拾取`);
        // 先水平走到目标正下方
        await this.navigator.goToXZ(Math.floor(pos.x), Math.floor(pos.z), { timeout: 8000 });
        // 搭建方块柱到目标高度
        await this.buildPillarUp(pos.y - 1, { maxBlocks: Math.ceil(yDiff) + 2 });
      }

      // 走过去拾取
      const r = await this.navigator.goToNear(
        Math.floor(pos.x),
        Math.floor(pos.y),
        Math.floor(pos.z),
        1,
        { timeout: 5000 }
      );
      // 不论是否到达都尝试等待几秒让服务器同步
      await this.waitForPickup(pos, options.timeout || 2000);
      if (this.inventory) this.inventory.sync();
      return { ok: true };
    }, options.timeout || 8000);
  }

  /**
   * 等待附近的掉落物被自动拾取（mineflayer 会自动拾取）
   */
  async waitForPickup(atPos, timeout = 3000) {
    const start = Date.now();
    while (Date.now() - start < timeout) {
      // 检查附近 1.5 格内是否还有掉落物
      const nearby = this.bot.nearestEntity(e =>
        e.entityType === 'item' || e.name === 'item' ||
        e.displayName === 'Item' || e.kind === 'Item'
      );
      if (!nearby) return true;
      await sleep(100);
    }
    return false;
  }

  /** 自动进食 */
  async eat() {
    if (!this.inventory) return false;
    const foods = this.inventory.getFoods();
    if (foods.length === 0) return false;
    // 选恢复饥饿值最高的食物
    foods.sort((a, b) => (b.food.saturation || 0) - (a.food.saturation || 0));
    try {
      await this.bot.equip(foods[0], 'hand');
      await this.bot.consume();
      this.log.info(`进食 ${foods[0].name}`);
      this.inventory.sync();
      return true;
    } catch (e) {
      this.log.warn(`进食失败: ${e.message}`);
      return false;
    }
  }

  /** 远离指定实体 */
  async fleeFrom(entity, distance = 16, options = {}) {
    return this.lock.run('flee', async () => {
      if (!entity || !entity.position) return { ok: false };
      const myPos = this.bot.entity.position.clone();
      const threatPos = entity.position;
      const dx = myPos.x - threatPos.x;
      const dz = myPos.z - threatPos.z;
      const len = Math.sqrt(dx * dx + dz * dz);
      if (len < 0.001) return { ok: false, reason: 'too-close' };

      const fleeTarget = myPos.offset(
        (dx / len) * distance,
        0,
        (dz / len) * distance
      );
      this.log.info(`逃跑至 (${fleeTarget.x.toFixed(1)}, ${fleeTarget.y.toFixed(1)}, ${fleeTarget.z.toFixed(1)})`);
      const r = await this.navigator.goToXZ(
        Math.floor(fleeTarget.x),
        Math.floor(fleeTarget.z),
        { timeout: 8000 }
      );
      return r;
    }, options.timeout || 10000);
  }

  /** 跳跃一次（用于卡住时） */
  async jump() {
    try {
      await this.bot.setControlState('jump', true);
      await sleep(300);
      await this.bot.setControlState('jump', false);
    } catch (e) {}
  }

  /** 脱困：当寻路失败或卡住时执行，尝试跳跃和侧向移动 */
  async unstick() {
    try {
      this.log.info('执行脱困动作...');
      // 1. 尝试跳跃
      await this.jump();
      // 2. 随机选一个方向移动 1-3 格
      const myPos = this.bot.entity.position;
      const angle = Math.random() * Math.PI * 2;
      const dist = 1 + Math.random() * 2;
      const target = {
        x: Math.floor(myPos.x + Math.cos(angle) * dist),
        y: Math.floor(myPos.y),
        z: Math.floor(myPos.z + Math.sin(angle) * dist)
      };
      this.log.debug(`脱困移动到 (${target.x}, ${target.y}, ${target.z})`);
      await this.navigator.goToNear(target.x, target.y, target.z, 1, { timeout: 3000 });
      // 3. 再次尝试跳跃
      await this.jump();
    } catch (e) {
      this.log.debug(`脱困动作异常: ${e.message}`);
    }
  }

  /** 看向目标 */
  async lookAt(pos) {
    try {
      await this.bot.lookAt(pos);
    } catch (e) {}
  }

  /** 是否忙碌 */
  isBusy() {
    return this.lock.isAnyBusy();
  }

  /** 当前动作键 */
  currentAction() {
    const keys = Array.from(this.lock.active.keys());
    return keys.length > 0 ? keys[0] : null;
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

module.exports = { Actions };

'use strict';

/**
 * 感知层 Sensor
 *
 * 从 bot 读取游戏世界信息:
 *  - 方块感知: bot.world.getBlock(pos)
 *  - 实体感知: bot.entities 获取怪物/动物/玩家
 *  - 自身状态: 血量、饥饿、背包、位置
 *
 * 注意: 网络协议只能获取已加载的区块，远处方块看不到。
 * 扩展: 通过 mineflayer-pathfinder 提供寻路能力。
 */
const { Vec3 } = require('vec3');

const HOSTILE_MOB_NAMES = new Set([
  'zombie', 'husk', 'drowned', 'zombie_villager',
  'skeleton', 'stray', 'wither_skeleton',
  'creeper', 'witch', 'spider', 'cave_spider',
  'enderman', 'blaze', 'ghast', 'slime', 'magma_cube',
  'silverfish', 'pillager', 'vindicator', 'evoker', 'ravager',
  'phantom', 'warden', 'zoglin', 'hoglin', 'piglin_brute'
]);

const PASSIVE_MOB_NAMES = new Set([
  'cow', 'pig', 'sheep', 'chicken', 'rabbit',
  'horse', 'donkey', 'mule', 'llama', 'trader_llama',
  'cat', 'ocelot', 'wolf', 'parrot', 'fox', 'bee',
  'turtle', 'axolotl', 'goat', 'frog', 'allay',
  'villager', 'wandering_trader'
]);

class Sensor {
  /**
   * @param {import('mineflayer').Bot} bot
   * @param {object} config sensor 配置
   */
  constructor(bot, config = {}) {
    this.bot = bot;
    this.config = config;
    this.scanRadius = config.scanRadius || 32;
    this.hostileScanRadius = config.hostileScanRadius || 24;
    this.lastScanAt = 0;
    this.cached = null;
  }

  /**
   * 感知一次，返回完整的环境快照
   * 调用方应限制频率（建议 20 tick / 1 秒刷新一次）
   */
  scan() {
    this.lastScanAt = Date.now();
    this.cached = {
      timestamp: this.lastScanAt,
      self: this.getSelfState(),
      hostiles: this.getHostiles(),
      passives: this.getPassives(),
      players: this.getNearbyPlayers(),
      nearestHostile: null,
      nearestPassive: null,
      nearestPlayer: null,
      blocks: null
    };

    if (this.cached.hostiles.length > 0) {
      this.cached.nearestHostile = this.cached.hostiles[0];
    }
    if (this.cached.passives.length > 0) {
      this.cached.nearestPassive = this.cached.passives[0];
    }
    if (this.cached.players.length > 0) {
      this.cached.nearestPlayer = this.cached.players[0];
    }

    return this.cached;
  }

  /** 自身状态快照 */
  getSelfState() {
    const bot = this.bot;
    const pos = bot.entity ? bot.entity.position : null;
    return {
      health: bot.health,
      food: bot.food,
      saturation: bot.foodSaturation,
      oxygen: bot.oxygenLevel,
      xp: bot.experience.points,
      level: bot.experience.level,
      position: pos ? pos.clone() : null,
      yaw: bot.entity ? bot.entity.yaw : 0,
      pitch: bot.entity ? bot.entity.pitch : 0,
      onGround: bot.entity ? bot.entity.onGround : false,
      inWater: bot.entity ? bot.entity.isInWater : false,
      inLava: bot.entity ? bot.entity.isInLava : false,
      isAlive: bot.health > 0,
      heldItem: bot.heldItem ? bot.heldItem.name : null,
      gameMode: bot.game ? bot.game.gameMode : 'survival'
    };
  }

  /** 获取附近敌对实体（按距离排序） */
  getHostiles() {
    return this.getNearbyEntities(this.hostileScanRadius)
      .filter(e => e.kind === 'hostile');
  }

  /** 获取附近被动实体（按距离排序） */
  getPassives() {
    return this.getNearbyEntities(this.scanRadius)
      .filter(e => e.kind === 'passive');
  }

  /** 获取附近玩家（不包括自己） */
  getNearbyPlayers() {
    const list = [];
    if (!this.bot.players) return list;
    const myName = this.bot.username;
    for (const name of Object.keys(this.bot.players)) {
      if (name === myName) continue;
      const player = this.bot.players[name];
      const entity = player.entity;
      if (!entity || !entity.position) continue;
      const dist = this.distanceTo(entity.position);
      if (dist > this.scanRadius) continue;
      list.push({
        type: 'player',
        name,
        username: name,
        uuid: player.uuid,
        position: entity.position.clone(),
        distance: dist,
        health: entity.metadata?.length > 7 ? entity.metadata[7] : undefined
      });
    }
    list.sort((a, b) => a.distance - b.distance);
    return list;
  }

  /** 内部: 枚举附近实体并归类 */
  getNearbyEntities(radius) {
    const list = [];
    if (!this.bot.entities) return list;
    const myPos = this.bot.entity.position;
    if (!myPos) return list;
    const r2 = radius * radius;

    for (const id of Object.keys(this.bot.entities)) {
      const e = this.bot.entities[id];
      if (!e || !e.position || e === this.bot.entity) continue;
      if (e.type !== 'mob' && e.type !== 'animal' && e.type !== 'player') continue;

      const dx = e.position.x - myPos.x;
      const dy = e.position.y - myPos.y;
      const dz = e.position.z - myPos.z;
      const d2 = dx * dx + dy * dy + dz * dz;
      if (d2 > r2) continue;

      const name = (e.name || e.username || 'unknown').toLowerCase();
      let kind = 'neutral';
      if (HOSTILE_MOB_NAMES.has(name)) kind = 'hostile';
      else if (PASSIVE_MOB_NAMES.has(name)) kind = 'passive';
      else if (e.type === 'player') kind = 'player';

      list.push({
        type: e.type,
        kind,
        name,
        displayName: e.name || e.username || name,
        uuid: e.uuid,
        id: e.id,
        position: e.position.clone(),
        distance: Math.sqrt(d2),
        health: this.readEntityHealth(e),
        velocity: e.velocity ? e.velocity.clone() : null
      });
    }

    list.sort((a, b) => a.distance - b.distance);
    return list;
  }

  /** 读取实体血量（不同版本字段不同） */
  readEntityHealth(entity) {
    try {
      const md = entity.metadata;
      if (!md) return null;
      // 1.20: mob metadata 索引 9 附近为 health
      for (let i = 8; i < md.length && i < 12; i++) {
        const v = md[i];
        if (typeof v === 'number' && v > 0 && v <= 1024) return v;
      }
    } catch (e) {}
    return null;
  }

  /**
   * 方块感知: 扫描范围内的方块
   * 警告: 这会同步读取大量方块，性能敏感
   */
  scanBlocks(center, radius, predicate) {
    const bot = this.bot;
    const result = [];
    if (!center || !bot.world) return result;

    const r = Math.min(radius, 16); // 限制最大 16，防止卡顿
    const cursor = new Vec3(0, 0, 0);
    for (cursor.x = -r; cursor.x <= r; cursor.x++) {
      for (cursor.y = -r; cursor.y <= r; cursor.y++) {
        for (cursor.z = -r; cursor.z <= r; cursor.z++) {
          const pos = center.plus(cursor);
          try {
            const block = bot.world.getBlock(pos);
            if (!block) continue;
            if (!predicate || predicate(block)) {
              result.push({
                name: block.name,
                position: pos.clone(),
                distance: this.distanceTo(pos),
                block
              });
            }
          } catch (e) {
            // 未加载区块跳过
          }
        }
      }
    }
    result.sort((a, b) => a.distance - b.distance);
    return result;
  }

  /** 查找附近的指定方块（如矿石） */
  findBlocks(center, radius, blockNames, limit = 32) {
    const nameSet = new Set(blockNames);
    return this.scanBlocks(center, radius, b => nameSet.has(b.name)).slice(0, limit);
  }

  /** 查找最近的方块 */
  findNearestBlock(center, radius, blockNames) {
    const list = this.findBlocks(center, radius, blockNames, 1);
    return list.length > 0 ? list[0] : null;
  }

  /** 检测头顶是否能看到天空（用于"是否在洞穴"判断） */
  canSeeSky(pos) {
    if (!this.bot.world) return false;
    const p = pos.clone();
    const maxY = (this.bot.game && this.bot.game.maxY) || 320;
    for (let y = p.y + 1; y <= maxY; y++) {
      p.y = y;
      try {
        const b = this.bot.world.getBlock(p);
        if (b && b.name !== 'air' && b.name !== 'cave_air') return false;
      } catch (e) {
        return false;
      }
    }
    return true;
  }

  /** 判断当前是否处于白天（基于游戏时间） */
  isDaytime() {
    if (!this.bot.time) return true;
    const time = this.bot.time.timeOfDay;
    // MC 日夜: 0-13000 白天, 13000-24000 夜晚
    return time >= 0 && time < 13000;
  }

  /** 获取最近的危险方块（岩浆、火） */
  findHazards(center, radius = 4) {
    return this.scanBlocks(center, radius, b =>
      b.name === 'lava' || b.name === 'fire' || b.name === 'soul_fire' ||
      b.name === 'cactus' || b.name === 'wither_rose' || b.name === 'sweet_berry_bush'
    );
  }

  /** 工具: 距离计算 */
  distanceTo(pos) {
    const myPos = this.bot.entity.position;
    if (!myPos) return Number.POSITIVE_INFINITY;
    return myPos.distanceTo(pos);
  }

  /** 获取缓存的快照 */
  getCached() {
    return this.cached;
  }
}

module.exports = { Sensor, HOSTILE_MOB_NAMES, PASSIVE_MOB_NAMES };

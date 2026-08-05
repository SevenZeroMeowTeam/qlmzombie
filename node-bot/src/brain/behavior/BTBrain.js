'use strict';

/**
 * 行为树大脑 BTBrain
 *
 * 树结构（选择节点为根，按优先级依次尝试）:
 *
 *   Selector (根)
 *   ├─ Sequence [危险响应]
 *   │   ├─ Condition: 血量过低 OR 附近有强敌
 *   │   └─ Action:   逃跑
 *   ├─ Sequence [战斗]
 *   │   ├─ Condition: 附近有怪物 (16格内)
 *   │   └─ Action:   攻击怪物
 *   ├─ Sequence [进食]
 *   │   ├─ Condition: 饥饿值 < 10
 *   │   └─ Action:   进食
 *   ├─ Sequence [挖矿任务]
 *   │   ├─ Condition: 有挖矿任务
 *   │   └─ Action:   挖矿
 *   ├─ Sequence [拾取任务]
 *   │   ├─ Condition: 有拾取任务
 *   │   └─ Action:   拾取
 *   └─ Action: 闲逛 (默认)
 */
const {
  BehaviorTree, Selector, Sequence, Condition, Action, Cooldown, STATUS
} = require('./BehaviorTree');
const { getLogger } = require('../../utils/logger');

class BTBrain {
  constructor(deps = {}) {
    this.actions = deps.actions;
    this.sensor = deps.sensor;
    this.memory = deps.memory;
    this.inventory = deps.inventory;
    this.config = deps.config || {};
    this.log = getLogger(this.config).child({ module: 'BT' });

    this.tickInterval = (this.config.brain && this.config.brain.tickInterval) || 5;
    this.tickCount = 0;
    this.lastExploreAt = 0;

    this.tree = new BehaviorTree(this.buildTree());
  }

  buildTree() {
    return new Selector([
      // 1. 危险响应
      new Sequence([
        new Condition(ctx => this.isInDanger(ctx)),
        new Action(async ctx => {
          const hostile = this.memory.hostileMobs[0];
          if (!hostile) return false;
          const entity = this.findEntity(hostile.uuid);
          if (entity) {
            const r = await this.actions.fleeFrom(entity, this.config.combat.safeDistance || 24);
            return r.ok;
          }
          return false;
        })
      ]),

      // 2. 战斗
      new Sequence([
        new Condition(ctx => {
          const h = this.memory.hostileMobs[0];
          return h && h.distance < 16;
        }),
        new Action(async ctx => {
          const hostile = this.memory.hostileMobs[0];
          if (!hostile) return false;
          const entity = this.findEntity(hostile.uuid);
          if (!entity) return false;
          const r = await this.actions.fightMob(entity, { timeout: 8000 });
          return r.ok || r.reason === 'target-dead';
        })
      ]),

      // 3. 进食
      new Sequence([
        new Condition(ctx => {
          const s = this.memory.selfState;
          return s && s.food < 10;
        }),
        new Action(async ctx => {
          return await this.actions.eat();
        })
      ]),

      // 4. 挖矿任务
      new Sequence([
        new Condition(ctx => {
          const t = this.memory.peekTask();
          return t && t.type === 'mine';
        }),
        new Action(async ctx => {
          const task = this.memory.peekTask();
          const myPos = this.memory.selfState.position;
          const blocks = this.sensor.findBlocks(
            myPos,
            this.config.mining.range || 16,
            task.targetBlocks || this.config.mining.preferredOres || ['iron_ore', 'coal_ore'],
            1
          );
          if (blocks.length === 0) {
            this.memory.dequeueTask();
            return true;
          }
          const target = blocks[0];
          this.memory.setMineTarget(target);
          const r = await this.actions.mineBlockAt(target.position, { timeout: 15000 });
          this.memory.clearMineTarget();
          if (r.ok) {
            if (task.count && task.count > 1) {
              task.count--;
            } else {
              this.memory.dequeueTask();
            }
          }
          return r.ok;
        })
      ]),

      // 5. 拾取任务
      new Sequence([
        new Condition(ctx => {
          const t = this.memory.peekTask();
          return t && t.type === 'collect';
        }),
        new Action(async ctx => {
          const item = this.actions.bot.nearestEntity(e =>
            e.entityType === 'item' || e.name === 'item'
          );
          if (!item) {
            this.memory.dequeueTask();
            return true;
          }
          await this.actions.collectItem(item);
          const task = this.memory.peekTask();
          if (task && task.count && task.count > 1) {
            task.count--;
          } else if (task) {
            this.memory.dequeueTask();
          }
          return true;
        })
      ]),

      // 6. 移动任务
      new Sequence([
        new Condition(ctx => {
          const t = this.memory.peekTask();
          return t && t.type === 'goto';
        }),
        new Action(async ctx => {
          const task = this.memory.peekTask();
          const r = await this.actions.goToBlock(task.x, task.y, task.z, { timeout: 15000 });
          this.memory.dequeueTask();
          return r.ok;
        })
      ]),

      // 7. 跟随任务
      new Sequence([
        new Condition(ctx => {
          const t = this.memory.peekTask();
          return t && t.type === 'follow';
        }),
        new Action(async ctx => {
          const task = this.memory.peekTask();
          if (!task.target) {
            this.memory.dequeueTask();
            return false;
          }
          const r = await this.actions.goToBlock(
            Math.floor(task.target.x),
            Math.floor(task.target.y),
            Math.floor(task.target.z),
            { timeout: 6000, range: 3 }
          );
          return r.ok;
        })
      ]),

      // 8. 默认: 闲逛
      new Cooldown(
        new Action(async ctx => {
          const now = Date.now();
          if (now - this.lastExploreAt > 8000) {
            this.lastExploreAt = now;
            const myPos = this.memory.selfState.position;
            const angle = Math.random() * Math.PI * 2;
            const dist = 5 + Math.random() * 8;
            await this.actions.goToBlock(
              Math.floor(myPos.x + Math.cos(angle) * dist),
              Math.floor(myPos.y),
              Math.floor(myPos.z + Math.sin(angle) * dist),
              { timeout: 6000 }
            );
          }
          return true;
        }),
        1000 // 至少间隔 1 秒
      )
    ]);
  }

  /** 判断是否处于危险（血量低或敌人很近） */
  isInDanger() {
    const self = this.memory.selfState;
    if (!self) return false;
    if (self.health <= (this.config.combat.fleeHealthThreshold || 8)) return true;
    const hostile = this.memory.hostileMobs[0];
    if (hostile && hostile.distance < 6) return true;
    return false;
  }

  /** 通过 UUID 查找实体 */
  findEntity(uuid) {
    const entities = this.actions.bot.entities;
    if (!entities) return null;
    for (const id of Object.keys(entities)) {
      const e = entities[id];
      if (e.uuid === uuid) return e;
    }
    return null;
  }

  async tick() {
    this.tickCount++;
    if (this.tickCount % this.tickInterval !== 0) return;
    if (this.actions.isBusy()) return;

    try {
      await this.tree.tick(this);
    } catch (e) {
      this.log.error(`行为树 tick 异常: ${e.message}`);
    }
  }
}

module.exports = { BTBrain };

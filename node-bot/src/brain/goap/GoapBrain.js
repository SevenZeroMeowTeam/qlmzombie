'use strict';

/**
 * GOAP 大脑
 *
 * 定义 AI 玩家的动作集和目标集:
 *
 * 动作 (Action):
 *   - flee              逃跑 (前置: in_danger, 效果: safe=true)
 *   - attack_enemy      攻击敌人 (前置: enemy_near, 效果: enemy_dead=true)
 *   - eat_food          进食 (前置: has_food, 效果: hunger=0)
 *   - goto_ore          走到矿石 (前置: ore_in_range, 效果: at_ore=true)
 *   - mine_ore          挖矿 (前置: at_ore, 效果: has_ore=true)
 *   - craft_pickaxe     合成镐 (前置: has_planks × 3, 效果: has_pickaxe=true)
 *   - craft_planks      合成木板 (前置: has_logs, 效果: has_planks += 4)
 *   - collect_drops     拾取掉落物 (前置: drops_nearby, 效果: collected=true)
 *   - explore           探索 (前置: 无, 效果: at_new_area=true)
 *
 * 目标 (Goal):
 *   - survive           生存 (血量 > 0, 无近距离敌人)
 *   - gather_iron       收集铁锭 (has_iron_ingot >= N)
 *   - craft_pickaxe     拥有镐
 *   - build_house       建造房屋
 */
const { GoapAction, GoapGoal, GoapPlanner } = require('./GoapPlanner');
const { getLogger } = require('../../utils/logger');

class GoapBrain {
  constructor(deps = {}) {
    this.actions = deps.actions;
    this.sensor = deps.sensor;
    this.memory = deps.memory;
    this.inventory = deps.inventory;
    this.config = deps.config || {};
    this.log = getLogger(this.config).child({ module: 'GOAP' });

    this.tickInterval = (this.config.brain && this.config.brain.tickInterval) || 10;
    this.tickCount = 0;
    this.currentPlan = null;
    this.currentPlanIndex = 0;
    this.currentGoal = null;
    this.lastPlanAt = 0;
    this.replanInterval = 15000; // 15 秒重新规划一次

    this.planner = new GoapPlanner(this.buildActions(), 6);
    this.goals = this.buildGoals();
  }

  /** 构建 GOAP 动作集 */
  buildActions() {
    return [
      new GoapAction('flee', {
        cost: 3,
        precondition: s => s.in_danger === true,
        effect: s => { s.in_danger = false; s.safe = true; return s; },
        execute: async () => {
          const hostile = this.memory.hostileMobs[0];
          if (!hostile) return false;
          const entity = this.findEntity(hostile.uuid);
          if (!entity) return false;
          const r = await this.actions.fleeFrom(entity, 24);
          return r.ok;
        }
      }),
      new GoapAction('attack_enemy', {
        cost: 2,
        precondition: s => s.enemy_near === true,
        effect: s => { s.enemy_near = false; s.enemy_dead = true; return s; },
        execute: async () => {
          const hostile = this.memory.hostileMobs[0];
          if (!hostile) return false;
          const entity = this.findEntity(hostile.uuid);
          if (!entity) return false;
          const r = await this.actions.fightMob(entity, { timeout: 10000 });
          return r.ok || r.reason === 'target-dead';
        }
      }),
      new GoapAction('eat_food', {
        cost: 2,
        precondition: s => s.hungry === true && s.has_food === true,
        effect: s => { s.hungry = false; s.food_level = 20; return s; },
        execute: async () => {
          return await this.actions.eat();
        }
      }),
      new GoapAction('goto_ore', {
        cost: 2,
        precondition: s => s.ore_in_range === false,
        effect: s => { s.ore_in_range = true; s.at_ore = true; return s; },
        execute: async () => {
          const myPos = this.memory.selfState.position;
          const blocks = this.sensor.findBlocks(
            myPos,
            this.config.mining.range || 16,
            this.config.mining.preferredOres || ['iron_ore', 'coal_ore'],
            1
          );
          if (blocks.length === 0) return false;
          const r = await this.actions.goToBlock(
            blocks[0].position.x,
            blocks[0].position.y,
            blocks[0].position.z,
            { timeout: 10000 }
          );
          return r.ok;
        }
      }),
      new GoapAction('mine_ore', {
        cost: 3,
        precondition: s => s.at_ore === true,
        effect: s => {
          s.has_ore = (s.has_ore || 0) + 1;
          s.at_ore = false; // 需要重新找下一个
          s.ore_in_range = false;
          return s;
        },
        execute: async () => {
          const myPos = this.memory.selfState.position;
          const blocks = this.sensor.findBlocks(
            myPos, 8,
            this.config.mining.preferredOres || ['iron_ore', 'coal_ore'],
            1
          );
          if (blocks.length === 0) return false;
          const r = await this.actions.mineBlockAt(blocks[0].position, { timeout: 15000 });
          return r.ok;
        }
      }),
      new GoapAction('collect_drops', {
        cost: 1,
        precondition: s => s.drops_nearby === true,
        effect: s => { s.drops_nearby = false; s.collected = true; return s; },
        execute: async () => {
          const item = this.actions.bot.nearestEntity(e =>
            e.entityType === 'item' || e.name === 'item'
          );
          if (!item) return false;
          await this.actions.collectItem(item);
          return true;
        }
      }),
      new GoapAction('explore', {
        cost: 4,
        precondition: s => true,
        effect: s => { s.at_new_area = true; s.ore_in_range = false; return s; },
        execute: async () => {
          const myPos = this.memory.selfState.position;
          const angle = Math.random() * Math.PI * 2;
          const dist = 10 + Math.random() * 15;
          const r = await this.actions.goToBlock(
            Math.floor(myPos.x + Math.cos(angle) * dist),
            Math.floor(myPos.y),
            Math.floor(myPos.z + Math.sin(angle) * dist),
            { timeout: 12000 }
          );
          return r.ok;
        }
      }),
      new GoapAction('craft_planks', {
        cost: 2,
        precondition: s => (s.has_logs || 0) >= 1,
        effect: s => {
          s.has_logs = (s.has_logs || 0) - 1;
          s.has_planks = (s.has_planks || 0) + 4;
          return s;
        },
        execute: async () => {
          // 调用合成（需要 bot.craft 或自定义）
          try {
            const recipe = this.actions.bot.recipesFor('oak_planks')[0];
            if (recipe) {
              await this.actions.bot.craft(recipe, 1, null);
              return true;
            }
          } catch (e) {}
          return false;
        }
      }),
      new GoapAction('craft_pickaxe', {
        cost: 3,
        precondition: s => (s.has_planks || 0) >= 3,
        effect: s => {
          s.has_planks = (s.has_planks || 0) - 3;
          s.has_pickaxe = true;
          return s;
        },
        execute: async () => {
          try {
            const recipe = this.actions.bot.recipesFor('wooden_pickaxe')[0];
            if (recipe) {
              await this.actions.bot.craft(recipe, 1, null);
              return true;
            }
          } catch (e) {}
          return false;
        }
      })
    ];
  }

  /** 构建 GOAP 目标集（按优先级排序） */
  buildGoals() {
    return [
      new GoapGoal('survive', {
        priority: 100,
        satisfied: s => s.in_danger === false && (s.enemy_near === false || s.enemy_dead === true)
      }),
      new GoapGoal('eat', {
        priority: 80,
        satisfied: s => s.hungry === false
      }),
      new GoapGoal('gather_iron', {
        priority: 50,
        satisfied: s => (s.has_ore || 0) >= 5
      }),
      new GoapGoal('craft_pickaxe', {
        priority: 60,
        satisfied: s => s.has_pickaxe === true
      })
    ];
  }

  /** 从记忆构建当前世界状态 */
  buildWorldState() {
    const self = this.memory.selfState || {};
    const hostiles = this.memory.hostileMobs || [];
    const inv = this.inventory;
    return {
      // 危险相关
      in_danger: self.health <= (this.config.combat.fleeHealthThreshold || 8),
      enemy_near: hostiles.length > 0 && hostiles[0].distance < 16,
      enemy_dead: false,
      safe: self.health > 15 && hostiles.length === 0,

      // 饥饿
      hungry: self.food < 10,
      has_food: inv ? inv.getFoods().length > 0 : false,
      food_level: self.food || 0,

      // 挖矿
      ore_in_range: false,
      at_ore: false,
      has_ore: inv ? inv.itemCount('iron_ore') + inv.itemCount('coal_ore') : 0,

      // 拾取
      drops_nearby: false,

      // 合成
      has_logs: inv ? inv.itemCount('oak_log') + inv.itemCount('spruce_log') : 0,
      has_planks: inv ? inv.itemCount('oak_planks') : 0,
      has_pickaxe: inv ? inv.findItem(item => item.name.endsWith('_pickaxe')) != null : false,

      // 探索
      at_new_area: false
    };
  }

  /** 选择优先级最高且未满足的目标 */
  pickGoal(worldState) {
    const sorted = [...this.goals].sort((a, b) => b.priority - a.priority);
    for (const g of sorted) {
      if (!g.satisfied(worldState)) return g;
    }
    return null;
  }

  async tick() {
    this.tickCount++;
    if (this.tickCount % this.tickInterval !== 0) return;
    if (this.actions.isBusy()) return;

    // 没有计划或计划过期 → 重新规划
    const now = Date.now();
    if (!this.currentPlan || this.currentPlanIndex >= this.currentPlan.length ||
        now - this.lastPlanAt > this.replanInterval) {
      this.replan();
    }
    if (!this.currentPlan || this.currentPlan.length === 0) return;

    // 执行下一个动作
    const action = this.currentPlan[this.currentPlanIndex];
    this.log.info(`执行 GOAP 动作: ${action.name} (${this.currentPlanIndex + 1}/${this.currentPlan.length})`);
    try {
      const ok = await action.execute();
      if (ok) {
        this.currentPlanIndex++;
      } else {
        // 动作失败，触发重新规划
        this.log.warn(`动作 ${action.name} 失败，重新规划`);
        this.currentPlan = null;
      }
    } catch (e) {
      this.log.error(`动作 ${action.name} 异常: ${e.message}`);
      this.currentPlan = null;
    }
  }

  /** 重新规划 */
  replan() {
    this.lastPlanAt = Date.now();
    this.currentPlan = null;
    this.currentPlanIndex = 0;

    const worldState = this.buildWorldState();
    const goal = this.pickGoal(worldState);
    if (!goal) {
      this.log.debug('所有目标已满足');
      return;
    }

    this.currentGoal = goal;
    const plan = this.planner.plan(worldState, goal);
    if (!plan) {
      this.log.warn(`无法规划达成目标: ${goal.name}`);
      return;
    }

    this.currentPlan = plan;
    this.currentPlanIndex = 0;
    this.log.info(`规划达成目标 [${goal.name}]: ${plan.map(a => a.name).join(' → ')}`);
  }

  findEntity(uuid) {
    const entities = this.actions.bot.entities;
    if (!entities) return null;
    for (const id of Object.keys(entities)) {
      if (entities[id].uuid === uuid) return entities[id];
    }
    return null;
  }

  getCurrentGoal() {
    return this.currentGoal;
  }

  getCurrentPlan() {
    return this.currentPlan;
  }
}

module.exports = { GoapBrain };

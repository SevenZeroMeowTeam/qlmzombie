'use strict';

/**
 * 任务系统 TaskSystem
 *
 * 串行执行一系列目标，支持的任务类型:
 *  - mine:    { type: 'mine', targetBlocks: ['iron_ore'], count: 10 }
 *  - collect: { type: 'collect', itemName: 'iron_ingot', count: 5 }
 *  - goto:    { type: 'goto', x, y, z }
 *  - follow:  { type: 'follow', target: { x, y, z } }
 *  - craft:   { type: 'craft', itemName: 'wooden_pickaxe', count: 1 }
 *  - build:   { type: 'build', x, y, z, blockName: 'oak_planks' }
 *  - wait:    { type: 'wait', durationMs: 5000 }
 *
 * 用法:
 *   const ts = new TaskSystem({ memory, log });
 *   ts.enqueueChain([
 *     { type: 'mine', targetBlocks: ['stone'], count: 20 },
 *     { type: 'craft', itemName: 'crafting_table', count: 1 },
 *     { type: 'craft', itemName: 'wooden_pickaxe', count: 1 }
 *   ]);
 */
const { getLogger } = require('../utils/logger');

class TaskSystem {
  constructor(deps = {}) {
    this.memory = deps.memory;
    this.config = deps.config || {};
    this.log = getLogger(this.config).child({ module: 'Task' });
    this.onTaskComplete = deps.onTaskComplete || null;
    this.onTaskFailed = deps.onTaskFailed || null;
  }

  /** 入队单个任务 */
  enqueue(task) {
    if (!task || !task.type) {
      this.log.warn('任务格式无效: 缺少 type');
      return;
    }
    task.enqueuedAt = Date.now();
    task.status = 'pending';
    if (this.memory) {
      this.memory.enqueueTask(task);
    } else {
      this._localQueue = this._localQueue || [];
      this._localQueue.push(task);
    }
    this.log.info(`任务入队: ${this.describe(task)}`);
  }

  /** 入队一组任务，按顺序执行 */
  enqueueChain(tasks) {
    for (const t of tasks) {
      this.enqueue(t);
    }
  }

  /** 取出下一个任务 */
  next() {
    if (this.memory) {
      return this.memory.dequeueTask();
    }
    return this._localQueue ? this._localQueue.shift() : null;
  }

  /** 查看当前任务（不出队） */
  peek() {
    if (this.memory) {
      return this.memory.peekTask();
    }
    return this._localQueue && this._localQueue.length > 0
      ? this._localQueue[0] : null;
  }

  /** 任务总数 */
  size() {
    if (this.memory) return this.memory.taskQueue.length;
    return this._localQueue ? this._localQueue.length : 0;
  }

  /** 清空所有任务 */
  clear() {
    if (this.memory) this.memory.clearTasks();
    if (this._localQueue) this._localQueue = [];
  }

  /** 任务描述（用于日志） */
  describe(task) {
    if (!task) return '<null>';
    switch (task.type) {
      case 'mine':
        return `挖矿 ${(task.targetBlocks || []).join('/')} × ${task.count || 1}`;
      case 'collect':
        return `收集 ${task.itemName || 'item'} × ${task.count || 1}`;
      case 'goto':
        return `前往 (${task.x},${task.y},${task.z})`;
      case 'follow':
        return `跟随 ${JSON.stringify(task.target)}`;
      case 'craft':
        return `合成 ${task.itemName} × ${task.count || 1}`;
      case 'build':
        return `建造 ${task.blockName} @ (${task.x},${task.y},${task.z})`;
      case 'wait':
        return `等待 ${task.durationMs || 1000}ms`;
      default:
        return JSON.stringify(task);
    }
  }

  /** 标记任务完成 */
  complete(task, result) {
    task.status = 'completed';
    task.completedAt = Date.now();
    task.result = result;
    this.log.info(`任务完成: ${this.describe(task)}`);
    if (this.onTaskComplete) {
      try { this.onTaskComplete(task, result); } catch (e) {}
    }
  }

  /** 标记任务失败 */
  fail(task, reason) {
    task.status = 'failed';
    task.failedAt = Date.now();
    task.failReason = reason;
    this.log.warn(`任务失败: ${this.describe(task)} - ${reason}`);
    if (this.onTaskFailed) {
      try { this.onTaskFailed(task, reason); } catch (e) {}
    }
  }
}

/** 工厂: 预设任务链 */
const TaskPresets = {
  /** 收集 N 个圆石 */
  gatherCobblestone(count = 20) {
    return [
      { type: 'mine', targetBlocks: ['stone'], count }
    ];
  },
  /** 制作木镐链 */
  craftWoodenPickaxe() {
    return [
      { type: 'mine', targetBlocks: ['oak_log', 'spruce_log', 'birch_log'], count: 3 },
      { type: 'craft', itemName: 'oak_planks', count: 4 },
      { type: 'craft', itemName: 'stick', count: 2 },
      { type: 'craft', itemName: 'crafting_table', count: 1 },
      { type: 'craft', itemName: 'wooden_pickaxe', count: 1 }
    ];
  },
  /** 探索 */
  explore(duration = 60000) {
    return [
      { type: 'goto', x: 0, y: 64, z: 0 },
      { type: 'wait', durationMs: duration }
    ];
  }
};

module.exports = { TaskSystem, TaskPresets };

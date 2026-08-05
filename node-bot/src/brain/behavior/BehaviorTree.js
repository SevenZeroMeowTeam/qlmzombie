'use strict';

/**
 * 行为树框架 (Behavior Tree)
 *
 * 节点类型:
 *  - BTNode          抽象基类，返回 SUCCESS / FAILURE / RUNNING
 *  - Sequence        顺序节点: 全部成功才返回 SUCCESS，任一失败返回 FAILURE
 *  - Selector        选择节点: 任一成功返回 SUCCESS，全部失败返回 FAILURE
 *  - Condition       条件节点: 包装布尔判断
 *  - Action          动作节点: 执行具体行为
 *  - Inverter        装饰器: 反转子节点结果
 *  - Cooldown        装饰器: 冷却时间内直接返回 FAILURE
 *  - BehaviorTree    主类: 持有根节点，每 tick 驱动整棵树
 */

const STATUS = Object.freeze({
  SUCCESS: 'SUCCESS',
  FAILURE: 'FAILURE',
  RUNNING: 'RUNNING'
});

class BTNode {
  constructor() {}
  async tick(context) { return STATUS.FAILURE; }
  reset() {}
}

/** 顺序节点: 全部成功才 SUCCESS */
class Sequence extends BTNode {
  constructor(children = []) {
    super();
    this.children = children;
    this.currentIndex = 0;
  }
  async tick(ctx) {
    while (this.currentIndex < this.children.length) {
      const child = this.children[this.currentIndex];
      const status = await child.tick(ctx);
      if (status === STATUS.RUNNING) return STATUS.RUNNING;
      if (status === STATUS.FAILURE) {
        this.currentIndex = 0;
        return STATUS.FAILURE;
      }
      this.currentIndex++;
    }
    this.currentIndex = 0;
    return STATUS.SUCCESS;
  }
  reset() {
    this.currentIndex = 0;
    for (const c of this.children) c.reset();
  }
}

/** 选择节点: 任一成功就 SUCCESS */
class Selector extends BTNode {
  constructor(children = []) {
    super();
    this.children = children;
    this.currentIndex = 0;
  }
  async tick(ctx) {
    while (this.currentIndex < this.children.length) {
      const child = this.children[this.currentIndex];
      const status = await child.tick(ctx);
      if (status === STATUS.RUNNING) return STATUS.RUNNING;
      if (status === STATUS.SUCCESS) {
        this.currentIndex = 0;
        return STATUS.SUCCESS;
      }
      this.currentIndex++;
    }
    this.currentIndex = 0;
    return STATUS.FAILURE;
  }
  reset() {
    this.currentIndex = 0;
    for (const c of this.children) c.reset();
  }
}

/** 条件节点 */
class Condition extends BTNode {
  constructor(predicate) {
    super();
    this.predicate = predicate;
  }
  async tick(ctx) {
    try {
      return this.predicate(ctx) ? STATUS.SUCCESS : STATUS.FAILURE;
    } catch (e) {
      return STATUS.FAILURE;
    }
  }
}

/** 动作节点 */
class Action extends BTNode {
  constructor(fn) {
    super();
    this.fn = fn;
  }
  async tick(ctx) {
    try {
      const r = await this.fn(ctx);
      return r === false ? STATUS.FAILURE
        : (r === STATUS.RUNNING ? STATUS.RUNNING : STATUS.SUCCESS);
    } catch (e) {
      return STATUS.FAILURE;
    }
  }
}

/** 反转装饰器 */
class Inverter extends BTNode {
  constructor(child) {
    super();
    this.child = child;
  }
  async tick(ctx) {
    const s = await this.child.tick(ctx);
    if (s === STATUS.SUCCESS) return STATUS.FAILURE;
    if (s === STATUS.FAILURE) return STATUS.SUCCESS;
    return STATUS.RUNNING;
  }
  reset() { this.child.reset(); }
}

/** 冷却装饰器 */
class Cooldown extends BTNode {
  constructor(child, cooldownMs) {
    super();
    this.child = child;
    this.cooldownMs = cooldownMs;
    this.lastRun = 0;
  }
  async tick(ctx) {
    const now = Date.now();
    if (now - this.lastRun < this.cooldownMs) return STATUS.FAILURE;
    const s = await this.child.tick(ctx);
    if (s === STATUS.SUCCESS || s === STATUS.FAILURE) {
      this.lastRun = now;
    }
    return s;
  }
  reset() { this.child.reset(); this.lastRun = 0; }
}

/** 行为树主类 */
class BehaviorTree {
  constructor(root) {
    this.root = root;
    this.lastStatus = STATUS.RUNNING;
  }
  async tick(ctx) {
    if (this.lastStatus === STATUS.SUCCESS || this.lastStatus === STATUS.FAILURE) {
      this.root.reset();
    }
    this.lastStatus = await this.root.tick(ctx);
    return this.lastStatus;
  }
}

module.exports = {
  STATUS,
  BTNode,
  Sequence,
  Selector,
  Condition,
  Action,
  Inverter,
  Cooldown,
  BehaviorTree
};

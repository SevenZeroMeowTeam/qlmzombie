'use strict';

/**
 * 动作锁 ActionLock
 *
 * 解决 mineflayer 动作时序问题:
 *  - 挖方块需要时间、攻击有冷却，不能疯狂循环调用 dig/attack
 *  - 每个行为返回 Promise，等待动作完成再执行下一个
 *
 * 用法:
 *   const lock = new ActionLock();
 *   await lock.run('mine', async () => { await bot.dig(block); });
 *   // 同名动作在执行中会被拒绝
 */
class ActionLock {
  constructor() {
    /** 当前活跃的动作 key → 开始时间 */
    this.active = new Map();
    /** 默认超时（毫秒），超过自动释放 */
    this.defaultTimeoutMs = 10000;
  }

  /**
   * 执行一个动作（带锁）
   * @param {string} key 动作键（如 'mine', 'attack', 'move'）
   * @param {function} fn async 函数
   * @param {number} timeoutMs 超时自动释放
   * @returns {Promise<any>} 动作返回值；被拒绝时返回 undefined
   */
  async run(key, fn, timeoutMs = this.defaultTimeoutMs) {
    if (this.active.has(key)) {
      return undefined;
    }
    this.active.set(key, Date.now());

    // 超时兜底释放
    const timer = setTimeout(() => {
      if (this.active.has(key)) {
        this.active.delete(key);
      }
    }, timeoutMs);

    try {
      return await fn();
    } finally {
      clearTimeout(timer);
      this.active.delete(key);
    }
  }

  /** 是否有指定动作在执行 */
  isBusy(key) {
    return this.active.has(key);
  }

  /** 是否有任何动作在执行 */
  isAnyBusy() {
    return this.active.size > 0;
  }

  /** 强制释放所有锁（用于 bot 卡住时） */
  releaseAll() {
    this.active.clear();
  }

  /** 释放指定动作 */
  release(key) {
    this.active.delete(key);
  }
}

module.exports = { ActionLock };

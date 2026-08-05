'use strict';

/**
 * 执行层 Navigator
 *
 * 封装 mineflayer-pathfinder:
 *  - 配置 Movements（允许爬梯、跳坑、避开岩浆等）
 *  - 提供 goToPos(target) 的 Promise 接口
 *  - 自动处理 pathfinder 事件（到达、停止、错误）
 *
 * pathfinder 是 AI 移动的基石，提供 A* 寻路，自动绕障碍、跳方块。
 */
const { GoalBlock, GoalNear, GoalXZ, GoalY, GoalCompositeAny } =
  require('mineflayer-pathfinder').goals;

class Navigator {
  /**
   * @param {import('mineflayer').Bot} bot
   * @param {object} config
   */
  constructor(bot, config = {}) {
    this.bot = bot;
    this.config = config;
    this.pathfinder = null;
    this.movements = null;
    this.lastGoal = null;
    this.defaultTimeout = 30000; // 单次寻路最长 30 秒
    this.setup();
  }

  setup() {
    try {
      const pathfinder = require('mineflayer-pathfinder');
      this.pathfinder = this.bot.pathfinder || pathfinder.pathfinder;
      if (!this.bot.pathfinder) {
        this.bot.loadPlugin(pathfinder.pathfinder);
      }
      const Movements = pathfinder.Movements;
      this.movements = new Movements(this.bot, require('minecraft-data')(this.bot.version));

      // === Movements 参数配置 ===
      // 允许爬梯子、藤蔓
      this.movements.allowSprinting = true;
      this.movements.canSprint = true;
      // 允许跳坑（最多 3 格高度差）
      this.movements.allowParkour = true;
      this.movements.allowFreeClearance = true;
      // 禁止踩危险方块
      this.movements.blocksCantBreak = new Set([
        'bedrock', 'barrier', 'command_block', 'structure_block',
        'spawner', 'end_portal_frame', 'obsidian'
      ]);
      this.movements.blocksToAvoid = new Set([
        'lava', 'fire', 'soul_fire', 'cactus', 'wither_rose',
        'sweet_berry_bush', 'powder_snow', 'magma_block'
      ]);
      // 允许挖方块穿过（如果装备了镐）
      this.movements.allowEntityInteraction = true;
      // 跳水（落入水中）
      this.movements.allowLiquidClearance = false;
      // 最大路径节点（防卡顿）
      this.movements.maxPathLength = 256;
      // 寻路范围
      this.movements.searchRadius = 64;

      this.bot.pathfinder.setMovements(this.movements);
    } catch (e) {
      console.error('[Navigator] 初始化失败:', e.message);
    }
  }

  /** 走到指定方块坐标 */
  async goToBlock(x, y, z, options = {}) {
    const goal = new GoalBlock(x, y, z);
    return this.goto(goal, options);
  }

  /** 走到坐标附近（半径范围内即达成） */
  async goToNear(x, y, z, range = 2, options = {}) {
    const goal = new GoalNear(x, y, z, range);
    return this.goto(goal, options);
  }

  /** 走到指定 XZ 坐标（忽略 Y） */
  async goToXZ(x, z, options = {}) {
    const goal = new GoalXZ(x, z);
    return this.goto(goal, options);
  }

  /** 走到指定 Y 高度 */
  async goToY(y, options = {}) {
    const goal = new GoalY(y);
    return this.goto(goal, options);
  }

  /** 复合目标：满足任一即达成 */
  async gotoAny(goals, options = {}) {
    const goal = new GoalCompositeAny(goals);
    return this.goto(goal, options);
  }

  /** 底层: 执行一个 goal */
  async goto(goal, options = {}) {
    if (!this.bot.pathfinder) {
      return { ok: false, reason: 'pathfinder-not-loaded' };
    }
    const timeout = options.timeout || this.defaultTimeout;
    this.lastGoal = goal;

    return new Promise((resolve) => {
      let settled = false;
      const onGoalReached = () => {
        if (settled) return;
        settled = true;
        cleanup();
        resolve({ ok: true, reason: 'reached' });
      };
      const onPathStop = () => {
        if (settled) return;
        settled = true;
        cleanup();
        resolve({ ok: false, reason: 'stopped' });
      };
      const onPathNotFound = (err) => {
        if (settled) return;
        settled = true;
        cleanup();
        resolve({ ok: false, reason: 'no-path', error: err });
      };

      const timer = setTimeout(() => {
        if (settled) return;
        settled = true;
        cleanup();
        try { this.bot.pathfinder.setGoal(null); } catch (e) {}
        resolve({ ok: false, reason: 'timeout' });
      }, timeout);

      function cleanup() {
        clearTimeout(timer);
        this.bot.removeListener('goal_reached', onGoalReached);
        this.bot.removeListener('path_stop', onPathStop);
        this.bot.removeListener('path_found', onPathFound);
      }
      const onPathFound = () => {};
      const cleanupBound = cleanup.bind(this);

      this.bot.once('goal_reached', onGoalReached);
      this.bot.once('path_stop', onPathStop);
      this.bot.once('path_found', onPathFound);

      try {
        this.bot.pathfinder.setGoal(goal, !options.async);
      } catch (e) {
        if (!settled) {
          settled = true;
          cleanupBound();
          resolve({ ok: false, reason: 'set-goal-error', error: e });
        }
      }
    });
  }

  /** 停止当前寻路 */
  stop() {
    try {
      if (this.bot.pathfinder) this.bot.pathfinder.setGoal(null);
    } catch (e) {}
  }

  /** 是否正在寻路 */
  isMoving() {
    return this.bot.pathfinder && this.bot.pathfinder.isMining();
  }

  /** 是否正在挖方块（pathfinder 自动挖） */
  isMining() {
    return this.bot.pathfinder && this.bot.pathfinder.isMining();
  }

  /** 是否正在搭方块（pathfinder 自动搭） */
  isBuilding() {
    return this.bot.pathfinder && this.bot.pathfinder.isBuilding();
  }
}

module.exports = { Navigator };

'use strict';

/**
 * GOAP (Goal-Oriented Action Planning) 目标导向行动规划
 *
 * 核心思想:
 *  - 定义目标 (Goal): 期望的世界状态
 *  - 定义动作 (Action): 前置条件 + 效果 + 代价
 *  - 规划器 (Planner): A* 搜索从当前状态到达目标的最小代价动作序列
 *
 * 例如目标 "获得 10 块圆石":
 *   当前状态: { has_cobblestone: 0 }
 *   动作:
 *     - mine_stone: 前置 { near_stone: true } → 效果 { has_cobblestone: +1 }
 *     - goto_stone: 前置 {} → 效果 { near_stone: true }
 *   规划结果: [goto_stone, mine_stone × 10]
 */

/** GOAP 动作 */
class GoapAction {
  /**
   * @param {string} name
   * @param {object} opts
   * @param {function} opts.precondition (state) => bool，是否满足前置条件
   * @param {function} opts.effect (state) => state，应用效果后的新状态
   * @param {number} opts.cost 代价（默认 1）
   * @param {function} opts.execute async (ctx) => bool，实际执行
   */
  constructor(name, opts = {}) {
    this.name = name;
    this.precondition = opts.precondition || (() => true);
    this.effect = opts.effect || (s => s);
    this.cost = opts.cost || 1;
    this.execute = opts.execute || (async () => true);
  }
}

/** GOAP 目标 */
class GoapGoal {
  /**
   * @param {string} name
   * @param {function} opts.satisfied (state) => bool，目标达成条件
   * @param {number} opts.priority 优先级
   */
  constructor(name, opts = {}) {
    this.name = name;
    this.satisfied = opts.satisfied || (() => true);
    this.priority = opts.priority || 1;
  }
}

/** 状态工具 */
function stateKey(state) {
  const keys = Object.keys(state).sort();
  return keys.map(k => `${k}=${state[k]}`).join('|');
}

function cloneState(state) {
  return { ...state };
}

/** GOAP 规划器（A* 搜索） */
class GoapPlanner {
  /**
   * @param {GoapAction[]} actions
   * @param {number} maxDepth 最大规划深度
   */
  constructor(actions = [], maxDepth = 8) {
    this.actions = actions;
    this.maxDepth = maxDepth;
    this.maxVisitedStates = 500;
  }

  /**
   * 规划: 给定起始状态和目标，返回动作序列
   * @param {object} startState
   * @param {GoapGoal} goal
   * @returns {GoapAction[] | null}
   */
  plan(startState, goal) {
    if (goal.satisfied(startState)) return [];

    const start = {
      state: cloneState(startState),
      actions: [],
      cost: 0,
      depth: 0
    };

    // 优先队列: 按 cost 排序
    const open = [start];
    const visited = new Set();
    visited.add(stateKey(startState));

    let iterations = 0;
    while (open.length > 0 && iterations < this.maxVisitedStates) {
      iterations++;
      // 取代价最小的节点
      open.sort((a, b) => a.cost - b.cost);
      const current = open.shift();

      if (current.depth >= this.maxDepth) continue;

      for (const action of this.actions) {
        if (!action.precondition(current.state)) continue;

        const newState = action.effect(cloneState(current.state));
        const key = stateKey(newState);
        if (visited.has(key)) continue;
        visited.add(key);

        const newActions = [...current.actions, action];
        const newCost = current.cost + action.cost;

        if (goal.satisfied(newState)) {
          return newActions;
        }

        open.push({
          state: newState,
          actions: newActions,
          cost: newCost,
          depth: current.depth + 1
        });
      }
    }

    return null; // 无解
  }
}

module.exports = { GoapAction, GoapGoal, GoapPlanner, stateKey, cloneState };

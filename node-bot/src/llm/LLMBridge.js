'use strict';

/**
 * LLM 大模型桥接层
 *
 * 职责: 将玩家的自然语言指令翻译成 AI 可执行的任务序列（JSON）。
 * LLM 只负责「规划」，不直接输出 MC 动作。规划结果交给 TaskSystem 串行执行。
 *
 * 架构:
 *   玩家: "帮我建一座房子"
 *     → LLMBridge.planTask() 调用大模型
 *     → 大模型输出 JSON 任务数组
 *     → TaskSystem.enqueueChain(tasks) 串行执行
 *
 * 支持的 LLM 后端（均使用 OpenAI 兼容的 /v1/chat/completions 接口）:
 *   - Ollama (本地，默认): http://localhost:11434/v1/chat/completions
 *   - OpenAI / 兼容服务:   https://api.openai.com/v1/chat/completions
 *
 * 配置 (config.llm):
 *   {
 *     "enabled": true,
 *     "provider": "ollama",          // "ollama" | "openai"
 *     "apiUrl": "http://localhost:11434/v1/chat/completions",
 *     "apiKey": "ollama",
 *     "model": "qwen2.5-coder:1.5b",
 *     "temperature": 0.3,
 *     "timeout": 30000
 *   }
 */
const { getLogger } = require('../utils/logger');

/** 支持的任务类型 schema（用于系统提示词） */
const TASK_SCHEMA = [
  { type: 'mine', desc: '挖矿/采集方块', fields: 'targetBlocks(数组), count(数量)' },
  { type: 'collect', desc: '收集掉落物', fields: 'itemName(物品名), count(数量)' },
  { type: 'goto', desc: '移动到坐标', fields: 'x, y, z(整数坐标)' },
  { type: 'follow', desc: '跟随目标坐标', fields: 'target: {x, y, z}' },
  { type: 'craft', desc: '合成物品', fields: 'itemName(物品名), count(数量)' },
  { type: 'build', desc: '放置方块', fields: 'x, y, z(坐标), blockName(方块名)' },
  { type: 'wait', desc: '原地等待', fields: 'durationMs(毫秒)' }
];

class LLMBridge {
  /**
   * @param {object} config llm 配置段
   * @param {object} deps { logger } 可选
   */
  constructor(config = {}, deps = {}) {
    this.config = config;
    this.enabled = config.enabled !== false;
    this.provider = config.provider || 'ollama';
    this.apiUrl = config.apiUrl ||
      (this.provider === 'openai'
        ? 'https://api.openai.com/v1/chat/completions'
        : 'http://localhost:11434/v1/chat/completions');
    this.apiKey = config.apiKey || (this.provider === 'openai' ? '' : 'ollama');
    this.model = config.model || 'qwen2.5-coder:1.5b';
    this.temperature = config.temperature != null ? config.temperature : 0.3;
    this.timeout = config.timeout || 30000;
    this.log = deps.logger || getLogger({ logging: { level: 'info' } }).child({ module: 'LLM' });

    // 已知合法任务类型集合
    this.validTypes = new Set(TASK_SCHEMA.map(t => t.type));
  }

  /**
   * 构建系统提示词（告诉大模型如何输出任务）
   * @param {object} context { position, inventory, health, food }
   */
  buildSystemPrompt(context = {}) {
    const schemaText = TASK_SCHEMA.map(t =>
      `  - ${t.type}: ${t.desc} → 字段: ${t.fields}`
    ).join('\n');

    let contextText = '';
    if (context.position) {
      const p = context.position;
      contextText += `当前坐标: (${Math.floor(p.x)}, ${Math.floor(p.y)}, ${Math.floor(p.z)})\n`;
    }
    if (context.inventory && context.inventory.length > 0) {
      contextText += `背包物品: ${context.inventory.join(', ')}\n`;
    }
    if (context.health != null) {
      contextText += `生命值: ${context.health}/20, 饥饿值: ${context.food || 0}/20\n`;
    }

    return `你是 Minecraft AI 任务规划器。你的职责是将玩家的自然语言指令翻译成 AI 可执行的任务序列。

## 支持的任务类型
${schemaText}

## 输出规则
1. 只输出一个 JSON 数组，不要输出任何其他文字、解释或 markdown。
2. 每个元素是一个任务对象，必须包含 "type" 字段。
3. 方块名使用 minecraft 标识符（如 oak_log, stone, cobblestone, dirt, oak_planks）。
4. 合成配方遵循原版: oak_log → 4 oak_planks, 2 planks + null → 4 sticks 等。
5. 坐标必须是整数。
6. 任务按执行顺序排列，保持简单可执行。
7. 如需采集材料再合成，先 mine 再 craft。
8. 建造任务用 build 类型，逐方块放置。

## 输出格式示例
[{"type":"mine","targetBlocks":["oak_log"],"count":5},{"type":"craft","itemName":"oak_planks","count":4},{"type":"craft","itemName":"stick","count":4},{"type":"craft","itemName":"crafting_table","count":1}]

## 当前 AI 状态
${contextText || '（无上下文信息）'}`;
  }

  /**
   * 调用大模型，将自然语言指令翻译成任务序列
   * @param {string} naturalLanguage 玩家输入的自然语言
   * @param {object} context AI 当前状态（位置/背包/血量）
   * @returns {Promise<Array>} 任务数组，可直接入队 TaskSystem
   */
  async planTask(naturalLanguage, context = {}) {
    if (!this.enabled) {
      throw new Error('LLM 未启用（config.llm.enabled = false）');
    }

    const systemPrompt = this.buildSystemPrompt(context);
    this.log.info(`规划指令: "${naturalLanguage}"`);

    const rawResponse = await this.callLLM(systemPrompt, naturalLanguage);
    const tasks = this.parseTasks(rawResponse);

    if (tasks.length === 0) {
      throw new Error('大模型未返回有效任务');
    }

    this.log.info(`规划完成: ${tasks.length} 个任务 → ${tasks.map(t => t.type).join(' → ')}`);
    return tasks;
  }

  /**
   * 调用 LLM API（OpenAI 兼容格式）
   * @returns {Promise<string>} 大模型返回的文本内容
   */
  async callLLM(systemPrompt, userPrompt) {
    const body = JSON.stringify({
      model: this.model,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userPrompt }
      ],
      temperature: this.temperature,
      stream: false
    });

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeout);

    try {
      const headers = { 'Content-Type': 'application/json' };
      if (this.apiKey) {
        headers['Authorization'] = `Bearer ${this.apiKey}`;
      }

      const res = await fetch(this.apiUrl, {
        method: 'POST',
        headers,
        body,
        signal: controller.signal
      });

      if (!res.ok) {
        const errText = await res.text().catch(() => '');
        throw new Error(`LLM API 返回 ${res.status}: ${errText.substring(0, 200)}`);
      }

      const data = await res.json();
      const content = data.choices?.[0]?.message?.content;
      if (!content) {
        throw new Error('LLM 返回内容为空');
      }
      return content;
    } catch (e) {
      if (e.name === 'AbortError') {
        throw new Error(`LLM 请求超时 (${this.timeout}ms)`);
      }
      throw new Error(`LLM 请求失败: ${e.message}`);
    } finally {
      clearTimeout(timer);
    }
  }

  /**
   * 从大模型返回文本中解析任务数组
   * 处理多种格式: 纯 JSON / markdown 代码块 / 带包装文字
   * @param {string} raw
   * @returns {Array} 合法任务数组
   */
  parseTasks(raw) {
    if (!raw || typeof raw !== 'string') return [];

    // 1. 尝试提取 JSON 代码块 ```json ... ```
    let jsonStr = raw.trim();

    // 去除 markdown 代码块标记
    const codeBlockMatch = jsonStr.match(/```(?:json)?\s*([\s\S]*?)```/);
    if (codeBlockMatch) {
      jsonStr = codeBlockMatch[1].trim();
    }

    // 2. 尝试直接解析
    let parsed = null;
    try {
      parsed = JSON.parse(jsonStr);
    } catch (e) {
      // 3. 尝试提取第一个 [ 到最后一个 ] 之间的内容
      const start = jsonStr.indexOf('[');
      const end = jsonStr.lastIndexOf(']');
      if (start !== -1 && end !== -1 && end > start) {
        try {
          parsed = JSON.parse(jsonStr.substring(start, end + 1));
        } catch (e2) {
          // 4. 尝试提取 { "tasks": [...] } 包装
          const objStart = jsonStr.indexOf('{');
          const objEnd = jsonStr.lastIndexOf('}');
          if (objStart !== -1 && objEnd !== -1) {
            try {
              const obj = JSON.parse(jsonStr.substring(objStart, objEnd + 1));
              parsed = obj.tasks || obj.plan || obj.actions || null;
            } catch (e3) {
              parsed = null;
            }
          }
        }
      }
    }

    if (!parsed) {
      this.log.warn(`无法解析 LLM 输出为 JSON: ${raw.substring(0, 200)}`);
      return [];
    }

    // 确保是数组
    if (!Array.isArray(parsed)) {
      parsed = [parsed];
    }

    // 过滤: 只保留合法任务类型，补全缺失字段
    const tasks = [];
    for (const t of parsed) {
      if (!t || typeof t !== 'object' || !t.type) continue;
      if (!this.validTypes.has(t.type)) {
        this.log.debug(`忽略未知任务类型: ${t.type}`);
        continue;
      }
      // 克隆，避免修改原始对象
      tasks.push({ ...t });
    }

    return tasks;
  }
}

module.exports = { LLMBridge, TASK_SCHEMA };

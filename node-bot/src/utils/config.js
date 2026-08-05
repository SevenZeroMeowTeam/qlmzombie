'use strict';

/**
 * 配置加载器
 * 优先级: config.local.json > config.json > 默认值
 */
const fs = require('fs');
const path = require('path');

const DEFAULT_CONFIG = {
  host: '127.0.0.1',
  port: 25565,
  username: 'QLM_AI_Bot',
  version: '1.20.1',
  auth: 'offline',
  brain: { type: 'fsm', tickInterval: 5, homePos: { x: 0, y: 64, z: 0 } },
  sensor: { scanRadius: 32, hostileScanRadius: 24, refreshInterval: 20 },
  combat: { attackRange: 4.0, attackCooldownMs: 600, fleeHealthThreshold: 8, safeDistance: 24 },
  mining: { range: 16, maxDepth: 60, preferredOres: [] },
  inventory: { autoEat: true, autoTool: true, dropTrash: true, trashItems: [] },
  logging: { level: 'info', file: 'bot.log' },
  llm: {
    enabled: true,
    provider: 'ollama',
    apiUrl: 'http://localhost:11434/v1/chat/completions',
    apiKey: 'ollama',
    model: 'qwen2.5-coder:1.5b',
    temperature: 0.3,
    timeout: 30000
  }
};

function deepMerge(target, source) {
  if (!source || typeof source !== 'object') return target;
  const out = { ...target };
  for (const key of Object.keys(source)) {
    if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
      out[key] = deepMerge(target[key] || {}, source[key]);
    } else {
      out[key] = source[key];
    }
  }
  return out;
}

function loadConfig(configDir = __dirname) {
  const cfgPath = path.resolve(configDir, 'config.json');
  const localPath = path.resolve(configDir, 'config.local.json');

  let cfg = { ...DEFAULT_CONFIG };

  if (fs.existsSync(cfgPath)) {
    try {
      cfg = deepMerge(cfg, JSON.parse(fs.readFileSync(cfgPath, 'utf8')));
    } catch (e) {
      console.error(`[Config] 解析 config.json 失败: ${e.message}`);
    }
  }

  if (fs.existsSync(localPath)) {
    try {
      cfg = deepMerge(cfg, JSON.parse(fs.readFileSync(localPath, 'utf8')));
    } catch (e) {
      console.error(`[Config] 解析 config.local.json 失败: ${e.message}`);
    }
  }

  // 命令行参数覆盖: --brain fsm
  const args = process.argv.slice(2);
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--brain' && args[i + 1]) {
      cfg.brain.type = args[i + 1];
    }
    if (args[i] === '--host' && args[i + 1]) cfg.host = args[i + 1];
    if (args[i] === '--port' && args[i + 1]) cfg.port = parseInt(args[i + 1], 10);
    if (args[i] === '--username' && args[i + 1]) cfg.username = args[i + 1];
  }

  return cfg;
}

module.exports = { loadConfig, DEFAULT_CONFIG };

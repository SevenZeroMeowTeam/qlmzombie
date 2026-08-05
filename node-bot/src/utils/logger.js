'use strict';

/**
 * Logger — 基于 winston 的日志工具
 * 控制台彩色输出 + 文件轮转
 */
const winston = require('winston');
const path = require('path');
const fs = require('fs');

const LOG_DIR = path.resolve(__dirname, '../../logs');

let loggerInstance = null;

function ensureLogDir() {
  try {
    if (!fs.existsSync(LOG_DIR)) fs.mkdirSync(LOG_DIR, { recursive: true });
  } catch (e) {
    // 日志目录创建失败不阻塞启动
  }
}

function buildLogger(level = 'info', file = 'bot.log') {
  ensureLogDir();
  return winston.createLogger({
    level,
    format: winston.format.combine(
      winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
      winston.format.errors({ stack: true }),
      winston.format.splat(),
      winston.format.printf(({ timestamp, level, message, stack }) => {
        const tag = `[${timestamp}] [${level.toUpperCase()}]`;
        return stack ? `${tag} ${message}\n${stack}` : `${tag} ${message}`;
      })
    ),
    transports: [
      new winston.transports.Console({
        format: winston.format.combine(
          winston.format.colorize({ all: true }),
          winston.format.printf(({ timestamp, level, message }) =>
            `[${timestamp}] [${level}] ${message}`)
        )
      }),
      new winston.transports.File({
        filename: path.join(LOG_DIR, file),
        maxsize: 5 * 1024 * 1024,
        maxFiles: 5
      })
    ]
  });
}

function getLogger(config = {}) {
  if (loggerInstance) return loggerInstance;
  const level = (config.logging && config.logging.level) || 'info';
  const file = (config.logging && config.logging.file) || 'bot.log';
  loggerInstance = buildLogger(level, file);
  return loggerInstance;
}

module.exports = { getLogger };

#!/usr/bin/env node
/* ============================================================
 * 七零喵僵尸末日生存 - SeverAdmin Web 后台
 * 功能：
 *   - 服务器介绍页 + 下载中心（单文件上限 500MB）
 *   - 后台管理：服务器配置(server.properties) / 游戏管理员(ops/whitelist/bans)
 *   - Docker 监控（docker stats/ps）或 Java 监控（systemd/进程/日志）
 *   - RCON 游戏内控制 + 静默重启（保存世界→公告→优雅重启，面板不中断）
 *   - 后台修改自动同步到 Docker/Java 部署
 * 域名：mc.sh197.dpdns.org
 * ============================================================ */
'use strict';

const express = require('express');
const jwt = require('jsonwebtoken');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const http = require('http');
const net = require('net');
const crypto = require('crypto');
const { execSync, exec } = require('child_process');

// ==================== 配置 ====================
const PORT = process.env.PORT || 3000;
const RCON_HOST = process.env.RCON_HOST || '127.0.0.1';
const RCON_PORT = parseInt(process.env.RCON_PORT || '25575', 10);
const RCON_PASSWORD = process.env.RCON_PASSWORD || '';
const MC_HOST = process.env.MC_HOST || '127.0.0.1';
const MC_PORT = parseInt(process.env.MC_PORT || '25565', 10);
const SERVER_NAME = process.env.SERVER_NAME || '七零喵僵尸末日生存';
const SERVER_ADDRESS = process.env.SERVER_ADDRESS || 'mc.sh197.dpdns.org';
const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(32).toString('hex');
const ADMIN_USER = process.env.ADMIN_USER || 'admin';
const ADMIN_PASS = process.env.ADMIN_PASS || 'admin';
const ADMIN_TOKEN = process.env.ADMIN_TOKEN || ''; // 备用 API Token
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'mcdata');
const DOWNLOADS_DIR = process.env.DOWNLOADS_DIR || path.join(__dirname, 'downloads');
const MAX_UPLOAD_BYTES = (parseInt(process.env.MAX_UPLOAD_MB || '500', 10)) * 1024 * 1024;
const DEPLOY_MODE = process.env.DEPLOY_MODE || 'auto'; // docker | java | auto
const MC_CONTAINER = process.env.MC_CONTAINER || 'qlm-minecraft';
const MC_SERVICE = process.env.MC_SERVICE || 'qlm-minecraft';
const DOCKER_SOCKET = '/var/run/docker.sock';
const RESTART_DELAY_SECONDS = parseInt(process.env.RESTART_DELAY_SECONDS || '10', 10);

for (const d of [DATA_DIR, DOWNLOADS_DIR]) {
  try { fs.mkdirSync(d, { recursive: true }); } catch (e) { console.error('[init] mkdir fail:', d, e.message); }
}

// ==================== 工具函数 ====================
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function detectDeployMode() {
  if (DEPLOY_MODE !== 'auto') return DEPLOY_MODE;
  // web 容器内通常没有 docker CLI，优先用 docker.sock 探测
  if (fs.existsSync(DOCKER_SOCKET)) return 'docker';
  try {
    execSync('docker ps >/dev/null 2>&1');
    return 'docker';
  } catch {
    try {
      execSync('systemctl is-system-running >/dev/null 2>&1');
      return 'java';
    } catch { return 'java'; }
  }
}

function checkTcpOpen(host, port, timeoutMs = 3000) {
  return new Promise((resolve) => {
    const sock = net.connect({ host, port, timeout: timeoutMs });
    sock.once('connect', () => { sock.destroy(); resolve(true); });
    sock.once('error', () => { sock.destroy(); resolve(false); });
    sock.once('timeout', () => { sock.destroy(); resolve(false); });
  });
}

// 实测 MC 延迟：完整走一遍状态握手（TCP 连接 + Status Request/Response），
// 返回往返耗时（ms）；服务器不可达时返回 null。
function measureMcLatency(timeoutMs = 4000) {
  return new Promise((resolve) => {
    const start = Date.now();
    const sock = net.connect({ host: MC_HOST, port: MC_PORT, timeout: timeoutMs });
    const timer = setTimeout(() => { sock.destroy(); resolve(null); }, timeoutMs);
    let done = false;
    const finish = (v) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      sock.destroy();
      resolve(v);
    };
    sock.once('connect', () => {
      try {
        const hostLen = Buffer.byteLength(MC_HOST, 'utf8');
        const handshake = Buffer.alloc(1 + 2 + 1 + hostLen + 2 + 1);
        handshake[0] = 0x00; // packet id
        handshake.writeUInt16BE(754, 1);   // protocol version
        handshake.writeUInt8(hostLen, 3);  // host 长度
        handshake.write(MC_HOST, 4, 'utf8');
        handshake.writeUInt16BE(MC_PORT, 4 + hostLen);
        handshake.writeUInt8(2, 6 + hostLen); // next state: status
        sock.write(handshake);
        sock.write(Buffer.from([0x01, 0x00])); // status request
      } catch { finish(null); }
    });
    sock.once('data', () => finish(Date.now() - start));
    sock.once('error', () => finish(null));
    sock.once('timeout', () => finish(null));
  });
}

// ---------- 性能监控（TPS/MSPT/内存/人数 来自模组 qlm-metrics.json，延迟实测） ----------
const METRICS_HISTORY = [];
const METRICS_MAX_POINTS = 120; // 2 秒一个点，约 4 分钟窗口
const METRICS_FILE = path.join(DATA_DIR, 'qlm-metrics.json');

function readModMetricsFile() {
  try {
    if (!fs.existsSync(METRICS_FILE)) return null;
    return JSON.parse(fs.readFileSync(METRICS_FILE, 'utf-8'));
  } catch { return null; }
}

async function sampleMetrics() {
  const mod = readModMetricsFile();
  const points = mod && Array.isArray(mod.points) ? mod.points : [];
  const latest = points.length ? points[points.length - 1] : null;
  const latency = await measureMcLatency();
  METRICS_HISTORY.push({
    t: Date.now(),
    tps: latest && latest.tps != null ? latest.tps : null,
    mspt: latest && latest.mspt != null ? latest.mspt : null,
    players: latest && latest.players != null ? latest.players : null,
    memUsedMB: latest && latest.memUsedMB != null ? latest.memUsedMB : null,
    memMaxMB: latest && latest.memMaxMB != null ? latest.memMaxMB : null,
    latency,
  });
  if (METRICS_HISTORY.length > METRICS_MAX_POINTS) METRICS_HISTORY.shift();
}
setInterval(() => { sampleMetrics().catch(() => {}); }, 2000);

// ---------- RCON 客户端（MC 控制协议） ----------
class RconClient {
  constructor(host, port, password, timeoutMs = 5000) {
    this.host = host; this.port = port; this.password = password; this.timeoutMs = timeoutMs;
  }
  async _request(type, id, body) {
    return new Promise((resolve, reject) => {
      const sock = net.connect({ host: this.host, port: this.port, timeout: this.timeoutMs });
      const timer = setTimeout(() => { sock.destroy(); reject(new Error('RCON 超时')); }, this.timeoutMs);
      let buf = Buffer.alloc(0);
      sock.on('connect', () => {
        const payload = Buffer.from(this.password, 'utf8');
        const len = Buffer.alloc(4); len.writeInt32LE(10 + payload.length, 0);
        const reqId = Buffer.alloc(4); reqId.writeInt32LE(id, 0);
        const type = Buffer.alloc(4); type.writeInt32LE(3, 0); // SERVERDATA_AUTH
        sock.write(Buffer.concat([len, reqId, type, payload, Buffer.from([0, 0])]));
      });
      sock.on('data', (chunk) => {
        buf = Buffer.concat([buf, chunk]);
        if (buf.length < 4) return;
        const total = buf.readInt32LE(0) + 4;
        if (buf.length < total) return;
        const body_buf = buf.subarray(4, total);
        const rid = body_buf.readInt32LE(0);
        const t = body_buf.readInt32LE(4);
        const data = body_buf.subarray(8, total - 2).toString('utf8');
        buf = buf.subarray(total);
        clearTimeout(timer); sock.destroy();
        if (type === 2 && rid === -1) return reject(new Error('RCON 认证失败'));
        if (t === 2 && rid === id) return resolve(data);
        resolve(data);
      });
      sock.on('error', (e) => { clearTimeout(timer); reject(e); });
      sock.on('timeout', () => { clearTimeout(timer); sock.destroy(); reject(new Error('RCON 超时')); });
    });
  }
  async auth() {
    // 登录请求 id=1；随后再发一次用于验证（部分服务端需要）
    await this._request(3, 1, this.password);
    return true;
  }
  async send(command) {
    await this.auth();
    return this._request(2, ++this._seq || 10, command);
  }
}
RconClient.prototype._seq = 0;

async function rconCommand(command) {
  if (!RCON_PASSWORD) throw new Error('RCON 密码未配置');
  const client = new RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD);
  return client.send(command);
}

// ---------- server.properties 读写 ----------
function parseServerProperties() {
  const p = path.join(DATA_DIR, 'server.properties');
  if (!fs.existsSync(p)) return {};
  const props = {};
  for (const line of fs.readFileSync(p, 'utf-8').split(/\r?\n/)) {
    const s = line.trim();
    if (!s || s.startsWith('#')) continue;
    const idx = s.indexOf('=');
    if (idx < 0) continue;
    props[s.slice(0, idx).trim()] = s.slice(idx + 1).trim();
  }
  return props;
}

function writeServerProperties(props) {
  const p = path.join(DATA_DIR, 'server.properties');
  const existing = fs.existsSync(p) ? fs.readFileSync(p, 'utf-8') : '';
  const lines = [];
  for (const line of existing.split(/\r?\n/)) {
    const s = line.trim();
    if (!s || s.startsWith('#')) { lines.push(line); continue; }
    const idx = s.indexOf('=');
    if (idx < 0) { lines.push(line); continue; }
    const key = s.slice(0, idx).trim();
    if (Object.prototype.hasOwnProperty.call(props, key)) {
      lines.push(`${key}=${props[key]}`);
      delete props[key];
    } else {
      lines.push(line);
    }
  }
  for (const [k, v] of Object.entries(props)) lines.push(`${k}=${v}`);
  fs.writeFileSync(p, lines.join('\n') + '\n', 'utf-8');
}

function readJsonArray(name) {
  const p = path.join(DATA_DIR, name);
  if (!fs.existsSync(p)) return [];
  try { return JSON.parse(fs.readFileSync(p, 'utf-8')); } catch { return []; }
}
function writeJsonArray(name, arr) {
  fs.writeFileSync(path.join(DATA_DIR, name), JSON.stringify(arr, null, 2), 'utf-8');
}

// ---------- Docker 监控（通过 docker.sock 只读） ----------
// 发送 Docker API 请求，返回 { status, headers, body(Buffer) }。
// 兼容 Content-Length 与 Transfer-Encoding: chunked（Docker daemon 常用 chunked，
// 若按原样返回字符串，JSON.parse 会失败 → 前台报“docker 返回格式异常: string”）。
function dockerRequestRaw(method, urlPath, body, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    if (!fs.existsSync(DOCKER_SOCKET)) return reject(new Error('docker.sock 不可用'));
    const sock = net.connect(DOCKER_SOCKET);
    // 超时保护：stats/exec 等接口可能长时间不响应，避免请求挂起
    const timer = setTimeout(() => { sock.destroy(); reject(new Error('docker API 超时')); }, timeoutMs);
    let raw = Buffer.alloc(0);
    sock.on('connect', () => {
      let head = `${method} ${urlPath} HTTP/1.1\r\nHost: docker\r\nConnection: close\r\n`;
      if (body != null) {
        const buf = Buffer.isBuffer(body) ? body : Buffer.from(String(body), 'utf8');
        head += `Content-Type: application/json\r\nContent-Length: ${buf.length}\r\n\r\n`;
        sock.write(Buffer.concat([Buffer.from(head, 'utf8'), buf]));
      } else {
        head += '\r\n';
        sock.write(head);
      }
    });
    sock.on('data', (c) => { raw = Buffer.concat([raw, c]); });
    sock.on('error', (e) => { clearTimeout(timer); reject(e); });
    sock.on('close', () => {
      clearTimeout(timer);
      const hdrEnd = raw.indexOf('\r\n\r\n');
      if (hdrEnd < 0) return reject(new Error('docker 响应无效'));
      const headStr = raw.subarray(0, hdrEnd).toString('utf8');
      const status = parseInt(headStr.split(' ')[1] || '500', 10);
      let bodyBuf = raw.subarray(hdrEnd + 4);
      // Docker 部分接口（logs/stats 等）使用 chunked 传输，需还原真实 body
      if (/transfer-encoding:\s*chunked/i.test(headStr)) bodyBuf = dechunkBody(bodyBuf);
      if (status >= 400) return reject(new Error(`docker API ${status}: ${bodyBuf.toString('utf8').slice(0, 300)}`));
      resolve({ status, headers: headStr, body: bodyBuf });
    });
  });
}

// 还原 HTTP chunked 编码（去掉每块的行长前缀与结尾 \r\n）
function dechunkBody(buf) {
  const parts = [];
  let i = 0;
  while (i < buf.length) {
    const lineEnd = buf.indexOf('\r\n', i);
    if (lineEnd < 0) break;
    const size = parseInt(buf.subarray(i, lineEnd).toString('utf8').split(';')[0].trim(), 16);
    if (!(size > 0)) break;
    const start = lineEnd + 2;
    const end = start + size;
    if (end > buf.length) { parts.push(buf.subarray(start)); break; }
    parts.push(buf.subarray(start, end));
    i = end + 2; // 跳过该 chunk 后的 \r\n
  }
  return parts.length ? Buffer.concat(parts) : buf;
}

// JSON 封装：空响应 / 非 JSON 返回明确错误，不再把原始字符串当成功结果返回
async function dockerRequest(method, urlPath, body, timeoutMs) {
  const r = await dockerRequestRaw(method, urlPath, body, timeoutMs);
  if (!r.body.length) throw new Error('docker API 返回空响应');
  const text = r.body.toString('utf8');
  try { return JSON.parse(text); } catch {
    throw new Error('docker API 返回非 JSON 响应: ' + text.slice(0, 200));
  }
}

// 无需响应体的操作（restart/stop/start 返回 204 空 body）
async function dockerAction(method, urlPath, timeoutMs = 15000) {
  await dockerRequestRaw(method, urlPath, null, timeoutMs);
}

// 按容器名查找容器 ID（兼容显式名 qlm-minecraft 与 compose 前缀名）
async function dockerContainerId(name) {
  const containers = await dockerRequest('GET', '/containers/json?all=1');
  if (!Array.isArray(containers)) throw new Error('docker 返回格式异常: ' + typeof containers);
  const hit = containers.find((c) => (c.Names || []).some((n) => n.replace(/^\//, '') === name));
  return hit ? hit.Id : null;
}

// 读取容器日志（Docker API 多路复用流：每条 8 字节帧头 [1B 类型 + 3B 填充 + 4B 大端长度] + 内容）
async function dockerContainerLogs(name, lines) {
  const id = await dockerContainerId(name);
  if (!id) throw new Error(`容器 ${name} 不存在`);
  const tail = Math.min(Math.max(parseInt(lines, 10) || 50, 1), 500);
  const r = await dockerRequestRaw('GET', `/containers/${id}/logs?stdout=1&stderr=1&tail=${tail}`, null, 15000);
  const b = r.body;
  const parts = [];
  let i = 0;
  while (i + 8 <= b.length) {
    const size = b.readUInt32BE(i + 4);
    const start = i + 8;
    const end = start + size;
    if (size <= 0 || end > b.length) break;
    parts.push(b.subarray(start, end));
    i = end;
  }
  if (parts.length) return Buffer.concat(parts).toString('utf8');
  return b.toString('utf8'); // 非多路复用（TTY）时直接当文本
}

// docker exec（依赖同步等后台命令）
async function dockerExec(name, cmd, timeoutMs = 60000) {
  const id = await dockerContainerId(name);
  if (!id) throw new Error(`容器 ${name} 不存在`);
  const created = await dockerRequest('POST', `/containers/${id}/exec`, JSON.stringify({ AttachStdout: true, AttachStderr: true, Cmd: cmd }), 15000);
  const execId = created && created.Id;
  if (!execId) throw new Error('docker exec 创建失败');
  const r = await dockerRequestRaw('POST', `/exec/${execId}/start`, '{"Detach":false,"Tty":false}', timeoutMs);
  return r.body.toString('utf8');
}

// 判断是否为本项目容器：兼容显式名（qlm-minecraft）与 compose 项目前缀名（minecraftsc-minecraft-1）
function isQlmContainer(c) {
  const raw = (c.Names?.[0] || '').replace(/^\//, '');
  const image = (c.Image || '').toLowerCase();
  const name = raw.replace(/^minecraftsc-/, '').replace(/-\d+$/, '');
  const bare = name.replace(/^qlm-/, '');
  if (['minecraft', 'web', 'nginx'].includes(bare)
      && (name.includes('qlm') || name.includes('minecraftsc') || raw === name)) {
    return true;
  }
  return image.includes('itzg/minecraft-server')
    || image.includes('minecraftsc-web')
    || image.includes('minecraftsc-nginx');
}

async function dockerStats() {
  // ?all=1：同时列出运行/重启/停止中的容器，崩溃循环时也能看到 MC 容器
  const containers = await dockerRequest('GET', '/containers/json?all=1');
  if (!Array.isArray(containers)) throw new Error('docker 返回格式异常: ' + typeof containers);
  const results = [];
  for (const c of containers) {
    if (!isQlmContainer(c)) continue;
    const info = await dockerRequest('GET', `/containers/${c.Id}/stats?stream=false`).catch(() => null);
    let cpu = null, mem = null;
    if (info) {
      const cpuDelta = info.cpu_stats.cpu_usage.total_usage - (info.precpu_stats.cpu_usage?.total_usage || info.cpu_stats.cpu_usage.total_usage);
      const sysDelta = info.cpu_stats.system_cpu_usage - (info.precpu_stats.system_cpu_usage || info.cpu_stats.system_cpu_usage);
      const online = info.cpu_stats.online_cpus || 1;
      if (sysDelta > 0) cpu = (cpuDelta / sysDelta) * online * 100;
      const memUsed = info.memory_stats.usage || 0;
      const memLimit = info.memory_stats.limit || 1;
      mem = { used: memUsed, limit: memLimit, pct: (memUsed / memLimit) * 100 };
    }
    results.push({
      name: c.Names?.[0]?.replace(/^\//, ''),
      image: c.Image,
      state: c.State,
      status: c.Status,
      created: c.Created,
      cpu: cpu != null ? Math.round(cpu * 10) / 10 : null,
      mem: mem ? { usedMB: Math.round(mem.used / 1048576), limitMB: Math.round(mem.limit / 1048576), pct: Math.round(mem.pct * 10) / 10 } : null,
      ports: (c.Ports || []).map((p) => `${p.IP || ''}:${p.PublicPort || ''}->${p.PrivatePort || ''}/${p.Type || ''}`),
    });
  }
  return results;
}

// ---------- Java（systemd）监控 ----------
function javaMonitor() {
  const out = {};
  try { out.service = execSync(`systemctl is-active ${MC_SERVICE} 2>/dev/null`).toString().trim(); } catch { out.service = 'inactive'; }
  try {
    const ps = execSync(`ps aux | grep -E 'java.*forge|java.*server.jar' | grep -v grep | head -5`).toString();
    out.process = ps.trim();
  } catch { out.process = ''; }
  try { out.uptime = execSync(`systemctl show ${MC_SERVICE} -p ActiveEnterTimestamp 2>/dev/null`).toString().trim(); } catch { out.uptime = ''; }
  try {
    out.logTail = execSync(`journalctl -u ${MC_SERVICE} -n 20 --no-pager 2>/dev/null`).toString();
  } catch { out.logTail = ''; }
  return out;
}

// ==================== Express ====================
const app = express();
app.use(express.json({ limit: '2mb' }));
app.use(express.urlencoded({ extended: true }));

// ---------- 认证 ----------
function signToken() {
  return jwt.sign({ user: ADMIN_USER, role: 'admin' }, JWT_SECRET, { expiresIn: '12h' });
}
function authMiddleware(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : (req.query.token || '');
  if (token && ADMIN_TOKEN && token === ADMIN_TOKEN) return next();
  if (!token) return res.status(401).json({ error: '未登录' });
  try { jwt.verify(token, JWT_SECRET); return next(); } catch { return res.status(401).json({ error: '登录已过期' }); }
}

// ==================== 公共路由 ====================
app.get('/health', (req, res) => res.json({ ok: true, name: SERVER_NAME, time: Date.now() }));

app.get('/api/info', (req, res) => {
  res.json({
    name: SERVER_NAME,
    address: SERVER_ADDRESS,
    port: MC_PORT,
    version: '1.20.1 Forge 47.4.22',
    description: process.env.SERVER_DESCRIPTION || '僵尸末日生存',
    domain: process.env.DOMAIN || 'mc.sh197.dpdns.org',
    features: [
      '动态难度：0-25天和平 / 26-50天简单 / 51-75天普通 / 76-100天困难 / 100天+锁定困难',
      '装备品质：劣质 → 一般 → 普通 → 精良 → 优秀 → 稀有 → 卓越 → 史诗 → 传说 → 神话',
      '神话品质：无视游戏规则，神话盔甲套装缺一不可',
      '合成随机品质 + Apotheosis 风格属性',
      '僵尸末日生存 · 口渴系统 · 空投 · 月亮事件'
    ],
    online: false,
  });
});

app.get('/api/status', async (req, res) => {
  try {
    const tcp = await checkTcpOpen(MC_HOST, MC_PORT, 3000);
    const rcon = await checkTcpOpen(RCON_HOST, RCON_PORT, 2000);
    let players = null, motd = null, version = null;
    if (tcp) {
      try {
        const info = await new Promise((resolve, reject) => {
          const sock = net.connect({ host: MC_HOST, port: MC_PORT, timeout: 4000 });
          const timer = setTimeout(() => { sock.destroy(); reject(new Error('timeout')); }, 4000);
          sock.on('connect', () => {
            // 修复：握手缓冲区需按 MC_HOST 字节长度动态分配，否则 offset 越界抛 RangeError
            // （该异常发生在 socket 事件回调，外层 try/catch 捕不到 → /api/status 永久挂起）
            const hostLen = Buffer.byteLength(MC_HOST, 'utf8');
            const handshake = Buffer.alloc(1 + 2 + 1 + hostLen + 2 + 1);
            handshake[0] = 0x00; // packet id
            handshake.writeUInt16BE(754, 1);   // protocol version
            handshake.writeUInt8(hostLen, 3);  // host 长度
            handshake.write(MC_HOST, 4, 'utf8');
            handshake.writeUInt16BE(MC_PORT, 4 + hostLen);
            handshake.writeUInt8(2, 6 + hostLen); // next state: status
            sock.write(handshake);
            sock.write(Buffer.from([0x01, 0x00])); // status request
          });
          sock.on('data', (d) => { clearTimeout(timer); sock.destroy(); resolve(d.toString('utf8')); });
          sock.on('error', reject);
        });
        const jsonStart = info.indexOf('{');
        if (jsonStart >= 0) {
          const parsed = JSON.parse(info.slice(jsonStart, info.lastIndexOf('}') + 1));
          players = parsed.players;
          motd = parsed.description?.text || JSON.stringify(parsed.description);
          version = parsed.version?.name;
        }
      } catch { /* 忽略 */ }
    }
    res.json({
      online: tcp,
      address: MC_PORT === 25565 ? SERVER_ADDRESS : `${SERVER_ADDRESS}:${MC_PORT}`,
      tcp25565: tcp,
      rcon25575: rcon,
      players,
      motd,
      version,
    });
  } catch (e) {
    // 兜底：接口异常也返回 JSON，前端不显示"无法连接"
    res.json({ online: false, address: MC_PORT === 25565 ? SERVER_ADDRESS : `${SERVER_ADDRESS}:${MC_PORT}`, tcp25565: false, rcon25575: false, players: null, motd: null, version: null, error: e.message });
  }
});

// ---- 性能监控数据（TPS/延迟 曲线） ----
app.get('/api/metrics', (req, res) => {
  res.json({
    history: METRICS_HISTORY,
    latest: METRICS_HISTORY[METRICS_HISTORY.length - 1] || null,
    maxPoints: METRICS_MAX_POINTS,
  });
});

// ==================== 下载中心（500MB 限制） ====================
const upload = multer({
  storage: multer.diskStorage({
    destination: (req, file, cb) => cb(null, DOWNLOADS_DIR),
    filename: (req, file, cb) => {
      const safe = file.originalname.replace(/[\\/:*?"<>|]/g, '_');
      cb(null, safe);
    },
  }),
  limits: { fileSize: MAX_UPLOAD_BYTES },
});

app.get('/api/downloads', (req, res) => {
  try {
    const files = fs.readdirSync(DOWNLOADS_DIR, { withFileTypes: true })
      .filter((e) => e.isFile() && !e.name.startsWith('.'))
      .map((e) => {
        const full = path.join(DOWNLOADS_DIR, e.name);
        const st = fs.statSync(full);
        return {
          name: e.name,
          size: st.size,
          sizeMB: Math.round((st.size / 1048576) * 100) / 100,
          mtime: st.mtimeMs,
        };
      })
      .sort((a, b) => b.mtime - a.mtime);
    res.json({ files, maxUploadMB: MAX_UPLOAD_BYTES / 1048576 });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/downloads', authMiddleware, upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: '未收到文件' });
  if (req.file.size > MAX_UPLOAD_BYTES) {
    fs.unlink(req.file.path, () => {});
    return res.status(413).json({ error: `文件超过 ${MAX_UPLOAD_BYTES / 1048576}MB 上限` });
  }
  res.json({ success: true, file: { name: req.file.filename, size: req.file.size } });
});

app.post('/api/downloads/:name/delete', authMiddleware, (req, res) => {
  const name = path.basename(req.params.name);
  const full = path.join(DOWNLOADS_DIR, name);
  if (!path.resolve(full).startsWith(path.resolve(DOWNLOADS_DIR))) return res.status(400).json({ error: '非法路径' });
  try { fs.unlinkSync(full); res.json({ success: true }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/downloads/:name', (req, res) => {
  const name = path.basename(req.params.name);
  const full = path.join(DOWNLOADS_DIR, name);
  if (!path.resolve(full).startsWith(path.resolve(DOWNLOADS_DIR)) || !fs.existsSync(full)) {
    return res.status(404).json({ error: '文件不存在' });
  }
  const st = fs.statSync(full);
  if (st.size > MAX_UPLOAD_BYTES) return res.status(413).json({ error: '文件超过下载限制' });
  res.download(full, name);
});

// ==================== 后台管理 API（需认证） ====================
app.post('/api/login', (req, res) => {
  const { username, password } = req.body || {};
  if (username === ADMIN_USER && password === ADMIN_PASS) {
    return res.json({ success: true, token: signToken() });
  }
  res.status(401).json({ error: '用户名或密码错误' });
});

app.get('/api/admin/me', authMiddleware, (req, res) => {
  res.json({ user: ADMIN_USER, deployMode: detectDeployMode(), server: SERVER_NAME });
});

// ---- 服务器配置 ----
app.get('/api/admin/properties', authMiddleware, (req, res) => {
  res.json({ success: true, properties: parseServerProperties() });
});

app.post('/api/admin/properties', authMiddleware, async (req, res) => {
  const { properties } = req.body || {};
  if (!properties || typeof properties !== 'object') return res.status(400).json({ error: '缺少 properties' });
  try {
    writeServerProperties(properties);
    // 同步：java 模式下属性文件即 /data 同路径（web 已挂载 mc-data）；docker 模式下由挂载自动同步
    // 再通过 RCON 刷新部分配置（如 view-distance 需重启生效，提示用户）
    res.json({ success: true, note: '已写入 server.properties，部分配置需重启服务器生效' });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ---- 游戏管理员 ----
app.get('/api/admin/ops', authMiddleware, (req, res) => res.json({ ops: readJsonArray('ops.json') }));
app.post('/api/admin/ops', authMiddleware, async (req, res) => {
  const { uuid, name, level } = req.body || {};
  if (!name) return res.status(400).json({ error: '缺少玩家名' });
  const ops = readJsonArray('ops.json');
  if (!ops.find((o) => (o.uuid || o.name) === (uuid || name))) {
    ops.push({ uuid: uuid || '', name, level: parseInt(level || '4', 10), bypassesPlayerLimit: false });
    writeJsonArray('ops.json', ops);
  }
  try { await rconCommand(`op ${name}`); } catch (e) { console.error('[op] rcon:', e.message); }
  res.json({ success: true, ops });
});
app.post('/api/admin/ops/remove', authMiddleware, async (req, res) => {
  const { name } = req.body || {};
  const ops = readJsonArray('ops.json').filter((o) => o.name !== name);
  writeJsonArray('ops.json', ops);
  try { await rconCommand(`deop ${name}`); } catch (e) {}
  res.json({ success: true, ops });
});

app.get('/api/admin/whitelist', authMiddleware, (req, res) => res.json({ whitelist: readJsonArray('whitelist.json') }));
app.post('/api/admin/whitelist', authMiddleware, async (req, res) => {
  const { uuid, name } = req.body || {};
  if (!name) return res.status(400).json({ error: '缺少玩家名' });
  const wl = readJsonArray('whitelist.json');
  if (!wl.find((o) => (o.uuid || o.name) === (uuid || name))) {
    wl.push({ uuid: uuid || '', name });
    writeJsonArray('whitelist.json', wl);
  }
  try { await rconCommand(`whitelist add ${name}`); } catch (e) {}
  res.json({ success: true, whitelist: wl });
});
app.post('/api/admin/whitelist/remove', authMiddleware, async (req, res) => {
  const { name } = req.body || {};
  const wl = readJsonArray('whitelist.json').filter((o) => o.name !== name);
  writeJsonArray('whitelist.json', wl);
  try { await rconCommand(`whitelist remove ${name}`); } catch (e) {}
  res.json({ success: true, whitelist: wl });
});

app.get('/api/admin/bans', authMiddleware, (req, res) => res.json({ bans: readJsonArray('banned-players.json') }));
app.post('/api/admin/bans', authMiddleware, async (req, res) => {
  const { name, reason } = req.body || {};
  if (!name) return res.status(400).json({ error: '缺少玩家名' });
  const bans = readJsonArray('banned-players.json');
  bans.push({ uuid: '', name, created: new Date().toISOString(), source: 'SeverAdmin', expires: 'forever', reason: reason || '由管理员封禁' });
  writeJsonArray('banned-players.json', bans);
  try { await rconCommand(`ban ${name} ${reason || ''}`); } catch (e) {}
  res.json({ success: true, bans });
});
app.post('/api/admin/bans/remove', authMiddleware, async (req, res) => {
  const { name } = req.body || {};
  const bans = readJsonArray('banned-players.json').filter((o) => o.name !== name);
  writeJsonArray('banned-players.json', bans);
  try { await rconCommand(`pardon ${name}`); } catch (e) {}
  res.json({ success: true, bans });
});

// ---- RCON 游戏内命令 ----
app.post('/api/admin/console', authMiddleware, async (req, res) => {
  const { command } = req.body || {};
  if (!command) return res.status(400).json({ error: '缺少命令' });
  try {
    const output = await rconCommand(command);
    res.json({ success: true, output });
  } catch (e) {
    res.status(502).json({ error: `RCON 执行失败: ${e.message}` });
  }
});

// ---- 监控 ----
app.get('/api/admin/monitor', authMiddleware, async (req, res) => {
  const mode = detectDeployMode();
  const tcp = await checkTcpOpen(MC_HOST, MC_PORT, 3000);
  const rcon = await checkTcpOpen(RCON_HOST, RCON_PORT, 2000);
  let docker = null, java = null, uptime = null;
  if (mode === 'docker') {
    docker = await dockerStats().catch((e) => ({ error: e.message }));
  } else {
    java = javaMonitor();
  }
  // 系统信息
  try {
    const info = execSync('uptime 2>/dev/null').toString().trim();
    uptime = info;
  } catch { uptime = ''; }
  res.json({ mode, tcp, rcon, docker, java, uptime });
});

app.get('/api/admin/logs', authMiddleware, async (req, res) => {
  const mode = detectDeployMode();
  try {
    if (mode === 'docker') {
      // web 容器内无 docker CLI，走 docker.sock 的 Docker API 拉取日志
      const out = await dockerContainerLogs(MC_CONTAINER, req.query.lines);
      res.json({ success: true, logs: out });
    } else {
      const out = execSync(`journalctl -u ${MC_SERVICE} -n ${Math.min(parseInt(req.query.lines || '50', 10), 500)} --no-pager 2>&1`).toString();
      res.json({ success: true, logs: out });
    }
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ---- 静默重启（不影响面板，优雅重启 MC） ----
app.post('/api/admin/restart', authMiddleware, async (req, res) => {
  const mode = detectDeployMode();
  const { delay } = req.body || {};
  const waitSec = Math.min(Math.max(parseInt(delay || RESTART_DELAY_SECONDS, 10), 3), 60);
  try {
    // 1. 公告 + 保存世界（通过 RCON）
    try {
      await rconCommand(`say [SeverAdmin] 服务器将在 ${waitSec} 秒后重启，请及时保存并退出！`);
      await rconCommand(`save-all`);
    } catch (e) { console.error('[restart] rcon warn:', e.message); }
    // 2. 立即返回（面板不阻塞），后台等待后执行重启
    res.json({ success: true, mode, message: `已安排 ${waitSec} 秒后静默重启（先公告并保存世界）` });
    setTimeout(() => { doRestart(mode); }, waitSec * 1000);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

async function doRestart(mode) {
  try {
    // 再次保存并公告
    try { await rconCommand('say [SeverAdmin] 正在重启服务器...'); } catch {}
    try { await rconCommand('save-all'); } catch {}
    if (mode === 'docker') {
      const id = await dockerContainerId(MC_CONTAINER);
      if (!id) throw new Error(`容器 ${MC_CONTAINER} 不存在`);
      await dockerAction('POST', `/containers/${id}/restart`);
    } else {
      execSync(`systemctl restart ${MC_SERVICE}`, { stdio: 'inherit' });
    }
    console.log(`[restart] ${new Date().toISOString()} 服务器已通过 ${mode} 重启`);
  } catch (e) {
    console.error('[restart] 失败:', e.message);
  }
}

app.post('/api/admin/stop', authMiddleware, async (req, res) => {
  const mode = detectDeployMode();
  try {
    try { await rconCommand('say [SeverAdmin] 服务器即将停止，请退出！'); await rconCommand('save-all'); } catch {}
    setTimeout(async () => {
      try {
        if (mode === 'docker') {
          const id = await dockerContainerId(MC_CONTAINER);
          if (id) await dockerAction('POST', `/containers/${id}/stop`);
        } else {
          execSync(`systemctl stop ${MC_SERVICE}`, { stdio: 'inherit' });
        }
      } catch (e) { console.error('[stop] 失败:', e.message); }
    }, 3000);
    res.json({ success: true, message: '已安排停止服务器（3 秒后）' });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/admin/start', authMiddleware, async (req, res) => {
  const mode = detectDeployMode();
  try {
    if (mode === 'docker') {
      const id = await dockerContainerId(MC_CONTAINER);
      if (!id) throw new Error(`容器 ${MC_CONTAINER} 不存在`);
      await dockerAction('POST', `/containers/${id}/start`);
    } else {
      execSync(`systemctl start ${MC_SERVICE}`, { stdio: 'inherit' });
    }
    res.json({ success: true, message: '服务器启动中...' });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ---- 依赖同步（自动释放模组，防止玩家无法加入） ----
app.post('/api/admin/sync-mods', authMiddleware, async (req, res) => {
  try {
    if (detectDeployMode() === 'docker') {
      await dockerExec(MC_CONTAINER, ['bash', '/entrypoint-wrapper.sh']).catch(() => {});
    } else {
      execSync('bash /opt/qlm/scripts/sync-mods.sh', { stdio: 'inherit' });
    }
    res.json({ success: true, message: '依赖同步完成' });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ==================== 前端静态页面 ====================
const PUBLIC_DIR = path.join(__dirname, 'public');
app.use(express.static(PUBLIC_DIR, { index: 'index.html', maxAge: '1h' }));
app.get('/admin', (req, res) => res.sendFile(path.join(PUBLIC_DIR, 'admin.html')));
app.get('/downloads', (req, res) => res.sendFile(path.join(PUBLIC_DIR, 'downloads.html')));

// ==================== 启动 ====================
app.listen(PORT, '0.0.0.0', () => {
  console.log(`[SeverAdmin] 后台已启动: http://0.0.0.0:${PORT}`);
  console.log(`[SeverAdmin] 站点: http://${process.env.DOMAIN || 'mc.sh197.dpdns.org'}`);
  console.log(`[SeverAdmin] 部署模式: ${detectDeployMode()}`);
  console.log(`[SeverAdmin] 下载中心上限: ${MAX_UPLOAD_BYTES / 1048576}MB`);
});

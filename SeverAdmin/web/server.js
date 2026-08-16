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
function dockerRequest(method, urlPath) {
  return new Promise((resolve, reject) => {
    if (!fs.existsSync(DOCKER_SOCKET)) return reject(new Error('docker.sock 不可用'));
    const sock = net.connect(DOCKER_SOCKET);
    const req = `${method} ${urlPath} HTTP/1.1\r\nHost: docker\r\nConnection: close\r\n\r\n`;
    let data = Buffer.alloc(0);
    sock.on('connect', () => sock.write(req));
    sock.on('data', (c) => { data = Buffer.concat([data, c]); });
    sock.on('error', reject);
    sock.on('close', () => {
      const hdrEnd = data.indexOf('\r\n\r\n');
      if (hdrEnd < 0) return reject(new Error('docker 响应无效'));
      const headers = data.subarray(0, hdrEnd).toString('utf8');
      const body = data.subarray(hdrEnd + 4).toString('utf8');
      const status = parseInt(headers.split(' ')[1] || '500', 10);
      if (status >= 400) return reject(new Error(`docker API ${status}: ${body.slice(0, 200)}`));
      try { resolve(JSON.parse(body)); } catch { resolve(body); }
    });
  });
}

async function dockerStats() {
  const containers = await dockerRequest('GET', '/containers/json');
  const results = [];
  for (const c of containers) {
    if (!['qlm-minecraft', 'qlm-web', 'qlm-nginx'].includes(c.Names?.[0]?.replace(/^\//, ''))) continue;
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
  const tcp = await checkTcpOpen(MC_HOST, MC_PORT, 3000);
  const rcon = await checkTcpOpen(RCON_HOST, RCON_PORT, 2000);
  let players = null, motd = null, version = null;
  if (tcp) {
    try {
      const info = await new Promise((resolve, reject) => {
        const sock = net.connect({ host: MC_HOST, port: MC_PORT, timeout: 4000 });
        const timer = setTimeout(() => { sock.destroy(); reject(new Error('timeout')); }, 4000);
        sock.on('connect', () => {
          const handshake = Buffer.alloc(1 + 2 + 1 + 2 + 1);
          handshake[0] = 0x00; // packet id
          handshake.writeUInt16BE(754, 1);
          handshake.writeUInt8(MC_HOST.length, 3);
          handshake.write(MC_HOST, 4, 'utf8');
          handshake.writeUInt16BE(MC_PORT, 4 + MC_HOST.length);
          handshake.writeUInt8(2, 6 + MC_HOST.length);
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
    address: `${SERVER_ADDRESS}:${MC_PORT}`,
    tcp25565: tcp,
    rcon25575: rcon,
    players,
    motd,
    version,
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

app.get('/api/admin/logs', authMiddleware, (req, res) => {
  const mode = detectDeployMode();
  try {
    if (mode === 'docker') {
      const out = execSync(`docker logs --tail ${Math.min(parseInt(req.query.lines || '50', 10), 500)} ${MC_CONTAINER} 2>&1`).toString();
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

function doRestart(mode) {
  try {
    // 再次保存并公告
    try { rconCommand('say [SeverAdmin] 正在重启服务器...'); } catch {}
    try { rconCommand('save-all'); } catch {}
    if (mode === 'docker') {
      execSync(`docker restart ${MC_CONTAINER}`, { stdio: 'inherit' });
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
    setTimeout(() => {
      try {
        if (mode === 'docker') execSync(`docker stop ${MC_CONTAINER}`, { stdio: 'inherit' });
        else execSync(`systemctl stop ${MC_SERVICE}`, { stdio: 'inherit' });
      } catch (e) { console.error('[stop] 失败:', e.message); }
    }, 3000);
    res.json({ success: true, message: '已安排停止服务器（3 秒后）' });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/admin/start', authMiddleware, async (req, res) => {
  const mode = detectDeployMode();
  try {
    if (mode === 'docker') execSync(`docker start ${MC_CONTAINER}`, { stdio: 'inherit' });
    else execSync(`systemctl start ${MC_SERVICE}`, { stdio: 'inherit' });
    res.json({ success: true, message: '服务器启动中...' });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ---- 依赖同步（自动释放模组，防止玩家无法加入） ----
app.post('/api/admin/sync-mods', authMiddleware, (req, res) => {
  try {
    if (detectDeployMode() === 'docker') {
      execSync(`docker exec ${MC_CONTAINER} bash /entrypoint-wrapper.sh >/dev/null 2>&1 || true`);
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

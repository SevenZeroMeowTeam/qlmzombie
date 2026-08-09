'use strict';

/**
 * Forge 1.20.1 (FML3) Handshake Handler for mineflayer
 *
 * Based on the PrismarineJS node-minecraft-protocol-forge FML3 implementation.
 *
 * FML3 协议格式 (1.18.2+):
 *
 * 1. 客户端在 handshake 包的 serverHost 中追加 \0FML3\0 标记 (含尾部 \0)
 * 2. 服务器发送 LoginPluginRequest (channel=fml:loginwrapper)
 * 3. loginwrapper 数据格式:
 *      [string: 内部通道名][VarInt: 数据长度][buffer: 握手数据]
 * 4. fml:handshake 握手数据格式:
 *      [VarInt: discriminator][消息数据]
 * 5. 客户端回复 LoginPluginResponse (understood=true, data=loginwrapper 包)
 *
 * fml:handshake discriminator 值:
 *   1  = ModList             (S→C: 服务器 mod 列表)
 *   2  = ModListReply        (C→S: 客户端 mod 列表回复)
 *   3  = ServerRegistry      (S→C: 注册表数据)
 *   4  = ConfigurationData   (S→C: 配置数据)
 *   5  = ModData             (S→C: mod 数据)
 *   6  = ChannelMismatchData (S→C: 通道不匹配)
 *   99 = Acknowledgement     (C→S: 确认)
 *
 * 握手流程:
 *   1. 服务器发送 ModData → 客户端回复 Acknowledgement
 *   2. 服务器发送 ModList → 客户端回复 ModListReply (回显 mod 列表)
 *   3. 握手完成，服务器发送 LoginSuccess
 */

const FML_LOGIN_WRAPPER_CHANNEL = 'fml:loginwrapper';
const FML_HANDSHAKE_CHANNEL = 'fml:handshake';

// fml:handshake discriminator 值
const MOD_LIST = 1;
const MOD_LIST_REPLY = 2;
const SERVER_REGISTRY = 3;
const CONFIGURATION_DATA = 4;
const MOD_DATA = 5;
const CHANNEL_MISMATCH = 6;
const ACKNOWLEDGE = 99;

// ======================== VarInt / String 工具函数 ========================

/** 编码 VarInt */
function writeVarInt(value) {
  const bytes = [];
  do {
    let temp = value & 0x7F;
    value >>>= 7;
    if (value !== 0) temp |= 0x80;
    bytes.push(temp);
  } while (value !== 0);
  return Buffer.from(bytes);
}

/** 读取 VarInt，返回 { value, bytesConsumed } */
function readVarInt(buf, offset) {
  let value = 0;
  let shift = 0;
  let pos = offset;
  while (pos < buf.length) {
    const byte = buf[pos];
    value |= (byte & 0x7F) << shift;
    pos++;
    if ((byte & 0x80) === 0) break;
    shift += 7;
    if (shift > 35) break;
  }
  return { value, bytesConsumed: pos - offset };
}

/** 编码字符串 (VarInt 长度 + UTF-8) */
function writeString(str) {
  const strBuf = Buffer.from(str, 'utf-8');
  return Buffer.concat([writeVarInt(strBuf.length), strBuf]);
}

/** 读取字符串，返回 { value, bytesConsumed } */
function readString(buf, offset) {
  const { value: len, bytesConsumed: lenBytes } = readVarInt(buf, offset);
  const strStart = offset + lenBytes;
  const str = buf.toString('utf-8', strStart, strStart + len);
  return { value: str, bytesConsumed: lenBytes + len };
}

// ======================== ForgeHandshake 类 ========================

class ForgeHandshake {
  constructor(bot, log) {
    this.bot = bot;
    this.log = log || console;
    this.serverMods = [];

    const client = bot._client;

    // 1. 设置 Forge 标记: \0FML3\0 (含尾部 \0!)
    //    setProtocol.js 会将其追加到 serverHost: host + '\0FML3\0'
    client.tagHost = '\0FML3\0';

    // 2. 移除 minecraft-protocol 内置的 login_plugin_request 处理器
    //    内置处理器会自动回复 understood=false，导致 Forge 服务器踢出客户端
    this._removeBuiltinLoginPluginHandler(client);

    // 3. 监听 login_plugin_request 事件
    client.on('login_plugin_request', (packet) => {
      this._handleLoginPluginRequest(packet);
    });

    this.log.info('[Forge] FML3 握手处理器已初始化 (tagHost=\\0FML3\\0)');
  }

  /**
   * 移除 minecraft-protocol 内置的 login_plugin_request 处理器
   * 内置处理器在 pluginChannels.js 中注册，会自动回复 understood=false
   */
  _removeBuiltinLoginPluginHandler(client) {
    const listeners = client.listeners('login_plugin_request');
    for (const listener of listeners) {
      client.removeListener('login_plugin_request', listener);
    }
  }

  /** 处理 LoginPluginRequest */
  _handleLoginPluginRequest(packet) {
    const { messageId, channel, data } = packet;

    this.log.info(`[Forge] 收到 LoginPluginRequest: messageId=${messageId}, channel=${channel}, dataLen=${data ? data.length : 0}`);

    if (channel !== FML_LOGIN_WRAPPER_CHANNEL) {
      // 非 Forge 通道，回复 not understood
      this.log.info(`[Forge] 非 Forge 通道 "${channel}"，回复 not understood`);
      this._sendLoginResponse(messageId, false, null);
      return;
    }

    if (!data || data.length === 0) {
      this.log.warn('[Forge] loginwrapper 数据为空');
      this._sendLoginResponse(messageId, false, null);
      return;
    }

    // 解析 fml:loginwrapper 数据
    // 格式: [string: 内部通道名][VarInt: 数据长度][buffer: 握手数据]
    let offset = 0;

    // 读取内部通道名 (如 "fml:handshake")
    const innerChannel = readString(data, offset);
    offset += innerChannel.bytesConsumed;

    // 读取数据长度 (VarInt) — 这是之前遗漏的关键步骤!
    const dataLenResult = readVarInt(data, offset);
    offset += dataLenResult.bytesConsumed;
    const handshakeDataLen = dataLenResult.value;

    // 提取握手数据 (handshakeDataLen 字节)
    const handshakeData = data.slice(offset, offset + handshakeDataLen);

    this.log.info(`[Forge] 内部通道: "${innerChannel.value}", 数据长度: ${handshakeDataLen}`);

    if (innerChannel.value !== FML_HANDSHAKE_CHANNEL) {
      // 非 fml:handshake 通道，回复 Acknowledgement (包装在 loginwrapper 中)
      this.log.info(`[Forge] 非握手通道 "${innerChannel.value}"，回复 Acknowledgement`);
      const ackPacket = this._buildHandshakePacket(ACKNOWLEDGE, Buffer.alloc(0));
      const loginWrapper = this._buildLoginWrapperPacket(FML_HANDSHAKE_CHANNEL, ackPacket);
      this._sendLoginResponse(messageId, true, loginWrapper);
      return;
    }

    // 解析 fml:handshake 数据
    // 格式: [VarInt: discriminator][消息数据]
    let hsOffset = 0;
    const discriminator = readVarInt(handshakeData, hsOffset);
    hsOffset += discriminator.bytesConsumed;
    const messageData = handshakeData.slice(hsOffset);

    this.log.info(`[Forge] 消息类型: ${discriminator.value} (${this._discriminatorName(discriminator.value)})`);

    switch (discriminator.value) {
      case MOD_LIST:
        this._handleModList(messageId, messageData);
        break;
      case MOD_DATA:
        // ModData 不需要 Ack 回复，只需告知服务器已收到 (understood=true, data=null)
        // 发送 Ack 会导致服务器警告 "Recieved unexpected index 0 in client reply"
        this.log.info(`[Forge] ModData 已接收，回复空响应`);
        this._sendLoginResponse(messageId, true, null);
        break;
      case SERVER_REGISTRY:
      case CONFIGURATION_DATA:
      case CHANNEL_MISMATCH:
        this._handleAckOnly(messageId, discriminator.value);
        break;
      default:
        this.log.warn(`[Forge] 未知消息类型 ${discriminator.value}，回复 Acknowledgement`);
        this._handleAckOnly(messageId, discriminator.value);
    }
  }

  /** 处理 ModList (discriminator=1) - 服务器发送 mod 列表，客户端回显 */
  _handleModList(messageId, data) {
    this.log.info('[Forge] 收到 ModList，解析并回显...');

    let offset = 0;
    const modNames = [];
    const channels = [];
    const registries = [];

    try {
      // 读取 modNames 数组
      const modCount = readVarInt(data, offset);
      offset += modCount.bytesConsumed;
      for (let i = 0; i < modCount.value; i++) {
        const name = readString(data, offset);
        offset += name.bytesConsumed;
        modNames.push(name.value);
      }

      // 读取 channels 数组 [{name, marker}]
      const channelCount = readVarInt(data, offset);
      offset += channelCount.bytesConsumed;
      for (let i = 0; i < channelCount.value; i++) {
        const name = readString(data, offset);
        offset += name.bytesConsumed;
        const marker = readString(data, offset);
        offset += marker.bytesConsumed;
        channels.push({ name: name.value, marker: marker.value });
      }

      // 读取 registries 数组 [{name}]
      const regCount = readVarInt(data, offset);
      offset += regCount.bytesConsumed;
      for (let i = 0; i < regCount.value; i++) {
        const name = readString(data, offset);
        offset += name.bytesConsumed;
        registries.push({ name: name.value });
      }

      this.log.info(`[Forge] 服务器: ${modCount.value} mods, ${channelCount.value} channels, ${regCount.value} registries`);
      if (modNames.length > 0) {
        this.log.info(`[Forge] Mods: ${modNames.slice(0, 15).join(', ')}${modNames.length > 15 ? '...' : ''}`);
      }
      this.serverMods = modNames;
    } catch (e) {
      this.log.warn(`[Forge] 解析 ModList 失败: ${e.message}，发送空回复`);
    }

    // 构建 ModListReply: 回显服务器的 mod 列表
    const replyData = this._buildModListReply(modNames, channels, registries);
    const handshakePacket = this._buildHandshakePacket(MOD_LIST_REPLY, replyData);
    const loginWrapper = this._buildLoginWrapperPacket(FML_HANDSHAKE_CHANNEL, handshakePacket);

    this.log.info('[Forge] 发送 ModListReply (回显服务器 mod 列表)');
    this._sendLoginResponse(messageId, true, loginWrapper);
  }

  /** 处理需要 Ack 的消息 (ModData, ServerRegistry, ConfigurationData, ChannelMismatch) */
  _handleAckOnly(messageId, discriminator) {
    this.log.info(`[Forge] 回复 Acknowledgement (type=${this._discriminatorName(discriminator)})`);

    const ackPacket = this._buildHandshakePacket(ACKNOWLEDGE, Buffer.alloc(0));
    const loginWrapper = this._buildLoginWrapperPacket(FML_HANDSHAKE_CHANNEL, ackPacket);
    this._sendLoginResponse(messageId, true, loginWrapper);
  }

  /**
   * 构建 ModListReply 数据
   * 格式: [VarInt: modNames count][string...][VarInt: channels count][{name,marker}...][VarInt: registries count][{name,marker}...]
   *
   * 回显策略 (与 PrismarineJS forgeHandshake3 一致):
   *   - modNames: 回显服务器的 modNames
   *   - channels: 回显服务器的 channels (排除 marker='FML3' 的)
   *   - registries: 回显服务器的 registries，marker='1.0'
   */
  _buildModListReply(modNames, channels, registries) {
    // modNames 数组 (回显)
    const modNamesBuf = Buffer.concat([
      writeVarInt(modNames.length),
      ...modNames.map(n => writeString(n))
    ]);

    // channels 数组 (回显，排除 FML3)
    const filteredChannels = channels.filter(c => c.marker !== 'FML3');
    const channelsBuf = Buffer.concat([
      writeVarInt(filteredChannels.length),
      ...filteredChannels.map(c =>
        Buffer.concat([writeString(c.name), writeString(c.marker)])
      )
    ]);

    // registries 数组 (回显，marker='1.0')
    const registriesBuf = Buffer.concat([
      writeVarInt(registries.length),
      ...registries.map(r =>
        Buffer.concat([writeString(r.name), writeString('1.0')])
      )
    ]);

    return Buffer.concat([modNamesBuf, channelsBuf, registriesBuf]);
  }

  /**
   * 构建 handshake 包
   * 格式: [VarInt: discriminator][消息数据]
   */
  _buildHandshakePacket(discriminator, messageData) {
    return Buffer.concat([
      writeVarInt(discriminator),
      messageData
    ]);
  }

  /**
   * 构建 loginwrapper 包
   * 格式: [string: 通道名][VarInt: 数据长度][buffer: 数据]
   */
  _buildLoginWrapperPacket(channel, handshakeData) {
    return Buffer.concat([
      writeString(channel),
      writeVarInt(handshakeData.length),
      handshakeData
    ]);
  }

  /** 发送 LoginPluginResponse */
  _sendLoginResponse(messageId, understood, data) {
    this.log.info(`[Forge] 发送 LoginPluginResponse: messageId=${messageId}, understood=${understood}, dataLen=${data ? data.length : 0}`);

    try {
      this.bot._client.write('login_plugin_response', {
        messageId: messageId,
        understood: understood,
        data: understood ? data : null
      });
    } catch (e) {
      this.log.error(`[Forge] 发送 LoginPluginResponse 失败: ${e.message}`);
    }
  }

  /** discriminator 名称 (用于日志) */
  _discriminatorName(d) {
    const names = {
      1: 'ModList', 2: 'ModListReply', 3: 'ServerRegistry',
      4: 'ConfigurationData', 5: 'ModData', 6: 'ChannelMismatchData',
      99: 'Acknowledgement'
    };
    return names[d] || `Unknown(${d})`;
  }
}

module.exports = { ForgeHandshake };

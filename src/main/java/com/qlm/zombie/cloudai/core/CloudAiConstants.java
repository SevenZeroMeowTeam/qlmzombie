package com.qlm.zombie.cloudai.core;

import com.qlm.zombie.QLMZombieMod;

/**
 * CloudAI Follower 常量配置
 * 修改 WS_URL / WS_TOKEN / 皮肤路径 请直接修改本类对应字段
 */
public class CloudAiConstants {

    public static final String MODULE_ID = "cloudai_follower";
    public static final String NAMESPACE = QLMZombieMod.MOD_ID; // 资源命名空间复用 qlmzombie

    // ============== WebSocket 配置 ==============
    // 修改服务器地址: 改这里 L16 (生产环境使用 wss:// TLS)
    public static final String WS_URL = "wss://player.qlm.org.cn/ws/cloudai";
    // 修改鉴权Token: 改这里 L18
    public static final String WS_TOKEN = "replace_your_cloudai_token_here";
    // 重连间隔(毫秒)
    public static final long WS_RECONNECT_INTERVAL_MS = 5000L;
    // 心跳间隔(毫秒)
    public static final long WS_HEARTBEAT_INTERVAL_MS = 15000L;
    // 连接超时(毫秒)
    public static final int WS_CONNECT_TIMEOUT_MS = 10000;

    // ============== AI 工作模式 ==============
    public static final class Modes {
        /** 跟随模式 - 仅跟随不攻击 */
        public static final String FOLLOW = "FOLLOW";
        /** 战斗模式 - 自动攻击敌对 */
        public static final String COMBAT = "COMBAT";
        /** 采集模式 - 自动拾取掉落物 */
        public static final String GATHER = "GATHER";
        /** 守卫模式 - 原地警戒不移动 */
        public static final String GUARD = "GUARD";

        public static final String[] ALL = {FOLLOW, COMBAT, GATHER, GUARD};
    }

    // ============== 皮肤配置 ==============
    // 皮肤路径说明：资源包中的皮肤文件，位于 assets/qlmzombie/textures/entity/cloudai/
    // 修改默认皮肤文件名: 改这里 L48
    public static final String DEFAULT_SKIN_PATH = "textures/entity/cloudai/default_skin.png";
    // 皮肤模型: slim(细臂) / default(粗臂)
    public static final String DEFAULT_SKIN_MODEL = "slim";
    // 外部皮肤 URL 列表（随机抽取使用）
    public static final String[] FALLBACK_SKINS = {
            "https://littleskin.cn/skinlib/show/1",
            "https://littleskin.cn/skinlib/show/2",
            "https://littleskin.cn/skinlib/show/3"
    };

    // ============== AI 参数 ==============
    /** 跟随距离(格) */
    public static final double FOLLOW_DISTANCE = 3.0D;
    /** 战斗搜索半径(格) */
    public static final double COMBAT_SEARCH_RADIUS = 16.0D;
    /** 采集搜索半径(格) */
    public static final double GATHER_SEARCH_RADIUS = 8.0D;
    /** 指令缓存 TTL (秒) */
    public static final int CMD_CACHE_TTL_SEC = 3;
    /** AI 攻击冷却 tick (20tick=1秒) */
    public static final int AI_ATTACK_COOLDOWN_TICK = 20;
    /** FakePlayer 移动速度倍率 */
    public static final float AI_SPEED_MULTIPLIER = 1.2F;

    // ============== LLM 大模型配置 (DeepSeek R1) ==============
    /** 选用的大模型名称 - 行为/决策/聊天接口均使用此模型 */
    public static final String LLM_MODEL = "deepseek-reasoner";
    /** 人类可读显示名 - 用于 tooltip / 聊天签名 */
    public static final String LLM_MODEL_DISPLAY = "DeepSeek-R1";
    /** LLM API 基础地址 (OpenAI 兼容协议, DeepSeek 官方端点) */
    public static final String LLM_API_BASE = "https://api.deepseek.com/v1";
    /** LLM API Key - 请改为您的真实 Key (仅服务端读取, 不进入客户端) */
    public static final String LLM_API_KEY = "sk-<paste_your_deepseek_api_key_here>";
    /** 聊天补全 endpoint (拼接到 LLM_API_BASE) */
    public static final String LLM_CHAT_PATH = "/chat/completions";
    /** 采样温度 (DeepSeek R1 推荐 0.7, 思考型对话) */
    public static final float LLM_TEMPERATURE = 0.7f;
    /** 最大输出 tokens (保护配额, R1 思考链较长建议 4096) */
    public static final int LLM_MAX_TOKENS = 4096;
    /** 单条请求超时 (毫秒) */
    public static final int LLM_TIMEOUT_MS = 60000;
    /** 是否将 R1 的 <think> 思考过程块保留在日志 (客户端仅显示最终回答) */
    public static final boolean LLM_LOG_THINKING_BLOCKS = true;
}

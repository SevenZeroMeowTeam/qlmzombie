package com.qlm.zombie.cloudai.event;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.cloudai.ai.AiEntityManager;
import com.qlm.zombie.cloudai.core.WsClient;
import com.qlm.zombie.cloudai.item.base.RegisterManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * CloudAI 模块事件总线订阅
 * - @Mod.EventBusSubscriber 自动注册到 Forge 事件总线（服务端 + 客户端通用）
 * 职责：
 *   ServerTickEvent    → 上传 WS / 驱动 AI tick
 *   PlayerLoggedInEvent → 初始化 / 提示 WS 连接状态
 *   RegisterEvents     → 绑定 DeferredRegister（显式调用 RegisterManager.bind）
 *   WsMessageEvent     → 下发 WS 指令缓存
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EventBusSubscriber {

    /**
     * Mod 总线生命周期事件：Mod 构造时调用此方法完成注册绑定
     * 由主类 QLMZombieMod 在构造函数内显式调用
     */
    public static void onModConstruct(FMLJavaModLoadingContext ctx) {
        // 绑定 DeferredRegister<Item> / DeferredRegister<CreativeModeTab>
        RegisterManager.bind(ctx.getModEventBus());
        // 监听 Mod 总线 commonSetup
        ctx.getModEventBus().addListener(EventBusSubscriber::onCommonSetup);
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 启动 WS 客户端（仅服务端逻辑，但初始化在 common 阶段完成）
            try {
                WsClient.getInstance().start();
                QLMZombieMod.LOGGER.info("[CloudAI] WsClient 启动完成，URL: {}",
                        com.qlm.zombie.cloudai.core.CloudAiConstants.WS_URL);
            } catch (Exception e) {
                QLMZombieMod.LOGGER.warn("[CloudAI] WsClient 启动失败（服务器未就绪时属正常）: {}", e.getMessage());
            }
        });
    }

    // ==================== Forge 事件 ====================

    /** 服务端 tick：驱动 AI 行为 + 定期上传 WS 状态 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.side != LogicalSide.SERVER) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        // 使用 level 的 gameTime 作为 tick 计数
        int tick = (int) (serverLevel.getGameTime() & Integer.MAX_VALUE);
        AiEntityManager.getInstance().onServerTick(serverLevel, tick);
    }

    /** 玩家登入：提示 CloudAI 模块状态 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() == null) return;
        if (event.getEntity().level().isClientSide) return;
        boolean ws = WsClient.getInstance().isConnected();
        String status = ws ? "§a已连接" : "§c未连接";
        event.getEntity().sendSystemMessage(Component.literal(
                "§b[CloudAI Follower] v1.0.0.beta.build.1 已加载 | WS: " + status));
        if (!ws) {
            event.getEntity().sendSystemMessage(Component.literal(
                    "§7[CloudAI] 提示: 未连接时物品功能仍可用，仅云端 AI 离线"));
        }
    }

    /** 玩家退出：清理绑定（可选，目前保留 AI 继续存在） */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 保留 AI 实体，不做清理（若需清理可启用下一行）
        // AiEntityManager.getInstance().removeAiForPlayer(event.getEntity());
    }

    /** WS 消息事件订阅（由 WsClient.WsListener 在 Forge 事件总线上发布） */
    @SubscribeEvent
    public static void onWsMessage(WsClient.WsMessageEvent event) {
        AiEntityManager.getInstance().handleWsMessage(event.message);
    }
}

package com.qlm.zombie.cloudai.client;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.cloudai.core.CloudAiConstants;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * CloudAI 客户端初始化（仅 Dist.CLIENT）
 * - 物品 Tooltip 颜色/装饰（可选扩展）
 * - 皮肤渲染注册：若需独立渲染器，在此绑定 EntityRenderers
 * - 模型加载：自定义 item model（默认使用 vanilla/generated）
 * 说明：
 * - 仅渲染相关代码放入此处（@OnlyIn / Dist.CLIENT）
 * - WS 通信仅服务端，此处不做任何 WS 调用
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CloudAiClientInit {

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // 1. 皮肤渲染器绑定：此处预留入口，若后续自定义 FakePlayerRenderer 可在此注入
        //    当前使用项目原有 FakePlayerEntityRenderer，无需重复注册
        // 2. 物品颜色（可选）：若需要为 AI_CALLER 等物品添加动态颜色，在此注册 ItemColor
        QLMZombieMod.LOGGER.info("[CloudAI] Client init finished (Dist.CLIENT) | skin model: {}",
                CloudAiConstants.DEFAULT_SKIN_MODEL);
    }

    /**
     * 预留：若后续需要 Forge 总线级客户端事件订阅，可在此恢复
     */

    /** 便捷方法：客户端发送聊天提示（由物品 use 在客户端分支调用） */
    @net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
    public static void sendClientTip(String msg) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
        } catch (Exception ignored) {}
    }
}

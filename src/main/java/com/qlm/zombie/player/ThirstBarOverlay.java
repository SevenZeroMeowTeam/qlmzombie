package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.feature.ThirstFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 经验条样式的口渴值显示：
 *  - 位于饱食度/血量图标行上方（screenHeight - 50）
 *  - 蓝色口渴条 + 像素风水滴图标（原版 Minecraft 风格）
 *  - 口渴值 0~20，值越低颜色越偏红（警告）
 *  - 纯代码绘制，不依赖资源包纹理
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, value = Dist.CLIENT)
public class ThirstBarOverlay {

    private static final ResourceLocation HOTBAR_OVERLAY =
            ResourceLocation.fromNamespaceAndPath("minecraft", "hotbar");

    // 缓存：避免每帧重新计算文本宽度
    private static int cachedThirst = -1;
    private static int cachedMaxThirst = -1;
    private static int cachedTextWidth = 0;
    private static String cachedText = "";

    @SubscribeEvent
    public static void onRenderThirstBar(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(HOTBAR_OVERLAY)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        Player player = mc.player;
        int thirst = ThirstFeature.getThirst(player);
        int maxThirst = ThirstFeature.getMaxThirst();

        GuiGraphics gui = event.getGuiGraphics();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 条的尺寸
        int barWidth = 182;
        int barHeight = 5;
        int posX = (screenWidth - barWidth) / 2;
        // 位于饱食度图标行上方（饱食度图标在 screenHeight - 39，口渴条放其上方）
        int posY = screenHeight - 50;

        float thirstPercent = maxThirst > 0 ? (float) thirst / maxThirst : 0.0f;
        int filledWidth = Math.round(barWidth * thirstPercent);

        // ── 1) 像素风水滴图标（原版 Minecraft 风格，6×8 像素） ──
        int iconX = posX - 16;
        int iconY = posY - 2;
        int dropColor = thirstPercent < 0.25f ? 0xFF_FF4040 : 0xFF_00B0FF;
        int dropDark  = thirstPercent < 0.25f ? 0xFF_CC2020 : 0xFF_0090CC;
        int dropLight = thirstPercent < 0.25f ? 0xFF_FF8080 : 0xFF_80D8FF;

        // 水滴形状（从上到下）：
        //   ..XX..   (顶部尖)
        //   .XXXX.
        //   XXXXXX   (最宽处)
        //   XXXXXX
        //   XXXXXX
        //   .XXXX.
        //   ..XX..
        //   ...X..   (底部尖)
        gui.fill(iconX + 2, iconY,     iconX + 4, iconY + 1, dropColor);   // 顶部
        gui.fill(iconX + 1, iconY + 1, iconX + 5, iconY + 2, dropColor);   // 上窄
        gui.fill(iconX,     iconY + 2, iconX + 6, iconY + 5, dropColor);   // 中宽
        gui.fill(iconX + 1, iconY + 5, iconX + 5, iconY + 6, dropColor);   // 下窄
        gui.fill(iconX + 2, iconY + 6, iconX + 4, iconY + 7, dropDark);    // 底部
        gui.fill(iconX + 3, iconY + 7, iconX + 4, iconY + 8, dropDark);    // 尖端
        // 高光（左上角白色亮点，增加 3D 质感）
        gui.fill(iconX + 1, iconY + 2, iconX + 2, iconY + 4, dropLight);

        // ── 2) 背景（黑色描边 + 深色底） ──
        gui.fill(posX - 1, posY - 1, posX + barWidth + 1, posY + barHeight + 1, 0xAA_000000);
        gui.fill(posX, posY, posX + barWidth, posY + barHeight, 0xFF_0A1A2A);

        // ── 3) 填充条（根据口渴值动态变色） ──
        int barColor;
        if (thirstPercent > 0.5f) {
            barColor = 0xFF_00B0FF;   // 亮蓝
        } else if (thirstPercent > 0.25f) {
            barColor = 0xFF_0090CC;   // 中蓝
        } else {
            boolean blink = (player.tickCount / 10) % 2 == 0;
            barColor = blink ? 0xFF_FF4040 : 0xFF_CC2020;
        }

        if (filledWidth > 0) {
            gui.fill(posX, posY, posX + filledWidth, posY + barHeight, barColor);
        }

        // ── 4) 顶部高光 + 底部暗边（3D 感） ──
        gui.fill(posX, posY, posX + barWidth, posY + 1, 0x44_FFFFFF);
        gui.fill(posX, posY + barHeight - 1, posX + barWidth, posY + barHeight, 0x44_000000);

        // ── 5) 数字标签（当前口渴 / 最大口渴） ──
        // 仅在口渴值变化时重新计算文本和宽度，减少每帧开销
        if (thirst != cachedThirst || maxThirst != cachedMaxThirst) {
            cachedText = thirst + " / " + maxThirst;
            cachedTextWidth = mc.font.width(cachedText);
            cachedThirst = thirst;
            cachedMaxThirst = maxThirst;
        }
        int textColor = thirstPercent < 0.25f ? 0xFF_FFD700 : 0xFF_FFFFFF;
        int textX = posX + (barWidth - cachedTextWidth) / 2;
        int textY = posY - 10;

        gui.fill(textX - 2, textY - 1, textX + cachedTextWidth + 2, textY + 8, 0x70_000000);
        gui.drawString(mc.font, cachedText, textX, textY, textColor, false);
    }
}

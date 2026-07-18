package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 经验条样式的简洁血量显示：
 *  - 屏幕底部中央绘制一条彩色 HP 条（类似经验条）
 *  - 可选择隐藏原版心形血量阵列
 *  - 不挡视野，不依赖资源包纹理
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, value = Dist.CLIENT)
public class HealthBarOverlay {

    // overlay 的 ResourceLocation 标识符（Forge 1.20.1 原版定义）
    // player_health: 用于隐藏原版心形血量
    // hotbar: 用于绘制血条（物品栏每帧都绘制，确保血条稳定不闪烁）
    private static final ResourceLocation PLAYER_HEALTH_OVERLAY = ResourceLocation.fromNamespaceAndPath("minecraft", "player_health");
    private static final ResourceLocation HOTBAR_OVERLAY = ResourceLocation.fromNamespaceAndPath("minecraft", "hotbar");

    @SubscribeEvent
    public static void onHideVanillaHealth(RenderGuiOverlayEvent.Pre event) {
        if (!QLMConfig.HIDE_VANILLA_HEALTH.get()) return;

        if (!event.getOverlay().id().equals(PLAYER_HEALTH_OVERLAY)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Player player = mc.player;
        if (player.isCreative() || player.isSpectator()) return;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHealthBar(RenderGuiOverlayEvent.Post event) {
        if (!QLMConfig.ENABLE_HEALTH_BAR.get()) return;

        if (!event.getOverlay().id().equals(HOTBAR_OVERLAY)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        Player player = mc.player;
        GuiGraphics guiGraphics = event.getGuiGraphics();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barWidth = QLMConfig.HEALTH_BAR_WIDTH.get();
        int barHeight = QLMConfig.HEALTH_BAR_HEIGHT.get();
        int posY = screenHeight - QLMConfig.HEALTH_BAR_POSITION_Y.get();
        int posX = (screenWidth - barWidth) / 2;

        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float absorb = player.getAbsorptionAmount();

        float healthPercent = maxHealth > 0 ? Math.min(1.0f, currentHealth / maxHealth) : 0.0f;
        float absorbPercent = maxHealth > 0 ? Math.min(1.0f - healthPercent, absorb / maxHealth) : 0.0f;

        int filledWidth = Math.round(barWidth * healthPercent);
        int absorbWidth = Math.round(barWidth * absorbPercent);

        // 1) 背景 (黑色带透明描边) - 类似原版经验条的背景条
        drawTransparentRect(guiGraphics, posX - 1, posY - 1, barWidth + 2, barHeight + 2, 0xAA_000000);
        drawSolidRect(guiGraphics, posX, posY, barWidth, barHeight, 0xFF_220000);

        // 2) 填充的血条 (根据百分比动态变色)
        int baseColor = 0xFF_FF3030;
        if (healthPercent > 0.66f) {
            baseColor = 0xFF_FF3030;   // 亮红
        } else if (healthPercent > 0.50f) {
            baseColor = 0xFF_FF6A00;   // 橙红
        } else if (healthPercent > 0.25f) {
            baseColor = 0xFF_C62828;   // 深红
        } else {
            baseColor = 0xFF_FF1010;   // 警告红（闪烁）
        }

        drawSolidRect(guiGraphics, posX, posY, filledWidth, barHeight, baseColor);

        // 3) 吸收伤害(金苹果/信标金色吸收条)
        if (absorbWidth > 0) {
            int absorbStart = Math.min(posX + filledWidth, posX + barWidth);
            int actualAbsorbWidth = Math.min(absorbWidth, (posX + barWidth) - absorbStart);
            if (actualAbsorbWidth > 0) {
                drawSolidRect(guiGraphics, absorbStart, posY, actualAbsorbWidth, barHeight, 0xFF_FFD700);
            }
        }

        // 4) 顶部高光（与原版经验条一致的 3D 感）
        drawSolidRect(guiGraphics, posX, posY, barWidth, 1, 0x44_FFFFFF);
        drawSolidRect(guiGraphics, posX, posY + barHeight - 1, barWidth, 1, 0x44_000000);

        // 5) 数字: 当前HP / 最大HP
        String hpText = String.format("%d / %d", Math.round(currentHealth), Math.round(maxHealth));
        int textColor = 0xFF_FFFFFF;
        if (currentHealth / maxHealth < 0.25f) {
            textColor = 0xFF_FFD700;  // 低于25%血量显示金色数字
        }

        int textWidth = mc.font.width(hpText);
        int textX = posX + (barWidth - textWidth) / 2;
        int textY = posY - 10;

        drawTransparentRect(guiGraphics, textX - 2, textY - 1, textWidth + 4, 9, 0x70_000000);
        guiGraphics.drawString(mc.font, hpText, textX, textY, textColor, false);

        // 6) 吸收数值显示（当有金色吸收HP时）
        if (absorb > 0) {
            String absorbText = String.format("+%d", Math.round(absorb));
            int absorbTextX = posX + barWidth + 4;
            int absorbTextY = posY - 2;
            guiGraphics.drawString(mc.font, absorbText, absorbTextX, absorbTextY, 0xFFFFD700, false);
        }
    }

    // ---------- 辅助方法 ----------

    private static void drawSolidRect(GuiGraphics gui, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        gui.fill(x, y, x + w, y + h, color);
    }

    private static void drawTransparentRect(GuiGraphics gui, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        gui.fill(x, y, x + w, y + h, color);
    }
}
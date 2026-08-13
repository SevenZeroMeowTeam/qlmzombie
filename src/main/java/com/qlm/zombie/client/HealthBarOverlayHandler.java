package com.qlm.zombie.client;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 血量显示覆盖层：
 * - 取消原版红心形血量/护甲/饥饿显示（通过Pre事件）
 * - 保留我们自定义的绿色血量条 + 护甲/饱食度文字
 * - 经验条位置在最下方（护甲+饱食度之上 = 血量条之下）
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID, value = Dist.CLIENT)
public class HealthBarOverlayHandler {

    /** 取消原版心形显示（血量/护甲/饥饿） */
    @SubscribeEvent
    public static void onPreRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        var type = event.getOverlay();
        // 直接取消血量/护甲/饥饿这三个原版心形
        if (type == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || type == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || type == VanillaGuiOverlay.FOOD_LEVEL.type()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        // 在经验条渲染完后绘制我们的内容
        if (event.getOverlay() != VanillaGuiOverlay.EXPERIENCE_BAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Player player = mc.player;
        GuiGraphics gui = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // 原版经验条 Y
        int xpBarY = height - 32 + 4;
        int barWidth = 182;
        int barX = (width - barWidth) / 2;

        // 自下而上：经验条（最下）→ 护甲/饱食度文字 → 血量条（最上）
        int infoY = xpBarY - 12;      // 护甲/饱食度：经验条上方 12px
        int healthBarY = xpBarY - 22; // 血量条：经验条上方 22px

        // 血量
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float healthRatio = Math.min(1.0F, health / maxHealth);

        // 饥饿值
        int food = player.getFoodData().getFoodLevel();
        int maxFood = 20;
        float foodRatio = Math.min(1.0F, (float) food / maxFood);

        int barColor;
        if (healthRatio > 0.6F) barColor = 0xFF00AA00;      // 绿
        else if (healthRatio > 0.3F) barColor = 0xFFFFAA00; // 黄
        else barColor = 0xFFFF0000;                          // 红

        int bgColor = 0x44000000;

        // ========== 血量条 ==========
        gui.fill(barX, healthBarY, barX + barWidth, healthBarY + 5, bgColor);
        int filledWidth = (int) (barWidth * healthRatio);
        if (filledWidth > 0) {
            gui.fill(barX, healthBarY, barX + filledWidth, healthBarY + 5, barColor);
        }
        // 边框
        gui.fill(barX - 1, healthBarY - 1, barX + barWidth + 1, healthBarY, 0xFF555555);
        gui.fill(barX - 1, healthBarY + 5, barX + barWidth + 1, healthBarY + 6, 0xFF555555);
        gui.fill(barX - 1, healthBarY - 1, barX, healthBarY + 6, 0xFF555555);
        gui.fill(barX + barWidth, healthBarY - 1, barX + barWidth + 1, healthBarY + 6, 0xFF555555);

        // 血量文字
        String healthText = String.format("❤ %.0f/%.0f", health, maxHealth);
        int textWidth = mc.font.width(healthText);
        int textX = barX + (barWidth - textWidth) / 2;
        int textY = healthBarY - 1;
        gui.drawString(mc.font, healthText, textX + 1, textY + 1, 0x44000000, false);
        gui.drawString(mc.font, healthText, textX, textY, 0xFFFFFFFF, false);

        // ========== 护甲（左）+ 饱食度（右） 在血量条和经验条之间 ==========
        int armor = mc.player.getArmorValue();
        if (armor > 0) {
            gui.drawString(mc.font, "🛡 " + armor, barX - 30, infoY, 0xFF55AAFF, false);
        }
        int foodColor;
        if (foodRatio > 0.7F) foodColor = 0xFF55FF55;
        else if (foodRatio > 0.3F) foodColor = 0xFFFFAA00;
        else foodColor = 0xFFFF5555;
        gui.drawString(mc.font, "☕ " + food, barX + barWidth + 8, infoY, foodColor, false);
    }
}
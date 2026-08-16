package com.qlm.zombie.thirst.foundation.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.thirst.Thirst;
import com.qlm.zombie.thirst.foundation.common.capability.IThirst;
import com.qlm.zombie.thirst.foundation.common.capability.ModCapabilities;
import com.qlm.zombie.thirst.foundation.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

public class ThirstBarRenderer
{
    public static IThirst PLAYER_THIRST = null;
    public static ResourceLocation THIRST_ICONS = Thirst.asResource("textures/gui/thirst_icons.png");

    public static final ResourceLocation MC_ICONS = new ResourceLocation("textures/gui/icons.png");
    public static Boolean cancelRender = false;
    static Minecraft minecraft = Minecraft.getInstance();
    protected final static RandomSource random = RandomSource.create();
    public static IGuiOverlay THIRST_OVERLAY = (gui, poseStack, partialTicks, screenWidth, screenHeight) ->
    {
        // 健壮性：玩家对象未就绪时直接隐藏口渴条，避免闪退
        if (minecraft.player == null)
        {
            cancelRender = true;
            return;
        }
        boolean isMounted = minecraft.player.getVehicle() instanceof LivingEntity;
        cancelRender =false;
        if (!QLMConfig.INSTANCE.getEnableThirst())
        {
            cancelRender = true;
            return;
        }
        if (!isMounted && !minecraft.options.hideGui && gui.shouldDrawSurvivalElements())
        {
            IThirst cap = minecraft.player.getCapability(ModCapabilities.PLAYER_THIRST).orElse(null);
            // 健壮性：口渴能力未附加（如切换维度/模组冲突）时隐藏，避免 NPE 闪退
            if (cap == null)
            {
                cancelRender = true;
                return;
            }
            if(minecraft.player.isAlive() && !cap.getShouldTickThirst()){
                cancelRender = true;
                return;
            }
            gui.setupOverlayRenderState(true, false);
            render(gui, screenWidth, screenHeight, poseStack);
        }
    };

    public static void registerThirstOverlay(RegisterGuiOverlaysEvent event)
    {
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "thirst_level", THIRST_OVERLAY);
    }
    public static void render(ForgeGui gui, int width, int height, GuiGraphics guiGraphics)
    {
        if (minecraft.player == null) return;
        minecraft.getProfiler().push("thirst");
        if (PLAYER_THIRST == null || minecraft.player.tickCount % 40 == 0)
        {
            PLAYER_THIRST = minecraft.player.getCapability(ModCapabilities.PLAYER_THIRST).orElse(null);
        }
        // 健壮性：能力缺失时跳过渲染，避免 NPE 闪退
        if (PLAYER_THIRST == null)
        {
            minecraft.getProfiler().pop();
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, THIRST_ICONS);
        int left = width / 2 + 91 + ClientConfig.THIRST_BAR_X_OFFSET.get();
        int top = height - gui.rightHeight + ClientConfig.THIRST_BAR_Y_OFFSET.get();
        gui.rightHeight += 10;
        boolean unused = false;// Unused flag in vanilla, seems to be part of a 'fade out' mechanic

        int level = PLAYER_THIRST.getThirst();

        for (int i = 0; i < 10; ++i)
        {
            int idx = i * 2 + 1;
            int x = left - i * 8 - 9;
            int y = top;

            if (PLAYER_THIRST.getQuenched() <= 0.0F && gui.getGuiTicks() % (level * 3 + 1) == 0)
            {
                y = top + (random.nextInt(3) - 1);
            }

            guiGraphics.blit(THIRST_ICONS, x, y, 0, 0, 9, 9, 25, 9);

            if (idx < level)
                guiGraphics.blit(THIRST_ICONS, x, y, 16, 0, 9, 9, 25, 9);
            else if (idx == level)
                guiGraphics.blit(THIRST_ICONS, x, y, 8, 0, 9, 9, 25, 9);
        }
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, MC_ICONS);

        minecraft.getProfiler().pop();
    }
}
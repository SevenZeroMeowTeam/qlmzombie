/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ----------------------------------------------------------------------------
 * QLM ModSDK — 渲染辅助 API（客户端侧）
 * 封装 GuiGraphics 常用绘制操作：文字、矩形、描边矩形、纹理、线条、颜色工具。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 渲染辅助 API（静态方法，仅客户端调用）。
 *
 * <p>用法：</p>
 * <pre>{@code
 * RenderAPI.drawText(graphics, "Hello", 10, 10, RenderAPI.colorHex(255, 255, 255));
 * RenderAPI.drawRect(graphics, 10, 20, 80, 20, 0x80000000);
 * RenderAPI.drawOutlinedRect(graphics, 10, 20, 80, 20, 0x80000000, 0xFFFFFFFF);
 * RenderAPI.drawTexture(graphics, new ResourceLocation("qlmzombie", "textures/gui/panel.png"), 10, 10, 100, 50);
 * }</pre>
 */
public final class RenderAPI {

    private RenderAPI() {
    }

    /**
     * 绘制文字（带阴影）。
     */
    public static void drawText(GuiGraphics graphics, String text, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, text, x, y, color, true);
    }

    /**
     * 绘制居中文字（以 x 为中心点）。
     */
    public static void drawCenteredText(GuiGraphics graphics, String text, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        graphics.drawString(font, text, x - textWidth / 2, y, color, true);
    }

    /**
     * 绘制实心矩形。
     *
     * @param x     左上角 x
     * @param y     左上角 y
     * @param width 宽度
     * @param height 高度
     * @param color ARGB 颜色
     */
    public static void drawRect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, color);
    }

    /**
     * 绘制带描边的实心矩形。
     *
     * @param fillColor   填充颜色（ARGB）
     * @param borderColor 边框颜色（ARGB）
     */
    public static void drawOutlinedRect(GuiGraphics graphics, int x, int y,
                                        int width, int height,
                                        int fillColor, int borderColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        // 填充
        graphics.fill(x, y, x + width, y + height, fillColor);
        // 边框：上/下/左/右
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    /**
     * 绘制纹理（以原始尺寸铺满指定区域）。
     *
     * @param texture 纹理 ResourceLocation
     * @param x       左上角 x
     * @param y       左上角 y
     * @param width   绘制宽度
     * @param height  绘制高度
     */
    public static void drawTexture(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(texture, x, y, 0, 0, width, height, width, height);
    }

    /**
     * 绘制线条（支持任意方向，使用 Bresenham 算法）。
     */
    public static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        // 水平线优化
        if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            graphics.fill(minX, y1, maxX + 1, y1 + 1, color);
            return;
        }
        // 垂直线优化
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            graphics.fill(x1, minY, x1 + 1, maxY + 1, color);
            return;
        }
        // 对角线：Bresenham
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1;
        int y = y1;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /**
     * RGB 转 ARGB int（Alpha = 255）。
     */
    public static int colorHex(int r, int g, int b) {
        return (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /**
     * ARGB 转 int。
     */
    public static int colorHexA(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}

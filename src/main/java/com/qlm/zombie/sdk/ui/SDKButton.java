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
 * QLM ModSDK — 按钮组件
 * Minecraft 风格按钮，使用纯色填充 + 3D 斜面边框绘制，支持悬停高亮与禁用态。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 按钮组件。
 *
 * <p>用法：</p>
 * <pre>{@code
 * SDKButton btn = SDKButton.builder("确认")
 *         .pos(10, 10)
 *         .size(80, 20)
 *         .onClick(() -> { ... });
 * screen.addComponent(btn);
 * }</pre>
 */
public class SDKButton extends SDKComponent {

    private String text;
    private Runnable onClick;

    public SDKButton(String text) {
        this.text = text == null ? "" : text;
        this.width = 100;
        this.height = 20;
    }

    /**
     * 创建按钮 builder（链式配置入口）。
     */
    public static SDKButton builder(String text) {
        return new SDKButton(text);
    }

    /**
     * 设置点击回调。
     */
    public SDKButton onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = enabled && isHovered(mouseX, mouseY);

        // 按钮底色：禁用/正常/悬停
        int bgColor;
        if (!enabled) {
            bgColor = 0xFF4A4A4A;
        } else if (hovered) {
            bgColor = 0xFF6B6B6B;
        } else {
            bgColor = 0xFF5A5A5A;
        }

        // 绘制底色
        graphics.fill(x, y, x + width, y + height, bgColor);

        // 3D 斜面边框：上/左亮，下/右暗
        int highlight = enabled ? (hovered ? 0xFFFFFFFF : 0xFFA0A0A0) : 0xFF666666;
        int shadow = enabled ? 0xFF2A2A2A : 0xFF333333;
        graphics.fill(x, y, x + width, y + 1, highlight);                 // 上
        graphics.fill(x, y, x + 1, y + height, highlight);                // 左
        graphics.fill(x, y + height - 1, x + width, y + height, shadow);  // 下
        graphics.fill(x + width - 1, y, x + width, y + height, shadow);   // 右

        // 居中绘制文字
        Font font = Minecraft.getInstance().font;
        int textColor = enabled ? 0xFFFFFFFF : 0xFFA0A0A0;
        int textX = x + (width - font.width(text)) / 2;
        int textY = y + (height - font.lineHeight) / 2;
        graphics.drawString(font, text, textX, textY, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && enabled && isHovered(mouseX, mouseY)) {
            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }
}

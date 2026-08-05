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
 * QLM ModSDK — 文本标签组件
 * 支持自定义颜色、居中、阴影的纯文字渲染。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 文本标签组件。
 *
 * <p>用法：</p>
 * <pre>{@code
 * SDKLabel label = SDKLabel.builder("生命值: 100/100")
 *         .pos(10, 5)
 *         .color(0xFFFF5555)
 *         .centered(false)
 *         .shadow(true);
 * screen.addComponent(label);
 * }</pre>
 */
public class SDKLabel extends SDKComponent {

    private String text;
    private int color = 0xFFFFFFFF;
    private boolean centered = false;
    private boolean shadow = true;

    public SDKLabel(String text) {
        this.text = text == null ? "" : text;
        this.width = 100;
        this.height = 10;
    }

    /**
     * 创建标签 builder（链式配置入口）。
     */
    public static SDKLabel builder(String text) {
        return new SDKLabel(text);
    }

    public SDKLabel color(int color) {
        this.color = color;
        return this;
    }

    public SDKLabel centered(boolean centered) {
        this.centered = centered;
        return this;
    }

    public SDKLabel shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public boolean isCentered() {
        return centered;
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int textX = x;
        if (centered) {
            textX = x + (width - font.width(text)) / 2;
        }
        int textY = y + (height - font.lineHeight) / 2;
        graphics.drawString(font, text, textX, textY, color, shadow);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}

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
 * QLM ModSDK — 文本输入框组件
 * 支持焦点切换、字符输入、退格删除、光标闪烁、占位提示文字。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * 文本输入框组件。
 *
 * <p>用法：</p>
 * <pre>{@code
 * SDKTextField field = SDKTextField.builder()
 *         .pos(10, 40)
 *         .size(150, 16)
 *         .placeholder("输入名称...")
 *         .maxLength(32);
 * screen.addComponent(field);
 * }</pre>
 *
 * <p>键盘事件由 {@link SDKScreen} 自动转发。点击输入框获取焦点，点击外部失去焦点。</p>
 */
public class SDKTextField extends SDKComponent {

    private String value = "";
    private int maxLength = 32;
    private boolean isFocused = false;
    private String placeholder = "";
    private int cursorTick = 0;

    public SDKTextField() {
        this.width = 150;
        this.height = 16;
    }

    /**
     * 创建文本框 builder（链式配置入口）。
     */
    public static SDKTextField builder() {
        return new SDKTextField();
    }

    public SDKTextField value(String value) {
        this.value = value == null ? "" : value;
        clampLength();
        return this;
    }

    public SDKTextField maxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        clampLength();
        return this;
    }

    public SDKTextField placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
        clampLength();
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        clampLength();
    }

    public boolean isFocused() {
        return isFocused;
    }

    public void setFocused(boolean focused) {
        this.isFocused = focused;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    private void clampLength() {
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cursorTick++;

        // 背景
        int bgColor = isFocused ? 0xFF000000 : 0xFF1A1A1A;
        int borderColor = isFocused ? 0xFFFFFFFF : 0xFF808080;
        graphics.fill(x, y, x + width, y + height, bgColor);

        // 边框
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);

        Font font = Minecraft.getInstance().font;
        int padX = 4;
        int textY = y + (height - font.lineHeight) / 2;

        if (value.isEmpty() && !placeholder.isEmpty()) {
            // 占位文字
            graphics.drawString(font, placeholder, x + padX, textY, 0xFF808080, false);
        } else {
            // 截断过长文字（从左侧裁剪以显示末尾）
            String toDraw = value;
            int maxWidth = width - padX * 2;
            int drawOffset = 0;
            while (font.width(toDraw) > maxWidth && toDraw.length() > 1) {
                toDraw = toDraw.substring(1);
                drawOffset = font.width(value) - font.width(toDraw);
            }
            int textX = x + padX - drawOffset;
            graphics.drawString(font, toDraw, textX, textY, 0xFFFFFFFF, false);

            // 光标闪烁（每 15 tick 切换）
            if (isFocused && (cursorTick / 15) % 2 == 0) {
                int cursorX = x + padX + font.width(toDraw) - drawOffset;
                graphics.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            isFocused = true;
            return true;
        }
        // 点击外部失去焦点
        isFocused = false;
        return false;
    }

    /**
     * 字符输入。由屏幕的 charTyped 转发调用。
     *
     * @return true 表示消费了此输入
     */
    public boolean charTyped(char c, int modifiers) {
        if (!isFocused) {
            return false;
        }
        if (value.length() < maxLength && c >= 32) {
            value += c;
            return true;
        }
        return false;
    }

    /**
     * 按键处理。由屏幕的 keyPressed 转发调用。
     *
     * @return true 表示消费了此按键
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused) {
            return false;
        }
        // 退格删除
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !value.isEmpty()) {
            value = value.substring(0, value.length() - 1);
            return true;
        }
        // ESC 失去焦点（不关闭屏幕，交给父类处理）
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            isFocused = false;
            return false;
        }
        return false;
    }
}

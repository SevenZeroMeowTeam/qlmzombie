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
 * QLM ModSDK — UI 组件抽象基类
 * 所有自定义 GUI 组件（按钮/文本框/标签/进度条）的父类，提供位置、尺寸、
 * 可见性、启用状态的统一管理，以及 builder 模式的链式 setter。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * UI 组件抽象基类。
 *
 * <p>子类需实现 {@link #render} 和 {@link #mouseClicked}。
 * 通过 builder 模式的 setter 链式配置：</p>
 * <pre>{@code
 * new SDKButton("OK")
 *     .pos(10, 10)
 *     .size(80, 20)
 *     .visible(true)
 *     .enabled(true);
 * }</pre>
 */
public abstract class SDKComponent {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean enabled = true;

    /**
     * 渲染组件。仅在 {@link #visible} 为 true 时由屏幕调用。
     */
    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /**
     * 鼠标点击处理。
     *
     * @param button 鼠标按键（0=左键, 1=右键, 2=中键）
     * @return true 表示消费了此点击事件（阻止后续组件响应）
     */
    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);

    /**
     * 判断鼠标坐标是否在组件范围内（悬停检测）。
     */
    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    // ===== Builder 模式 setter =====

    public SDKComponent pos(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public SDKComponent size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public SDKComponent visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public SDKComponent enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    // ===== Getter =====

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}

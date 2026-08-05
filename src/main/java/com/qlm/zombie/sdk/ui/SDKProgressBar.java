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
 * QLM ModSDK — 进度条组件
 * 绘制背景 + 填充条 + 边框的可自定义进度条，progress 范围 0.0~1.0。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 进度条组件。
 *
 * <p>用法：</p>
 * <pre>{@code
 * SDKProgressBar bar = SDKProgressBar.builder()
 *         .pos(10, 60)
 *         .size(120, 12)
 *         .barColor(0xFF00FF00)
 *         .progress(0.75f);
 * screen.addComponent(bar);
 * }</pre>
 */
public class SDKProgressBar extends SDKComponent {

    private float progress = 0.0f;
    private int barColor = 0xFF00FF00;
    private int backgroundColor = 0xFF404040;
    private int borderColor = 0xFF000000;

    public SDKProgressBar() {
        this.width = 120;
        this.height = 12;
    }

    /**
     * 创建进度条 builder（链式配置入口）。
     */
    public static SDKProgressBar builder() {
        return new SDKProgressBar();
    }

    public SDKProgressBar progress(float progress) {
        this.progress = clamp(progress);
        return this;
    }

    public SDKProgressBar barColor(int color) {
        this.barColor = color;
        return this;
    }

    public SDKProgressBar backgroundColor(int color) {
        this.backgroundColor = color;
        return this;
    }

    public SDKProgressBar borderColor(int color) {
        this.borderColor = color;
        return this;
    }

    public void setProgress(float progress) {
        this.progress = clamp(progress);
    }

    public float getProgress() {
        return progress;
    }

    public int getBarColor() {
        return barColor;
    }

    public void setBarColor(int barColor) {
        this.barColor = barColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
    }

    private static float clamp(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景
        graphics.fill(x, y, x + width, y + height, backgroundColor);

        // 填充进度
        int fillWidth = (int) (width * progress);
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + height, barColor);
        }

        // 边框
        graphics.fill(x, y, x + width, y + 1, borderColor);                 // 上
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor); // 下
        graphics.fill(x, y, x + 1, y + height, borderColor);                // 左
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor); // 右
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}

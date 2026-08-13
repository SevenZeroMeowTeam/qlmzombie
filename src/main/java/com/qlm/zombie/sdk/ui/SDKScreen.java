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
 * QLM ModSDK — 自定义 GUI 屏幕基类
 * 继承 Minecraft {@link net.minecraft.client.gui.screens.Screen}，提供组件化的
 * UI 布局系统：添加/移除组件、统一渲染、转发鼠标与键盘事件。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义 GUI 屏幕基类。
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * SDKScreen screen = new SDKScreen("我的面板");
 * screen.addComponent(new SDKButton("确认")
 *         .pos(10, 10)
 *         .size(80, 20)
 *         .onClick(() -> System.out.println("clicked")));
 * Minecraft.getInstance().setScreen(screen);
 * }</pre>
 *
 * <p>屏幕会自动将鼠标点击、字符输入、按键事件转发给可见组件（如文本框）。</p>
 */
public class SDKScreen extends Screen {

    private final List<SDKComponent> components = new ArrayList<>();
    private Runnable onCloseCallback;

    /**
     * 构造屏幕。
     *
     * @param title 屏幕标题（显示在顶部）
     */
    public SDKScreen(String title) {
        super(Component.literal(title == null ? "" : title));
    }

    /**
     * 添加组件到屏幕。
     */
    public void addComponent(SDKComponent component) {
        if (component != null) {
            components.add(component);
        }
    }

    /**
     * 移除组件。
     */
    public void removeComponent(SDKComponent component) {
        components.remove(component);
    }

    /**
     * 返回所有组件（不可修改视图）。
     */
    public List<SDKComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }

    /**
     * 设置关闭回调。屏幕关闭时（ESC 或程序调用 onClose）触发。
     */
    public void onCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    @Override
    public void onClose() {
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
        super.onClose();
    }

    @Override
    protected void init() {
        super.init();
        // 子类可覆盖此方法完成布局初始化
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 渲染标题
        if (this.title != null) {
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        }
        // 渲染所有可见组件
        for (SDKComponent component : components) {
            if (component.isVisible()) {
                component.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (SDKComponent component : components) {
            if (component.isVisible() && component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (SDKComponent component : components) {
            if (component instanceof SDKTextField textField) {
                if (textField.charTyped(codePoint, modifiers)) {
                    return true;
                }
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (SDKComponent component : components) {
            if (component instanceof SDKTextField textField) {
                if (textField.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

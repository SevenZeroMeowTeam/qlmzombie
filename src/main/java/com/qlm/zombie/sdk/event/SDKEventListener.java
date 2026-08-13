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
 * QLM ModSDK — SDK 事件监听器函数式接口
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.event;

/**
 * SDK 事件监听器函数式接口，用于在 {@link SDKEventBus} 上注册回调。
 *
 * <p>用法示例：
 * <pre>{@code
 * SDKModSDK.getEventBus().registerListener("block_break", event -> {
 *     if (event instanceof SDKEvent.BlockBreakEvent breakEvent) {
 *         // 处理方块破坏
 *     }
 * });
 * }</pre>
 */
@FunctionalInterface
public interface SDKEventListener {

    /**
     * 事件触发时调用。
     *
     * @param event 触发的 SDK 事件
     */
    void onEvent(SDKEvent event);
}

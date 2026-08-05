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
 * QLM ModSDK — SDK 事件总线
 * 基于 Forge @SubscribeEvent 但提供更简洁的回调式 API
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SDK 事件总线。线程安全地管理事件名到监听器列表的映射。
 *
 * <p>支持的常见事件名见 {@link SDKEvent} 的各子类 {@code NAME} 常量，例如
 * {@code "block_break"}, {@code "player_join"}, {@code "server_start"} 等。</p>
 */
public final class SDKEventBus {

    private final Map<String, List<SDKEventListener>> listeners = new ConcurrentHashMap<>();

    /**
     * 注册监听器。
     *
     * @param eventName 事件名（如 "block_break"）
     * @param callback  回调
     */
    public void registerListener(String eventName, SDKEventListener callback) {
        if (eventName == null || eventName.isEmpty() || callback == null) {
            return;
        }
        listeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    /**
     * 取消注册指定事件名下的某个监听器。
     *
     * @param eventName 事件名
     * @param callback  要移除的回调
     */
    public void unregisterListener(String eventName, SDKEventListener callback) {
        if (eventName == null || callback == null) {
            return;
        }
        List<SDKEventListener> list = listeners.get(eventName);
        if (list != null) {
            list.remove(callback);
        }
    }

    /**
     * 发布事件。按注册顺序回调所有监听器；若事件可取消且被取消，则停止后续回调。
     *
     * @param event 要发布的事件
     */
    public void post(SDKEvent event) {
        if (event == null) {
            return;
        }
        List<SDKEventListener> list = listeners.get(event.getEventName());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SDKEventListener listener : list) {
            try {
                listener.onEvent(event);
            } catch (Throwable t) {
                // 单个监听器异常不应影响其他监听器
                System.err.println("[QLM ModSDK] 监听器处理事件 " + event.getEventName() + " 时异常: " + t);
                t.printStackTrace();
            }
            if (event.isCancelable() && event.isCanceled()) {
                break;
            }
        }
    }

    /**
     * 清空所有监听器。
     */
    public void clear() {
        listeners.clear();
    }

    /**
     * 获取指定事件名下已注册的监听器数量。
     */
    public int getListenerCount(String eventName) {
        List<SDKEventListener> list = listeners.get(eventName);
        return list == null ? 0 : list.size();
    }

    /**
     * 获取所有已注册的事件名（只读视图）。
     */
    public List<String> getRegisteredEventNames() {
        return Collections.unmodifiableList(new ArrayList<>(listeners.keySet()));
    }
}

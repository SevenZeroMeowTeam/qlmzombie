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
 * QLM ModSDK — SDK 入口类
 * 负责初始化所有子系统（事件总线 / 注册表 / 任务调度器）。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.sdk.command.CommandBuilder;
import com.qlm.zombie.sdk.event.SDKEventBus;
import com.qlm.zombie.sdk.registry.SDKRegistry;
import com.qlm.zombie.sdk.task.TaskScheduler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * QLM ModSDK 入口类。静态类，提供 init() 与各子系统的获取方法。
 *
 * <p>SDK 自身不是独立 Forge mod，而是宿主 mod 的子模块。宿主 mod 在其
 * 构造函数中调用 {@link #init(FMLJavaModLoadingContext)} 完成初始化，
 * 之后即可通过静态方法获取各子系统。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * // 在宿主 mod 构造函数中：
 * QLMModSDK.init(context);  // context 为 FMLJavaModLoadingContext
 *
 * // 之后任意位置：
 * SDKEventBus bus = QLMModSDK.getEventBus();
 * bus.registerListener("block_break", event -> { ... });
 *
 * TaskScheduler scheduler = QLMModSDK.getTaskScheduler();
 * scheduler.runTaskTimer(() -> { ... }, 0L, 20L);
 * }</pre>
 */
public final class QLMModSDK {

    /**
     * SDK 模块的内部 mod id。
     * 用于 {@link SDKRegistry} 的 Forge 注册表命名空间（qlmsdk:xxx）。
     * SDK 本身不是独立 mod，宿主 mod 须通过 mods.toml 提供 qlmsdk 命名空间，
     * 或在自定义内容时使用宿主自己的 mod id。
     */
    public static final String MOD_ID = "qlmsdk";

    /** SDK 版本 */
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final SDKEventBus EVENT_BUS = new SDKEventBus();
    private static final TaskScheduler TASK_SCHEDULER = new TaskScheduler();

    private static volatile boolean initialized = false;

    private QLMModSDK() {}

    /**
     * 初始化 SDK 子系统。应在 mod 构造函数中调用一次。
     *
     * <p>做了以下事情：</p>
     * <ul>
     *   <li>绑定 SDKRegistry 的 DeferredRegister 到 mod 事件总线</li>
     *   <li>注册 {@link ForgeEventBridge} 到 Forge 事件总线</li>
     *   <li>注册命令分发器绑定监听器</li>
     * </ul>
     */
    @SuppressWarnings("removal")
    public static synchronized void init(FMLJavaModLoadingContext context) {
        if (initialized) {
            return;
        }
        IEventBus modEventBus = context.getModEventBus();
        // 注册表 DeferredRegister 绑定
        SDKRegistry.init(modEventBus);

        // Forge 事件桥接（@Mod.EventBusSubscriber 已自动注册，这里作为双保险）
        MinecraftForge.EVENT_BUS.register(ForgeEventBridge.class);

        // 命令分发器绑定
        MinecraftForge.EVENT_BUS.addListener(QLMModSDK::onRegisterCommands);

        // 服务端 tick 调度（驱动 TaskScheduler）
        MinecraftForge.EVENT_BUS.addListener(QLMModSDK::onServerTick);

        initialized = true;
        LOGGER.info("[QLM ModSDK] v{} 已初始化", VERSION);
    }

    /**
     * 初始化（兼容入口）。若未提供 context，仅完成 Forge 事件总线相关注册，
     * 调用方需自行通过 {@link SDKRegistry#init(IEventBus)} 绑定 DeferredRegister。
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(ForgeEventBridge.class);
        MinecraftForge.EVENT_BUS.addListener(QLMModSDK::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(QLMModSDK::onServerTick);
        initialized = true;
        LOGGER.info("[QLM ModSDK] v{} 已初始化（轻量模式）", VERSION);
    }

    /** 获取 SDK 事件总线 */
    public static SDKEventBus getEventBus() {
        return EVENT_BUS;
    }

    /** 获取 SDK 注册表（静态方法集合，返回类型仅用于链式调用） */
    public static Class<SDKRegistry> getRegistry() {
        return SDKRegistry.class;
    }

    /** 获取 SDK 任务调度器 */
    public static TaskScheduler getTaskScheduler() {
        return TASK_SCHEDULER;
    }

    /** 获取 SDK 版本号 */
    public static String getVersion() {
        return VERSION;
    }

    // ====================================================================
    // Forge 事件处理
    // ====================================================================

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandBuilder.bindDispatcher(event.getDispatcher());
        LOGGER.debug("[QLM ModSDK] 命令分发器已绑定");
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        // 仅在 END 阶段驱动调度器，避免双触发
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        TASK_SCHEDULER.tick();
    }
}

/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * 功能：统一 Python 脚本引擎
 * 支持：GraalPy (Python 3.x) > Jython (Python 2.7) > Jep (CPython)
 * 用法：将 .py 文件放入 scripts/python/ 目录，启动时自动加载执行
 */
package com.qlm.zombie.script;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 统一 Python 脚本引擎入口。
 * <p>
 * 引擎优先级：GraalPy → Jython → Jep
 * - GraalPy: 支持 Python 3.x，需要 GraalVM 运行时 JAR 在 classpath
 * - Jython:  支持 Python 2.7，纯 Java 实现已打包进 mod JAR，开箱即用
 * - Jep:     支持 CPython 3.x，需要系统安装 Python + jep native 库
 */
public class PythonScriptEngine {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SCRIPT_DIR = "scripts/python";

    private static EngineBackend activeBackend;
    private static boolean initialized = false;

    /**
     * 初始化 Python 引擎，加载并执行 scripts/python/ 下所有 .py 脚本。
     *
     * @param forgeEventBus Forge 事件总线，传递给 Python 脚本用于注册事件
     */
    public static void initialize(IEventBus forgeEventBus) {
        if (initialized) return;
        initialized = true;

        // 1) 检测可用引擎
        activeBackend = detectEngine();
        if (activeBackend == null) {
            LOGGER.warn("[QLM Zombie] 未找到可用的 Python 引擎，跳过脚本加载");
            LOGGER.warn("[QLM Zombie] 可选方案：1) Jython 已内置 2) 添加 GraalPy JAR 3) 安装 Jep");
            return;
        }

        LOGGER.info("[QLM Zombie] Python 引擎: {} ({})", activeBackend.getName(), activeBackend.getPythonVersion());

        // 2) 查找脚本文件
        List<File> scripts = findScripts();
        if (scripts.isEmpty()) {
            LOGGER.info("[QLM Zombie] scripts/python/ 目录为空，跳过脚本执行");
            return;
        }

        // 3) 注入 Java API 并执行脚本
        PythonAPI api = new PythonAPI(forgeEventBus);
        activeBackend.injectAPI("qlm", api);

        for (File script : scripts) {
            try {
                String code = Files.readString(script.toPath());
                activeBackend.execute(script.getName(), code);
                LOGGER.info("[QLM Zombie] 已加载 Python 脚本: {}", script.getName());
            } catch (Exception e) {
                LOGGER.error("[QLM Zombie] Python 脚本执行失败: {} - {}", script.getName(), e.getMessage());
            }
        }

        // 4) 绑定事件桥接器（脚本执行后，回调已注册）
        PythonEventBridge.bind(api);

        LOGGER.info("[QLM Zombie] Python 脚本引擎初始化完成，共加载 {} 个脚本", scripts.size());
    }

    /**
     * 按优先级检测可用的 Python 引擎。
     */
    private static EngineBackend detectEngine() {
        // 1. 尝试 GraalPy (Python 3.x)
        EngineBackend graalpy = tryGraalPy();
        if (graalpy != null) return graalpy;

        // 2. 尝试 Jython (Python 2.7，纯 Java)
        EngineBackend jython = tryJython();
        if (jython != null) return jython;

        // 3. 尝试 Jep (CPython)
        EngineBackend jep = tryJep();
        if (jep != null) return jep;

        return null;
    }

    // ── GraalPy 检测 ──
    private static EngineBackend tryGraalPy() {
        try {
            Class<?> ctxClass = Class.forName("org.graalvm.polyglot.Context");
            Class<?> pythonLang = Class.forName("org.graalvm.polyglot.python.PythonLanguage");
            LOGGER.info("[QLM Zombie] 检测到 GraalPy (Python 3.x)");
            return new GraalPyBackend();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // ── Jython 检测 ──
    private static EngineBackend tryJython() {
        try {
            Class.forName("org.python.util.PythonInterpreter");
            LOGGER.info("[QLM Zombie] 检测到 Jython (Python 2.7)");
            return new JythonBackend();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // ── Jep 检测 ──
    private static EngineBackend tryJep() {
        try {
            Class.forName("jep.Jep");
            LOGGER.info("[QLM Zombie] 检测到 Jep (CPython)");
            return new JepBackend();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * 查找 scripts/python/ 目录下所有 .py 文件。
     */
    private static List<File> findScripts() {
        Path dir = FMLPaths.GAMEDIR.get().resolve(SCRIPT_DIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (Exception ignored) {}
            return List.of();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".py"))
                    .map(Path::toFile)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.error("[QLM Zombie] 扫描 Python 脚本目录失败: {}", e.getMessage());
            return List.of();
        }
    }

    public static String getActiveEngineName() {
        return activeBackend != null ? activeBackend.getName() : "无";
    }

    public static boolean isAvailable() {
        return activeBackend != null;
    }

    // ── 引擎后端接口 ──

    interface EngineBackend {
        String getName();
        String getPythonVersion();
        void injectAPI(String name, Object api);
        void execute(String scriptName, String code) throws Exception;
    }

    // ── GraalPy 后端 (Python 3.x) ──

    static class GraalPyBackend implements EngineBackend {
        private Object context; // org.graalvm.polyglot.Context

        GraalPyBackend() {
            try {
                Class<?> ctxBuilder = Class.forName("org.graalvm.polyglot.Context$Builder");
                Object builder = ctxBuilder.getDeclaredMethod("create").invoke(null);
                // 允许所有权限
                builder.getClass().getMethod("allowAllAccess", boolean.class).invoke(builder, true);
                context = builder.getClass().getMethod("build").invoke(builder);
            } catch (Exception e) {
                LOGGER.error("[QLM Zombie] GraalPy 初始化失败: {}", e.getMessage());
            }
        }

        @Override public String getName() { return "GraalPy"; }
        @Override public String getPythonVersion() { return "3.x"; }

        @Override
        public void injectAPI(String name, Object api) {
            try {
                Class<?> bindingsClass = Class.forName("org.graalvm.polyglot.Value");
                Object bindings = context.getClass().getMethod("getBindings", String.class)
                        .invoke(context, "python");
                bindings.getClass().getMethod("putMember", String.class, Object.class)
                        .invoke(bindings, name, api);
            } catch (Exception e) {
                LOGGER.error("[QLM Zombie] GraalPy API 注入失败: {}", e.getMessage());
            }
        }

        @Override
        public void execute(String scriptName, String code) throws Exception {
            context.getClass().getMethod("eval", String.class, String.class)
                    .invoke(context, "python", code);
        }
    }

    // ── Jython 后端 (Python 2.7) ──

    static class JythonBackend implements EngineBackend {
        private Object interpreter; // org.python.util.PythonInterpreter

        @SuppressWarnings("unchecked")
        JythonBackend() {
            try {
                Class<?> interpClass = Class.forName("org.python.util.PythonInterpreter");
                interpreter = interpClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                LOGGER.error("[QLM Zombie] Jython 初始化失败: {}", e.getMessage());
            }
        }

        @Override public String getName() { return "Jython"; }
        @Override public String getPythonVersion() { return "2.7"; }

        @Override
        public void injectAPI(String name, Object api) {
            try {
                interpreter.getClass().getMethod("set", String.class, Object.class)
                        .invoke(interpreter, name, api);
            } catch (Exception e) {
                LOGGER.error("[QLM Zombie] Jython API 注入失败: {}", e.getMessage());
            }
        }

        @Override
        public void execute(String scriptName, String code) throws Exception {
            interpreter.getClass().getMethod("exec", String.class).invoke(interpreter, code);
        }
    }

    // ── Jep 后端 (CPython) ──

    static class JepBackend implements EngineBackend {
        private Object jep; // jep.Jep

        JepBackend() {
            try {
                Class<?> jepClass = Class.forName("jep.Jep");
                Class<?> jepConfig = Class.forName("jep.JepConfig");
                Object config = jepConfig.getDeclaredConstructor().newInstance();
                config.getClass().getMethod("setIncludePath", String.class)
                        .invoke(config, FMLPaths.GAMEDIR.get().resolve(SCRIPT_DIR).toString());
                jep = jepClass.getDeclaredConstructor(jepConfig).newInstance(config);
            } catch (Exception e) {
                LOGGER.error("[QLM Zombie] Jep 初始化失败: {}", e.getMessage());
            }
        }

        @Override public String getName() { return "Jep"; }
        @Override public String getPythonVersion() { return "3.x (CPython)"; }

        @Override
        public void injectAPI(String name, Object api) {
            try {
                jep.getClass().getMethod("set", String.class, Object.class).invoke(jep, name, api);
            } catch (Exception e) {
                LOGGER.error("[QLM Zombie] Jep API 注入失败: {}", e.getMessage());
            }
        }

        @Override
        public void execute(String scriptName, String code) throws Exception {
            jep.getClass().getMethod("runScript", String.class).invoke(jep, code);
        }
    }
}

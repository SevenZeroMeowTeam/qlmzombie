package com.qlm.zombie.util;

import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * 反射工具类：兼容 Mojang mappings / SRG 名称 / 类型匹配
 */
public final class ReflectionHelper {

    private ReflectionHelper() {}

    /**
     * 查找字段：依次尝试 Mojang 名 → SRG 名 → ObfuscationReflectionHelper
     */
    public static Field findField(Class<?> clazz, String mojangName, String srgName) {
        // 1. 尝试 Mojang mappings 名称（开发环境 / Official mappings 运行时）
        Field f = tryGetField(clazz, mojangName);
        if (f != null) return f;

        // 2. 尝试 SRG 名称（生产环境运行时）
        if (srgName != null && !srgName.isEmpty()) {
            f = tryGetField(clazz, srgName);
            if (f != null) return f;

            // 3. 使用 Forge 的 ObfuscationReflectionHelper（内部处理映射）
            try {
                f = ObfuscationReflectionHelper.findField(clazz, srgName);
                if (f != null) return f;
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * 按类型查找字段（用于名称无法匹配的情况）
     */
    public static Field findFieldByType(Class<?> clazz, Class<?> fieldType) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getType() == fieldType) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    /**
     * 按类型查找字段（用于 Set 等接口类型匹配）
     */
    public static Field findFieldByAssignableType(Class<?> clazz, Class<?> interfaceType) {
        for (Field f : clazz.getDeclaredFields()) {
            if (interfaceType.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    /**
     * 按类型查找字段，跳过指定名称的字段
     */
    public static Field findFieldByTypeExcluding(Class<?> clazz, Class<?> fieldType, String... excludeNames) {
        outer:
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getType() != fieldType) continue;
            String fname = f.getName();
            for (String ex : excludeNames) {
                if (fname.equals(ex) || fname.contains(ex)) continue outer;
            }
            f.setAccessible(true);
            return f;
        }
        return null;
    }

    private static Field tryGetField(Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}

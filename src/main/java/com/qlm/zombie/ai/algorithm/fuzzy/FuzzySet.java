/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Fuzzy Logic Framework — 原创实现
 * 参考: Zadeh (1965) "Fuzzy Sets"; 《游戏人工智能编程案例精粹》
 *
 * FuzzySet — 模糊集合
 * 用隶属度函数将精确值映射到 [0,1]
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.fuzzy;

/**
 * 模糊集合
 *
 * 隶属度函数类型:
 *   - TRIANGLE: 三角形 (a, b, c) — b 处为 1，a/c 处为 0
 *   - TRAPEZOID: 梯形 (a, b, c, d) — b-c 段为 1
 *   - LEFT_SHOULDER: 左肩 (a, b) — ≤a 为 1，≥b 为 0
 *   - RIGHT_SHOULDER: 右肩 (a, b) — ≤a 为 0，≥b 为 1
 *   - SINGLETON: 单点 (v) — 仅在 v 处为 1
 */
public class FuzzySet {

    public enum Type { TRIANGLE, TRAPEZOID, LEFT_SHOULDER, RIGHT_SHOULDER, SINGLETON }

    private final Type type;
    private final double a, b, c, d;

    private FuzzySet(Type type, double a, double b, double c, double d) {
        this.type = type;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public static FuzzySet triangle(double a, double b, double c) {
        return new FuzzySet(Type.TRIANGLE, a, b, c, 0);
    }

    public static FuzzySet trapezoid(double a, double b, double c, double d) {
        return new FuzzySet(Type.TRAPEZOID, a, b, c, d);
    }

    public static FuzzySet leftShoulder(double a, double b) {
        return new FuzzySet(Type.LEFT_SHOULDER, a, b, 0, 0);
    }

    public static FuzzySet rightShoulder(double a, double b) {
        return new FuzzySet(Type.RIGHT_SHOULDER, a, b, 0, 0);
    }

    public static FuzzySet singleton(double v) {
        return new FuzzySet(Type.SINGLETON, v, 0, 0, 0);
    }

    /** 计算隶属度 μ(x) ∈ [0,1] */
    public double membership(double x) {
        return switch (type) {
            case TRIANGLE -> {
                if (x <= a || x >= c) yield 0.0;
                if (x == b) yield 1.0;
                if (x < b) yield (x - a) / (b - a);
                yield (c - x) / (c - b);
            }
            case TRAPEZOID -> {
                if (x <= a || x >= d) yield 0.0;
                if (x >= b && x <= c) yield 1.0;
                if (x < b) yield (x - a) / (b - a);
                yield (d - x) / (d - c);
            }
            case LEFT_SHOULDER -> {
                if (x <= a) yield 1.0;
                if (x >= b) yield 0.0;
                yield (b - x) / (b - a);
            }
            case RIGHT_SHOULDER -> {
                if (x <= a) yield 0.0;
                if (x >= b) yield 1.0;
                yield (x - a) / (b - a);
            }
            case SINGLETON -> x == a ? 1.0 : 0.0;
        };
    }

    public Type getType() {
        return type;
    }

    public double getA() { return a; }
    public double getB() { return b; }
    public double getC() { return c; }
    public double getD() { return d; }
}

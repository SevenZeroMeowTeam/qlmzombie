/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Utility AI Framework — 原创实现
 * 参考: "Behavioral Mathematics for Game AI" (Dave Mark, 2009)
 *
 * Consideration — 效用考量
 * 将环境输入映射到 [0,1] 区间的"满意度"分值
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.utility;

import java.util.function.DoubleSupplier;

/**
 * 效用考量
 *
 * 一个 Consideration 接收一个 [0,1] 输入，通过 ResponseCurve 转换为最终分值。
 * 例如: 健康度考量 → 当 HP=0 时返回 1.0（急需治疗），HP 满时返回 0.0
 */
public class Consideration {

    private final DoubleSupplier input;
    private final ResponseCurve curve;

    public Consideration(DoubleSupplier input, ResponseCurve curve) {
        this.input = input;
        this.curve = curve;
    }

    public Consideration(DoubleSupplier input) {
        this(input, ResponseCurve.LINEAR);
    }

    /** 计算考量的分值 [0,1] */
    public double evaluate() {
        double raw = input.getAsDouble();
        if (raw < 0) raw = 0;
        if (raw > 1) raw = 1;
        return curve.map(raw);
    }

    /**
     * 响应曲线 — 将 [0,1] 输入映射到 [0,1] 输出
     */
    public enum ResponseCurve {
        LINEAR {
            @Override public double map(double x) { return x; }
        },
        QUADRATIC {
            @Override public double map(double x) { return x * x; }
        },
        LOGISTIC {
            @Override public double map(double x) {
                return 1.0 / (1.0 + Math.exp(-6.0 * (x - 0.5)));
            }
        },
        INVERSE {
            @Override public double map(double x) { return 1.0 - x; }
        },
        INVERSE_QUADRATIC {
            @Override public double map(double x) { return 1.0 - x * x; }
        },
        STEP {
            @Override public double map(double x) { return x >= 0.5 ? 1.0 : 0.0; }
        };

        public abstract double map(double x);
    }
}

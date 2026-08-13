/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * FuzzyVariable — 模糊变量
 * 持有多个 FuzzySet，对输入值进行模糊化
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.fuzzy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模糊变量
 *
 * 例如: 健康变量 = {LOW: leftShoulder(0, 0.4), MEDIUM: triangle(0.2, 0.5, 0.8), HIGH: rightShoulder(0.6, 1.0)}
 */
public class FuzzyVariable {

    private final String name;
    private final Map<String, FuzzySet> sets = new LinkedHashMap<>();
    private double minValue;
    private double maxValue;

    public FuzzyVariable(String name, double minValue, double maxValue) {
        this.name = name;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public FuzzyVariable addSet(String label, FuzzySet set) {
        sets.put(label, set);
        return this;
    }

    /** 模糊化: 将精确值映射为各集合的隶属度 */
    public Map<String, Double> fuzzify(double value) {
        Map<String, Double> memberships = new HashMap<>();
        for (Map.Entry<String, FuzzySet> entry : sets.entrySet()) {
            memberships.put(entry.getKey(), entry.getValue().membership(value));
        }
        return memberships;
    }

    /** 反模糊化: 将模糊输出转为精确值（加权平均法） */
    public double defuzzify(Map<String, Double> memberships) {
        double weightedSum = 0.0;
        double sumWeights = 0.0;

        for (Map.Entry<String, FuzzySet> entry : sets.entrySet()) {
            Double m = memberships.get(entry.getKey());
            if (m == null || m <= 0.0) continue;
            double repValue = representativeValue(entry.getValue());
            weightedSum += m * repValue;
            sumWeights += m;
        }

        return sumWeights > 0.0 ? weightedSum / sumWeights : (minValue + maxValue) / 2.0;
    }

    /** 取集合的代表值（最大隶属度对应的输入值） */
    private double representativeValue(FuzzySet set) {
        return switch (set.getType()) {
            case TRIANGLE, TRAPEZOID -> (set.getB() + set.getC()) / 2.0;
            case LEFT_SHOULDER -> minValue;
            case RIGHT_SHOULDER -> maxValue;
            case SINGLETON -> set.getA();
        };
    }

    public String getName() {
        return name;
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }
}

/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * FuzzyInferenceSystem — 模糊推理系统
 * 模糊化输入 → 应用规则 → 反模糊化输出
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.fuzzy;

import com.qlm.zombie.QLMZombieMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模糊推理系统
 *
 * 推理流程:
 *   1. fuzzify(inputs): 将精确输入转为各变量的隶属度
 *   2. apply rules: 对每条规则计算激活强度
 *   3. aggregate: 将各规则结论聚合（取 max）
 *   4. defuzzify: 反模糊化得到精确输出
 *
 * 用法:
 *   FuzzyInferenceSystem fis = new FuzzyInferenceSystem();
 *   fis.addInputVariable(healthVar);
 *   fis.addOutputVariable(behaviorVar);
 *   fis.addRule(rule);
 *   double result = fis.evaluate(Map.of("health", 0.3));
 */
public class FuzzyInferenceSystem {

    private final Map<String, FuzzyVariable> inputVariables = new HashMap<>();
    private final Map<String, FuzzyVariable> outputVariables = new HashMap<>();
    private final List<FuzzyRule> rules = new ArrayList<>();

    public void addInputVariable(FuzzyVariable variable) {
        inputVariables.put(variable.getName(), variable);
    }

    public void addOutputVariable(FuzzyVariable variable) {
        outputVariables.put(variable.getName(), variable);
    }

    public void addRule(FuzzyRule rule) {
        rules.add(rule);
    }

    /** 评估: 输入精确值 → 输出精确值 */
    public Map<String, Double> evaluate(Map<String, Double> inputs) {
        // 1. 模糊化
        Map<String, Map<String, Double>> fuzzified = new HashMap<>();
        for (Map.Entry<String, Double> entry : inputs.entrySet()) {
            FuzzyVariable var = inputVariables.get(entry.getKey());
            if (var != null) {
                fuzzified.put(entry.getKey(), var.fuzzify(entry.getValue()));
            }
        }

        // 2 & 3. 应用规则并聚合（每个输出变量的每个集合取最大激活强度）
        Map<String, Map<String, Double>> aggregated = new HashMap<>();
        for (FuzzyRule rule : rules) {
            double firing = rule.apply(fuzzified);
            if (firing <= 0.0) continue;
            aggregated.computeIfAbsent(rule.getConclusionVariable(), k -> new HashMap<>())
                    .merge(rule.getConclusionSet(), firing, Math::max);
        }

        // 4. 反模糊化
        Map<String, Double> outputs = new HashMap<>();
        for (Map.Entry<String, FuzzyVariable> entry : outputVariables.entrySet()) {
            Map<String, Double> memberships = aggregated.get(entry.getKey());
            if (memberships == null || memberships.isEmpty()) {
                outputs.put(entry.getKey(), (entry.getValue().getMinValue() + entry.getValue().getMaxValue()) / 2.0);
            } else {
                outputs.put(entry.getKey(), entry.getValue().defuzzify(memberships));
            }
        }

        if (QLMZombieMod.LOGGER.isDebugEnabled()) {
            QLMZombieMod.LOGGER.debug("[Fuzzy] inputs={} -> outputs={}", inputs, outputs);
        }

        return outputs;
    }

    public List<FuzzyRule> getRules() {
        return rules;
    }

    // 便捷方法暴露 FuzzyVariable 的 min/max（用于反模糊化兜底）
    static {
        // 占位: 通过 FuzzyVariable 实例方法访问
    }
}

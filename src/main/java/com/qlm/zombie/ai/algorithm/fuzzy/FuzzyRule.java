/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * FuzzyRule — 模糊规则
 * 形如: IF antecedent THEN conclusion
 * antecedent 是多个 (variable, setLabel) 的合取 (AND)
 * conclusion 是 (variable, setLabel)
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.fuzzy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模糊规则
 *
 * 例如:
 *   IF health IS LOW AND enemy IS NEAR THEN behavior IS FLEE
 *
 * 规则强度 = min(antecedent 各隶属度) — Mamdani 推理
 */
public class FuzzyRule {

    private final List<AntecedentTerm> antecedents = new ArrayList<>();
    private final String conclusionVariable;
    private final String conclusionSet;

    public FuzzyRule(String conclusionVariable, String conclusionSet) {
        this.conclusionVariable = conclusionVariable;
        this.conclusionSet = conclusionSet;
    }

    public FuzzyRule when(String variable, String setLabel) {
        antecedents.add(new AntecedentTerm(variable, setLabel));
        return this;
    }

    /**
     * 应用规则
     * @param fuzzifiedInputs 每个变量的模糊化结果
     * @return 规则的激活强度 ( firingStrength ∈ [0,1] )，若任一前件不存在则返回 0
     */
    public double apply(Map<String, Map<String, Double>> fuzzifiedInputs) {
        double firing = Double.MAX_VALUE;
        for (AntecedentTerm term : antecedents) {
            Map<String, Double> sets = fuzzifiedInputs.get(term.variable);
            if (sets == null) return 0.0;
            Double m = sets.get(term.setLabel);
            if (m == null) return 0.0;
            firing = Math.min(firing, m);
        }
        return firing == Double.MAX_VALUE ? 0.0 : firing;
    }

    public String getConclusionVariable() {
        return conclusionVariable;
    }

    public String getConclusionSet() {
        return conclusionSet;
    }

    private static class AntecedentTerm {
        final String variable;
        final String setLabel;

        AntecedentTerm(String variable, String setLabel) {
            this.variable = variable;
            this.setLabel = setLabel;
        }
    }
}

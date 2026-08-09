package com.qlm.zombie.cloudai.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机昵称池（中英文混合）
 * 生成 AI 追随者的显示名称
 */
public final class NamePool {

    private NamePool() {}

    // 中文昵称前缀
    private static final String[] CN_PREFIX = {
            "小七", "阿白", "云梦", "星河", "夜阑", "青柠", "墨染", "清欢",
            "南鸢", "北梦", "西风", "东篱", "晨曦", "暮色", "流光", "听雪"
    };

    // 中文昵称后缀
    private static final String[] CN_SUFFIX = {
            "喵", "酱", "菌", "先生", "女士", "队长", "副官", "学徒",
            "猎手", "守卫", "游侠", "贤者", "旅人", "侠客", "刺客", "战士"
    };

    // 英文昵称
    private static final String[] EN_NAMES = {
            "Alex", "Steve", "Luna", "Nova", "Echo", "Raven", "Willow", "Sage",
            "Atlas", "Cyrus", "Dante", "Ember", "Freya", "Gwen", "Hazel", "Iris",
            "Juno", "Kira", "Lila", "Mira", "Nora", "Opal", "Piper", "Quinn",
            "Rhea", "Suki", "Tara", "Una", "Vera", "Wren", "Xena", "Yara"
    };

    /** 随机生成一个昵称（中英文各 50% 概率） */
    public static String randomName() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextBoolean()) {
            // 中文: 前缀 + 后缀
            String pre = CN_PREFIX[rand.nextInt(CN_PREFIX.length)];
            String suf = CN_SUFFIX[rand.nextInt(CN_SUFFIX.length)];
            return pre + suf;
        } else {
            // 英文
            return EN_NAMES[rand.nextInt(EN_NAMES.length)];
        }
    }
}

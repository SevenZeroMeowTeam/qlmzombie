/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * LittleSkinClient — 从 https://littleskin.cn/skinlib 抓取随机皮肤
 *
 * 策略：
 *   1. 在线：随机抓取 skinlib 页面（按喜欢/时间排序），正则提取 /skinlib/show/{tid}
 *      链接中的 TID，组装成 https://littleskin.cn/preview/{tid} 皮肤图片 URL。
 *   2. 离线/失败：使用内置的 FALLBACK_TIDS（来自皮肤库高人气皮肤）。
 *   3. 结果同时写入本地 LRU 缓存，避免重复请求。
 *
 * 说明：LittleSkin 官方 Blessing Skin 5.x 的皮肤库 JSON API 不稳定（常 404），
 * 所以这里退化为 HTML 正则抓取；如果未来 API 恢复可改为 JSON 解析。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LittleSkinClient {

    public static final String BASE_URL = "https://littleskin.cn";
    public static final String PREVIEW_URL = BASE_URL + "/preview/%s";
    public static final String SKINLIB_URL = BASE_URL + "/skinlib?filter=skin&uploader=0&sort=%s&page=%d";
    public static final String SORT_LIKES = "likes";
    public static final String SORT_TIME = "time";

    /** skinlib 最大页数（保守估计，超限则 404） */
    private static final int MAX_PAGE = 500;

    /** 单次从在线列表抓取后缓存的 TID 队列（供后续快速取随机） */
    private static final ConcurrentLinkedQueue<String> ONLINE_TID_QUEUE = new ConcurrentLinkedQueue<>();

    /** LRU 缓存，key=tid, value=ture，最近使用的排前，最多 200 条 */
    @SuppressWarnings("serial")
    private static final LinkedHashMap<String, Boolean> RECENT_TIDS = new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
            return size() > 200;
        }
    };

    /** HTML 中 /skinlib/show/123456 模式 */
    private static final Pattern SHOW_TID_PATTERN =
            Pattern.compile("/skinlib/show/(\\d+)");

    /**
     * 内置兜底皮肤 TID — 从 LittleSkin 皮肤库的高人气 / 经典皮肤里挑的。
     * 即使网络不通，也能让每个 AI 玩家有 1/40+ 不同形象。
     *
     * 来源（2026-08-05 抓取）：
     *   https://littleskin.cn/skinlib?filter=skin&sort=likes  Top 20 页面
     */
    private static final String[] FALLBACK_TIDS = {
            // Top 20 喜欢榜
            "15433",     // 彩虹人【外国搬运】      Alex / 110k+ 赞
            "48951",     // 苦力怕娘（钻茜）           Alex / 80k+
            "109478",    // 酔った東京空<3            Alex / 65k+
            "21615",     // Cute                    Alex / 65k+
            "60389",     // gawrgura                Alex / 48k+
            "16688",     // 条纹袜兽耳萝莉-无鞋子      Alex / 47k+
            "17446",     // 蔡徐坤                  Steve / 45k+
            "11034",     // 僵尸                   Steve / 38k+
            "28072",     // ooops                  Alex / 38k+
            "29048",     // 8a87d06f39a53482      Alex / 35k+
            "27248",     // miku酱                  Alex / 35k+
            "43886",     // 花季少女                 Alex / 30k+
            "99100",     // 美西螈                   Alex / 29k+
            "2199",      // 菜鸟                    Steve / 29k+
            "94360",     // 幼猫                    Alex / 29k+
            "28093",     // 宇智波佐助                Steve / 28k+
            "128811",    // 胡桃                    Alex / 26k+
            "29529",     // 电脑人                   Alex / 25k+
            "7170",      // Easy                    Alex / 25k+
            "179",       // NiceG_XiGua             Steve / 24k+
            // 次高人气
            "270",       // Steve 默认变种？
            "85615",     // NewTesta-BH 高画质
            "411302",    // 人皮收藏（透明黑色）     Steve
            "552024",    // 陈伶                    steve
            "685182",    // skin                    Alex
            "360287",    // 蔚蓝之线-冰凌             Alex
            "596018",    // joke_bear / 自嘲熊         Alex
            "831348",    // pasta 明日方舟 意大利面鼠pasta
            "831347",    // 达妮娅 pasta
            "831346",    // c闪贤王吉尔伽美什 pasta
            "831344",    // 贝洛内 pasta
            "831343",    // 伊什塔尔 pasta
            "831342",    // 吉尔伽美什 pasta
            "831330",    // 日々pri3K
            "831328",    // 陈德潘尔
            "831322",    // 猫(自用)
            "831320",    // XingwanAC
            "831319",    // chauu
            "831318",    // 星穹铁道 昔涟
            "831314",    // 猫猫
    };

    private static final String[] CUSTOM_NAMES_LINKS = {
            // 角色名 -> LittleSkin 已有角色名（对应 /skin/{name}.png 传统加载 API）
            // 即使 skinlib 页面抓取失败，也可以尝试 /skin/{name}.png
            "Steve", "Alex", "Him", "Noor", "Sunny", "Ari", "Zuri", "Makena",
            "Kai", "Efe", "XingwanAC", "chahu", "pasta", "joke_bear", "gawrgura",
            "miku", "胡桃", "佐助", "幼猫", "美西螈", "彩虹人", "苦力怕娘"
    };

    private LittleSkinClient() {}

    // ==================== 对外 API ====================

    /**
     * 异步获取一张随机皮肤 URL。
     * 优先在线抓取，失败则使用内置兜底。
     *
     * @return CompletableFuture<String> — 皮肤图片的 URL
     *         （https://littleskin.cn/preview/{tid} 或 https://littleskin.cn/skin/{name}.png）
     */
    public static CompletableFuture<String> fetchRandomSkinAsync() {
        return CompletableFuture.supplyAsync(LittleSkinClient::fetchRandomSkinSync);
    }

    /** 同步版本（建议仅在已有后台线程里调用） */
    public static String fetchRandomSkinSync() {
        // 1) 如果在线队列缓存了 TID，直接消费
        String tid = ONLINE_TID_QUEUE.poll();
        if (tid != null) {
            markUsed(tid);
            return buildPreviewUrl(tid);
        }

        // 2) 在线抓取：随机 sort + 随机页，抓取 1 页（20 条）后塞回队列
        try {
            String sort = Math.random() < 0.5 ? SORT_LIKES : SORT_TIME;
            int page = 1 + ThreadLocalRandom.current().nextInt(MAX_PAGE);
            List<String> tids = scrapeTidsFromPage(sort, page);
            if (tids.isEmpty()) {
                // 失败了再试一次首页
                tids = scrapeTidsFromPage(SORT_LIKES, 1);
            }
            if (!tids.isEmpty()) {
                Collections.shuffle(tids);
                ONLINE_TID_QUEUE.addAll(tids);
                String first = ONLINE_TID_QUEUE.poll();
                if (first != null) {
                    markUsed(first);
                    return buildPreviewUrl(first);
                }
            }
        } catch (Exception e) {
            QLMZombieMod.LOGGER.debug("[LittleSkin] 在线抓取失败，使用兜底: {}", e.getMessage());
        }

        // 3) 兜底：内置 FALLBACK_TIDS（取最近没被用过的）
        return buildPreviewUrl(pickFallbackTid());
    }

    /** 直接取一个兜底皮肤 URL（同步，无网络）。 */
    public static String randomFallbackSkin() {
        return buildPreviewUrl(pickFallbackTid());
    }

    /** 组装 preview URL。 */
    public static String buildPreviewUrl(String tid) {
        return String.format(PREVIEW_URL, tid);
    }

    /** 组装传统加载 API URL（/skin/{playername}.png）。 */
    public static String buildPlayerSkinUrl(String playerName) {
        return BASE_URL + "/skin/" + playerName + ".png";
    }

    // ==================== 内部 ====================

    /**
     * 抓取皮肤库页面，用正则提取所有 TID。
     */
    private static List<String> scrapeTidsFromPage(String sort, int page) throws IOException {
        String urlStr = String.format(SKINLIB_URL, sort, page);
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 QLMZombieMod/1.0");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        int code = conn.getResponseCode();
        if (code != 200) {
            QLMZombieMod.LOGGER.debug("[LittleSkin] HTTP {} for {}", code, urlStr);
            return result;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = SHOW_TID_PATTERN.matcher(line);
                while (m.find()) {
                    String tid = m.group(1);
                    if (seen.add(tid) && !RECENT_TIDS.containsKey(tid)) {
                        result.add(tid);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }

        QLMZombieMod.LOGGER.debug("[LittleSkin] 抓取 {} 得到 {} TIDs", urlStr, result.size());
        return result;
    }

    private static String pickFallbackTid() {
        Random rnd = ThreadLocalRandom.current();
        // 最多试 10 次找一个近期没用过的
        for (int i = 0; i < 10; i++) {
            String t = FALLBACK_TIDS[rnd.nextInt(FALLBACK_TIDS.length)];
            if (!RECENT_TIDS.containsKey(t)) {
                markUsed(t);
                return t;
            }
        }
        String t = FALLBACK_TIDS[rnd.nextInt(FALLBACK_TIDS.length)];
        markUsed(t);
        return t;
    }

    private static void markUsed(String tid) {
        synchronized (RECENT_TIDS) {
            RECENT_TIDS.put(tid, Boolean.TRUE);
        }
    }
}

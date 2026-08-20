// QLM Zombie Mod - Main KubeJS Script
// Handles crafting recipes, tag modifications, and Crafting Dead integration

global.QLM_RECIPES = {
    'zombie_core': {
        ingredients: ['iron_ingot', 'rotten_flesh', 'string'],
        pattern: ['IRI', 'RFR', 'ISI'],
        description: '僵尸核心 - 僵尸掉落的核心物品'
    },
    'infected_essence': {
        ingredients: ['glass_bottle', 'rotten_flesh', 'spider_eye'],
        pattern: ['SGS', 'GRG', 'SES'],
        description: '感染精华 - 用于感染实验'
    },
    'survival_kit': {
        ingredients: ['iron_ingot', 'bread', 'bandage'],
        pattern: ['III', 'BRB', 'IBI'],
        description: '生存工具包'
    },
    'emergency_ration': {
        ingredients: ['bread', 'dried_kelp', 'cooked_beef'],
        pattern: ['BBB', 'DKD', 'BBB'],
        description: '紧急口粮'
    },
    'medical_supply': {
        ingredients: ['paper', 'gold_ingot', 'glass_bottle'],
        pattern: ['PGP', 'GBG', 'PGP'],
        description: '医疗用品'
    },
    'reinforced_parts': {
        ingredients: ['iron_block', 'iron_ingot', 'redstone'],
        pattern: ['IBI', 'IRI', 'IBI'],
        description: '强化部件'
    },
    'biohazard_sample': {
        ingredients: ['glass_bottle', 'rotten_flesh', 'blaze_powder'],
        pattern: ['BGB', 'RBR', 'BPB'],
        description: '生物危害样本'
    },
    'tactical_ammo': {
        ingredients: ['iron_ingot', 'gunpowder', 'copper_ingot'],
        pattern: ['III', 'GCG', 'III'],
        description: '战术弹药'
    },
    'antidote': {
        ingredients: ['glass_bottle', 'sugar', 'spider_eye'],
        pattern: ['SGS', 'GBG', 'SES'],
        description: '解毒剂'
    },
    'plank_axe': {
        ingredients: ['stick', 'planks', 'string'],
        pattern: ['PP', 'PS', ' S'],
        description: '木板斧 - 快速砍树'
    },
    'plank_collector': {
        ingredients: ['chest', 'hopper', 'planks'],
        pattern: ['CHC', 'HPH', 'CHC'],
        description: '木板收集器'
    },
    'purified_water_bottle': {
        ingredients: ['glass_bottle', 'coal', 'water_bucket'],
        pattern: ['GBG', 'CWC', 'GBG'],
        description: '净化水瓶'
    }
};

ServerEvents.recipes(event => {
    // --- QLM Item Crafting Recipes ---
    
    // Zombie Core
    event.shaped('qlmzombie:zombie_core', ['IRI', 'RFR', 'ISI'], {
        I: '#forge:ingots/iron',
        R: 'minecraft:rotten_flesh',
        F: 'minecraft:string',
        S: '#forge:string'
    });

    // Infected Essence
    event.shaped('qlmzombie:infected_essence', ['SGS', 'GRG', 'SES'], {
        S: '#forge:string',
        G: 'minecraft:glass_bottle',
        R: 'minecraft:rotten_flesh',
        E: 'minecraft:spider_eye'
    });

    // Survival Kit
    event.shaped('qlmzombie:survival_kit', ['III', 'BRB', 'IBI'], {
        I: '#forge:ingots/iron',
        B: 'minecraft:bread',
        R: '#forge:rotten_flesh',
    });

    // Emergency Ration
    event.shaped('qlmzombie:emergency_ration', ['BBB', 'DKD', 'BBB'], {
        B: 'minecraft:bread',
        D: 'minecraft:dried_kelp',
        K: 'minecraft:cooked_beef'
    });

    // Medical Supply
    event.shaped('qlmzombie:medical_supply', ['PGP', 'GBG', 'PGP'], {
        P: 'minecraft:paper',
        G: '#forge:ingots/gold',
        B: 'minecraft:glass_bottle'
    });

    // Reinforced Parts
    event.shaped('qlmzombie:reinforced_parts', ['IBI', 'IRI', 'IBI'], {
        I: '#forge:ingots/iron',
        B: '#forge:block/iron',
        R: '#forge:dusts/redstone'
    });

    // Biohazard Sample
    event.shaped('qlmzombie:biohazard_sample', ['BGB', 'RBR', 'BPB'], {
        B: 'minecraft:glass_bottle',
        G: '#forge:blaze',
        R: 'minecraft:rotten_flesh',
        P: 'minecraft:blaze_powder'
    });

    // Tactical Ammo
    event.shaped('qlmzombie:tactical_ammo', ['III', 'GCG', 'III'], {
        I: '#forge:ingots/iron',
        G: '#forge:gunpowder',
        C: '#forge:ingots/copper'
    });

    // Antidote
    event.shaped('qlmzombie:antidote', ['SGS', 'GBG', 'SES'], {
        S: 'minecraft:sugar',
        G: 'minecraft:glass_bottle',
        B: '#forge:milk',
        E: 'minecraft:spider_eye'
    });

    // Plank Axe
    event.shaped('qlmzombie:plank_axe', ['PP', 'PS', ' S'], {
        P: '#minecraft:planks',
        S: '#forge:rods/wooden'
    });

    // Plank Collector
    event.shaped('qlmzombie:plank_collector', ['CHC', 'HPH', 'CHC'], {
        C: 'minecraft:chest',
        H: 'minecraft:hopper',
        P: '#minecraft:planks'
    });

    // Purified Water Bottle
    event.shapeless('qlmzombie:purified_water_bottle', [
        'minecraft:glass_bottle',
        '#forge:charcoal',
        'minecraft:water_bucket'
    ]);

    // 水瓶(水药水)熔炉烧制为纯净水瓶 - 任意水质等级的水瓶均可烧制
    event.custom({
        type: 'minecraft:smelting',
        ingredient: {
            type: 'forge:nbt',
            item: 'minecraft:potion',
            nbt: '{Potion:"minecraft:water"}'
        },
        result: 'qlmzombie:purified_water_bottle',
        experience: 0.1,
        cookingtime: 200
    }).id('qlmzombie:purified_water_bottle_from_water_bottle');

    // Sleeping Bag - 3 wool in a row (only if item is registered)
    if (Ingredient.of('qlmzombie:sleeping_bag').itemIds.length > 0) {
        event.shaped('qlmzombie:sleeping_bag', ['WWW'], {
            W: '#minecraft:wool'
        });
    }

    // --- AI Helper Items ---

    // AI Caller
    event.shaped('qlmzombie:ai_caller', ['QRQ', 'RNR', 'QRQ'], {
        Q: '#forge:nuggets/gold',
        R: '#forge:dusts/redstone',
        N: 'minecraft:name_tag'
    });

    // AI Recover
    event.shaped('qlmzombie:ai_recover', ['HHH', 'HRH', 'HHH'], {
        H: '#forge:heal',
        R: '#forge:rotten_flesh'
    });

    // AI Shield
    event.shaped('qlmzombie:ai_shield', ['III', 'IRI', 'III'], {
        I: '#forge:ingots/iron',
        R: '#forge:dusts/redstone'
    });

    // AI Speed Pill
    event.shaped('qlmzombie:ai_speed_pill', ['SGS', 'GRG', 'SGS'], {
        S: '#forge:sugar',
        G: '#forge:ingots/gold',
        R: '#forge:dusts/redstone'
    });

    // Mode Switch
    event.shaped('qlmzombie:mode_switch', ['ONO', 'NRN', 'ONO'], {
        O: '#forge:obsidian',
        N: 'minecraft:netherite_ingot',
        R: '#forge:dusts/redstone'
    });

    // --- Upgrade / Conversion Recipes ---

    // Rotten Flesh -> Infected Essence conversion
    event.shapeless('qlmzombie:infected_essence', [
        '4x minecraft:rotten_flesh',
        'minecraft:glass_bottle',
        'minecraft:spider_eye'
    ]);

    // Iron Block -> Reinforced Parts
    event.shapeless('qlmzombie:reinforced_parts', [
        '2x #forge:block/iron',
        '#forge:ingots/iron',
        '#forge:dusts/redstone'
    ]);

    // Wooden tools -> Plank Axe conversion
    event.shapeless('qlmzombie:plank_axe', [
        'minecraft:wooden_axe',
        '#minecraft:planks',
        '#forge:string'
    ]);

    // === 下界星核合成（神话合成核心物品） ===
    // 下界合金锭×4 + 钻石×4 + 金苹果×1 → 下界星核
    // 神话装备合成必须消耗 1 个下界星核（核心物品）
    event.shaped('qlmzombie:mythic_core', ['NDN', 'DGD', 'NDN'], {
        N: '#forge:ingots/netherite',
        D: '#forge:gems/diamond',
        G: 'minecraft:golden_apple'
    });

    // === 神话装备合成（中心装备 + 下界星核核心 + 四角下界合金锭 + 钻石） ===
    // 配方产物带 qlm_mythic_forced=true NBT，由 MythicCraftHandler 拦截并赋予神话品质
    //
    // 统一 3×3 模板结构（图示）：
    //   +----------------+----------------+----------------+
    //   | 下界合金锭 (N) |  钻石 (D)      | 下界合金锭 (N) |
    //   +----------------+----------------+----------------+
    //   | 钻石 (D)       |  对应装备 (X)  | 钻石 (D)       |
    //   +----------------+----------------+----------------+
    //   | 下界合金锭 (N) | 下界星核 (C)   | 下界合金锭 (N) |
    //   +----------------+----------------+----------------+
    // 耗材总计：下界合金锭 × 4 + 钻石 × 3 + 下界星核 × 1 + 对应下界合金装备 × 1

    // 神话剑
    event.shaped({ item: 'minecraft:netherite_sword', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_sword',
            C: 'qlmzombie:mythic_core'
        });

    // 神话斧
    event.shaped({ item: 'minecraft:netherite_axe', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_axe',
            C: 'qlmzombie:mythic_core'
        });

    // 神话镐
    event.shaped({ item: 'minecraft:netherite_pickaxe', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_pickaxe',
            C: 'qlmzombie:mythic_core'
        });

    // 神话锹
    event.shaped({ item: 'minecraft:netherite_shovel', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_shovel',
            C: 'qlmzombie:mythic_core'
        });

    // 神话锄
    event.shaped({ item: 'minecraft:netherite_hoe', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_hoe',
            C: 'qlmzombie:mythic_core'
        });

    // 神话弓
    event.shaped({ item: 'minecraft:bow', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:bow',
            C: 'qlmzombie:mythic_core'
        });

    // 神话头盔
    event.shaped({ item: 'minecraft:netherite_helmet', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_helmet',
            C: 'qlmzombie:mythic_core'
        });

    // 神话胸甲
    event.shaped({ item: 'minecraft:netherite_chestplate', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_chestplate',
            C: 'qlmzombie:mythic_core'
        });

    // 神话护腿
    event.shaped({ item: 'minecraft:netherite_leggings', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_leggings',
            C: 'qlmzombie:mythic_core'
        });

    // 神话靴子
    event.shaped({ item: 'minecraft:netherite_boots', nbt: { qlm_mythic_forced: true } },
        ['NDN', 'DXD', 'NCN'],
        {
            N: '#forge:ingots/netherite',
            D: '#forge:gems/diamond',
            X: 'minecraft:netherite_boots',
            C: 'qlmzombie:mythic_core'
        });
});

// --- Tag Modifications ---
ServerEvents.tags('item', event => {
    // Add QLM items to forge tags
    event.add('forge:ingots', 'qlmzombie:zombie_core');
    event.add('forge:ingots/infected', 'qlmzombie:infected_essence');
    event.add('forge:rotten_flesh', 'qlmzombie:infected_essence');
    event.add('forge:food', [
        'qlmzombie:emergency_ration',
        'qlmzombie:purified_water_bottle'
    ]);
    event.add('forge:tools', 'qlmzombie:plank_axe');
    event.add('forge:armor', 'qlmzombie:reinforced_parts');
    event.add('forge:chests', 'qlmzombie:plank_collector');
    event.add('forge:ammo', 'qlmzombie:tactical_ammo');
    event.add('forge:medicine', [
        'qlmzombie:antidote',
        'qlmzombie:medical_supply'
    ]);
    event.add('forge:ai_items', [
        'qlmzombie:ai_caller',
        'qlmzombie:ai_recover',
        'qlmzombie:ai_shield',
        'qlmzombie:ai_speed_pill',
        'qlmzombie:mode_switch'
    ]);

    // Add to Minecraft tags for vanilla compatibility
    event.add('minecraft:mineable/axe', 'qlmzombie:plank_axe');
    event.add('minecraft:mineable/pickaxe', 'qlmzombie:reinforced_parts');
    event.add('minecraft:beacon_payment_items', 'qlmzombie:biohazard_sample');
});

// --- Crafting Dead Integration ---
ServerEvents.recipes(event => {
    if (Platform.isLoaded('craftingdead')) {
        // Crafting Dead - Custom melee weapon crafting
        event.shaped('qlmzombie:infected_essence', ['IRI', 'RFR', 'IRI'], {
            I: '#forge:ingots/infected',
            R: '#forge:rotten_flesh',
            F: 'minecraft:blaze_rod'
        });
    }
});

// --- Player Login Messages ---
PlayerEvents.loggedIn(event => {
    const player = event.player;
    if (!player.stage.getPersistentData().getBoolean('qlm_welcomed')) {
        player.stage.getPersistentData().putBoolean('qlm_welcomed', true);
        player.tell('§6[七零喵僵尸末日] §b欢迎来到末日求生!');
        player.tell('§7- 使用 §b/qlm help§7 查看命令列表');
        player.tell('§7- 初始饥饿值已设置, 注意饮水和食物');
        player.tell('§7- 僵尸在黑暗中更危险, 小心!');
    }
});
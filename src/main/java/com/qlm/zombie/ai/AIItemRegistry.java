package com.qlm.zombie.ai;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * AI物品注册表：中文/英文名称 → Minecraft物品ID映射
 * 使AI玩家可以正确识别玩家用中文描述的物品
 */
public class AIItemRegistry {

    private static final Map<String, String> NAME_TO_ID = new HashMap<>();

    static {
        // === 原木/木材 ===
        register("木头", "minecraft:oak_log");
        register("橡木", "minecraft:oak_log");
        register("橡木原木", "minecraft:oak_log");
        register("云杉木", "minecraft:spruce_log");
        register("云杉原木", "minecraft:spruce_log");
        register("白桦木", "minecraft:birch_log");
        register("白桦原木", "minecraft:birch_log");
        register("丛林木", "minecraft:jungle_log");
        register("丛林原木", "minecraft:jungle_log");
        register("金合欢木", "minecraft:acacia_log");
        register("金合欢原木", "minecraft:acacia_log");
        register("深色橡木", "minecraft:dark_oak_log");
        register("深色橡木原木", "minecraft:dark_oak_log");
        register("红树木", "minecraft:mangrove_log");
        register("樱花木", "minecraft:cherry_log");
        register("绯红菌柄", "minecraft:crimson_stem");
        register("诡异菌柄", "minecraft:warped_stem");
        register("原木", "minecraft:oak_log");
        register("wood", "minecraft:oak_log");
        register("log", "minecraft:oak_log");

        // === 木板 ===
        register("木板", "minecraft:oak_planks");
        register("橡木板", "minecraft:oak_planks");
        register("云杉木板", "minecraft:spruce_planks");
        register("白桦木板", "minecraft:birch_planks");
        register("丛林木板", "minecraft:jungle_planks");
        register("金合欢木板", "minecraft:acacia_planks");
        register("深色橡木板", "minecraft:dark_oak_planks");
        register("planks", "minecraft:oak_planks");
        register("plank", "minecraft:oak_planks");

        // === 木棍/工具 ===
        register("木棍", "minecraft:stick");
        register("棍子", "minecraft:stick");
        register("stick", "minecraft:stick");
        register("火把", "minecraft:torch");
        register("torch", "minecraft:torch");
        register("工作台", "minecraft:crafting_table");
        register("合成台", "minecraft:crafting_table");
        register("crafting_table", "minecraft:crafting_table");
        register("crafting", "minecraft:crafting_table");
        register("熔炉", "minecraft:furnace");
        register("furnace", "minecraft:furnace");
        register("高炉", "minecraft:blast_furnace");
        register("blast_furnace", "minecraft:blast_furnace");
        register("烟熏炉", "minecraft:smoker");
        register("smoker", "minecraft:smoker");
        register("锻造台", "minecraft:smithing_table");
        register("smithing_table", "minecraft:smithing_table");
        register("制箭台", "minecraft:fletching_table");
        register("fletching_table", "minecraft:fletching_table");
        register("切石机", "minecraft:stonecutter");
        register("stonecutter", "minecraft:stonecutter");
        register("砂轮", "minecraft:grindstone");
        register("grindstone", "minecraft:grindstone");
        register("铁砧", "minecraft:anvil");
        register("anvil", "minecraft:anvil");
        register("酿造台", "minecraft:brewing_stand");
        register("brewing_stand", "minecraft:brewing_stand");
        register("织布机", "minecraft:loom");
        register("loom", "minecraft:loom");
        register("制图台", "minecraft:cartography_table");
        register("cartography_table", "minecraft:cartography_table");

        // === 矿物/锭 ===
        register("铁锭", "minecraft:iron_ingot");
        register("铁", "minecraft:iron_ingot");
        register("iron", "minecraft:iron_ingot");
        register("iron_ingot", "minecraft:iron_ingot");
        register("铁矿", "minecraft:iron_ore");
        register("铁矿石", "minecraft:iron_ore");
        register("iron_ore", "minecraft:iron_ore");
        register("粗铁", "minecraft:raw_iron");
        register("raw_iron", "minecraft:raw_iron");
        register("金锭", "minecraft:gold_ingot");
        register("金", "minecraft:gold_ingot");
        register("gold", "minecraft:gold_ingot");
        register("gold_ingot", "minecraft:gold_ingot");
        register("金矿", "minecraft:gold_ore");
        register("金矿石", "minecraft:gold_ore");
        register("gold_ore", "minecraft:gold_ore");
        register("粗金", "minecraft:raw_gold");
        register("raw_gold", "minecraft:raw_gold");
        register("铜锭", "minecraft:copper_ingot");
        register("铜", "minecraft:copper_ingot");
        register("copper", "minecraft:copper_ingot");
        register("copper_ingot", "minecraft:copper_ingot");
        register("铜矿", "minecraft:copper_ore");
        register("铜矿石", "minecraft:copper_ore");
        register("copper_ore", "minecraft:copper_ore");
        register("粗铜", "minecraft:raw_copper");
        register("raw_copper", "minecraft:raw_copper");
        register("钻石", "minecraft:diamond");
        register("diamond", "minecraft:diamond");
        register("钻石矿", "minecraft:diamond_ore");
        register("diamond_ore", "minecraft:diamond_ore");
        register("绿宝石", "minecraft:emerald");
        register("emerald", "minecraft:emerald");
        register("绿宝石矿", "minecraft:emerald_ore");
        register("emerald_ore", "minecraft:emerald_ore");
        register("青金石", "minecraft:lapis_lazuli");
        register("lapis", "minecraft:lapis_lazuli");
        register("lapis_lazuli", "minecraft:lapis_lazuli");
        register("红石", "minecraft:redstone");
        register("红石粉", "minecraft:redstone");
        register("redstone", "minecraft:redstone");
        register("红石矿", "minecraft:redstone_ore");
        register("redstone_ore", "minecraft:redstone_ore");
        register("煤", "minecraft:coal");
        register("煤炭", "minecraft:coal");
        register("coal", "minecraft:coal");
        register("煤矿", "minecraft:coal_ore");
        register("煤矿石", "minecraft:coal_ore");
        register("coal_ore", "minecraft:coal_ore");
        register("木炭", "minecraft:charcoal");
        register("charcoal", "minecraft:charcoal");
        register("下界合金锭", "minecraft:netherite_ingot");
        register("下界合金", "minecraft:netherite_ingot");
        register("netherite", "minecraft:netherite_ingot");
        register("netherite_ingot", "minecraft:netherite_ingot");
        register("下界合金碎片", "minecraft:netherite_scrap");
        register("netherite_scrap", "minecraft:netherite_scrap");
        register("远古残骸", "minecraft:ancient_debris");
        register("ancient_debris", "minecraft:ancient_debris");
        register("石英", "minecraft:quartz");
        register("quartz", "minecraft:quartz");
        register("下界石英矿", "minecraft:nether_quartz_ore");
        register("nether_quartz_ore", "minecraft:nether_quartz_ore");

        // === 武器/工具 ===
        register("剑", "minecraft:iron_sword");
        register("铁剑", "minecraft:iron_sword");
        register("iron_sword", "minecraft:iron_sword");
        register("sword", "minecraft:iron_sword");
        register("木剑", "minecraft:wooden_sword");
        register("wooden_sword", "minecraft:wooden_sword");
        register("石剑", "minecraft:stone_sword");
        register("stone_sword", "minecraft:stone_sword");
        register("钻石剑", "minecraft:diamond_sword");
        register("diamond_sword", "minecraft:diamond_sword");
        register("金剑", "minecraft:golden_sword");
        register("golden_sword", "minecraft:golden_sword");
        register("下界合金剑", "minecraft:netherite_sword");
        register("netherite_sword", "minecraft:netherite_sword");
        register("弓", "minecraft:bow");
        register("bow", "minecraft:bow");
        register("弩", "minecraft:crossbow");
        register("crossbow", "minecraft:crossbow");
        register("箭", "minecraft:arrow");
        register("箭矢", "minecraft:arrow");
        register("arrow", "minecraft:arrow");
        register("三叉戟", "minecraft:trident");
        register("trident", "minecraft:trident");

        register("镐", "minecraft:iron_pickaxe");
        register("镐子", "minecraft:iron_pickaxe");
        register("铁镐", "minecraft:iron_pickaxe");
        register("iron_pickaxe", "minecraft:iron_pickaxe");
        register("pickaxe", "minecraft:iron_pickaxe");
        register("木镐", "minecraft:wooden_pickaxe");
        register("wooden_pickaxe", "minecraft:wooden_pickaxe");
        register("石镐", "minecraft:stone_pickaxe");
        register("stone_pickaxe", "minecraft:stone_pickaxe");
        register("钻石镐", "minecraft:diamond_pickaxe");
        register("diamond_pickaxe", "minecraft:diamond_pickaxe");
        register("金镐", "minecraft:golden_pickaxe");
        register("golden_pickaxe", "minecraft:golden_pickaxe");
        register("下界合金镐", "minecraft:netherite_pickaxe");
        register("netherite_pickaxe", "minecraft:netherite_pickaxe");

        register("斧", "minecraft:iron_axe");
        register("斧头", "minecraft:iron_axe");
        register("铁斧", "minecraft:iron_axe");
        register("iron_axe", "minecraft:iron_axe");
        register("axe", "minecraft:iron_axe");
        register("木斧", "minecraft:wooden_axe");
        register("wooden_axe", "minecraft:wooden_axe");
        register("石斧", "minecraft:stone_axe");
        register("stone_axe", "minecraft:stone_axe");
        register("钻石斧", "minecraft:diamond_axe");
        register("diamond_axe", "minecraft:diamond_axe");

        register("铲", "minecraft:iron_shovel");
        register("铲子", "minecraft:iron_shovel");
        register("铁锹", "minecraft:iron_shovel");
        register("铁铲", "minecraft:iron_shovel");
        register("iron_shovel", "minecraft:iron_shovel");
        register("shovel", "minecraft:iron_shovel");

        register("锄", "minecraft:iron_hoe");
        register("锄头", "minecraft:iron_hoe");
        register("铁锄", "minecraft:iron_hoe");
        register("iron_hoe", "minecraft:iron_hoe");
        register("hoe", "minecraft:iron_hoe");

        register("剪刀", "minecraft:shears");
        register("shears", "minecraft:shears");
        register("打火石", "minecraft:flint_and_steel");
        register("flint_and_steel", "minecraft:flint_and_steel");
        register("钓鱼竿", "minecraft:fishing_rod");
        register("fishing_rod", "minecraft:fishing_rod");
        register("盾牌", "minecraft:shield");
        register("盾", "minecraft:shield");
        register("shield", "minecraft:shield");

        // === 盔甲 ===
        register("头盔", "minecraft:iron_helmet");
        register("铁头盔", "minecraft:iron_helmet");
        register("iron_helmet", "minecraft:iron_helmet");
        register("helmet", "minecraft:iron_helmet");
        register("胸甲", "minecraft:iron_chestplate");
        register("铁胸甲", "minecraft:iron_chestplate");
        register("iron_chestplate", "minecraft:iron_chestplate");
        register("chestplate", "minecraft:iron_chestplate");
        register("护腿", "minecraft:iron_leggings");
        register("铁护腿", "minecraft:iron_leggings");
        register("iron_leggings", "minecraft:iron_leggings");
        register("leggings", "minecraft:iron_leggings");
        register("靴子", "minecraft:iron_boots");
        register("铁靴子", "minecraft:iron_boots");
        register("iron_boots", "minecraft:iron_boots");
        register("boots", "minecraft:iron_boots");
        register("钻石头盔", "minecraft:diamond_helmet");
        register("钻石胸甲", "minecraft:diamond_chestplate");
        register("钻石护腿", "minecraft:diamond_leggings");
        register("钻石靴子", "minecraft:diamond_boots");
        register("皮革帽子", "minecraft:leather_helmet");
        register("皮革外套", "minecraft:leather_chestplate");
        register("皮革裤子", "minecraft:leather_leggings");
        register("皮革靴子", "minecraft:leather_boots");
        register("皮革", "minecraft:leather");
        register("leather", "minecraft:leather");

        // === 食物 ===
        register("面包", "minecraft:bread");
        register("bread", "minecraft:bread");
        register("苹果", "minecraft:apple");
        register("apple", "minecraft:apple");
        register("金苹果", "minecraft:golden_apple");
        register("golden_apple", "minecraft:golden_apple");
        register("附魔金苹果", "minecraft:enchanted_golden_apple");
        register("生牛肉", "minecraft:raw_beef");
        register("raw_beef", "minecraft:raw_beef");
        register("牛排", "minecraft:cooked_beef");
        register("熟牛肉", "minecraft:cooked_beef");
        register("cooked_beef", "minecraft:cooked_beef");
        register("生猪排", "minecraft:raw_porkchop");
        register("raw_porkchop", "minecraft:raw_porkchop");
        register("熟猪排", "minecraft:cooked_porkchop");
        register("cooked_porkchop", "minecraft:cooked_porkchop");
        register("生鸡肉", "minecraft:raw_chicken");
        register("熟鸡肉", "minecraft:cooked_chicken");
        register("生羊肉", "minecraft:raw_mutton");
        register("熟羊肉", "minecraft:cooked_mutton");
        register("生鳕鱼", "minecraft:raw_cod");
        register("熟鳕鱼", "minecraft:cooked_cod");
        register("生鲑鱼", "minecraft:raw_salmon");
        register("熟鲑鱼", "minecraft:cooked_salmon");
        register("胡萝卜", "minecraft:carrot");
        register("carrot", "minecraft:carrot");
        register("土豆", "minecraft:potato");
        register("potato", "minecraft:potato");
        register("烤土豆", "minecraft:baked_potato");
        register("baked_potato", "minecraft:baked_potato");
        register("小麦", "minecraft:wheat");
        register("wheat", "minecraft:wheat");
        register("种子", "minecraft:wheat_seeds");
        register("seed", "minecraft:wheat_seeds");
        register("wheat_seeds", "minecraft:wheat_seeds");
        register("西瓜", "minecraft:melon_slice");
        register("melon", "minecraft:melon_slice");
        register("南瓜", "minecraft:pumpkin");
        register("pumpkin", "minecraft:pumpkin");
        register("甘蔗", "minecraft:sugar_cane");
        register("sugar_cane", "minecraft:sugar_cane");
        register("甜菜根", "minecraft:beetroot");
        register("beetroot", "minecraft:beetroot");
        register("蛋糕", "minecraft:cake");
        register("cake", "minecraft:cake");
        register("曲奇", "minecraft:cookie");
        register("cookie", "minecraft:cookie");

        // === 建筑方块 ===
        register("石头", "minecraft:stone");
        register("stone", "minecraft:stone");
        register("圆石", "minecraft:cobblestone");
        register("cobblestone", "minecraft:cobblestone");
        register("泥土", "minecraft:dirt");
        register("dirt", "minecraft:dirt");
        register("草方块", "minecraft:grass_block");
        register("grass_block", "minecraft:grass_block");
        register("沙子", "minecraft:sand");
        register("sand", "minecraft:sand");
        register("沙砾", "minecraft:gravel");
        register("gravel", "minecraft:gravel");
        register("玻璃", "minecraft:glass");
        register("glass", "minecraft:glass");
        register("玻璃板", "minecraft:glass_pane");
        register("glass_pane", "minecraft:glass_pane");
        register("砖", "minecraft:brick");
        register("砖块", "minecraft:bricks");
        register("brick", "minecraft:brick");
        register("bricks", "minecraft:bricks");
        register("石砖", "minecraft:stone_bricks");
        register("stone_bricks", "minecraft:stone_bricks");
        register("平滑石", "minecraft:smooth_stone");
        register("smooth_stone", "minecraft:smooth_stone");
        register("楼梯", "minecraft:oak_stairs");
        register("台阶", "minecraft:oak_slab");
        register("栅栏", "minecraft:oak_fence");
        register("门", "minecraft:oak_door");
        register("oak_door", "minecraft:oak_door");
        register("陷阱门", "minecraft:oak_trapdoor");
        register("箱子", "minecraft:chest");
        register("chest", "minecraft:chest");
        register("陷阱箱", "minecraft:trapped_chest");
        register("trapped_chest", "minecraft:trapped_chest");
        register("床", "minecraft:red_bed");
        register("bed", "minecraft:red_bed");
        register("书架", "minecraft:bookshelf");
        register("bookshelf", "minecraft:bookshelf");
        register("附魔台", "minecraft:enchanting_table");
        register("enchanting_table", "minecraft:enchanting_table");
        register("黑曜石", "minecraft:obsidian");
        register("obsidian", "minecraft:obsidian");
        register("TNT", "minecraft:tnt");
        register("tnt", "minecraft:tnt");
        register("火药", "minecraft:gunpowder");
        register("gunpowder", "minecraft:gunpowder");
        register("灯笼", "minecraft:lantern");
        register("lantern", "minecraft:lantern");
        register("信标", "minecraft:beacon");
        register("beacon", "minecraft:beacon");

        // === 杂项材料 ===
        register("骨头", "minecraft:bone");
        register("bone", "minecraft:bone");
        register("骨粉", "minecraft:bone_meal");
        register("bone_meal", "minecraft:bone_meal");
        register("线", "minecraft:string");
        register("string", "minecraft:string");
        register("羽毛", "minecraft:feather");
        register("feather", "minecraft:feather");
        register("燧石", "minecraft:flint");
        register("flint", "minecraft:flint");
        register("粘液球", "minecraft:slime_ball");
        register("slime_ball", "minecraft:slime_ball");
        register("末影珍珠", "minecraft:ender_pearl");
        register("ender_pearl", "minecraft:ender_pearl");
        register("末影之眼", "minecraft:ender_eye");
        register("ender_eye", "minecraft:ender_eye");
        register("末影箱", "minecraft:ender_chest");
        register("ender_chest", "minecraft:ender_chest");
        register("皮革", "minecraft:leather");
        register("羊毛", "minecraft:white_wool");
        register("wool", "minecraft:white_wool");
        register("纸", "minecraft:paper");
        register("paper", "minecraft:paper");
        register("书", "minecraft:book");
        register("book", "minecraft:book");
        register("书与笔", "minecraft:writable_book");
        register("指南针", "minecraft:compass");
        register("compass", "minecraft:compass");
        register("地图", "minecraft:map");
        register("map", "minecraft:map");
        register("时钟", "minecraft:clock");
        register("clock", "minecraft:clock");
        register("桶", "minecraft:bucket");
        register("bucket", "minecraft:bucket");
        register("水桶", "minecraft:water_bucket");
        register("water_bucket", "minecraft:water_bucket");
        register("岩浆桶", "minecraft:lava_bucket");
        register("lava_bucket", "minecraft:lava_bucket");
        register("牛奶桶", "minecraft:milk_bucket");
        register("milk_bucket", "minecraft:milk_bucket");
        register("鞍", "minecraft:saddle");
        register("saddle", "minecraft:saddle");
        register("命名牌", "minecraft:name_tag");
        register("name_tag", "minecraft:name_tag");
        register("经验瓶", "minecraft:experience_bottle");
        register("experience_bottle", "minecraft:experience_bottle");
        register("腐肉", "minecraft:rotten_flesh");
        register("rotten_flesh", "minecraft:rotten_flesh");
        register("蜘蛛眼", "minecraft:spider_eye");
        register("末影石", "minecraft:end_stone");
        register("末地石", "minecraft:end_stone");
        register("end_stone", "minecraft:end_stone");
        register("下界岩", "minecraft:netherrack");
        register("netherrack", "minecraft:netherrack");
        register("灵魂沙", "minecraft:soul_sand");
        register("soul_sand", "minecraft:soul_sand");
        register("岩浆膏", "minecraft:magma_cream");
        register("magma_cream", "minecraft:magma_cream");
        register("烈焰棒", "minecraft:blaze_rod");
        register("blaze_rod", "minecraft:blaze_rod");
        register("烈焰粉", "minecraft:blaze_powder");
        register("blaze_powder", "minecraft:blaze_powder");
        register("恶魂之泪", "minecraft:ghast_tear");
        register("ghast_tear", "minecraft:ghast_tear");
        register("下界之星", "minecraft:nether_star");
        register("nether_star", "minecraft:nether_star");
        register("恶魂之泪", "minecraft:ghast_tear");
        register("萤石粉", "minecraft:glowstone_dust");
        register("glowstone_dust", "minecraft:glowstone_dust");
        register("萤石", "minecraft:glowstone");
        register("glowstone", "minecraft:glowstone");
        register("红石火把", "minecraft:redstone_torch");
        register("redstone_torch", "minecraft:redstone_torch");
        register("红石中继器", "minecraft:repeater");
        register("repeater", "minecraft:repeater");
        register("红石比较器", "minecraft:comparator");
        register("comparator", "minecraft:comparator");
        register("发射器", "minecraft:dispenser");
        register("投掷器", "minecraft:dropper");
        register("活塞", "minecraft:piston");
        register("piston", "minecraft:piston");
        register("粘性活塞", "minecraft:sticky_piston");
        register("漏斗", "minecraft:hopper");
        register("hopper", "minecraft:hopper");
        register("阳光传感器", "minecraft:daylight_detector");
        register("按钮", "minecraft:oak_button");
        register("压力板", "minecraft:oak_pressure_plate");
        register("拉杆", "minecraft:lever");
        register("lever", "minecraft:lever");
        register("红石块", "minecraft:redstone_block");
        register("redstone_block", "minecraft:redstone_block");
    }

    private static void register(String alias, String itemId) {
        NAME_TO_ID.put(alias.toLowerCase(), itemId);
    }

    /**
     * 通过名称查找物品ID
     * @param name 中文名/英文名/registry ID
     * @return Minecraft物品ID (如 "minecraft:iron_ingot")，未找到返回null
     */
    public static String findItemId(String name) {
        if (name == null || name.isEmpty()) return null;

        String lower = name.toLowerCase().trim();

        // 直接匹配注册表
        if (NAME_TO_ID.containsKey(lower)) {
            return NAME_TO_ID.get(lower);
        }

        // 尝试作为ResourceLocation直接解析
        if (lower.contains(":")) {
            ResourceLocation rl = ResourceLocation.tryParse(lower);
            if (rl != null) {
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null) {
                    return ForgeRegistries.ITEMS.getKey(item).toString();
                }
            }
        }

        // 模糊匹配：遍历注册表找包含关系的
        for (Map.Entry<String, String> entry : NAME_TO_ID.entrySet()) {
            if (lower.contains(entry.getKey()) || entry.getKey().contains(lower)) {
                return entry.getValue();
            }
        }

        // 尝试不带namespace的匹配
        String withMinecraft = "minecraft:" + lower;
        ResourceLocation rl = ResourceLocation.tryParse(withMinecraft);
        if (rl != null) {
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null) {
                return ForgeRegistries.ITEMS.getKey(item).toString();
            }
        }

        return null;
    }

    /**
     * 查找物品对象
     */
    public static Item findItem(String name) {
        String id = findItemId(name);
        if (id == null) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        return ForgeRegistries.ITEMS.getValue(rl);
    }

    /**
     * 检查物品名称是否匹配
     * @param itemName 玩家输入的名称
     * @param itemRegistryId 物品的registry ID
     * @return 是否匹配
     */
    public static boolean matches(String itemName, String itemRegistryId) {
        if (itemName == null || itemRegistryId == null) return false;

        String resolved = findItemId(itemName);
        if (resolved != null) {
            return resolved.equals(itemRegistryId);
        }

        // 回退到包含匹配
        String lower = itemName.toLowerCase();
        return itemRegistryId.toLowerCase().contains(lower) ||
               lower.contains(itemRegistryId.split(":")[1].toLowerCase());
    }

    // === Tag 动态识别（支持所有模组的工具/武器/盔甲） ===

    /**
     * 通过类型关键词查找物品 — 参考 TLM 的 Tag 识别模式
     * 支持的类型：pickaxe/axe/shovel/hoe/sword/bow/helmet/chestplate/leggings/boots/armor/weapon/tool
     * @param typeKeyword 类型关键词（中英文均可）
     * @return 匹配的第一个物品ID，未找到返回null
     */
    public static String findItemIdByType(String typeKeyword) {
        if (typeKeyword == null || typeKeyword.isEmpty()) return null;
        String lower = typeKeyword.toLowerCase().trim();

        String tagType = resolveTagType(lower);
        if (tagType == null) return null;

        // 遍历注册表找第一个匹配Tag的物品
        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack stack = new ItemStack(item);
            if (matchesTagType(stack, tagType)) {
                return ForgeRegistries.ITEMS.getKey(item).toString();
            }
        }
        return null;
    }

    /** 解析中文/英文类型关键词为标准 Tag 类型 */
    private static String resolveTagType(String lower) {
        if (lower.contains("镐") || lower.contains("pickaxe")) return "pickaxe";
        if (lower.contains("斧") && !lower.contains("盔")) return "axe";
        if (lower.contains("铲") || lower.contains("锹") || lower.contains("shovel")) return "shovel";
        if (lower.contains("锄") || lower.contains("hoe")) return "hoe";
        if (lower.contains("剑") || lower.contains("sword") || lower.contains("刀")) return "sword";
        if (lower.contains("弓") || lower.contains("bow") || lower.contains("弩") || lower.contains("crossbow")) return "bow";
        if (lower.contains("头盔") || lower.contains("helmet")) return "helmet";
        if (lower.contains("胸甲") || lower.contains("chestplate")) return "chestplate";
        if (lower.contains("护腿") || lower.contains("leggings")) return "leggings";
        if (lower.contains("靴") || lower.contains("boots")) return "boots";
        if (lower.contains("盔甲") || lower.contains("armor") || lower.contains("armour")) return "armor";
        if (lower.contains("武器") || lower.contains("weapon")) return "weapon";
        if (lower.contains("工具") || lower.contains("tool")) return "tool";
        return null;
    }

    /** 检查物品是否匹配指定 Tag 类型 */
    public static boolean matchesTagType(ItemStack stack, String tagType) {
        if (stack.isEmpty()) return false;
        switch (tagType) {
            case "pickaxe": return EquipmentHelper.isPickaxe(stack);
            case "axe": return EquipmentHelper.isAxe(stack);
            case "shovel": return EquipmentHelper.isShovel(stack);
            case "hoe": return EquipmentHelper.isHoe(stack);
            case "sword": return EquipmentHelper.isSword(stack);
            case "bow": return EquipmentHelper.isBow(stack);
            case "helmet": return EquipmentHelper.isHelmet(stack);
            case "chestplate": return EquipmentHelper.isChestplate(stack);
            case "leggings": return EquipmentHelper.isLeggings(stack);
            case "boots": return EquipmentHelper.isBoots(stack);
            case "armor": return EquipmentHelper.isArmor(stack);
            case "weapon": return EquipmentHelper.isWeapon(stack);
            case "tool": return EquipmentHelper.isTool(stack);
            default: return false;
        }
    }
}

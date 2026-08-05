package com.qlm.zombie.loot;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * QLM 全局战利品修改器注册。
 *
 * 通过 data/qlmzombie/loot_modifiers/*.json 声明，把"武器 / 装备 / 弹药"
 * 注入到所有可能的建筑物宝箱（沙漠神殿、丛林神殿、村庄房屋、雪屋、前哨站、
 * 古城、废弃矿井、沉船、末地城、地牢等等）。
 *
 * 这样即使其他 mod 新增建筑，也可以在 JSON 中追加目标 loot_table_id
 * 即可生效，无需改动 Java 代码。
 */
public class QLMGlobalLootModifiers {

    public static final String MOD_ID = "qlmzombie";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> BUILDING_WEAPON =
            LOOT_MODIFIER_SERIALIZERS.register("building_weapon", () -> BuildingWeaponLootModifier.CODEC);

    public static class BuildingWeaponLootModifier extends LootModifier {

        public static final Codec<BuildingWeaponLootModifier> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                LootPoolEntry.CODEC.listOf().fieldOf("entries").forGetter(m -> m.entries),
                Codec.INT.fieldOf("rolls").forGetter(m -> m.rolls),
                Codec.INT.fieldOf("bonusRolls").forGetter(m -> m.bonusRolls),
                Codec.FLOAT.fieldOf("injectChance").forGetter(m -> m.injectChance),
                Codec.STRING.listOf().optionalFieldOf("scanNamespaces", List.of()).forGetter(m -> m.scanNamespaces),
                Codec.INT.fieldOf("scanWeight").forGetter(m -> m.scanWeight),
                Codec.INT.fieldOf("scanMinCount").forGetter(m -> m.scanMinCount),
                Codec.INT.fieldOf("scanMaxCount").forGetter(m -> m.scanMaxCount)
        ).apply(inst, (entries, rolls, bonusRolls, injectChance, scanNamespaces, scanWeight, scanMinCount, scanMaxCount) ->
                new BuildingWeaponLootModifier(new LootItemCondition[0], entries, rolls, bonusRolls, injectChance, scanNamespaces, scanWeight, scanMinCount, scanMaxCount)
        ));

        public BuildingWeaponLootModifier(LootItemCondition[] conditionsIn,
                                           List<LootPoolEntry> entries,
                                           int rolls,
                                           int bonusRolls,
                                           float injectChance,
                                           List<String> scanNamespaces,
                                           int scanWeight,
                                           int scanMinCount,
                                           int scanMaxCount) {
            super(conditionsIn);
            this.entries = entries;
            this.rolls = rolls;
            this.bonusRolls = bonusRolls;
            this.injectChance = injectChance;
            this.scanNamespaces = scanNamespaces != null ? scanNamespaces : List.of();
            this.scanWeight = scanWeight;
            this.scanMinCount = scanMinCount;
            this.scanMaxCount = scanMaxCount;
        }

        final List<LootPoolEntry> entries;
        final int rolls;
        final int bonusRolls;
        final float injectChance;
        final List<String> scanNamespaces;
        final int scanWeight;
        final int scanMinCount;
        final int scanMaxCount;

        /** 已解析到的有效条目（带实际物品引用），仅在首次需要时计算一次。 */
        private volatile List<WeightedResolvedEntry> resolvedEntries;
        private final AtomicBoolean warnedAboutEmpty = new AtomicBoolean(false);

        public List<LootPoolEntry> getEntries() { return entries; }
        public int getRolls() { return rolls; }
        public int getBonusRolls() { return bonusRolls; }
        public float getInjectChance() { return injectChance; }
        public List<String> getScanNamespaces() { return scanNamespaces; }
        public int getScanWeight() { return scanWeight; }
        public int getScanMinCount() { return scanMinCount; }
        public int getScanMaxCount() { return scanMaxCount; }

        /** 记录：已经解析到的物品 + 权重 + 数量范围 */
        private record WeightedResolvedEntry(Item item, int weight, int minCount, int maxCount) {}

        private List<WeightedResolvedEntry> getResolvedEntries() {
            if (resolvedEntries != null) return resolvedEntries;
            synchronized (this) {
                if (resolvedEntries != null) return resolvedEntries;
                List<WeightedResolvedEntry> list = new java.util.ArrayList<>(entries.size());
                for (LootPoolEntry e : entries) {
                    Optional<Item> resolved = e.resolveItem();
                    if (resolved.isPresent()) {
                        list.add(new WeightedResolvedEntry(resolved.get(),
                                Math.max(1, e.weight()),
                                Math.max(1, e.minCount()),
                                Math.max(e.minCount(), e.maxCount())));
                    }
                }

                for (String namespace : scanNamespaces) {
                    scanNamespaceItems(namespace, list);
                }

                resolvedEntries = list;
                if (resolvedEntries.isEmpty() && warnedAboutEmpty.compareAndSet(false, true)) {
                    LOGGER.warn("[QLM Zombie] building_weapon 战利品修改器中没有条目可解析为已注册物品，"
                            + "已跳过注入。请确认 TaCZ / SpartanWeaponry 等 mod 已正确安装。");
                }
                return resolvedEntries;
            }
        }

        private void scanNamespaceItems(String namespace, List<WeightedResolvedEntry> list) {
            Set<ResourceLocation> existingIds = new HashSet<>();
            for (WeightedResolvedEntry e : list) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.item());
                if (id != null) {
                    existingIds.add(id);
                }
            }

            int count = 0;
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id != null && namespace.equals(id.getNamespace()) && item != net.minecraft.world.item.Items.AIR) {
                    if (existingIds.contains(id)) {
                        continue;
                    }

                    String path = id.getPath();
                    if (isExcludedItem(path)) {
                        continue;
                    }

                    ItemType type = classifyItemType(path);
                    int weight = type.weight;
                    int minCount = type.minCount;
                    int maxCount = type.maxCount;

                    list.add(new WeightedResolvedEntry(item, weight, minCount, maxCount));
                    count++;
                }
            }
            if (count > 0) {
                LOGGER.info("[QLM Zombie] 动态扫描命名空间 {} 发现 {} 个物品", namespace, count);
            }
        }

        private boolean isExcludedItem(String path) {
            String lower = path.toLowerCase();
            String[] excluded = {
                    "craftskill", "craft_skill", "blueprint", "manual", "guide",
                    "recipe", "token", "coin", "currency", "upgrade", "part",
                    "scrap", "junk", "broken", "damaged", "worn", "dirty",
                    "box", "case", "container", "bag", "sack", "pack",
                    "fuel", "oil", "gas", "can", "tank", "barrel_item",
                    "empty", "placeholder", "debug", "test", "dummy",
                    "frame", "base", "component", "material", "ingot", "ore",
                    "wood", "stone", "cloth", "fiber", "thread", "leather",
                    "plastic", "metal", "steel", "iron", "copper", "aluminum",
                    "zinc", "lead", "brass", "titanium", "ceramic", "glass",
                    "paper", "cardboard", "rubber", "silicon", "circuit",
                    "chip", "board", "wire", "cable", "tube", "pipe",
                    "spring", "screw", "bolt", "nut", "washer", "rivet",
                    "glue", "adhesive", "tape", "string", "rope", "chain",
                    "paint", "enamel", "coating", "finish", "polish", "cleaner",
                    "salt", "sugar", "flour", "water", "food", "meat", "bread",
                    "vegetable", "fruit", "seed", "plant", "herb", "mushroom",
                    "bone", "tooth", "horn", "scale", "feather", "fur",
                    "skin", "hide", "pelt", "wool", "silk", "cotton",
                    "dye", "ink", "wax", "resin", "gum", "tar",
                    "ash", "charcoal", "coal", "sulfur", "nitrate", "saltpeter",
                    "acid", "alkali", "solution", "compound", "mixture", "powder",
                    "crystal", "gem", "diamond", "emerald", "ruby", "sapphire",
                    "pearl", "shell", "coral", "amber", "obsidian", "quartz",
                    "ice", "snow", "slime", "lava", "magma", "steam",
                    "bottle", "jar", "cup", "bowl", "plate", "spoon",
                    "fork", "knife", "chopstick", "can opener", "tool",
                    "hammer", "wrench", "screwdriver", "pliers", "saw", "drill",
                    "file", "grinder", "polisher", "sandpaper", "brush", "sponge",
                    "bucket", "pot", "pan", "kettle", "cooker", "oven",
                    "fridge", "freezer", "heater", "cooler", "fan", "pump",
                    "generator", "battery_pack", "charger", "transformer", "inverter",
                    "switch", "button", "lever", "dial", "gauge", "meter",
                    "light", "lamp", "bulb", "candle", "torch", "lantern",
                    "mirror", "glass_pane", "window", "door", "gate", "fence",
                    "wall", "floor", "roof", "ceiling", "pillar", "beam",
                    "brick", "block", "tile", "slab", "stair", "step",
                    "ladder", "rope_ladder", "elevator", "escalator", "conveyor",
                    "pipe_item", "tube_item", "hose", "valve", "faucet", "tap",
                    "filter", "sieve", "screen", "mesh", "net", "wire_net",
                    "bag_item", "backpack", "satchel", "pouch", "wallet", "purse",
                    "lock", "key", "keycard", "badge", "tag", "label",
                    "sign", "poster", "billboard", "banner", "flag", "banner_pattern",
                    "book", "scroll", "map", "chart", "diagram", "blueprint_item",
                    "pen", "pencil", "marker", "crayon", "chalk", "stamp",
                    "envelope", "letter", "mail", "package", "parcel", "box_item",
                    "trash", "garbage", "waste", "rubbish", "debris", "scrap_item",
                    "ash_item", "dust", "sand", "dirt", "mud", "clay",
                    "rock", "gravel", "pebble", "boulder", "cobblestone", "stone_item",
                    "wood_item", "log_item", "plank", "stick", "twig", "leaf",
                    "flower", "plant_item", "seed_item", "sapling", "tree", "bush",
                    "grass", "moss", "lichen", "algae", "seaweed", "coral_item",
                    "fish", "meat_item", "egg", "milk", "cheese", "butter",
                    "bread_item", "cake", "cookie", "candy", "chocolate", "ice_cream",
                    "juice", "soda", "beer", "wine", "whiskey", "vodka",
                    "coffee", "tea", "cocoa", "chai", "mate", "kombucha",
                    "soap", "shampoo", "conditioner", "lotion", "cream", "perfume",
                    "medicine", "pill", "tablet", "syrup", "ointment", "bandage_item",
                    "needle", "thread_item", "scissors", "sewing", "knitting", "crochet",
                    "jewelry", "ring", "necklace", "bracelet", "earring", "brooch",
                    "watch", "clock", "timer", "stopwatch", "alarm", "bell",
                    "music", "instrument", "drum", "guitar", "flute", "trumpet",
                    "camera", "video", "recorder", "microphone", "speaker", "headphone",
                    "telephone", "radio", "tv", "monitor", "screen_item", "projector",
                    "computer", "laptop", "tablet_item", "phone", "smartphone", "pda",
                    "game", "console", "controller", "joystick", "keyboard", "mouse",
                    "disk", "cd", "dvd", "usb", "flash_drive", "hard_drive",
                    "cable_item", "wire_item", "plug", "socket", "adapter", "converter",
                    "battery_item", "cell", "battery_cell", "power_cell", "energy_cell",
                    "fuel_cell", "solar", "wind", "water_turbine", "steam_engine",
                    "engine", "motor", "gear", "shaft", "piston", "crank",
                    "wheel", "tire", "rim", "hub", "spoke", "axle",
                    "frame_item", "chassis", "body", "cabin", "cockpit", "hull",
                    "wing", "tail", "propeller", "jet", "rocket_engine", "thruster",
                    "parachute_item", "paraglider", "hang_glider", "balloon", "airship", "zeppelin",
                    "boat_item", "ship", "submarine", "torpedo", "depth_charge", "mine_item",
                    "tank_item", "armor_vehicle", "truck", "car", "bike", "motorcycle",
                    "helicopter", "plane", "airplane", "drone", "missile_item", "rocket_item"
            };
            for (String kw : excluded) {
                if (lower.contains(kw)) return true;
            }
            return false;
        }

        private ItemType classifyItemType(String path) {
            String lower = path.toLowerCase();
            if (lower.contains("rifle") || lower.contains("pistol") || lower.contains("gun") ||
                lower.contains("sniper") || lower.contains("shotgun") || lower.contains("smg") ||
                lower.contains("machine") || lower.contains("assault") || lower.contains("cannon") ||
                lower.contains("artillery") || lower.contains("mortar") || lower.contains("bazooka") ||
                lower.contains("launcher") || lower.contains("minigun") || lower.contains("automatic") ||
                lower.contains("semiauto") || lower.contains("revolver") || lower.contains("derringer") ||
                lower.contains("submachine") || lower.contains("carbine") || lower.contains("battle_rifle") ||
                lower.contains("designated") || lower.contains("marksman") || lower.contains("anti_materiel") ||
                lower.contains("grenade_launcher") || lower.contains("rocket_launcher") || lower.contains("flamethrower")) {
                return ItemType.WEAPON;
            }
            if (lower.contains("9mm") || lower.contains("45_acp") || lower.contains("556") ||
                lower.contains("762") || lower.contains("300") || lower.contains("12gauge") ||
                lower.contains("ammo") || lower.contains("bullet") || lower.contains("shell") ||
                lower.contains("cartridge") || lower.contains("magazine") || lower.contains("clip") ||
                lower.contains("round") || lower.contains("charge") || lower.contains("powder") ||
                lower.contains("ball") || lower.contains("slug") || lower.contains("buckshot") ||
                lower.contains("shot") || lower.contains("pellet") || lower.contains("dart") ||
                lower.contains("arrow") || lower.contains("bolt") || lower.contains("missile") ||
                lower.contains("rocket")) {
                return ItemType.AMMO;
            }
            if (lower.contains("scope") || lower.contains("grip") || lower.contains("stock") ||
                lower.contains("silencer") || lower.contains("barrel") || lower.contains("laser") ||
                lower.contains("flashlight") || lower.contains("attachment") || lower.contains("mod") ||
                lower.contains("sight") || lower.contains("magnifier") || lower.contains("holo") ||
                lower.contains("dot") || lower.contains("extended") || lower.contains("suppressor") ||
                lower.contains("bipod") || lower.contains("foregrip") || lower.contains("handguard") ||
                lower.contains("rail") || lower.contains("mount") || lower.contains("adapter") ||
                lower.contains("compensator") || lower.contains("muzzle") || lower.contains("choke") ||
                lower.contains("sling") || lower.contains("cheek_rest") || lower.contains("buttpad")) {
                return ItemType.ACCESSORY;
            }
            if (lower.contains("grenade") || lower.contains("bomb") || lower.contains("c4") ||
                lower.contains("explosive") || lower.contains("tnt") || lower.contains("dynamite") ||
                lower.contains("molotov") || lower.contains("smoke") || lower.contains("flash") ||
                lower.contains("flare") || lower.contains("signal") || lower.contains("landmine") ||
                lower.contains("claymore") || lower.contains("proximity") || lower.contains("detonator") ||
                lower.contains("trigger") || lower.contains("fuse") || lower.contains("timer")) {
                return ItemType.GRENADE;
            }
            if (lower.contains("first_aid") || lower.contains("medkit") || lower.contains("bandage") ||
                lower.contains("heal") || lower.contains("antibiotic") || lower.contains("painkiller") ||
                lower.contains("adrenaline") || lower.contains("stimulant") || lower.contains("injector") ||
                lower.contains("syringe") || lower.contains("splint") || lower.contains("tourniquet") ||
                lower.contains("gauze") || lower.contains("salve") || lower.contains("ointment") ||
                lower.contains("pill") || lower.contains("tablet") || lower.contains("capsule")) {
                return ItemType.MEDICAL;
            }
            return ItemType.OTHER;
        }

        private enum ItemType {
            WEAPON(10, 1, 1),
            AMMO(8, 3, 12),
            ACCESSORY(5, 1, 1),
            GRENADE(6, 1, 2),
            MEDICAL(7, 1, 3),
            OTHER(3, 1, 1);

            final int weight;
            final int minCount;
            final int maxCount;

            ItemType(int weight, int minCount, int maxCount) {
                this.weight = weight;
                this.minCount = minCount;
                this.maxCount = maxCount;
            }
        }

        @Nonnull
        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                     LootContext context) {
            RandomSource random = context.getRandom();

            if (injectChance < 1.0f && random.nextFloat() > injectChance) {
                return generatedLoot;
            }

            List<WeightedResolvedEntry> pool = getResolvedEntries();
            if (pool.isEmpty()) return generatedLoot;

            int realRolls = rolls + random.nextInt(Math.max(bonusRolls + 1, 1));
            for (int i = 0; i < realRolls; i++) {
                WeightedResolvedEntry entry = weightedPick(pool, random);
                if (entry == null) continue;

                int count = entry.minCount() + random.nextInt(
                        Math.max(entry.maxCount() - entry.minCount() + 1, 1));
                if (count <= 0) continue;

                ItemStack stack = new ItemStack(entry.item(), count);
                if (!stack.isEmpty()) {
                    generatedLoot.add(stack);
                }
            }
            return generatedLoot;
        }

        private static WeightedResolvedEntry weightedPick(List<WeightedResolvedEntry> pool,
                                                          RandomSource random) {
            if (pool.isEmpty()) return null;
            if (pool.size() == 1) return pool.get(0);
            int total = 0;
            for (WeightedResolvedEntry e : pool) total += e.weight();
            int r = random.nextInt(total);
            int acc = 0;
            for (WeightedResolvedEntry e : pool) {
                acc += e.weight();
                if (r < acc) return e;
            }
            return pool.get(pool.size() - 1);
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    /** JSON 中配置的单条战利品条目。item 字段用 registry name（如 tacz:rifle_m14）。
     *  如果某个 mod 未加载，对应物品运行时查找会返回 Optional.empty，被安全地跳过。 */
    public record LootPoolEntry(
            ResourceLocation itemId,
            int weight,
            int minCount,
            int maxCount
    ) {
        public static final Codec<LootPoolEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(LootPoolEntry::itemId),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(LootPoolEntry::weight),
                Codec.INT.optionalFieldOf("min", 1).forGetter(LootPoolEntry::minCount),
                Codec.INT.optionalFieldOf("max", 1).forGetter(LootPoolEntry::maxCount)
        ).apply(inst, LootPoolEntry::new));

        /** 运行时查找物品。若物品未注册（对应 mod 未安装）则返回 Optional.empty。 */
        public java.util.Optional<Item> resolveItem() {
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            return (item != null && item != net.minecraft.world.item.Items.AIR)
                    ? java.util.Optional.of(item)
                    : java.util.Optional.empty();
        }
    }
}
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
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
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

        public static final Codec<BuildingWeaponLootModifier> CODEC = RecordCodecBuilder.create(inst ->
                codecStart(inst).and(inst.group(
                        LootPoolEntry.CODEC.listOf().fieldOf("entries").forGetter(m -> m.entries),
                        Codec.INT.optionalFieldOf("rolls", 2).forGetter(m -> m.rolls),
                        Codec.INT.optionalFieldOf("bonusRolls", 0).forGetter(m -> m.bonusRolls),
                        Codec.FLOAT.optionalFieldOf("injectChance", 1.0f).forGetter(m -> m.injectChance)
                )).apply(inst, BuildingWeaponLootModifier::new));

        final List<LootPoolEntry> entries;
        final int rolls;
        final int bonusRolls;
        final float injectChance;

        /** 已解析到的有效条目（带实际物品引用），仅在首次需要时计算一次。 */
        private volatile List<WeightedResolvedEntry> resolvedEntries;
        private final AtomicBoolean warnedAboutEmpty = new AtomicBoolean(false);

        protected BuildingWeaponLootModifier(LootItemCondition[] conditionsIn,
                                             List<LootPoolEntry> entries,
                                             int rolls,
                                             int bonusRolls,
                                             float injectChance) {
            super(conditionsIn);
            this.entries = entries;
            this.rolls = rolls;
            this.bonusRolls = bonusRolls;
            this.injectChance = injectChance;
        }

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
                resolvedEntries = list;
                if (resolvedEntries.isEmpty() && warnedAboutEmpty.compareAndSet(false, true)) {
                    LOGGER.warn("[QLM Zombie] building_weapon 战利品修改器中没有条目可解析为已注册物品，"
                            + "已跳过注入。请确认 TaCZ / SpartanWeaponry 等 mod 已正确安装。");
                }
                return resolvedEntries;
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
package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 随机品质装备掉落：击杀敌对生物（非玩家/村民/铁傀儡）有概率掉落装备。
 * 品质使用统一 EquipmentQuality 系统。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class RandomEquipmentDropHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean isExcluded(LivingEntity e) {
        if (e instanceof Player) return true;
        if (e instanceof Villager) return true;
        if (e instanceof IronGolem) return true;
        String cn = e.getClass().getName().toLowerCase();
        if (cn.contains("villager") || cn.contains("irongolem")) return true;
        // 也排除被动/和平型生物
        if (!(e instanceof Enemy)
                && !EntityType.getKey(e.getType()).toString().matches(
                    ".*(zombie|skeleton|creeper|spider|slime|ghast|blaze|witch|husk|drowned|stray|phantom|raider|illager|pillager|evoker|vindicator|wither|dragon).*")) {
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;
        if (isExcluded(entity)) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // 5% 概率掉落
        if (serverLevel.getRandom().nextFloat() > 0.05F) return;

        EquipmentQuality quality = EquipmentQuality.randomRoll(serverLevel.getRandom());
        ItemStack drop = generateRandomEquipment(quality, serverLevel.getRandom());
        if (drop == null || drop.isEmpty()) return;

        quality.applyToStack(drop, serverLevel.getRandom());

        // 对镐子随机附加能力
        if (drop.getItem() instanceof PickaxeItem) {
            PickaxeAbility.rollAbilities(drop, serverLevel.getRandom());
        }

        applyName(drop, quality);

        BlockPos pos = entity.blockPosition();
        ItemEntity itemEntity = new ItemEntity(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
        itemEntity.setDeltaMovement(0, 0.2, 0);
        serverLevel.addFreshEntity(itemEntity);

        LOGGER.info("[QLM Zombie] 随机品质装备掉落: {}品质 {} 于 ({},{},{})",
                quality.getDisplayName(), drop.getItem(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static ItemStack generateRandomEquipment(EquipmentQuality q, net.minecraft.util.RandomSource rnd) {
        int t = rnd.nextInt(3);
        return switch (t) {
            case 0 -> generateWeapon(q, rnd);
            case 1 -> generateTool(q, rnd);
            case 2 -> generateArmor(q, rnd);
            default -> null;
        };
    }

    private static ItemStack generateWeapon(EquipmentQuality q, net.minecraft.util.RandomSource rnd) {
        Item[] weapons = {
            Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.BOW, Items.CROSSBOW, Items.TRIDENT,
            Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE
        };
        ItemStack stack = new ItemStack(weapons[rnd.nextInt(weapons.length)]);
        stack.getOrCreateTag().putFloat(EquipmentQuality.NBT_ATTACK, q.getBonusAttack());
        return stack;
    }

    private static ItemStack generateTool(EquipmentQuality q, net.minecraft.util.RandomSource rnd) {
        Item[] tools = {
            Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
            Items.IRON_AXE,    Items.DIAMOND_AXE,    Items.NETHERITE_AXE,
            Items.IRON_SHOVEL,  Items.DIAMOND_SHOVEL,  Items.NETHERITE_SHOVEL,
            Items.IRON_HOE,     Items.DIAMOND_HOE,     Items.NETHERITE_HOE,
            Items.FISHING_ROD,  Items.SHEARS
        };
        return new ItemStack(tools[rnd.nextInt(tools.length)]);
    }

    private static ItemStack generateArmor(EquipmentQuality q, net.minecraft.util.RandomSource rnd) {
        Item[][] sets = {
            { Items.IRON_HELMET,       Items.IRON_CHESTPLATE,       Items.IRON_LEGGINGS,       Items.IRON_BOOTS },
            { Items.DIAMOND_HELMET,    Items.DIAMOND_CHESTPLATE,    Items.DIAMOND_LEGGINGS,    Items.DIAMOND_BOOTS },
            { Items.NETHERITE_HELMET,  Items.NETHERITE_CHESTPLATE,  Items.NETHERITE_LEGGINGS,  Items.NETHERITE_BOOTS },
            { Items.CHAINMAIL_HELMET,  Items.CHAINMAIL_CHESTPLATE,  Items.CHAINMAIL_LEGGINGS,  Items.CHAINMAIL_BOOTS },
            { Items.GOLDEN_HELMET,     Items.GOLDEN_CHESTPLATE,     Items.GOLDEN_LEGGINGS,     Items.GOLDEN_BOOTS },
            { Items.TURTLE_HELMET,     Items.LEATHER_CHESTPLATE,    Items.LEATHER_LEGGINGS,    Items.LEATHER_BOOTS }
        };
        Item[] set = sets[rnd.nextInt(sets.length)];
        ItemStack stack = new ItemStack(set[rnd.nextInt(4)]);
        var tag = stack.getOrCreateTag();
        if (q.getBonusHealth() > 0) tag.putFloat(EquipmentQuality.NBT_HEALTH, q.getBonusHealth());
        if (q.getBonusArmor()  > 0) tag.putFloat(EquipmentQuality.NBT_ARMOR,  q.getBonusArmor());
        if (q.getBonusArmor()  > 0) tag.putFloat(EquipmentQuality.NBT_TOUGHNESS, q.getBonusArmor() * 0.5F);
        return stack;
    }

    private static void applyName(ItemStack stack, EquipmentQuality q) {
        stack.getOrCreateTag().putBoolean("qlm_has_quality", true);
        stack.setHoverName(Component.empty()
                .append(q.getDisplayComponent())
                .append(" ")
                .append(Component.translatable(stack.getDescriptionId()))
                .withStyle(q.getFormatting()));
    }

    @SubscribeEvent
    public static void onItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        EquipmentQuality q = EquipmentQuality.fromStack(stack);
        if (q == null) return;

        var tip = event.getToolTip();
        tip.add(1, Component.empty()
                .append(Component.literal("✦ 品质: ").withStyle(ChatFormatting.GRAY))
                .append(q.getDisplayComponent()));

        var tag = stack.getTag();
        if (tag != null) {
            float ba = tag.getFloat(EquipmentQuality.NBT_ATTACK);
            if (ba > 0) tip.add(Component.empty()
                    .append(Component.literal("  ⚔ 攻击 +").withStyle(ChatFormatting.RED))
                    .append(Component.literal(String.format("%.0f", ba)).withStyle(ChatFormatting.RED)));

            float bh = tag.getFloat(EquipmentQuality.NBT_HEALTH);
            if (bh > 0) tip.add(Component.empty()
                    .append(Component.literal("  ❤ 生命上限 +").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(String.format("%.0f", bh)).withStyle(ChatFormatting.GREEN)));

            float bAr = tag.getFloat(EquipmentQuality.NBT_ARMOR);
            if (bAr > 0) tip.add(Component.empty()
                    .append(Component.literal("  🛡 护甲 +").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(String.format("%.0f", bAr)).withStyle(ChatFormatting.BLUE)));

            double rd = tag.getDouble(EquipmentQuality.NBT_RANDOM_DMG);
            if (rd > 0 && q != EquipmentQuality.MYTHIC) tip.add(Component.empty()
                    .append(Component.literal("  ☄ 随机伤害 +").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(String.format("%.1f", rd)).withStyle(ChatFormatting.GOLD)));
        }

        if (q == EquipmentQuality.MYTHIC) {
            tip.add(Component.literal("  ✦ 无耐久消耗").withStyle(ChatFormatting.GOLD));
            tip.add(Component.literal("  ✦ 可破坏基岩").withStyle(ChatFormatting.DARK_PURPLE));
            tip.add(Component.literal("  ✦ 虚空免伤（盔甲）").withStyle(ChatFormatting.AQUA));
        }

        if (PickaxeAbility.hasAnyAbility(stack)) {
            for (PickaxeAbility ab : PickaxeAbility.getAbilities(stack)) {
                tip.add(Component.literal("✦ ").append(ab.getDisplayName()));
            }
        }
    }
}

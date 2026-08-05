package com.qlm.zombie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 一键砍木板斧子
 * 右键使用时，将玩家物品栏中所有原木一次性转换为对应的木板
 * 支持原版和其他模组的原木（通过合成配方自动查找转换关系）
 */
public class PlankAxeItem extends AxeItem {

    private static final int PLANK_COUNT_PER_LOG = 4;

    public PlankAxeItem() {
        super(Tiers.IRON, 6, -3.2F, new Item.Properties()
                .durability(512)
                .rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack axeStack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(axeStack, true);
        }

        // 查找物品栏中所有原木并转换
        int totalConverted = 0;
        Map<Item, Integer> plankResults = new HashMap<>();

        // 遍历玩家物品栏
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // 检查是否为原木（通过标签或合成配方）
            ItemStack plankResult = findPlankResult(level, stack);
            if (plankResult != null && !plankResult.isEmpty()) {
                int logCount = stack.getCount();
                int plankCount = logCount * PLANK_COUNT_PER_LOG;

                // 统计转换结果
                Item plankItem = plankResult.getItem();
                plankResults.merge(plankItem, plankCount, Integer::sum);

                // 清空原木
                player.getInventory().setItem(i, ItemStack.EMPTY);
                totalConverted += logCount;
            }
        }

        if (totalConverted > 0) {
            // 给玩家木板
            for (Map.Entry<Item, Integer> entry : plankResults.entrySet()) {
                ItemStack planks = new ItemStack(entry.getKey(), entry.getValue());
                if (!player.getInventory().add(planks)) {
                    player.drop(planks, false);
                }
            }

            // 消耗耐久
            axeStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));

            // 音效和提示
            level.playSound(null, player.blockPosition(), SoundEvents.AXE_STRIP, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(
                    Component.literal("§a一键砍木板！§7转换了 §e" + totalConverted + " §7个原木"), true);

            return InteractionResultHolder.sidedSuccess(axeStack, false);
        } else {
            player.displayClientMessage(
                    Component.literal("§7物品栏中没有原木可转换").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.pass(axeStack);
        }
    }

    /**
     * 查找原木对应的木板产物
     * 优先使用原版硬编码映射，然后通过合成配方查找（支持其他模组）
     */
    private ItemStack findPlankResult(Level level, ItemStack logStack) {
        // 1. 检查是否为原木标签
        if (logStack.is(net.minecraft.tags.ItemTags.LOGS)) {
            // 通过合成配方查找对应的木板
            RecipeManager recipeManager = level.getRecipeManager();
            NonNullList<ItemStack> remaining = NonNullList.create();

            for (var recipe : recipeManager.getRecipes()) {
                if (!(recipe instanceof CraftingRecipe craftingRecipe)) continue;
                // 检查配方是否符合：1个原木 → 4个木板
                if (craftingRecipe.getIngredients().size() == 1) {
                    var ingredient = craftingRecipe.getIngredients().get(0);
                    if (ingredient.test(logStack)) {
                        ItemStack result = craftingRecipe.getResultItem(level.registryAccess());
                        if (!result.isEmpty() && result.getCount() == PLANK_COUNT_PER_LOG) {
                            // 验证产物是否为木板标签
                            if (result.is(net.minecraft.tags.ItemTags.PLANKS)) {
                                return result;
                            }
                        }
                    }
                }
            }

            // 2. 原版原木的硬编码回退
            Item logItem = logStack.getItem();
            if (logItem == Items.OAK_LOG) return new ItemStack(Items.OAK_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.SPRUCE_LOG) return new ItemStack(Items.SPRUCE_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.BIRCH_LOG) return new ItemStack(Items.BIRCH_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.JUNGLE_LOG) return new ItemStack(Items.JUNGLE_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.ACACIA_LOG) return new ItemStack(Items.ACACIA_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.DARK_OAK_LOG) return new ItemStack(Items.DARK_OAK_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.MANGROVE_LOG) return new ItemStack(Items.MANGROVE_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.CHERRY_LOG) return new ItemStack(Items.CHERRY_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.CRIMSON_STEM) return new ItemStack(Items.CRIMSON_PLANKS, PLANK_COUNT_PER_LOG);
            if (logItem == Items.WARPED_STEM) return new ItemStack(Items.WARPED_PLANKS, PLANK_COUNT_PER_LOG);
        }

        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§a右键使用：§7一键将物品栏所有原木转换为木板").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7支持原版和其他模组的原木").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§8每次使用消耗1点耐久").withStyle(ChatFormatting.DARK_GRAY));
    }
}

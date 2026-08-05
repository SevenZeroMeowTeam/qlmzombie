package com.qlm.zombie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一键收集木板斧
 * 右键使用时，收集玩家附近一定范围内所有地面上的木板掉落物
 * 支持原版和其他模组的木板（通过 minecraft:planks 标签识别）
 */
public class PlankCollectorItem extends AxeItem {

    private static final double COLLECT_RANGE = 16.0D;

    public PlankCollectorItem() {
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

        // 搜索附近所有掉落物
        AABB searchArea = player.getBoundingBox().inflate(COLLECT_RANGE);
        List<ItemEntity> itemEntities = level.getEntitiesOfClass(ItemEntity.class, searchArea);

        int totalCollected = 0;
        Map<Item, Integer> collectedMap = new HashMap<>();

        for (ItemEntity itemEntity : itemEntities) {
            ItemStack droppedStack = itemEntity.getItem();
            if (droppedStack.isEmpty()) continue;

            // 检查是否为木板（通过标签，支持其他模组）
            if (droppedStack.is(net.minecraft.tags.ItemTags.PLANKS)) {
                int count = droppedStack.getCount();
                Item plankItem = droppedStack.getItem();
                collectedMap.merge(plankItem, count, Integer::sum);
                totalCollected += count;

                // 移除掉落物
                itemEntity.discard();
            }
        }

        if (totalCollected > 0) {
            // 将木板放入玩家物品栏
            for (Map.Entry<Item, Integer> entry : collectedMap.entrySet()) {
                ItemStack planks = new ItemStack(entry.getKey(), entry.getValue());
                if (!player.getInventory().add(planks)) {
                    player.drop(planks, false);
                }
            }

            // 消耗耐久
            axeStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));

            // 音效和提示
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
            player.displayClientMessage(
                    Component.literal("§a一键收集！§7收集了 §e" + totalCollected + " §7个木板"), true);

            return InteractionResultHolder.sidedSuccess(axeStack, false);
        } else {
            player.displayClientMessage(
                    Component.literal("§7附近没有木板掉落物可收集").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.pass(axeStack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§a右键使用：§7一键收集附近16格内所有木板掉落物").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7支持原版和其他模组的木板").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§8每次使用消耗1点耐久").withStyle(ChatFormatting.DARK_GRAY));
    }
}

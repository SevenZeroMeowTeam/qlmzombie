package com.qlm.zombie.cloudai.item.base;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * CloudAI 物品统一接口
 * 所有新增 CloudAI 物品都应实现此接口
 * - getItemName(): 返回物品注册名（蛇形）
 * - buildProp(): 构建 Item.Properties
 * - appendHoverText(): 追加 Tooltip
 * - 重载 use() / finishUsingItem() 实现右键行为
 */
public interface IAutoItem {

    /** 物品注册名（蛇形命名，如 ai_caller） */
    String getItemName();

    /** 构建该物品的属性配置 */
    Item.Properties buildProp();

    /** 获取物品默认显示名称的 key（用于 lang 文件） */
    default String getDescriptionKey() {
        return "item." + com.qlm.zombie.QLMZombieMod.MOD_ID + "." + getItemName();
    }

    /**
     * 追加物品 Tooltip（默认空实现）
     * 由 AllModItems.appendHoverText 统一代理调用
     */
    default void addToTooltip(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
    }

    /**
     * 右键持有物品开始使用时调用（默认返回 PASS，由子类覆盖）
     */
    default InteractionResultHolder<ItemStack> onUse(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /**
     * 使用完成时调用（如食物吃完）
     */
    default ItemStack onFinishUsingItem(ItemStack stack, Level level, Player player) {
        return stack;
    }

    /** 使用动画（默认 NONE，食物类改为 EAT） */
    default UseAnim getUseAnim() {
        return UseAnim.NONE;
    }

    /** 使用持续时间（0 = 不进入使用动画） */
    default int getUseDuration() {
        return 0;
    }
}

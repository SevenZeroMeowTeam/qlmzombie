package com.qlm.zombie.craftingdead.item.medical;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 医疗用品基类：使用动画（DRINK 动画 + 1.6 秒使用时长）。
 * 子类只需实现 canUse / onCannotUse / applyEffect / consumeStack。
 */
public abstract class MedicalUseItem extends Item {

    protected MedicalUseItem(Item.Properties properties) {
        super(properties);
    }

    /** 使用时长（tick，32 = 1.6 秒，带使用动画） */
    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    /** 使用动画：饮用/注射 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    /** 是否可用（子类可覆写，如绷带仅在出血时可用） */
    protected boolean canUse(Level level, Player player, ItemStack stack) {
        return true;
    }

    /** 不可用提示（子类可覆写） */
    protected void onCannotUse(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("§7当前状态无法使用该物品"), true);
        }
    }

    /** 服务端生效逻辑（子类实现） */
    protected abstract void applyEffect(Level level, Player player, ItemStack stack);

    /** 使用后消耗（默认 -1；如剪刀改为耐久消耗可覆写） */
    protected void consumeStack(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(level, player, stack)) {
            onCannotUse(level, player, stack);
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) {
            if (!level.isClientSide) {
                applyEffect(level, player, stack);
            }
            consumeStack(player, stack);
        }
        return stack;
    }
}

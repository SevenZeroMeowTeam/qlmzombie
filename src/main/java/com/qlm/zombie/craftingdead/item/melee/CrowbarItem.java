package com.qlm.zombie.craftingdead.item.melee;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class CrowbarItem extends SwordItem {

    public CrowbarItem() {
        super(Tiers.IRON, 5, -2.8F, new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.COMMON));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (player != null && !level.isClientSide) {
            if (player.getRandom().nextFloat() < 0.10F) {
                level.destroyBlock(pos, true);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("多功能撬棍，可破门撬锁，高伤害慢攻速").withStyle(ChatFormatting.GRAY));
    }
}

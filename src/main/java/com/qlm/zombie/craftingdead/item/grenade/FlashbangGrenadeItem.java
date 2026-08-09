package com.qlm.zombie.craftingdead.item.grenade;

import com.qlm.zombie.craftingdead.entity.CDEntities;
import com.qlm.zombie.craftingdead.entity.ThrownGrenadeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class FlashbangGrenadeItem extends Item {

    public FlashbangGrenadeItem() {
        super(new Item.Properties()
                .stacksTo(8)
                .rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.level().isClientSide) {
            ThrownGrenadeEntity entity = new ThrownGrenadeEntity(
                    CDEntities.THROWN_GRENADE.get(),
                    player,
                    level,
                    ThrownGrenadeEntity.GrenadeType.FLASHBANG
            );
            entity.setPos(
                    player.getX() + player.getLookAngle().x,
                    player.getEyeY() + player.getLookAngle().y,
                    player.getZ() + player.getLookAngle().z
            );
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(entity);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("闪光震撼弹，使敌人短暂失明失能").withStyle(ChatFormatting.GRAY));
    }
}

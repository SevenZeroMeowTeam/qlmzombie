package com.qlm.zombie.craftingdead.block;

import com.qlm.zombie.craftingdead.item.CDItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@SuppressWarnings("deprecation")
public class AmmoCrateBlock extends Block {

    protected static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    private static final List<RegistryObject<Item>> AMMO_TYPES = Arrays.asList(
            CDItems.CD_AMMO_556X45,
            CDItems.CD_AMMO_762X39,
            CDItems.CD_AMMO_9X19,
            CDItems.CD_AMMO_12_GAUGE,
            CDItems.CD_AMMO_45_ACP,
            CDItems.CD_AMMO_50_BMG,
            CDItems.CD_AMMO_338_LAPUA
    );

    public AmmoCrateBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                .strength(3.0F, 20.0F)
                .requiresCorrectToolForDrops());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        player.sendSystemMessage(Component.literal("§e[弹药箱] §7获得弹药补给！"));

        if (!level.isClientSide) {
            RandomSource randomSource = level.random;
            int typeCount = randomSource.nextInt(3) + 1;

            List<RegistryObject<Item>> shuffledAmmo = new ArrayList<>(AMMO_TYPES);
            Collections.shuffle(shuffledAmmo, new Random());

            for (int i = 0; i < Math.min(typeCount, shuffledAmmo.size()); i++) {
                int amount = randomSource.nextInt(25) + 8;
                ItemStack ammoStack = shuffledAmmo.get(i).get().getDefaultInstance();
                ammoStack.setCount(amount);
                player.addItem(ammoStack);
            }

            if (randomSource.nextFloat() >= 0.6F) {
                level.destroyBlock(pos, false);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}

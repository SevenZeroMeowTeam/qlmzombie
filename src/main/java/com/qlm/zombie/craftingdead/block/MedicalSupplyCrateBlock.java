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

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public class MedicalSupplyCrateBlock extends Block {

    protected static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    public MedicalSupplyCrateBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                .strength(2.5F, 15.0F)
                .requiresCorrectToolForDrops());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        player.sendSystemMessage(Component.literal("§a[医疗补给箱] §7正在打开..."));

        if (!level.isClientSide) {
            RandomSource random = level.random;
            ItemStack medicalItem = getRandomMedicalItem(random);

            if (!medicalItem.isEmpty()) {
                player.addItem(medicalItem);
            }

            if (random.nextFloat() >= 0.6F) {
                level.destroyBlock(pos, false);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private ItemStack getRandomMedicalItem(RandomSource random) {
        float roll = random.nextFloat();
        RegistryObject<Item> selected;

        if (roll < 0.6F) {
            selected = CDItems.CD_BANDAGE;
        } else if (roll < 0.85F) {
            selected = CDItems.CD_FIRST_AID_KIT;
        } else {
            List<RegistryObject<Item>> otherMedical = Arrays.asList(
                    CDItems.CD_ADRENALINE_SYRINGE,
                    CDItems.CD_SPLINT,
                    CDItems.CD_PAINKILLERS,
                    CDItems.CD_TOURNIQUET,
                    CDItems.CD_SALINE_BAG,
                    CDItems.CD_SURGICAL_SCISSORS
            );
            selected = otherMedical.get(random.nextInt(otherMedical.size()));
        }

        return selected.get().getDefaultInstance();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}

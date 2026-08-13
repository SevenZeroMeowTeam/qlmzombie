package com.qlm.zombie.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;

/**
 * 睡袋方块：
 * - 右键夜晚入睡（不重置出生点，使用 LivingEntity.startSleeping 而非 Player.startSleepInBed）
 * - 白天右键或随机刻自动收起（破坏并掉落为物品）
 * - 无碰撞箱，高度 2 像素（类似地毯）
 */
public class SleepingBagBlock extends Block {

    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public SleepingBagBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOL)
                .strength(0.2F, 0.2F)
                .sound(SoundType.WOOL)
                .noCollission()
                .randomTicks()
                .instabreak()
        );
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isSleeping()) {
            return InteractionResult.PASS;
        }

        if (level.isDay()) {
            level.destroyBlock(pos, true);
            player.sendSystemMessage(Component.literal("\u00A7e[\u7761\u888b] \u00A77\u767d\u5929\u4e86\uff0c\u7761\u888b\u5df2\u6536\u8d77"));
            return InteractionResult.PASS;
        }

        player.startSleeping(pos);
        player.sendSystemMessage(Component.literal("\u00A7b[\u7761\u888b] \u00A77\u665a\u5b89\uff0c\u4e0d\u4f1a\u91cd\u7f6e\u51fa\u751f\u70b9"));
        return InteractionResult.PASS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isDay()) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.entity.Entity sleeper) {
        return true;
    }
}

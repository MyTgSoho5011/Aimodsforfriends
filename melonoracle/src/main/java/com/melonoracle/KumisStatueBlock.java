package com.melonoracle;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.Random;

public class KumisStatueBlock extends Block {

    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
    private static final Random RNG = new Random();
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(0.1, 0.0, 0.1, 0.9, 1.0, 0.9);

    public KumisStatueBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        if (pos.getY() < ctx.getWorld().getTopY() - 1 && ctx.getWorld().getBlockState(pos.up()).canReplace(ctx)) {
            return getDefaultState().with(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient && state.get(HALF) == DoubleBlockHalf.LOWER) {
            world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), 3);
        }
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldAccess world, net.minecraft.block.BlockTickScheduler tickScheduler,
                                                      BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, net.minecraft.util.math.random.Random random) {
        DoubleBlockHalf half = state.get(HALF);
        if (direction.getAxis() == Direction.Axis.Y
            && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)
            && (!neighborState.isOf(this) || neighborState.get(HALF) == half)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, world, tickScheduler, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        DoubleBlockHalf half = state.get(HALF);
        if (!world.isClient) {
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
            BlockState otherState = world.getBlockState(otherPos);
            if (otherState.isOf(this) && otherState.get(HALF) != half) {
                world.setBlockState(otherPos, Blocks.AIR.getDefaultState(), 35);
            }
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                  PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockPos lowerPos = state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;

        if (world instanceof ServerWorld sw) {
            double roll = RNG.nextDouble();
            int melons = 1 + RNG.nextInt(2);
            ItemStack reward = new ItemStack(Items.MELON_SLICE, melons);
            if (!player.getInventory().insertStack(reward)) {
                player.dropItem(reward, false);
            }

            if (roll < 0.10) {
                player.sendMessage(Text.literal("[Статуя Кумыса] Молоко помнит лошадь. Лошадь помнит тебя."), false);
                sw.playSound(null, lowerPos, SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.BLOCKS, 1.0f, 0.5f);
            } else {
                player.sendMessage(Text.literal("[Статуя Кумыса] Кап. Кап. Дыня."), false);
                sw.playSound(null, lowerPos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0f, 0.8f);
            }
        }

        return ActionResult.SUCCESS;
    }
}
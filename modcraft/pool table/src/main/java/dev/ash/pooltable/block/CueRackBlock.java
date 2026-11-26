package dev.ash.pooltable.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableMap.Builder;
import dev.ash.pooltable.PoolTable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public abstract class CueRackBlock extends BaseEntityBlock {
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
   private final Map<BlockState, VoxelShape> shapes;

   protected CueRackBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(HALF, DoubleBlockHalf.LOWER)
      );
      Builder<BlockState, VoxelShape> map = ImmutableMap.builder();
      UnmodifiableIterator var3 = this.stateDefinition.getPossibleStates().iterator();

      while (var3.hasNext()) {
         BlockState state = (BlockState)var3.next();
         double[] b = state.getValue(HALF) == DoubleBlockHalf.LOWER ? this.lowerShape() : this.upperShape();
         double[] p = toWorld((Direction)state.getValue(FACING), b[0], b[2]);
         double[] q = toWorld((Direction)state.getValue(FACING), b[3], b[5]);
         map.put(state, Block.box(Math.min(p[0], q[0]), b[1], Math.min(p[1], q[1]), Math.max(p[0], q[0]), b[4], Math.max(p[1], q[1])));
      }

      this.shapes = map.build();
   }

   protected abstract double[] slots();

   protected abstract double buttY();

   protected abstract double[] lowerShape();

   protected abstract double[] upperShape();

   @Nullable
   protected abstract Direction facingFor(BlockPlaceContext var1);

   protected abstract boolean supported(BlockState var1, LevelReader var2, BlockPos var3);

   public int size() {
      return this.slots().length / 2;
   }

   private static double[] toWorld(Direction facing, double x, double z) {
      Direction right = facing.getClockWise();
      Direction back = facing.getOpposite();
      double cx = x - 8.0;
      double cz = z - 8.0;
      return new double[]{8.0 + cx * right.getStepX() + cz * back.getStepX(), 8.0 + cx * right.getStepZ() + cz * back.getStepZ()};
   }

   public Vec3 slotPos(BlockState state, int i) {
      double[] p = toWorld((Direction)state.getValue(FACING), this.slots()[2 * i], this.slots()[2 * i + 1]);
      return new Vec3(p[0] / 16.0, this.buttY() / 16.0, p[1] / 16.0);
   }

   protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING, HALF});
   }

   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.shapes.get(state);
   }

   protected VoxelShape getOcclusionShape(BlockState state) {
      return Shapes.empty();
   }

   protected boolean propagatesSkylightDown(BlockState state) {
      return true;
   }

   protected BlockState rotate(BlockState state, Rotation rotation) {
      return (BlockState)state.setValue(FACING, rotation.rotate((Direction)state.getValue(FACING)));
   }

   protected BlockState mirror(BlockState state, Mirror mirror) {
      return this.rotate(state, mirror.getRotation((Direction)state.getValue(FACING)));
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockPos pos = context.getClickedPos();
      Level level = context.getLevel();
      if (pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(context)) {
         Direction facing = this.facingFor(context);
         return facing == null ? null : (BlockState)this.defaultBlockState().setValue(FACING, facing);
      } else {
         return null;
      }
   }

   public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
      level.setBlock(pos.above(), (BlockState)state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
   }

   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
         return this.supported(state, level, pos);
      } else {
         BlockState below = level.getBlockState(pos.below());
         return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
      }
   }

   protected BlockState updateShape(
      BlockState state,
      LevelReader level,
      ScheduledTickAccess ticks,
      BlockPos pos,
      Direction direction,
      BlockPos neighbourPos,
      BlockState neighbour,
      RandomSource random
   ) {
      DoubleBlockHalf half = (DoubleBlockHalf)state.getValue(HALF);
      if (direction.getAxis() == Axis.Y && half == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
         return neighbour.is(this) && neighbour.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
      } else {
         return half == DoubleBlockHalf.LOWER && !this.supported(state, level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
      }
   }

   public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
         BlockPos below = pos.below();
         BlockState lower = level.getBlockState(below);
         if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, below, Block.getId(lower));
         }
      }

      return super.playerWillDestroy(level, pos, state, player);
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new CueRackBlockEntity(pos, state) : null;
   }

   @Nullable
   private static CueRackBlockEntity rack(BlockGetter level, BlockPos pos, BlockState state) {
      BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
      return level.getBlockEntity(lower) instanceof CueRackBlockEntity rack ? rack : null;
   }

   private int nearest(BlockState state, CueRackBlockEntity rack, Vec3 hit, boolean free) {
      BlockPos base = rack.getBlockPos();
      double hx = hit.x - base.getX();
      double hz = hit.z - base.getZ();
      int best = -1;
      double bestDistance = Double.MAX_VALUE;

      for (int i = 0; i < Math.min(this.size(), rack.size()); i++) {
         if (rack.get(i).isEmpty() == free) {
            Vec3 slot = this.slotPos(state, i);
            double d = (slot.x - hx) * (slot.x - hx) + (slot.z - hz) * (slot.z - hz);
            if (d < bestDistance) {
               bestDistance = d;
               best = i;
            }
         }
      }

      return best;
   }

   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!stack.is(PoolTable.CUE.get())) {
         return InteractionResult.TRY_WITH_EMPTY_HAND;
      } else {
         CueRackBlockEntity rack = rack(level, pos, state);
         int slot = rack == null ? -1 : this.nearest(state, rack, hit.getLocation(), true);
         if (slot < 0) {
            return InteractionResult.FAIL;
         } else {
            if (!level.isClientSide()) {
               rack.put(slot, stack.consumeAndReturn(1, player));
               level.playSound(null, rack.getBlockPos(), SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 1.0F, 1.1F);
               level.gameEvent(player, GameEvent.BLOCK_CHANGE, rack.getBlockPos());
            }

            return InteractionResult.SUCCESS;
         }
      }
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!player.getMainHandItem().isEmpty()) {
         return InteractionResult.PASS;
      } else {
         CueRackBlockEntity rack = rack(level, pos, state);
         int slot = rack == null ? -1 : this.nearest(state, rack, hit.getLocation(), false);
         if (slot < 0) {
            return InteractionResult.PASS;
         } else {
            if (!level.isClientSide()) {
               player.setItemInHand(InteractionHand.MAIN_HAND, rack.take(slot));
               level.playSound(null, rack.getBlockPos(), SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 1.0F, 1.1F);
               level.gameEvent(player, GameEvent.BLOCK_CHANGE, rack.getBlockPos());
            }

            return InteractionResult.SUCCESS;
         }
      }
   }

   public void tooltip(Consumer<Component> tooltip) {
      tooltip.accept(PoolTable.text("tooltip.rack", this.size()).withStyle(ChatFormatting.GRAY));
      tooltip.accept(PoolTable.text("tooltip.rack.use").withStyle(ChatFormatting.GRAY));
   }

   protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
      return state.getValue(HALF) == DoubleBlockHalf.LOWER ? List.of(new ItemStack(this)) : List.of();
   }
}

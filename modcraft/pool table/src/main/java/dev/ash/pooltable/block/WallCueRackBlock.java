package dev.ash.pooltable.block;

import com.mojang.serialization.MapCodec;
import dev.ash.pooltable.compat.Shim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import org.jetbrains.annotations.Nullable;

public class WallCueRackBlock extends CueRackBlock {
   public WallCueRackBlock(Properties properties) {
      super(properties);
   }

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return Shim.get().simpleCodec(WallCueRackBlock::new);
   }

   @Override
   protected double[] slots() {
      return RackDims.WALL_SLOTS;
   }

   @Override
   protected double buttY() {
      return 2.5;
   }

   @Override
   protected double[] lowerShape() {
      return RackDims.WALL_LOWER_SHAPE;
   }

   @Override
   protected double[] upperShape() {
      return RackDims.WALL_UPPER_SHAPE;
   }

   @Nullable
   @Override
   protected Direction facingFor(BlockPlaceContext context) {
      for (Direction look : context.getNearestLookingDirections()) {
         if (look.getAxis().isHorizontal()) {
            BlockState state = (BlockState)this.defaultBlockState().setValue(FACING, look.getOpposite());
            if (this.supported(state, context.getLevel(), context.getClickedPos())) {
               return look.getOpposite();
            }
         }
      }

      return null;
   }

   @Override
   protected boolean supported(BlockState lower, LevelReader level, BlockPos pos) {
      Direction facing = (Direction)lower.getValue(FACING);
      BlockPos wall = pos.relative(facing.getOpposite());
      return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
   }
}

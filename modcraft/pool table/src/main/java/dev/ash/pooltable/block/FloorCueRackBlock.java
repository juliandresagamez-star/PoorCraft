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

public class FloorCueRackBlock extends CueRackBlock {
   public FloorCueRackBlock(Properties properties) {
      super(properties);
   }

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return Shim.get().simpleCodec(FloorCueRackBlock::new);
   }

   @Override
   protected double[] slots() {
      return RackDims.FLOOR_SLOTS;
   }

   @Override
   protected double buttY() {
      return 2.5;
   }

   @Override
   protected double[] lowerShape() {
      return RackDims.FLOOR_LOWER_SHAPE;
   }

   @Override
   protected double[] upperShape() {
      return RackDims.FLOOR_UPPER_SHAPE;
   }

   @Override
   protected Direction facingFor(BlockPlaceContext context) {
      return context.getHorizontalDirection().getOpposite();
   }

   @Override
   protected boolean supported(BlockState lower, LevelReader level, BlockPos pos) {
      return true;
   }
}

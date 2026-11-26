package dev.ash.pooltable.block;

import dev.ash.pooltable.PoolTable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PoolLampBlockEntity extends BlockEntity {
   public static final int MAX_HANG = 24;
   private int hang = -1;

   public PoolLampBlockEntity(BlockPos pos, BlockState state) {
      super(PoolTable.LAMP_BE.get(), pos, state);
   }

   public LampKind kind() {
      return this.getBlockState().getBlock() instanceof PoolLampBlock lamp ? lamp.kind : LampKind.TIFFANY;
   }

   public int hang() {
      if (this.hang < 0 && this.level != null) {
         this.hang = measure(this.level, this.worldPosition);
      }

      return Math.max(this.hang, 0);
   }

   public static int measure(BlockGetter level, BlockPos pos) {
      for (int i = 1; i <= 24; i++) {
         BlockState above = level.getBlockState(pos.above(i));
         if (!above.isAir() && !above.canBeReplaced()) {
            return i - 1;
         }
      }

      return 24;
   }

   public static void clientTick(Level level, BlockPos pos, BlockState state, PoolLampBlockEntity lamp) {
      if ((level.getGameTime() + pos.hashCode()) % 20L == 0L) {
         lamp.hang = measure(level, pos);
      }
   }
}

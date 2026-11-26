package dev.ash.pooltable.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record TableFrame(BlockPos master, Direction facing) {
   public static TableFrame of(BlockPos partPos, BlockState state) {
      Direction f = (Direction)state.getValue(PoolTableBlock.FACING);
      int part = (Integer)state.getValue(PoolTableBlock.PART);
      return new TableFrame(offset(partPos, f, -PoolTableBlock.partX(part), -PoolTableBlock.partZ(part)), f);
   }

   static BlockPos offset(BlockPos from, Direction facing, int lx, int lz) {
      Direction side = facing.getCounterClockWise();
      return from.relative(side, lx).relative(facing, lz);
   }

   public BlockPos part(int lx, int lz) {
      return offset(this.master, this.facing, lx, lz);
   }

   private int sx() {
      return this.facing.getCounterClockWise().getStepX();
   }

   private int sz() {
      return this.facing.getCounterClockWise().getStepZ();
   }

   public Vec3 toWorld(double lx, double ly, double lz) {
      return new Vec3(
         this.master.getX() + 0.5 + lx * this.sx() + lz * this.facing.getStepX(),
         this.master.getY() + ly,
         this.master.getZ() + 0.5 + lx * this.sz() + lz * this.facing.getStepZ()
      );
   }

   public double localX(Vec3 world) {
      return (world.x - this.master.getX() - 0.5) * this.sx() + (world.z - this.master.getZ() - 0.5) * this.sz();
   }

   public double localZ(Vec3 world) {
      return (world.x - this.master.getX() - 0.5) * this.facing.getStepX() + (world.z - this.master.getZ() - 0.5) * this.facing.getStepZ();
   }

   public double localAngle(Vec3 dir) {
      double lx = dir.x * this.sx() + dir.z * this.sz();
      double lz = dir.x * this.facing.getStepX() + dir.z * this.facing.getStepZ();
      return Math.atan2(lx, lz);
   }

   public Vec3 centre() {
      return this.toWorld(0.0, 0.8125, 0.0);
   }

   public AABB bounds() {
      return new AABB(this.master).inflate(this.facing.getAxis() == Axis.X ? 2.0 : 1.0, 0.0, this.facing.getAxis() == Axis.Z ? 2.0 : 1.0);
   }
}

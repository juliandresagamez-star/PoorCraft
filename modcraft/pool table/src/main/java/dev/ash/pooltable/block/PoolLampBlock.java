package dev.ash.pooltable.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.compat.Shim;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PoolLampBlock extends BaseEntityBlock {
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   private static final VoxelShape SHAPE = Block.box(1.0, 2.0, 1.0, 15.0, 16.0, 15.0);
   public final LampKind kind;

   public PoolLampBlock(LampKind kind, Properties properties) {
      super(properties);
      this.kind = kind;
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.SOUTH));
   }

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return Shim.get().codecWith(Codec.STRING.xmap(LampKind::byId, kind -> kind.id).fieldOf("kind"), lamp -> lamp.kind, PoolLampBlock::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
   }

   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
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

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new PoolLampBlockEntity(pos, state);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide() ? createTickerHelper(type, PoolTable.LAMP_BE.get(), PoolLampBlockEntity::clientTick) : null;
   }

   protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
      return List.of(new ItemStack(this));
   }
}

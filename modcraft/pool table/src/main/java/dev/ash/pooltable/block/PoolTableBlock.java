package dev.ash.pooltable.block;

import com.mojang.serialization.MapCodec;
import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.compat.Shim;
import dev.ash.pooltable.item.PoolLampItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PoolTableBlock extends BaseEntityBlock {
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final IntegerProperty PART = IntegerProperty.create("part", 0, 14);
   public static final EnumProperty<TableWood> WOOD = EnumProperty.create("wood", TableWood.class);
   public static final int MASTER = 7;
   private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.5, 16.0);

   public PoolTableBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.SOUTH)).setValue(PART, 7))
            .setValue(WOOD, TableWood.MAHOGANY)
      );
   }

   public static int partX(int part) {
      return part % 3 - 1;
   }

   public static int partZ(int part) {
      return part / 3 - 2;
   }

   public static int part(int lx, int lz) {
      return lx + 1 + (lz + 2) * 3;
   }

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return Shim.get().simpleCodec(PoolTableBlock::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING, PART, WOOD});
   }

   protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
      return new ItemStack((ItemLike)PoolTable.TABLE_ITEMS.get(state.getValue(WOOD)).get());
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

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return state.getValue(PART) == 7 ? new PoolTableBlockEntity(pos, state) : null;
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return state.getValue(PART) != 7
         ? null
         : createTickerHelper(type, PoolTable.TABLE_BE.get(), level.isClientSide() ? PoolTableBlockEntity::clientTick : PoolTableBlockEntity::serverTick);
   }

   @Nullable
   public static PoolTableBlockEntity master(BlockGetter level, BlockPos pos, BlockState state) {
      if (!(state.getBlock() instanceof PoolTableBlock)) {
         return null;
      } else {
         return level.getBlockEntity(TableFrame.of(pos, state).master()) instanceof PoolTableBlockEntity be ? be : null;
      }
   }

   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!stack.is(PoolTable.CUE.get()) && !(stack.getItem() instanceof PoolLampItem)) {
         DyeColor dye = (DyeColor)stack.get(DataComponents.DYE);
         if (dye != null) {
            PoolTableBlockEntity be = master(level, pos, state);
            if (be != null && be.clothColor() != dye) {
               if (!level.isClientSide()) {
                  be.setClothColor(dye);
                  stack.consume(1, player);
                  level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
               }

               return InteractionResult.SUCCESS;
            } else {
               return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
         } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      PoolTableBlockEntity be = master(level, pos, state);
      if (be == null) {
         return InteractionResult.PASS;
      } else {
         if (level.isClientSide()) {
            PoolTable.clientHooks.openTableScreen(be);
         }

         return InteractionResult.SUCCESS;
      }
   }

   protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
      TableFrame frame = TableFrame.of(pos, state);

      for (int lz = -2; lz <= 2; lz++) {
         for (int lx = -1; lx <= 1; lx++) {
            BlockPos p = frame.part(lx, lz);
            BlockState other = level.getBlockState(p);
            if (!p.equals(pos) && other.is(this) && other.getValue(FACING) == frame.facing() && (Integer)other.getValue(PART) == part(lx, lz)) {
               level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
            }
         }
      }

      super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
   }

   protected boolean propagatesSkylightDown(BlockState state) {
      return true;
   }

   protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
      return List.of(new ItemStack((ItemLike)PoolTable.TABLE_ITEMS.get(state.getValue(WOOD)).get()));
   }
}

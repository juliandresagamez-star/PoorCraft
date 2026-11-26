package dev.ash.pooltable.item;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.PoolTableBlock;
import dev.ash.pooltable.block.TableFrame;
import dev.ash.pooltable.block.TableWood;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class PoolTableItem extends BlockItem {
   public final TableWood wood;

   public PoolTableItem(Block block, TableWood wood, Properties properties) {
      super(block, properties);
      this.wood = wood;
   }

   public void registerBlocks(Map<Block, Item> map, Item item) {
      if (this.wood == TableWood.MAHOGANY) {
         super.registerBlocks(map, item);
      }
   }

   public InteractionResult place(BlockPlaceContext ctx) {
      Level level = ctx.getLevel();
      Player player = ctx.getPlayer();
      Direction facing = ctx.getHorizontalDirection();
      TableFrame frame = new TableFrame(ctx.getClickedPos().relative(facing, 2), facing);

      for (int lz = -2; lz <= 2; lz++) {
         for (int lx = -1; lx <= 1; lx++) {
            BlockPos p = frame.part(lx, lz);
            if (!level.getBlockState(p).canBeReplaced() || !level.isInWorldBounds(p) || !level.getWorldBorder().isWithinBounds(p)) {
               return fail(level, player, "msg.no_room");
            }
         }
      }

      List<Entity> inTheWay = level.getEntities((Entity)null, frame.bounds().setMaxY(frame.master().getY() + 1), e -> !e.isSpectator() && e.blocksBuilding);
      if (!inTheWay.isEmpty()) {
         return fail(level, player, "msg.no_room");
      } else {
         if (!level.isClientSide()) {
            for (int lz = -2; lz <= 2; lz++) {
               for (int lxx = -1; lxx <= 1; lxx++) {
                  BlockState state = (BlockState)((BlockState)((BlockState)this.getBlock().defaultBlockState().setValue(PoolTableBlock.FACING, facing))
                        .setValue(PoolTableBlock.PART, PoolTableBlock.part(lxx, lz)))
                     .setValue(PoolTableBlock.WOOD, this.wood);
                  level.setBlock(frame.part(lxx, lz), state, 3);
               }
            }

            level.gameEvent(player, GameEvent.BLOCK_PLACE, frame.master());
         }

         SoundType sound = SoundType.WOOD;
         level.playSound(player, frame.master(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
         ctx.getItemInHand().consume(1, player);
         return InteractionResult.SUCCESS;
      }
   }

   private static InteractionResult fail(Level level, Player player, String key) {
      if (level.isClientSide() && player != null) {
         player.sendOverlayMessage(PoolTable.text(key));
      }

      return InteractionResult.FAIL;
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
      tooltip.accept(PoolTable.text("tooltip.table").withStyle(ChatFormatting.GRAY));
   }
}

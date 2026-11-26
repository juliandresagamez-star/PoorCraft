package dev.ash.pooltable.item;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.PoolLampBlock;
import dev.ash.pooltable.block.PoolTableBlock;
import dev.ash.pooltable.block.TableFrame;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class PoolLampItem extends BlockItem {
   public PoolLampItem(PoolLampBlock block, Properties properties) {
      super(block, properties);
   }

   public PoolLampBlock lamp() {
      return (PoolLampBlock)this.getBlock();
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      BlockState clicked = level.getBlockState(context.getClickedPos());
      if (!(clicked.getBlock() instanceof PoolTableBlock)) {
         return super.useOn(context);
      } else {
         TableFrame frame = TableFrame.of(context.getClickedPos(), clicked);
         BlockPos at = frame.master().above(2);
         Player player = context.getPlayer();
         if (level.getBlockState(at.below()).canBeReplaced() && level.getBlockState(at).canBeReplaced()) {
            BlockState state = (BlockState)this.lamp().defaultBlockState().setValue(PoolLampBlock.FACING, frame.facing());
            if (!level.isClientSide()) {
               level.setBlock(at, state, 3);
               level.gameEvent(player, GameEvent.BLOCK_PLACE, at);
            }

            SoundType sound = state.getSoundType();
            level.playSound(player, at, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            context.getItemInHand().consume(1, player);
            return InteractionResult.SUCCESS;
         } else {
            if (level.isClientSide() && player != null) {
               player.sendOverlayMessage(PoolTable.text("msg.lamp_no_room"));
            }

            return InteractionResult.FAIL;
         }
      }
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
      tooltip.accept(PoolTable.text("tooltip.lamp").withStyle(ChatFormatting.GRAY));
      tooltip.accept(PoolTable.text(this.lamp().kind.hanger == null ? "tooltip.lamp.float" : "tooltip.lamp.hang").withStyle(ChatFormatting.GRAY));
   }
}

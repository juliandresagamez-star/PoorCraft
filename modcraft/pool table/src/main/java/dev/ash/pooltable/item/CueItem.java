package dev.ash.pooltable.item;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.PoolTableBlock;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CueItem extends Item {
   public CueItem(Properties properties) {
      super(properties);
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      PoolTableBlockEntity table = hand == InteractionHand.MAIN_HAND ? PoolTableBlockEntity.nearest(level, player.getEyePosition(), 6.0) : null;
      if (table != null && !table.moving() && table.game.mayShoot(player.getUUID())) {
         player.startUsingItem(hand);
         return InteractionResult.CONSUME;
      } else {
         return InteractionResult.PASS;
      }
   }

   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return 72000;
   }

   public ItemUseAnimation getUseAnimation(ItemStack stack) {
      return ItemUseAnimation.NONE;
   }

   public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
      if (level.isClientSide() && entity instanceof Player) {
         PoolTable.clientHooks.cueReleased(this.getUseDuration(stack, entity) - timeLeft);
      }

      return false;
   }

   public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
      return !(state.getBlock() instanceof PoolTableBlock);
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
      tooltip.accept(PoolTable.text("tooltip.cue.1").withStyle(ChatFormatting.GRAY));
      tooltip.accept(PoolTable.text("tooltip.cue.2").withStyle(ChatFormatting.GRAY));
      tooltip.accept(PoolTable.text("tooltip.cue.3").withStyle(ChatFormatting.GRAY));
   }
}

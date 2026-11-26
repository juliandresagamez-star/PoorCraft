package dev.ash.pooltable.item;

import dev.ash.pooltable.block.CueRackBlock;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class CueRackItem extends DoubleHighBlockItem {
   public CueRackItem(Block block, Properties properties) {
      super(block, properties);
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
      if (this.getBlock() instanceof CueRackBlock rack) {
         rack.tooltip(tooltip);
      }
   }
}

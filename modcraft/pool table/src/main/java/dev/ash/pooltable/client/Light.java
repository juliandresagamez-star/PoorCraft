package dev.ash.pooltable.client;

import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.LightLayer;

final class Light {
   private Light() {
   }

   static int at(BlockAndLightGetter level, BlockPos pos) {
      return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
   }
}

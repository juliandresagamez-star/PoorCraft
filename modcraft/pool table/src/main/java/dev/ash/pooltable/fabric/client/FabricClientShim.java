package dev.ash.pooltable.fabric.client;

import dev.ash.pooltable.compat.Shims;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.phys.AABB;

public interface FabricClientShim {
   static FabricClientShim get() {
      return FabricClientShim.Holder.SHIM;
   }

   void drawOutline(LevelRenderContext var1, AABB var2);

   public static final class Holder {
      static final FabricClientShim SHIM = Shims.load(FabricClientShim.class, "dev.ash.pooltable.fabric.client", "FabricClientShim");

      private Holder() {
      }
   }
}

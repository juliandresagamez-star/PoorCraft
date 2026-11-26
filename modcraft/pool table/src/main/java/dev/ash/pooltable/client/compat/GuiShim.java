package dev.ash.pooltable.client.compat;

import dev.ash.pooltable.compat.Shims;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public interface GuiShim {
   static GuiShim get() {
      return GuiShim.Holder.SHIM;
   }

   void blit(GuiGraphicsExtractor var1, Identifier var2, int var3, int var4, float var5, float var6, int var7, int var8, int var9, int var10);

   public static final class Holder {
      static final GuiShim SHIM = Shims.load(GuiShim.class, "dev.ash.pooltable.client.compat", "GuiShim");

      private Holder() {
      }
   }
}

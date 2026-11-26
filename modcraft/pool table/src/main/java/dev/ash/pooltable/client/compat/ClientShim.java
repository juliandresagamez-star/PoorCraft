package dev.ash.pooltable.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.ash.pooltable.compat.Shims;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public interface ClientShim {
   static ClientShim get() {
      return ClientShim.Holder.SHIM;
   }

   @Nullable
   Screen screen();

   void setScreen(@Nullable Screen var1);

   Camera mainCamera();

   boolean hudHidden();

   void setHudHidden(boolean var1);

   RenderTarget mainRenderTarget();

   public static final class Holder {
      static final ClientShim SHIM = Shims.load(ClientShim.class, "dev.ash.pooltable.client.compat", "ClientShim");

      private Holder() {
      }
   }
}

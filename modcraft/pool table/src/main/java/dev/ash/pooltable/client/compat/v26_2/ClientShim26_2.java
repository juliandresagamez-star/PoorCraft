package dev.ash.pooltable.client.compat.v26_2;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.ash.pooltable.client.compat.ClientShim;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public class ClientShim26_2 implements ClientShim {
   @Nullable
   @Override
   public Screen screen() {
      return Minecraft.getInstance().gui.screen();
   }

   @Override
   public void setScreen(@Nullable Screen screen) {
      Minecraft.getInstance().gui.setScreen(screen);
   }

   @Override
   public Camera mainCamera() {
      return Minecraft.getInstance().gameRenderer.mainCamera();
   }

   @Override
   public boolean hudHidden() {
      return Minecraft.getInstance().gui.hud.isHidden();
   }

   @Override
   public void setHudHidden(boolean hidden) {
      if (this.hudHidden() != hidden) {
         Minecraft.getInstance().gui.hud.toggle();
      }
   }

   @Override
   public RenderTarget mainRenderTarget() {
      return Minecraft.getInstance().gameRenderer.mainRenderTarget();
   }
}

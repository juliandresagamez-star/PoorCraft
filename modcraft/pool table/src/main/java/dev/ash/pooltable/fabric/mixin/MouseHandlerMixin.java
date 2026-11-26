package dev.ash.pooltable.fabric.mixin;

import dev.ash.pooltable.client.PoolClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MouseHandler.class})
public abstract class MouseHandlerMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(
      method = {"onScroll"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;",
         shift = Shift.AFTER
      )},
      cancellable = true
   )
   private void pooltable$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
      double delta = (this.minecraft.options.discreteMouseScroll().get() ? Math.signum(yOffset) : yOffset)
         * (Double)this.minecraft.options.mouseWheelSensitivity().get();
      if (PoolClientEvents.scrolled(delta)) {
         ci.cancel();
      }
   }
}

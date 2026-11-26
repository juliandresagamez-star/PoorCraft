package dev.ash.pooltable.mixin;

import dev.ash.pooltable.client.ClientAim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public abstract class EntityTurnMixin {
   @Inject(
      method = {"turn"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void pooltable$aimWithMouse(double yRot, double xRot, CallbackInfo ci) {
      if ((Object)this == Minecraft.getInstance().player && ClientAim.turn(yRot, xRot)) {
         ci.cancel();
      }
   }
}

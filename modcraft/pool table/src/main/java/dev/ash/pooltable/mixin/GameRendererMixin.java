package dev.ash.pooltable.mixin;

import dev.ash.pooltable.client.PoolClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Inject(
      method = {"update(Lnet/minecraft/client/DeltaTracker;Z)V", "update(Lnet/minecraft/client/DeltaTracker;)V"},
      at = {@At("HEAD")}
   )
   private void pooltable$frameStart(CallbackInfo ci) {
      PoolClientEvents.frameStart(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
   }
}

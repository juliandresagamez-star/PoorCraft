package dev.ash.pooltable.fabric.mixin;

import dev.ash.pooltable.client.PoolClientEvents;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({KeyboardInput.class})
public abstract class KeyboardInputMixin {
   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void pooltable$movement(CallbackInfo ci) {
      PoolClientEvents.movement((ClientInput)(Object)this);
   }
}

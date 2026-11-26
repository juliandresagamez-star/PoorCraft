package dev.ash.pooltable.fabric.mixin;

import dev.ash.pooltable.client.PoolClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Minecraft.class})
public abstract class MinecraftMixin {
   @Shadow
   @Nullable
   public LocalPlayer player;
   @Shadow
   @Nullable
   public HitResult hitResult;
   @Shadow
   protected int missTime;

   @Inject(
      method = {"startUseItem"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I",
         opcode = 181,
         shift = Shift.AFTER
      )},
      cancellable = true
   )
   private void pooltable$useKey(CallbackInfo ci) {
      if (this.player != null && !this.player.isHandsBusy() && PoolClientEvents.useKey()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"pickBlockOrEntity"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void pooltable$pickKey(CallbackInfo ci) {
      if (PoolClientEvents.pickKey()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void pooltable$attackKey(CallbackInfoReturnable<Boolean> cir) {
      if (this.missTime <= 0 && this.hitResult != null && this.player != null && !this.player.isHandsBusy() && PoolClientEvents.attackKey()) {
         cir.setReturnValue(false);
      }
   }
}

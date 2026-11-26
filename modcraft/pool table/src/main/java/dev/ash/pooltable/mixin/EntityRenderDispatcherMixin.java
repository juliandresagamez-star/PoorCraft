package dev.ash.pooltable.mixin;

import dev.ash.pooltable.client.PoolClientEvents;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EntityRenderDispatcher.class})
public abstract class EntityRenderDispatcherMixin {
   @Inject(
      method = {"shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"},
      require = 0,
      at = {@At("HEAD")},
      cancellable = true
   )
   @Group(
      name = "pooltable$shouldRender",
      min = 1,
      max = 1
   )
   private void pooltable$hideInCameraWay(Entity entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
      if (PoolClientEvents.hideEntity(entity)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z"},
      require = 0,
      at = {@At("HEAD")},
      cancellable = true
   )
   @Group(
      name = "pooltable$shouldRender",
      min = 1,
      max = 1
   )
   private void pooltable$hideInCameraWay(
      Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTick, CallbackInfoReturnable<Boolean> cir
   ) {
      if (PoolClientEvents.hideEntity(entity)) {
         cir.setReturnValue(false);
      }
   }
}

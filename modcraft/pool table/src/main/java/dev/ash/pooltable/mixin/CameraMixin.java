package dev.ash.pooltable.mixin;

import dev.ash.pooltable.client.AimCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @Shadow
   private boolean detached;
   @Shadow
   private Entity entity;

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Shadow
   public abstract Vec3 position();

   @Shadow
   public abstract float yRot();

   @Shadow
   public abstract float xRot();

   @Inject(
      method = {"alignWithEntity"},
      at = {@At("TAIL")}
   )
   private void pooltable$ballCamera(float partialTick, CallbackInfo ci) {
      AimCamera.Pose pose = AimCamera.pose(this.entity, partialTick, this.position(), this.yRot(), this.xRot());
      if (pose != null) {
         this.detached = true;
         this.setRotation(pose.yaw(), pose.pitch());
         this.setPosition(pose.pos());
      }
   }
}

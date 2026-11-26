package dev.ash.pooltable.bbecompat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LevelRenderer.class, priority = 900)
abstract class BetterBlockEntitiesNullGuardMixin {
   @WrapOperation(
      method = "submitBlockEntities",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
      )
   )
   private void pooltable$guardMissingBlockEntity(
      BlockEntityRenderDispatcher dispatcher,
      BlockEntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera,
      Operation<Void> original
   ) {
      try {
         original.call(dispatcher, state, poseStack, collector, camera);
      } catch (NullPointerException error) {
         if (!isBbeMissingEntityCrash(error)) {
            throw error;
         }

         dispatcher.submit(state, poseStack, collector, camera);
      }
   }

   private static boolean isBbeMissingEntityCrash(NullPointerException error) {
      StackTraceElement[] trace = error.getStackTrace();
      return error.getMessage() != null
         && error.getMessage().contains("bbe$isSupportedBlockEntity")
         && trace.length > 0
         && trace[0].getClassName().equals("net.minecraft.client.renderer.LevelRenderer")
         && trace[0].getMethodName().contains("betterblockentities$bbe$submitBreakingOverlays");
   }
}

package com.nettakrim.signed_paintings.mixin.compat.bbe;

import betterblockentities.client.render.immediate.blockentity.manager.SpecialBlockEntityManager;
import com.nettakrim.signed_paintings.SignedPaintingsClient;
import com.nettakrim.signed_paintings.access.SignBlockEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(SpecialBlockEntityManager.class)
public class SpecialBlockEntityManagerExtractMixin {
    @Inject(
            method = "extractManagedState(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void forceExtractSignedPaintings(
            BlockEntity blockEntity,
            CameraRenderState cameraRenderState,
            float partialTicks,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            boolean bl,
            CallbackInfoReturnable<BlockEntityRenderState> cir
    ) {
        if (blockEntity instanceof SignBlockEntity sign) {
            if (Minecraft.getInstance().getCameraEntity() == null || SignedPaintingsClient.paintingRenderer == null) {
                return;
            }

            SignBlockEntityAccessor signAccessor = (SignBlockEntityAccessor) sign;
            signAccessor.signedPaintings$reloadIfNeeded();
            if (SignedPaintingsClient.paintingRenderer.renderWithReducedCulling(signAccessor)
                    || signAccessor.signedPaintings$shouldHide()) {
                cir.setReturnValue(
                        Minecraft.getInstance().getBlockEntityRenderDispatcher()
                                .tryExtractRenderState(sign, partialTicks, crumblingOverlay, bl)
                );
            }
        }
    }
}

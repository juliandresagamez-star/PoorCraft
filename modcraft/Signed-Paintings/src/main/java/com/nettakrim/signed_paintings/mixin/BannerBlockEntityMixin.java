package com.nettakrim.signed_paintings.mixin;

import com.nettakrim.signed_paintings.access.OverlayInfoAccessor;
import com.nettakrim.signed_paintings.rendering.OverlayInfo;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerBlockEntity.class)
public class BannerBlockEntityMixin implements OverlayInfoAccessor {
    @Shadow
    private Component name;

    @Unique
    private OverlayInfo overlayInfo;

    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/DyeColor;)V")
    private void init(CallbackInfo ci) {
        overlayInfo = new OverlayInfo();
    }

    @Inject(at = @At("TAIL"), method = "loadAdditional")
    private void onNBTRead(ValueInput view, CallbackInfo ci) {
        if (name != null) overlayInfo.loadOverlay(name.getString());
    }

    @Inject(at = @At("TAIL"), method = "applyImplicitComponents")
    private void onComponentsRead(DataComponentGetter components, CallbackInfo ci) {
        if (name != null) overlayInfo.loadOverlay(name.getString());
    }

    @Override
    public OverlayInfo signedPaintings$getOverlayInfo() {
        return overlayInfo;
    }

    @Override
    public void signedPaintings$reloadIfNeeded() {
        if (overlayInfo != null && overlayInfo.needsReload()) {
            overlayInfo = new OverlayInfo();
            if (name != null) {
                overlayInfo.loadOverlay(name.getString());
            }
        }
    }
}

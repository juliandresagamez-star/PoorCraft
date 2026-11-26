package dev.ash.pooltable.fabric.mixin;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.client.PoolClientEvents;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AvatarRenderer.class})
public abstract class PlayerRendererMixin {
   @Inject(
      method = {"getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void pooltable$armPose(Avatar player, ItemStack held, InteractionHand hand, CallbackInfoReturnable<ArmPose> cir) {
      if (held.is(PoolTable.CUE.get())) {
         ArmPose pose = PoolClientEvents.armPose(player);
         if (pose != null) {
            cir.setReturnValue(pose);
         }
      }
   }
}

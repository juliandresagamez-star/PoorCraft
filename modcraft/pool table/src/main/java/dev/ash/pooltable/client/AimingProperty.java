package dev.ash.pooltable.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record AimingProperty() implements ConditionalItemModelProperty {
   public static final MapCodec<AimingProperty> MAP_CODEC = MapCodec.unit(new AimingProperty());

   public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
      return context != ItemDisplayContext.GUI && owner instanceof Player p && ClientAim.isAiming(p);
   }

   public MapCodec<AimingProperty> type() {
      return MAP_CODEC;
   }
}

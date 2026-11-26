package dev.ash.pooltable;

import dev.ash.pooltable.block.LampKind;
import dev.ash.pooltable.compat.Shim;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

public final class PoolContent {
   public static final String[] SOUNDS = new String[]{"ball_hit", "cushion", "pocket", "cue_hit", "rack"};

   private PoolContent() {
   }

   public static Properties table() {
      return Shim.get()
         .neverViewBlocking(
            Shim.get()
               .immovable(
                  Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F).sound(SoundType.WOOD).noOcclusion().isSuffocating((state, level, pos) -> false)
               )
         );
   }

   public static Properties rack() {
      return Shim.get()
         .neverViewBlocking(
            Shim.get()
               .popped(
                  Properties.of()
                     .mapColor(MapColor.COLOR_BROWN)
                     .strength(1.5F)
                     .sound(SoundType.WOOD)
                     .ignitedByLava()
                     .noOcclusion()
                     .isSuffocating((state, level, pos) -> false)
               )
         );
   }

   public static Properties lamp(LampKind kind) {
      return Shim.get()
         .neverViewBlocking(
            Shim.get()
               .popped(
                  Properties.of()
                     .mapColor(MapColor.METAL)
                     .strength(0.5F)
                     .sound(SoundType.LANTERN)
                     .lightLevel(state -> kind.light)
                     .noCollision()
                     .noOcclusion()
                     .isSuffocating((state, level, pos) -> false)
               )
         );
   }
}

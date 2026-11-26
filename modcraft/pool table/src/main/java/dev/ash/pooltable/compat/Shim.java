package dev.ash.pooltable.compat;

import com.mojang.serialization.MapCodec;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public interface Shim {
   static Shim get() {
      return Shim.Holder.SHIM;
   }

   Properties immovable(Properties var1);

   Properties popped(Properties var1);

   Properties neverViewBlocking(Properties var1);

   <B extends Block> MapCodec<B> simpleCodec(Function<Properties, B> var1);

   <B extends Block, A> MapCodec<B> codecWith(MapCodec<A> var1, Function<B, A> var2, BiFunction<A, Properties, B> var3);

   public static final class Holder {
      static final Shim SHIM = Shims.load(Shim.class, "dev.ash.pooltable.compat", "Shim");

      private Holder() {
      }
   }
}

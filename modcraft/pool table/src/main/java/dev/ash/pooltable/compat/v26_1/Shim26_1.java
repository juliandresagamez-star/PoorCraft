package dev.ash.pooltable.compat.v26_1;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ash.pooltable.compat.Shim;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.PushReaction;

public class Shim26_1 implements Shim {
   @Override
   public Properties immovable(Properties properties) {
      return properties.pushReaction(PushReaction.BLOCK);
   }

   @Override
   public Properties popped(Properties properties) {
      return properties.pushReaction(PushReaction.DESTROY);
   }

   @Override
   public Properties neverViewBlocking(Properties properties) {
      return properties.isViewBlocking((state, level, pos) -> false);
   }

   @Override
   public <B extends Block> MapCodec<B> simpleCodec(Function<Properties, B> constructor) {
      return BlockBehaviour.simpleCodec(constructor);
   }

   @Override
   public <B extends Block, A> MapCodec<B> codecWith(MapCodec<A> field, Function<B, A> getter, BiFunction<A, Properties, B> constructor) {
      return RecordCodecBuilder.mapCodec(i -> i.group(field.forGetter(getter), Shim26_1.Access.propertiesField()).apply(i, constructor));
   }

   private static final class Access extends Block {
      private Access() {
         super(null);
      }

      static <B extends Block> RecordCodecBuilder<B, Properties> propertiesField() {
         return propertiesCodec();
      }
   }
}

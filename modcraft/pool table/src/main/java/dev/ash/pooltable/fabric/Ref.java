package dev.ash.pooltable.fabric;

import java.util.function.Supplier;

public final class Ref<T> implements Supplier<T> {
   private T value;

   @Override
   public T get() {
      if (this.value == null) {
         throw new IllegalStateException("used before the mod registered it");
      } else {
         return this.value;
      }
   }

   public T set(T value) {
      this.value = value;
      return value;
   }
}

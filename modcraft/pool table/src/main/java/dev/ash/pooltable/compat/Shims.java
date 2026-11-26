package dev.ash.pooltable.compat;

import dev.ash.pooltable.VersionGate;

public final class Shims {
   private Shims() {
   }

   public static <T> T load(Class<T> type, String pkg, String name) {
      int n = Math.max(1, VersionGate.release());

      while (n >= 1) {
         String cls = pkg + ".v26_" + n + "." + name + "26_" + n;

         Class<?> found;
         try {
            found = Class.forName(cls, true, Shims.class.getClassLoader());
         } catch (ClassNotFoundException var8) {
            n--;
            continue;
         }

         try {
            return type.cast(found.getDeclaredConstructor().newInstance());
         } catch (ReflectiveOperationException var7) {
            throw new IllegalStateException("Pool Table: couldn't make " + cls, var7);
         }
      }

      throw new IllegalStateException("Pool Table: no " + name + " for this Minecraft");
   }
}

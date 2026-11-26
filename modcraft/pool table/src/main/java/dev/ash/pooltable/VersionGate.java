package dev.ash.pooltable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;

public final class VersionGate {
   private static final Pattern SUPPORTED = Pattern.compile("26\\.([123])(?:\\.\\d+)?");

   private VersionGate() {
   }

   public static int release() {
      Matcher m = SUPPORTED.matcher(SharedConstants.getCurrentVersion().name());
      return m.matches() ? Integer.parseInt(m.group(1)) : 0;
   }

   public static String problem(String name, boolean stable) {
      if (!stable) {
         return "Pool Table runs only on full releases of Minecraft 26.1, 26.2 and 26.3, not on snapshots, pre-releases or release candidates such as "
            + name
            + ".";
      } else {
         return !SUPPORTED.matcher(name).matches() ? "This Pool Table jar is for Minecraft 26.1, 26.2 and 26.3, not " + name + "." : null;
      }
   }

   public static void check() {
      WorldVersion v = SharedConstants.getCurrentVersion();
      String problem = problem(v.name(), v.stable());
      if (problem != null) {
         throw new IllegalStateException(problem);
      }
   }
}

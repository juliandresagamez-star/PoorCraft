package dev.ash.pooltable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PoolClientConfig {
   private static final Logger LOG = LoggerFactory.getLogger("PoolTable");
   private static boolean loaded;
   private static boolean aimCamera = true;
   private static double aimSensitivity = 0.4;
   private static boolean cubeBalls;
   private static PoolConfig.Guide aimGuide = PoolConfig.Guide.FULL;

   private PoolClientConfig() {
   }

   public static PoolConfig.Guide aimGuide() {
      load();
      return aimGuide;
   }

   public static void setAimGuide(PoolConfig.Guide guide) {
      load();
      aimGuide = guide;
      save();
   }

   public static boolean aimCamera() {
      load();
      return aimCamera;
   }

   public static double aimSensitivity() {
      load();
      return aimSensitivity;
   }

   public static boolean cubeBalls() {
      load();
      return cubeBalls;
   }

   public static void setAimCamera(boolean on) {
      load();
      aimCamera = on;
      save();
   }

   public static void setCubeBalls(boolean on) {
      load();
      cubeBalls = on;
      save();
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("pooltable-client.properties");
   }

   private static void load() {
      if (!loaded) {
         loaded = true;
         Properties p = new Properties();
         if (Files.exists(file())) {
            try (Reader in = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
               p.load(in);
            } catch (IOException var8) {
               LOG.warn("Couldn't read {}, using the defaults", file(), var8);
            }
         }

         aimCamera = Boolean.parseBoolean(p.getProperty("aimCamera", "true").trim());

         try {
            aimSensitivity = Mth.clamp(Double.parseDouble(p.getProperty("aimSensitivity", "0.4").trim()), 0.05, 2.0);
         } catch (NumberFormatException var5) {
            aimSensitivity = 0.4;
         }

         cubeBalls = Boolean.parseBoolean(p.getProperty("cubeBalls", "false").trim());

         try {
            aimGuide = PoolConfig.Guide.valueOf(p.getProperty("aimGuide", "FULL").trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var4) {
            aimGuide = PoolConfig.Guide.FULL;
         }

         save();
      }
   }

   private static void save() {
      try (Writer out = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
         out.write("# Pool Table settings for this player\n\n");
         out.write("# After the break, move the camera behind the cue ball while lining up a shot.\n");
         out.write("aimCamera=" + aimCamera + "\n\n");
         out.write("# How far the cue turns per mouse movement in the ball camera (1 = like turning your head).\n");
         out.write("# Sneaking makes it a quarter of this. 0.05 to 2.\n");
         out.write("aimSensitivity=" + aimSensitivity + "\n\n");
         out.write("# Draw the balls as little cubes that slide instead of roll. They play exactly the same.\n");
         out.write("cubeBalls=" + cubeBalls + "\n\n");
         out.write("# Your own aiming guide: FULL, LINE (aim line and ghost ball only) or OFF.\n");
         out.write("# The server's aimGuide can take it down further, never up.\n");
         out.write("aimGuide=" + aimGuide + "\n");
      } catch (IOException var5) {
         LOG.warn("Couldn't write {}", file(), var5);
      }
   }
}

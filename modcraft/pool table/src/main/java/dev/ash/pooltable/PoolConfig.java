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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PoolConfig {
   private static final Logger LOG = LoggerFactory.getLogger("PoolTable");
   private static PoolConfig.Guide guide = PoolConfig.Guide.FULL;
   private static boolean practiceBallInHand = true;
   private static PoolConfig.Guide syncedGuide;
   private static Boolean syncedPractice;

   private PoolConfig() {
   }

   public static PoolConfig.Guide guide() {
      return syncedGuide != null ? syncedGuide : guide;
   }

   public static boolean practiceBallInHand() {
      return syncedPractice != null ? syncedPractice : practiceBallInHand;
   }

   public static PoolConfig.Guide serverGuide() {
      return guide;
   }

   public static boolean serverPracticeBallInHand() {
      return practiceBallInHand;
   }

   public static void synced(PoolConfig.Guide g, boolean practice) {
      syncedGuide = g;
      syncedPractice = practice;
   }

   public static void clearSynced() {
      syncedGuide = null;
      syncedPractice = null;
   }

   public static void load() {
      Path file = FabricLoader.getInstance().getConfigDir().resolve("pooltable-server.properties");
      Properties p = new Properties();
      if (Files.exists(file)) {
         try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
         } catch (IOException var11) {
            LOG.warn("Couldn't read {}, using the defaults", file, var11);
         }
      }

      try {
         guide = PoolConfig.Guide.valueOf(p.getProperty("aimGuide", "FULL").trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var6) {
         guide = PoolConfig.Guide.FULL;
      }

      practiceBallInHand = Boolean.parseBoolean(p.getProperty("practiceBallInHand", "true").trim());

      try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
         out.write("# Pool Table server settings (sent to players when they join)\n\n");
         out.write("# How much aiming help the shooter sees on the cloth: FULL, LINE or OFF.\n");
         out.write("aimGuide=" + guide + "\n\n");
         out.write("# In practice (fewer than two seated players) anyone may move the cue ball.\n");
         out.write("practiceBallInHand=" + practiceBallInHand + "\n");
      } catch (IOException var9) {
         LOG.warn("Couldn't write {}", file, var9);
      }
   }

   public static enum Guide {
      FULL,
      LINE,
      OFF;
   }
}

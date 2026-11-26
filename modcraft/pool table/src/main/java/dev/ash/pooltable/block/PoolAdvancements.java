package dev.ash.pooltable.block;

import dev.ash.pooltable.PoolTable;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

final class PoolAdvancements {
   private PoolAdvancements() {
   }

   static void award(MinecraftServer server, @Nullable UUID player, String name) {
      ServerPlayer p = player == null ? null : server.getPlayerList().getPlayer(player);
      AdvancementHolder advancement = p == null ? null : server.getAdvancements().get(PoolTable.id("pool/" + name));
      if (advancement != null) {
         p.getAdvancements().award(advancement, "done");
      }
   }
}

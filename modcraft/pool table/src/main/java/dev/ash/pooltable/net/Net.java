package dev.ash.pooltable.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class Net {
   private static Net.Sender sender;

   private Net() {
   }

   public static void install(Net.Sender s) {
      sender = s;
   }

   public static void toServer(CustomPacketPayload payload) {
      sender.toServer(payload);
   }

   public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
      sender.toPlayer(player, payload);
   }

   public static void toTracking(ServerLevel level, BlockPos pos, CustomPacketPayload payload) {
      sender.toTracking(level, pos, payload);
   }

   public interface Sender {
      void toServer(CustomPacketPayload var1);

      void toPlayer(ServerPlayer var1, CustomPacketPayload var2);

      void toTracking(ServerLevel var1, BlockPos var2, CustomPacketPayload var3);
   }
}

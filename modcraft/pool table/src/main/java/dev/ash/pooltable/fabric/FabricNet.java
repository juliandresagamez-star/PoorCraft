package dev.ash.pooltable.fabric;

import dev.ash.pooltable.net.Net;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNet implements Net.Sender {
   public static Consumer<CustomPacketPayload> toServer = payload -> {
      throw new IllegalStateException("only a client sends to the server");
   };

   @Override
   public void toServer(CustomPacketPayload payload) {
      toServer.accept(payload);
   }

   @Override
   public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
      ServerPlayNetworking.send(player, payload);
   }

   @Override
   public void toTracking(ServerLevel level, BlockPos pos, CustomPacketPayload payload) {
      for (ServerPlayer player : PlayerLookup.tracking(level, pos)) {
         ServerPlayNetworking.send(player, payload);
      }
   }
}

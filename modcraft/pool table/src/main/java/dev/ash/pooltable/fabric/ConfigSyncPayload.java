package dev.ash.pooltable.fabric;

import dev.ash.pooltable.PoolConfig;
import dev.ash.pooltable.PoolTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ConfigSyncPayload(int guide, boolean practiceBallInHand) implements CustomPacketPayload {
   public static final Type<ConfigSyncPayload> TYPE = new Type(PoolTable.id("config"));
   public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, ConfigSyncPayload::guide, ByteBufCodecs.BOOL, ConfigSyncPayload::practiceBallInHand, ConfigSyncPayload::new
   );

   public static ConfigSyncPayload current() {
      return new ConfigSyncPayload(PoolConfig.serverGuide().ordinal(), PoolConfig.serverPracticeBallInHand());
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void apply() {
      PoolConfig.Guide[] guides = PoolConfig.Guide.values();
      PoolConfig.synced(guides[Math.floorMod(this.guide, guides.length)], this.practiceBallInHand);
   }
}

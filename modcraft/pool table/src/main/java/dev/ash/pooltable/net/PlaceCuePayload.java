package dev.ash.pooltable.net;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record PlaceCuePayload(BlockPos table, double x, double z) implements CustomPacketPayload {
   public static final Type<PlaceCuePayload> TYPE = new Type(PoolTable.id("place_cue"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PlaceCuePayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, PlaceCuePayload::table, ByteBufCodecs.DOUBLE, PlaceCuePayload::x, ByteBufCodecs.DOUBLE, PlaceCuePayload::z, PlaceCuePayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(Player player) {
      if (player instanceof ServerPlayer sp && sp.level().isLoaded(this.table()) && sp.level().getBlockEntity(this.table()) instanceof PoolTableBlockEntity be) {
         be.placeCue(sp, this.x(), this.z());
      }
   }
}

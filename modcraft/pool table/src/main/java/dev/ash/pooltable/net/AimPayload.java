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

public record AimPayload(BlockPos table, float angle, float power, int call) implements CustomPacketPayload {
   public static final Type<AimPayload> TYPE = new Type(PoolTable.id("aim"));
   public static final StreamCodec<RegistryFriendlyByteBuf, AimPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC,
      AimPayload::table,
      ByteBufCodecs.FLOAT,
      AimPayload::angle,
      ByteBufCodecs.FLOAT,
      AimPayload::power,
      ByteBufCodecs.VAR_INT,
      AimPayload::call,
      AimPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(Player player) {
      if (player instanceof ServerPlayer sp && sp.level().isLoaded(this.table()) && sp.level().getBlockEntity(this.table()) instanceof PoolTableBlockEntity be) {
         be.relayAim(sp, this.angle(), this.power(), this.call());
      }
   }
}

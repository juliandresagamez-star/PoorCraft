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

public record ShootPayload(BlockPos table, float angle, float power, float spin, int call) implements CustomPacketPayload {
   public static final Type<ShootPayload> TYPE = new Type(PoolTable.id("shoot"));
   public static final StreamCodec<RegistryFriendlyByteBuf, ShootPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC,
      ShootPayload::table,
      ByteBufCodecs.FLOAT,
      ShootPayload::angle,
      ByteBufCodecs.FLOAT,
      ShootPayload::power,
      ByteBufCodecs.FLOAT,
      ShootPayload::spin,
      ByteBufCodecs.VAR_INT,
      ShootPayload::call,
      ShootPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(Player player) {
      if (player instanceof ServerPlayer sp && sp.level().isLoaded(this.table()) && sp.level().getBlockEntity(this.table()) instanceof PoolTableBlockEntity be) {
         be.shoot(sp, this.angle(), this.power(), this.spin(), this.call());
      }
   }
}

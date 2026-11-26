package dev.ash.pooltable.net;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public record ShotPayload(BlockPos table, int shotId, long[] state, float angle, float power, float spin) implements CustomPacketPayload {
   public static final Type<ShotPayload> TYPE = new Type(PoolTable.id("shot"));
   private static final StreamCodec<RegistryFriendlyByteBuf, long[]> LONGS = StreamCodec.of(
      (buf, longs) -> buf.writeLongArray(longs), buf -> buf.readLongArray()
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, ShotPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC,
      ShotPayload::table,
      ByteBufCodecs.VAR_INT,
      ShotPayload::shotId,
      LONGS,
      ShotPayload::state,
      ByteBufCodecs.FLOAT,
      ShotPayload::angle,
      ByteBufCodecs.FLOAT,
      ShotPayload::power,
      ByteBufCodecs.FLOAT,
      ShotPayload::spin,
      ShotPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(Player player) {
      Level level = player.level();
      if (level.isLoaded(this.table()) && level.getBlockEntity(this.table()) instanceof PoolTableBlockEntity be) {
         be.clientShot(this.shotId(), this.state(), this.angle(), this.power(), this.spin());
      }
   }
}

package dev.ash.pooltable.net;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.block.TableVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public record AimSyncPayload(BlockPos table, int playerId, float angle, float power, int call) implements CustomPacketPayload {
   public static final Type<AimSyncPayload> TYPE = new Type(PoolTable.id("aim_sync"));
   public static final StreamCodec<RegistryFriendlyByteBuf, AimSyncPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC,
      AimSyncPayload::table,
      ByteBufCodecs.VAR_INT,
      AimSyncPayload::playerId,
      ByteBufCodecs.FLOAT,
      AimSyncPayload::angle,
      ByteBufCodecs.FLOAT,
      AimSyncPayload::power,
      ByteBufCodecs.VAR_INT,
      AimSyncPayload::call,
      AimSyncPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(Player player) {
      Level level = player.level();
      if (level.isLoaded(this.table()) && level.getBlockEntity(this.table()) instanceof PoolTableBlockEntity be) {
         TableVisuals v = be.visuals;
         if (v.aimPlayer != this.playerId() || level.getGameTime() - v.aimTime > 20.0) {
            v.shownAimAngle = this.angle();
         }

         v.aimPlayer = this.playerId();
         v.aimAngle = this.angle();
         v.aimPower = this.power();
         v.aimCall = this.call();
         v.aimTime = level.getGameTime();
      }
   }
}

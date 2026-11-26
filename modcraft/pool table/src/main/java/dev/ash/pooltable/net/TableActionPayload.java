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

public record TableActionPayload(BlockPos table, TableActionPayload.Action action) implements CustomPacketPayload {
   public static final Type<TableActionPayload> TYPE = new Type(PoolTable.id("table_action"));
   public static final StreamCodec<RegistryFriendlyByteBuf, TableActionPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC,
      TableActionPayload::table,
      ByteBufCodecs.idMapper(i -> TableActionPayload.Action.values()[Math.floorMod(i, TableActionPayload.Action.values().length)], Enum::ordinal),
      TableActionPayload::action,
      TableActionPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(Player player) {
      if (player instanceof ServerPlayer sp && sp.level().isLoaded(this.table()) && sp.level().getBlockEntity(this.table()) instanceof PoolTableBlockEntity be) {
         be.action(sp, this.action());
      }
   }

   public static enum Action {
      SIT_1,
      SIT_2,
      LEAVE,
      RACK,
      CONCEDE,
      BOT_EASY,
      BOT_MEDIUM,
      BOT_HARD,
      BOT_EXPERT,
      REMOVE_BOT,
      SIT_3,
      GAME_NEXT,
      TARGET_NEXT,
      SIT_4,
      SIT_5,
      FORMAT_NEXT;
   }
}

package dev.ash.pooltable.block;

import dev.ash.pooltable.PoolTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CueRackBlockEntity extends BlockEntity {
   private final NonNullList<ItemStack> cues;

   public CueRackBlockEntity(BlockPos pos, BlockState state) {
      super(PoolTable.RACK_BE.get(), pos, state);
      this.cues = NonNullList.withSize(state.getBlock() instanceof CueRackBlock rack ? rack.size() : 8, ItemStack.EMPTY);
   }

   public int size() {
      return this.cues.size();
   }

   public ItemStack get(int slot) {
      return (ItemStack)this.cues.get(slot);
   }

   public int count() {
      int n = 0;

      for (ItemStack cue : this.cues) {
         n += cue.isEmpty() ? 0 : 1;
      }

      return n;
   }

   NonNullList<ItemStack> cues() {
      return this.cues;
   }

   void put(int slot, ItemStack cue) {
      this.cues.set(slot, cue);
      this.changed();
   }

   ItemStack take(int slot) {
      ItemStack cue = (ItemStack)this.cues.get(slot);
      this.cues.set(slot, ItemStack.EMPTY);
      this.changed();
      return cue;
   }

   private void changed() {
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
      }
   }

   protected void saveAdditional(ValueOutput output) {
      super.saveAdditional(output);
      ContainerHelper.saveAllItems(output, this.cues);
   }

   protected void loadAdditional(ValueInput input) {
      super.loadAdditional(input);

      for (int i = 0; i < this.cues.size(); i++) {
         this.cues.set(i, ItemStack.EMPTY);
      }

      ContainerHelper.loadAllItems(input, this.cues);
   }

   public void preRemoveSideEffects(BlockPos pos, BlockState state) {
      if (this.level != null) {
         Containers.dropContents(this.level, pos, this.cues);
      }
   }

   public CompoundTag getUpdateTag(Provider registries) {
      return this.saveCustomOnly(registries);
   }

   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}

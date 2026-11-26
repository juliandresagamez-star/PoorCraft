package dev.ash.pooltable;

import dev.ash.pooltable.block.PoolTableBlockEntity;
import net.minecraft.sounds.SoundEvent;

public interface ClientHooks {
   default void openTableScreen(PoolTableBlockEntity table) {
   }

   default void cueReleased(int ticks) {
   }

   default void playSound(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
   }

   default void clothChanged(PoolTableBlockEntity table) {
   }
}

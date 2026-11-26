package dev.ash.pooltable.client;

import dev.ash.pooltable.ClientHooks;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.client.compat.ClientShim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

final class PoolClientHooks implements ClientHooks {
   @Override
   public void openTableScreen(PoolTableBlockEntity table) {
      ClientShim.get().setScreen(new PoolTableScreen(table.getBlockPos()));
   }

   @Override
   public void cueReleased(int ticks) {
      ClientAim.released(ticks);
   }

   @Override
   public void playSound(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         mc.level.playLocalSound(x, y, z, sound, SoundSource.BLOCKS, volume, pitch, false);
      }
   }

   @Override
   public void clothChanged(PoolTableBlockEntity table) {
      AABB box = table.frame().bounds();
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         for (int sx = SectionPos.blockToSectionCoord(Mth.floor(box.minX)); sx <= SectionPos.blockToSectionCoord(Mth.floor(box.maxX)); sx++) {
            for (int sy = SectionPos.blockToSectionCoord(Mth.floor(box.minY)); sy <= SectionPos.blockToSectionCoord(Mth.floor(box.maxY)); sy++) {
               for (int sz = SectionPos.blockToSectionCoord(Mth.floor(box.minZ)); sz <= SectionPos.blockToSectionCoord(Mth.floor(box.maxZ)); sz++) {
                  level.setSectionDirtyWithNeighbors(sx, sy, sz);
               }
            }
         }
      }
   }
}

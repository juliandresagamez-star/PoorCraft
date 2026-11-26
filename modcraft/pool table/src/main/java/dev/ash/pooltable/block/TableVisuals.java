package dev.ash.pooltable.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.joml.Quaternionf;

public final class TableVisuals {
   public static final double DROP_TICKS = 8.0;
   public final Quaternionf[] orient = new Quaternionf[16];
   public final double[] shownX = new double[16];
   public final double[] shownZ = new double[16];
   public boolean shownValid;
   public double lastFrame = Double.NaN;
   public final List<TableVisuals.Drop> drops = new ArrayList<>();
   public double strikeTime = -1000.0;
   public float strikeAngle;
   public float strikePull;
   public float strikeSpin;
   public double strikeX;
   public double strikeZ;
   public int aimPlayer = -1;
   public float aimAngle;
   public float aimPower;
   public int aimCall = -1;
   public double aimTime = -1000.0;
   public float shownAimAngle;
   public float shownAimPower;
   public long aimNanos;
   public double messageTime = -1000.0;

   public TableVisuals() {
      Random r = new Random(99L);

      for (int i = 0; i < 16; i++) {
         this.orient[i] = new Quaternionf().rotateY((float)(r.nextGaussian() * 0.5)).rotateX((float)(r.nextGaussian() * 0.3));
      }
   }

   public boolean dropping(int ball, double now) {
      for (TableVisuals.Drop d : this.drops) {
         if (d.ball() == ball && now - d.start() < 8.0) {
            return true;
         }
      }

      return false;
   }

   public void prune(double now) {
      this.drops.removeIf(d -> now - d.start() > 10.0);
   }

   public record Drop(int ball, int pocket, double x, double z, double start) {
   }
}

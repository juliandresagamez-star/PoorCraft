package dev.ash.pooltable.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

public final class PoolSim {
   public static final int BALLS = 16;
   public static final int CUE = 0;
   public static final int EIGHT = 8;
   public static final int SUBSTEPS = 20;
   public static final double DT = 0.0025;
   public static final double MIN_SPEED = 0.18;
   public static final double MAX_SPEED = 12.0;
   static final double SLIDE = 3.2;
   static final double ROLL = 0.2;
   static final double DRAG = 0.05;
   static final double E_BALL = 0.94;
   static final double E_CUSHION = 0.84;
   static final double CUSHION_GRIP = 0.1;
   static final double CUSHION_ROLL = 0.6;
   static final double STOP = 0.012;
   static final double CREEP = 0.2;
   static final double CREEP_ROLL = 0.3;
   private static final double R = 0.075;
   private static final double D2 = 0.0225;
   public final double[] x = new double[16];
   public final double[] z = new double[16];
   public final double[] vx = new double[16];
   public final double[] vz = new double[16];
   public final double[] ux = new double[16];
   public final double[] uz = new double[16];
   public final boolean[] on = new boolean[16];
   public int escapes;
   private double[] path;
   public static final PoolSim.Events NO_EVENTS = new PoolSim.Events() {};

   public void rack(RandomGenerator random) {
      List<Integer> solids = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7));
      List<Integer> stripes = new ArrayList<>(List.of(9, 10, 11, 12, 13, 14, 15));
      Collections.shuffle(solids, new Random(random.nextLong()));
      Collections.shuffle(stripes, new Random(random.nextLong()));
      int[] order = new int[15];
      int back0 = 10;
      int back4 = 14;
      int eightSlot = 4;
      boolean solidLeft = random.nextBoolean();
      order[back0] = solidLeft ? solids.remove(0) : stripes.remove(0);
      order[back4] = solidLeft ? stripes.remove(0) : solids.remove(0);
      order[eightSlot] = 8;
      List<Integer> rest = new ArrayList<>();
      rest.addAll(solids);
      rest.addAll(stripes);
      Collections.shuffle(rest, new Random(random.nextLong()));
      int k = 0;

      for (int s = 0; s < 15; s++) {
         if (s != back0 && s != back4 && s != eightSlot) {
            order[s] = rest.get(k++);
         }
      }

      Arrays.fill(this.on, false);
      double gap = 0.0015;
      double dz = (0.15 + gap) * Math.sqrt(3.0) / 2.0;
      int sx = 0;

      for (int row = 0; row < 5; row++) {
         for (int i = 0; i <= row; i++) {
            int b = order[sx++];
            this.place(b, (i - row / 2.0) * (0.15 + gap) + (random.nextDouble() - 0.5) * 0.001, 1.09375 + row * dz + (random.nextDouble() - 0.5) * 0.001);
         }
      }

      this.place(0, 0.0, -1.09375);
   }

   public void rack(GameType type, RandomGenerator random) {
      if (type.eightBall()) {
         this.rack(random);
      } else {
         int[] rows = switch (type) {
            case NINE -> new int[]{1, 2, 3, 2, 1};
            case TEN -> new int[]{1, 2, 3, 4};
            default -> new int[]{1, 2, 3, 4, 5};
         };
         int n = type.balls();
         int[] order = new int[n];
         Random shuffle = new Random(random.nextLong());
         List<Integer> loose = new ArrayList<>();

         for (int b = 1; b <= n; b++) {
            loose.add(b);
         }
         int[][] fixed = switch (type) {
            case NINE -> new int[][]{{0, 1}, {4, 9}};
            case TEN -> shuffle.nextBoolean() ? new int[][]{{0, 1}, {4, 10}, {6, 2}, {9, 3}} : new int[][]{{0, 1}, {4, 10}, {6, 3}, {9, 2}};
            case CUTTHROAT -> shuffle.nextBoolean() ? new int[][]{{0, 1}, {10, 6}, {14, 11}} : new int[][]{{0, 1}, {10, 11}, {14, 6}};
            default -> new int[0][];
         };
         Arrays.fill(order, 0);

         for (int[] f : fixed) {
            order[f[0]] = f[1];
            loose.remove(Integer.valueOf(f[1]));
         }

         Collections.shuffle(loose, shuffle);
         int slot = 0;

         for (int k = 0; slot < n; slot++) {
            if (order[slot] == 0) {
               order[slot] = loose.get(k++);
            }
         }

         Arrays.fill(this.on, false);
         slot = 0;

         for (int row = 0; row < rows.length; row++) {
            for (int i = 0; i < rows[row]; i++) {
               double[] at = rackSpot(row, i, rows[row], random);
               this.place(order[slot++], at[0], at[1]);
            }
         }

         this.place(0, 0.0, -1.09375);
      }
   }

   private static double[] rackSpot(int row, int i, int len, RandomGenerator random) {
      double gap = 0.0015;
      double dz = (0.15 + gap) * Math.sqrt(3.0) / 2.0;
      return new double[]{(i - (len - 1) / 2.0) * (0.15 + gap) + (random.nextDouble() - 0.5) * 0.001, 1.09375 + row * dz + (random.nextDouble() - 0.5) * 0.001};
   }

   public boolean rerack(List<Integer> balls, RandomGenerator random) {
      Random shuffle = new Random(random.nextLong());
      List<Integer> order = new ArrayList<>(balls);
      Collections.shuffle(order, shuffle);
      List<double[]> spots = new ArrayList<>();

      for (int row = 0; row < 5; row++) {
         for (int i = 0; i <= row; i++) {
            if (row != 0 || order.size() >= 15) {
               spots.add(rackSpot(row, i, row + 1, random));
            }
         }
      }

      boolean cueInWay = false;

      for (int b = 0; b < 16; b++) {
         if (this.on[b] && !balls.contains(b)) {
            for (double[] at : spots) {
               if (Math.hypot(this.x[b] - at[0], this.z[b] - at[1]) < 0.152) {
                  cueInWay |= b == 0;
                  this.remove(b);
                  this.spot(b, 0.0, -1.09375);
                  break;
               }
            }
         }
      }

      for (int k = 0; k < order.size() && k < spots.size(); k++) {
         this.place(order.get(k), spots.get(k)[0], spots.get(k)[1]);
      }

      return cueInWay;
   }

   public void place(int ball, double px, double pz) {
      this.x[ball] = px;
      this.z[ball] = pz;
      this.vx[ball] = this.vz[ball] = this.ux[ball] = this.uz[ball] = 0.0;
      this.on[ball] = true;
   }

   public void remove(int ball) {
      this.on[ball] = false;
      this.vx[ball] = this.vz[ball] = this.ux[ball] = this.uz[ball] = 0.0;
   }

   public void strike(double angle, double power, double spin) {
      double speed = speedFor(power);
      double dx = StrictMath.sin(angle);
      double dz = StrictMath.cos(angle);
      this.vx[0] = speed * dx;
      this.vz[0] = speed * dz;
      this.ux[0] = 1.25 * spin * this.vx[0];
      this.uz[0] = 1.25 * spin * this.vz[0];
   }

   public static double speedFor(double power) {
      double p = Math.max(0.0, Math.min(1.0, power));
      return 0.18 + 11.82 * StrictMath.pow(p, 1.5);
   }

   public boolean moving() {
      for (int i = 0; i < 16; i++) {
         if (this.on[i] && (this.vx[i] != 0.0 || this.vz[i] != 0.0 || this.ux[i] != 0.0 || this.uz[i] != 0.0)) {
            return true;
         }
      }

      return false;
   }

   public boolean isFree(int ball, double px, double pz) {
      if (!(Math.abs(px) > 1.1125) && !(Math.abs(pz) > 2.1125)) {
         for (int i = 0; i < 16; i++) {
            if (i != ball && this.on[i]) {
               double dx = this.x[i] - px;
               double dz = this.z[i] - pz;
               if (dx * dx + dz * dz < 0.022500001) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean spot(int ball, double px, double pz) {
      for (double d = 0.0; d < 4.375; d += 0.01) {
         if (this.isFree(ball, px, pz + d)) {
            this.place(ball, px, pz + d);
            return true;
         }

         if (this.isFree(ball, px, pz - d)) {
            this.place(ball, px, pz - d);
            return true;
         }
      }

      return false;
   }

   public void recordPath(boolean record) {
      this.path = record ? new double[672] : null;
   }

   public double pathX(int ball, double t) {
      return this.pathAt(ball, t, 0, this.x);
   }

   public double pathZ(int ball, double t) {
      return this.pathAt(ball, t, 1, this.z);
   }

   private double pathAt(int ball, double t, int axis, double[] now) {
      if (this.path == null) {
         return now[ball];
      } else {
         double f = Math.max(0.0, Math.min(1.0, t)) * 20.0;
         int i = Math.min(19, (int)f);
         double a = this.path[(i * 16 + ball) * 2 + axis];
         double b = this.path[((i + 1) * 16 + ball) * 2 + axis];
         return a + (b - a) * (f - i);
      }
   }

   public void settlePath() {
      if (this.path != null) {
         for (int s = 0; s <= 20; s++) {
            this.store(s);
         }
      }
   }

   private void store(int s) {
      for (int i = 0; i < 16; i++) {
         this.path[(s * 16 + i) * 2] = this.x[i];
         this.path[(s * 16 + i) * 2 + 1] = this.z[i];
      }
   }

   public boolean tick(PoolSim.Events events) {
      if (this.path != null) {
         this.store(0);
      }

      for (int s = 0; s < 20; s++) {
         this.step(events);
         if (this.path != null) {
            this.store(s + 1);
         }
      }

      return this.moving();
   }

   private void step(PoolSim.Events events) {
      for (int i = 0; i < 16; i++) {
         if (this.on[i]) {
            this.friction(i);
            this.x[i] = this.x[i] + this.vx[i] * 0.0025;
            this.z[i] = this.z[i] + this.vz[i] * 0.0025;
         }
      }

      for (int ix = 0; ix < 16; ix++) {
         if (this.on[ix]) {
            for (int j = ix + 1; j < 16; j++) {
               if (this.on[j]) {
                  this.collide(ix, j, events);
               }
            }
         }
      }

      for (int ixx = 0; ixx < 16; ixx++) {
         if (this.on[ixx]) {
            this.cushions(ixx, events);
            this.pockets(ixx, events);
         }
      }
   }

   private void friction(int i) {
      double sx = this.vx[i] - this.ux[i];
      double sz = this.vz[i] - this.uz[i];
      double slip = Math.sqrt(sx * sx + sz * sz);
      double dv = 0.008;
      if (slip > 3.5 * dv) {
         this.vx[i] = this.vx[i] - dv * sx / slip;
         this.vz[i] = this.vz[i] - dv * sz / slip;
         this.ux[i] = this.ux[i] + 2.5 * dv * sx / slip;
         this.uz[i] = this.uz[i] + 2.5 * dv * sz / slip;
      } else {
         if (slip > 0.0) {
            this.vx[i] = (5.0 * this.vx[i] + 2.0 * this.ux[i]) / 7.0;
            this.vz[i] = (5.0 * this.vz[i] + 2.0 * this.uz[i]) / 7.0;
         }

         double speed = Math.sqrt(this.vx[i] * this.vx[i] + this.vz[i] * this.vz[i]);
         double loss = (0.2 + 0.05 * speed + (speed < 0.2 ? 0.3 : 0.0)) * 0.0025;
         if (!(speed <= loss) && !(speed < 0.012)) {
            double k = (speed - loss) / speed;
            this.vx[i] = this.vx[i] * k;
            this.vz[i] = this.vz[i] * k;
         } else {
            this.vx[i] = this.vz[i] = 0.0;
         }

         this.ux[i] = this.vx[i];
         this.uz[i] = this.vz[i];
      }
   }

   private void collide(int a, int b, PoolSim.Events events) {
      double dx = this.x[b] - this.x[a];
      double dz = this.z[b] - this.z[a];
      double d2 = dx * dx + dz * dz;
      if (!(d2 >= 0.0225)) {
         double d = Math.sqrt(d2);
         double nx;
         double nz;
         if (d < 1.0E-9) {
            nx = 1.0;
            nz = 0.0;
         } else {
            nx = dx / d;
            nz = dz / d;
         }

         double approach = (this.vx[a] - this.vx[b]) * nx + (this.vz[a] - this.vz[b]) * nz;
         if (approach > 0.0) {
            double j = 0.97 * approach;
            this.vx[a] = this.vx[a] - j * nx;
            this.vz[a] = this.vz[a] - j * nz;
            this.vx[b] = this.vx[b] + j * nx;
            this.vz[b] = this.vz[b] + j * nz;
            events.ballHit(a, b, approach);
         }

         double push = (0.15 - d) / 2.0;
         this.x[a] = this.x[a] - nx * push;
         this.z[a] = this.z[a] - nz * push;
         this.x[b] = this.x[b] + nx * push;
         this.z[b] = this.z[b] + nz * push;
      }
   }

   private void cushions(int i, PoolSim.Events events) {
      double[] seg = TableDims.SEGMENTS;

      for (int k = 0; k < seg.length; k += 4) {
         double ax = seg[k];
         double az = seg[k + 1];
         double bx = seg[k + 2];
         double bz = seg[k + 3];
         double ex = bx - ax;
         double ez = bz - az;
         double t = ((this.x[i] - ax) * ex + (this.z[i] - az) * ez) / (ex * ex + ez * ez);
         t = Math.max(0.0, Math.min(1.0, t));
         double cx = ax + ex * t;
         double cz = az + ez * t;
         double dx = this.x[i] - cx;
         double dz = this.z[i] - cz;
         double d2 = dx * dx + dz * dz;
         if (!(d2 >= 0.005625) && !(d2 < 1.0E-18)) {
            double d = Math.sqrt(d2);
            double nx = dx / d;
            double nz = dz / d;
            double vn = this.vx[i] * nx + this.vz[i] * nz;
            if (vn < 0.0) {
               double tx = this.vx[i] - vn * nx;
               double tz = this.vz[i] - vn * nz;
               double out = -0.84 * vn;
               this.vx[i] = tx * 0.9 + out * nx;
               this.vz[i] = tz * 0.9 + out * nz;
               double un = this.ux[i] * nx + this.uz[i] * nz;
               this.ux[i] = this.ux[i] + (0.6 * out - un) * nx;
               this.uz[i] = this.uz[i] + (0.6 * out - un) * nz;
               events.cushionHit(i, -vn);
            }

            this.x[i] = cx + nx * 0.075;
            this.z[i] = cz + nz * 0.075;
         }
      }

      if (Math.abs(this.x[i]) > 1.45 || Math.abs(this.z[i]) > 2.45) {
         this.escapes++;
         this.place(i, Math.max(-1.1125, Math.min(1.1125, this.x[i])), Math.max(-2.1125, Math.min(2.1125, this.z[i])));
      }
   }

   private void pockets(int i, PoolSim.Events events) {
      double[] p = TableDims.POCKETS;

      for (int k = 0; k < p.length; k += 4) {
         double dx = this.x[i] - p[k];
         double dz = this.z[i] - p[k + 1];
         if (dx * dx + dz * dz < p[k + 2] * p[k + 2]) {
            this.remove(i);
            events.pocketed(i, k / 4);
            return;
         }
      }
   }

   public long[] save() {
      long[] out = new long[112];

      for (int i = 0; i < 16; i++) {
         int o = i * 7;
         out[o] = Double.doubleToRawLongBits(this.x[i]);
         out[o + 1] = Double.doubleToRawLongBits(this.z[i]);
         out[o + 2] = Double.doubleToRawLongBits(this.vx[i]);
         out[o + 3] = Double.doubleToRawLongBits(this.vz[i]);
         out[o + 4] = Double.doubleToRawLongBits(this.ux[i]);
         out[o + 5] = Double.doubleToRawLongBits(this.uz[i]);
         out[o + 6] = this.on[i] ? 1L : 0L;
      }

      return out;
   }

   public void load(long[] in) {
      if (in.length == 112) {
         for (int i = 0; i < 16; i++) {
            int o = i * 7;
            this.x[i] = Double.longBitsToDouble(in[o]);
            this.z[i] = Double.longBitsToDouble(in[o + 1]);
            this.vx[i] = Double.longBitsToDouble(in[o + 2]);
            this.vz[i] = Double.longBitsToDouble(in[o + 3]);
            this.ux[i] = Double.longBitsToDouble(in[o + 4]);
            this.uz[i] = Double.longBitsToDouble(in[o + 5]);
            this.on[i] = in[o + 6] != 0L;
            if (!Double.isFinite(this.x[i] + this.z[i] + this.vx[i] + this.vz[i] + this.ux[i] + this.uz[i])) {
               this.place(i, 0.0, 0.0);
               this.on[i] = false;
            }
         }
      }
   }

   public void copyFrom(PoolSim other) {
      this.load(other.save());
   }

   public interface Events {
      default void ballHit(int a, int b, double speed) {
      }

      default void cushionHit(int ball, double speed) {
      }

      default void pocketed(int ball, int pocket) {
      }
   }
}

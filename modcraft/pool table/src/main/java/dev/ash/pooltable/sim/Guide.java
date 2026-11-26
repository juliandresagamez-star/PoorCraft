package dev.ash.pooltable.sim;

public final class Guide {
   public static final int NOTHING = -1;
   public int ball = -1;
   public double endX;
   public double endZ;
   public double objX;
   public double objZ;
   public double cueX;
   public double cueZ;
   public double bounceX;
   public double bounceZ;

   public static Guide cast(PoolSim sim, double angle) {
      Guide g = new Guide();
      double r = 0.075;
      double ox = sim.x[0];
      double oz = sim.z[0];
      double dx = StrictMath.sin(angle);
      double dz = StrictMath.cos(angle);
      double best = Double.MAX_VALUE;

      for (int i = 1; i < 16; i++) {
         if (sim.on[i]) {
            double cx = sim.x[i] - ox;
            double cz = sim.z[i] - oz;
            double along = cx * dx + cz * dz;
            if (!(along <= 0.0)) {
               double perp2 = cx * cx + cz * cz - along * along;
               double reach = 4.0 * r * r - perp2;
               if (!(reach < 0.0)) {
                  double t = along - Math.sqrt(reach);
                  if (t < best) {
                     best = t;
                     g.ball = i;
                  }
               }
            }
         }
      }

      double wallX = 1.1875 - r;
      double wallZ = 2.1875 - r;
      double tx = dx > 1.0E-9 ? (wallX - ox) / dx : (dx < -1.0E-9 ? (-wallX - ox) / dx : Double.MAX_VALUE);
      double tz = dz > 1.0E-9 ? (wallZ - oz) / dz : (dz < -1.0E-9 ? (-wallZ - oz) / dz : Double.MAX_VALUE);
      double wall = Math.max(0.0, Math.min(tx, tz));
      if (wall < best) {
         g.ball = -1;
         best = wall;
         g.bounceX = tx < tz ? -dx : dx;
         g.bounceZ = tx < tz ? dz : -dz;
      }

      g.endX = ox + dx * best;
      g.endZ = oz + dz * best;
      if (g.ball != -1) {
         double nx = sim.x[g.ball] - g.endX;
         double nz = sim.z[g.ball] - g.endZ;
         double n = Math.sqrt(nx * nx + nz * nz);
         nx /= n;
         nz /= n;
         double share = dx * nx + dz * nz;
         g.objX = nx * share;
         g.objZ = nz * share;
         g.cueX = dx - g.objX;
         g.cueZ = dz - g.objZ;
      }

      return g;
   }
}

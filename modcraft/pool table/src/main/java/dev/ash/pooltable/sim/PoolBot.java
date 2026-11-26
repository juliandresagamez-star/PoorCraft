package dev.ash.pooltable.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

public final class PoolBot {
   private static final double R = 0.075;
   private static final double WIN = 5000.0;
   private static final double LOSS = -10000.0;
   private static final double FOUL = -400.0;
   private static final double POT = 1000.0;
   private static final double POINT = 600.0;
   private static final double POSITION = 400.0;
   private static final double MISS = 200.0;
   private static final double SAFETY = 300.0;
   private static final double OPPONENT_AIM = Math.toRadians(0.6);
   private static final int MAX_TICKS = 600;

   private PoolBot() {
   }

   public static PoolBot.Plan plan(PoolSim table, Rules.Match match, boolean isBreak, EightBall.BallInHand inHand, PoolBot.Level level, RandomGenerator random) {
      PoolSim sim = new PoolSim();
      sim.copyFrom(table);
      if (isBreak) {
         return breakShot(sim, inHand != EightBall.BallInHand.NONE, level, random);
      } else {
         int me = match.shooter();
         PoolBot.Aims aims = PoolBot.Aims.of(match.type(), me, match.teamOf(me), match.group(), sim.on);
         double cx = sim.x[0];
         double cz = sim.z[0];
         List<PoolBot.Option> options = new ArrayList<>();
         if (inHand == EightBall.BallInHand.NONE) {
            pots(sim, aims, cx, cz, false, level.aimError, options);
         } else {
            for (double[] spot : spots(sim, aims, inHand)) {
               pots(sim, aims, spot[0], spot[1], true, level.aimError, options);
            }
         }

         options.sort(Comparator.comparingDouble(PoolBot.Option::chance).reversed());
         PoolBot.Option bestOption = null;
         double best = Double.NEGATIVE_INFINITY;
         double bestPower = 0.0;
         double bestSpin = 0.0;

         for (int i = 0; i < Math.min(level.tries, options.size()); i++) {
            PoolBot.Option o = options.get(i);

            for (double power : level.powers) {
               for (double spin : level.spins) {
                  double v = value(sim, o, power, spin, match, level);
                  if (v > best) {
                     best = v;
                     bestOption = o;
                     bestPower = power;
                     bestSpin = spin;
                  }
               }
            }
         }

         if (best < (level.thinks ? 0 : 1)) {
            List<PoolBot.Option> hits = new ArrayList<>();

            for (int t = 1; t < 16; t++) {
               if (aims.hit[t] && !blocked(sim, cx, cz, sim.x[t], sim.z[t], 0, t)) {
                  hits.add(new PoolBot.Option(cx, cz, false, Math.atan2(sim.x[t] - cx, sim.z[t] - cz), 1.0, -1));
               }
            }

            if (hits.isEmpty()) {
               for (int k = 0; k < 48; k++) {
                  hits.add(new PoolBot.Option(cx, cz, false, (Math.PI * 2) * k / 48.0, 1.0, -1));
               }
            }

            for (PoolBot.Option o : hits) {
               for (double power : new double[]{0.22, 0.36}) {
                  double v = value(sim, o, power, 0.0, match, level);
                  if (v > best) {
                     best = v;
                     bestOption = o;
                     bestPower = power;
                     bestSpin = 0.0;
                  }
               }
            }
         }

         if (bestOption == null) {
            bestOption = new PoolBot.Option(cx, cz, false, 0.0, 1.0, -1);
            bestPower = 0.3;
         }

         return noisy(bestOption.place, bestOption.cueX, bestOption.cueZ, bestOption.angle, bestPower, bestSpin, bestOption.pocket, level, random);
      }
   }

   private static PoolBot.Plan breakShot(PoolSim sim, boolean place, PoolBot.Level level, RandomGenerator random) {
      int apex = -1;
      double nearest = Double.MAX_VALUE;

      for (int b = 1; b < 16; b++) {
         double d = sim.on[b] ? Math.hypot(sim.x[b], sim.z[b] - 1.09375) : Double.MAX_VALUE;
         if (d < nearest) {
            nearest = d;
            apex = b;
         }
      }

      double x = sim.x[0];
      double z = sim.z[0];
      if (place) {
         x = (random.nextDouble() - 0.5) * 0.5;
         z = -1.10375;
         if (!sim.isFree(0, x, z)) {
            x = 0.0;
         }
      }

      double angle = apex < 0 ? 0.0 : Math.atan2(sim.x[apex] - x, sim.z[apex] - z);

      double power = switch (level) {
         case EASY -> 0.75;
         case MEDIUM -> 0.9;
         default -> 1.0;
      };
      return noisy(place, x, z, angle, power, 0.0, -1, level, random);
   }

   private static PoolBot.Plan noisy(
      boolean place, double x, double z, double angle, double power, double spin, int call, PoolBot.Level level, RandomGenerator random
   ) {
      double a = angle + random.nextGaussian() * level.aimError;
      double p = Math.max(0.05, Math.min(1.0, power * (1.0 + random.nextGaussian() * level.powerError)));
      double s = spin == 0.0 ? 0.0 : Math.max(-1.0, Math.min(1.0, spin + random.nextGaussian() * 0.1));
      return new PoolBot.Plan(place, x, z, (float)a, (float)p, (float)s, call);
   }

   private static void pots(PoolSim sim, PoolBot.Aims aims, double cx, double cz, boolean place, double aimError, List<PoolBot.Option> out) {
      double[] angle = new double[1];

      for (int t = 1; t < 16; t++) {
         if (aims.hit[t] && aims.pot[t]) {
            for (int k = 0; k < 6; k++) {
               if (aims.pockets[k]) {
                  double chance = aims.banks ? bank(sim, cx, cz, t, k, aimError, angle) : pot(sim, cx, cz, t, k, aimError, angle);
                  if (chance > 0.0) {
                     out.add(new PoolBot.Option(cx, cz, place, angle[0], chance, k));
                  }
               }
            }
         }
      }
   }

   private static double bestChance(PoolSim sim, PoolBot.Aims aims, double cx, double cz, double aimError) {
      double best = 0.0;
      double[] angle = new double[1];

      for (int t = 1; t < 16; t++) {
         if (aims.hit[t] && aims.pot[t]) {
            for (int k = 0; k < 6; k++) {
               if (aims.pockets[k]) {
                  best = Math.max(best, aims.banks ? bank(sim, cx, cz, t, k, aimError, angle) : pot(sim, cx, cz, t, k, aimError, angle));
               }
            }
         }
      }

      return best;
   }

   private static double pot(PoolSim sim, double cx, double cz, int t, int k, double aimError, double[] angle) {
      double[] p = TableDims.POCKETS;
      double px = p[k * 4];
      double pz = p[k * 4 + 1];
      if ((k == 2 || k == 3) && Math.abs(px - sim.x[t]) < 0.4 * Math.hypot(px - sim.x[t], pz - sim.z[t])) {
         return 0.0;
      } else {
         return blocked(sim, sim.x[t], sim.z[t], px, pz, t, 0)
            ? 0.0
            : cut(sim, cx, cz, t, px, pz, Math.hypot(px - sim.x[t], pz - sim.z[t]), p[k * 4 + 2], aimError, angle);
      }
   }

   private static double bank(PoolSim sim, double cx, double cz, int t, int k, double aimError, double[] angle) {
      double[] p = TableDims.POCKETS;
      double px = p[k * 4];
      double pz = p[k * 4 + 1];
      double ox = sim.x[t];
      double oz = sim.z[t];
      double wx = 1.1125;
      double wz = 2.1125;
      double best = 0.0;
      double bestAngle = 0.0;
      double[] a = new double[1];

      for (int rail = 0; rail < 4; rail++) {
         boolean alongX = rail < 2;
         double line = (rail % 2 == 0 ? -1 : 1) * (alongX ? wx : wz);
         double mx = alongX ? 2.0 * line - px : px;
         double mz = alongX ? pz : 2.0 * line - pz;
         double f = alongX ? (line - ox) / (mx - ox) : (line - oz) / (mz - oz);
         if (f > 0.0 && f < 1.0) {
            double hx = ox + (mx - ox) * f;
            double hz = oz + (mz - oz) * f;
            if (!Rules.inJaws(hx, hz) && !blocked(sim, ox, oz, hx, hz, t, 0) && !blocked(sim, hx, hz, px, pz, t, 0)) {
               double run = Math.hypot(hx - ox, hz - oz) + Math.hypot(px - hx, pz - hz);
               double chance = 0.7 * cut(sim, cx, cz, t, hx + (mx - hx) * 0.001, hz + (mz - hz) * 0.001, run, p[k * 4 + 2], aimError, a);
               if (chance > best) {
                  best = chance;
                  bestAngle = a[0];
               }
            }
         }
      }

      angle[0] = bestAngle;
      return best;
   }

   private static double cut(PoolSim sim, double cx, double cz, int t, double tx, double tz, double run, double capture, double aimError, double[] angle) {
      double ox = sim.x[t];
      double oz = sim.z[t];
      double ux = tx - ox;
      double uz = tz - oz;
      double d = Math.sqrt(ux * ux + uz * uz);
      if (d < 1.0E-9) {
         return 0.0;
      } else {
         ux /= d;
         uz /= d;
         double gx = ox - 0.15 * ux;
         double gz = oz - 0.15 * uz;
         double vx = gx - cx;
         double vz = gz - cz;
         double d1 = Math.sqrt(vx * vx + vz * vz);
         if (d1 < 1.0E-6) {
            return 0.0;
         } else {
            double cos = (vx * ux + vz * uz) / d1;
            if (cos < 0.17) {
               return 0.0;
            } else if (blocked(sim, cx, cz, gx, gz, 0, t)) {
               return 0.0;
            } else {
               angle[0] = Math.atan2(vx, vz);
               double tolerance = 0.55 * capture * 2.0 * 0.075 * cos / (Math.max(d1, 0.15) * Math.max(run, 0.05));
               return erf(tolerance / (aimError * Math.sqrt(2.0)));
            }
         }
      }
   }

   private static boolean blocked(PoolSim sim, double x0, double z0, double x1, double z1, int a, int b) {
      double ex = x1 - x0;
      double ez = z1 - z0;
      double len2 = ex * ex + ez * ez;

      for (int j = 0; j < 16; j++) {
         if (j != a && j != b && sim.on[j]) {
            double t = len2 < 1.0E-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((sim.x[j] - x0) * ex + (sim.z[j] - z0) * ez) / len2));
            double dx = sim.x[j] - (x0 + ex * t);
            double dz = sim.z[j] - (z0 + ez * t);
            if (dx * dx + dz * dz < 0.0225) {
               return true;
            }
         }
      }

      return false;
   }

   private static List<double[]> spots(PoolSim sim, PoolBot.Aims aims, EightBall.BallInHand inHand) {
      double maxZ = inHand == EightBall.BallInHand.KITCHEN ? -1.09575 : 2.1125;
      List<double[]> out = new ArrayList<>();
      double[] p = TableDims.POCKETS;

      for (int t = 1; t < 16; t++) {
         if (aims.hit[t] && aims.pot[t] && !aims.banks) {
            for (int k = 0; k < 6; k++) {
               if (aims.pockets[k]) {
                  double ux = p[k * 4] - sim.x[t];
                  double uz = p[k * 4 + 1] - sim.z[t];
                  double d = Math.sqrt(ux * ux + uz * uz);

                  for (double back : new double[]{0.25, 0.5}) {
                     double x = sim.x[t] - ux / d * (0.15 + back);
                     double z = sim.z[t] - uz / d * (0.15 + back);
                     if (z <= maxZ && sim.isFree(0, x, z)) {
                        out.add(new double[]{x, z});
                     }
                  }
               }
            }
         }
      }

      double edgeX = 1.0625;
      double edgeZ = 2.0625;

      for (double x = -edgeX; x <= edgeX; x += 0.2) {
         for (double z = -edgeZ; z <= Math.min(edgeZ, maxZ); z += 0.2) {
            if (sim.isFree(0, x, z)) {
               out.add(new double[]{x, z});
            }
         }
      }

      return out;
   }

   private static double value(PoolSim table, PoolBot.Option o, double power, double spin, Rules.Match m, PoolBot.Level level) {
      PoolSim sim = new PoolSim();
      sim.copyFrom(table);
      if (o.place) {
         sim.place(0, o.cueX, o.cueZ);
      }

      boolean[] before = (boolean[])sim.on.clone();
      int call = o.pocket >= 0 ? o.pocket : Rules.aimedPocket(sim, o.angle);
      EightBall.Shot shot = play(sim, (float)o.angle, (float)power, (float)spin, call);
      Rules.Outcome out = Rules.judge(m, before, sim.on, shot);
      int me = m.shooter();
      if (out.winner() >= 0) {
         return out.winner() == me ? 5000.0 * o.chance : -10000.0;
      } else if (out.foul() != EightBall.Foul.NONE) {
         return -400.0;
      } else {
         double v = 0.0;

         for (int seat = 0; seat < m.players(); seat++) {
            double side = m.teamOf(seat) == m.teamOf(me) ? 600.0 : -600.0;
            v += side * out.points()[seat];
         }

         if (m.type() == GameType.CUTTHROAT) {
            for (int b : shot.pocketed) {
               v += m.group()[me].has(b) ? -900.0 : 600.0;
            }
         }

         EightBall.Group[] groups = (EightBall.Group[])m.group().clone();
         if (out.assigned() != null) {
            for (int seat = 0; seat < m.players(); seat++) {
               groups[seat] = m.teamOf(seat) == m.teamOf(me) ? out.assigned() : out.assigned().other();
            }
         }

         if (out.continueTurn()) {
            v += 1000.0;
            if (level.thinks) {
               v += 400.0 * bestChance(sim, PoolBot.Aims.of(m.type(), me, m.teamOf(me), groups, sim.on), sim.x[0], sim.z[0], level.aimError);
            }

            return v * o.chance - (level.thinks ? (1.0 - o.chance) * 200.0 : 0.0);
         } else {
            if (level.thinks && out.next() >= 0) {
               v -= 300.0 * bestChance(
                  sim, PoolBot.Aims.of(m.type(), out.next(), m.teamOf(out.next()), groups, sim.on), sim.x[0], sim.z[0], OPPONENT_AIM
               );
            }

            return v;
         }
      }
   }

   static EightBall.Shot play(final PoolSim sim, float angle, float power, float spin, int call) {
      final EightBall.Shot shot = new EightBall.Shot();
      shot.callPocket = call;
      PoolSim.Events events = new PoolSim.Events() {
         @Override
         public void ballHit(int a, int b, double speed) {
            shot.ballHit(a, b);
         }

         @Override
         public void cushionHit(int ball, double speed) {
            shot.cushion(ball, sim.x[ball], sim.z[ball]);
         }

         @Override
         public void pocketed(int ball, int pocket) {
            shot.pocketed(ball, pocket);
         }
      };
      sim.strike(angle, power, spin);
      int t = 0;

      while (t < 600 && sim.tick(events)) {
         t++;
      }

      return shot;
   }

   private static double erf(double x) {
      double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
      double y = 1.0 - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
      return x >= 0.0 ? y : -y;
   }

   private record Aims(boolean[] hit, boolean[] pot, boolean[] pockets, boolean banks) {
      static PoolBot.Aims of(GameType type, int seat, int team, EightBall.Group[] group, boolean[] on) {
         boolean[] hit = Rules.targets(type, seat, group, on);
         boolean[] pot = (boolean[])hit.clone();
         if (type == GameType.CUTTHROAT) {
            for (int b = 1; b < 16; b++) {
               pot[b] = on[b] && !group[seat].has(b);
            }
         }

         boolean[] pockets = new boolean[6];

         for (int k = 0; k < 6; k++) {
            pockets[k] = type != GameType.ONE_POCKET || k == Rules.ownPocket(team);
         }

         return new PoolBot.Aims(hit, pot, pockets, type == GameType.BANK);
      }
   }

   public static enum Level {
      EASY(2.2, 0.12, 3, new double[]{0.35}, new double[]{0.0}, false),
      MEDIUM(0.9, 0.07, 8, new double[]{0.25, 0.4, 0.55}, new double[]{0.0}, false),
      HARD(0.45, 0.04, 14, new double[]{0.2, 0.3, 0.42, 0.56}, new double[]{-0.6, 0.0, 0.6}, true),
      EXPERT(0.2, 0.02, 20, new double[]{0.2, 0.3, 0.42, 0.56}, new double[]{-0.6, 0.0, 0.6}, true);

      final double aimError;
      final double powerError;
      final int tries;
      final double[] powers;
      final double[] spins;
      final boolean thinks;

      private Level(double aimDegrees, double powerError, int tries, double[] powers, double[] spins, boolean thinks) {
         this.aimError = Math.toRadians(aimDegrees);
         this.powerError = powerError;
         this.tries = tries;
         this.powers = powers;
         this.spins = spins;
         this.thinks = thinks;
      }

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public PoolBot.Level next() {
         return values()[(this.ordinal() + 1) % values().length];
      }
   }

   private record Option(double cueX, double cueZ, boolean place, double angle, double chance, int pocket) {
   }

   public record Plan(boolean place, double placeX, double placeZ, float angle, float power, float spin, int call) {
   }
}

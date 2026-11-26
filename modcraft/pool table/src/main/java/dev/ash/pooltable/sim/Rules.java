package dev.ash.pooltable.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class Rules {
   public static final int POCKETS = 6;
   static final double JAW = 0.3;

   private Rules() {
   }

   public static boolean[] targets(GameType type, int shooter, EightBall.Group[] group, boolean[] on) {
      boolean[] t = new boolean[16];
      if (type.eightBall()) {
         EightBall.Group mine = group[shooter];
         boolean cleared = mine != EightBall.Group.NONE && EightBall.remaining(mine, on) == 0;

         for (int b = 1; b < 16; b++) {
            t[b] = on[b] && (cleared ? b == 8 : (mine == EightBall.Group.NONE ? b != 8 : mine.has(b)));
         }
      } else if (type != GameType.NINE && type != GameType.TEN) {
         for (int b = 1; b < 16; b++) {
            t[b] = on[b];
         }
      } else {
         int lowest = lowest(on);
         if (lowest > 0) {
            t[lowest] = true;
         }
      }

      return t;
   }

   public static boolean callNeeded(GameType type, int shooter, EightBall.Group[] group, boolean[] on, boolean isBreak) {
      if (isBreak) {
         return false;
      } else {
         return switch (type) {
            case TEN, STRAIGHT, BANK -> true;
            case AMERICAN_8 -> group[shooter] != EightBall.Group.NONE && EightBall.remaining(group[shooter], on) == 0;
            default -> false;
         };
      }
   }

   public static int ownPocket(int team) {
      return team == 0 ? 5 : 4;
   }

   public static int aimedPocket(PoolSim sim, double angle) {
      Guide g = Guide.cast(sim, angle);
      double len = Math.hypot(g.objX, g.objZ);
      if (g.ball != -1 && !(len < 1.0E-6)) {
         double dx = g.objX / len;
         double dz = g.objZ / len;
         double[] p = TableDims.POCKETS;
         int best = -1;
         double bestCos = 0.5;

         for (int k = 0; k < 6; k++) {
            double px = p[k * 4] - sim.x[g.ball];
            double pz = p[k * 4 + 1] - sim.z[g.ball];
            double d = Math.hypot(px, pz);
            double cos = (px * dx + pz * dz) / d;
            if (cos > bestCos) {
               bestCos = cos;
               best = k;
            }
         }

         return best;
      } else {
         return -1;
      }
   }

   public static boolean inJaws(double x, double z) {
      double[] p = TableDims.POCKETS;

      for (int k = 0; k < 6; k++) {
         if (Math.hypot(x - p[k * 4], z - p[k * 4 + 1]) < 0.3) {
            return true;
         }
      }

      return false;
   }

   public static int lowest(boolean[] on) {
      for (int b = 1; b < 16; b++) {
         if (on[b]) {
            return b;
         }
      }

      return -1;
   }

   public static boolean in(EightBall.Group group, boolean[] on) {
      return EightBall.remaining(group, on) > 0;
   }

   public static boolean settle(GameType type, PoolSim sim, List<Integer> tray, Rules.Outcome o, RandomGenerator random) {
      for (int b : o.respot()) {
         tray.remove(Integer.valueOf(b));
         sim.spot(b, 0.0, 1.09375);
      }

      if (o.restore()) {
         for (int i = tray.size() - 1; i >= 0; i--) {
            int b = tray.get(i);
            if (!sim.on[b]) {
               tray.remove(i);
               sim.spot(b, 0.0, 1.09375);
               break;
            }
         }
      }

      if (!sim.on[0]) {
         sim.spot(0, 0.0, -1.09375);
      }

      if (type == GameType.STRAIGHT && o.continueTurn() && o.winner() < 0) {
         int left = 0;

         for (int b = 1; b < 16; b++) {
            left += sim.on[b] ? 1 : 0;
         }

         if (left <= 1) {
            List<Integer> back = new ArrayList<>();

            for (int b = 1; b < 16; b++) {
               if (!sim.on[b]) {
                  back.add(b);
               }
            }

            tray.removeAll(back);
            return sim.rerack(back, random);
         }
      }

      return false;
   }

   public static Rules.Outcome judge(Rules.Match m, boolean[] onBefore, boolean[] onAfter, EightBall.Shot s) {
      return switch (m.type()) {
         case TEN, NINE -> lowestFirst(m, onBefore, s);
         case STRAIGHT -> straight(m, s);
         case BANK -> bank(m, s);
         case AMERICAN_8, ENGLISH_8 -> eightBall(m, onBefore, s);
         case ONE_POCKET -> onePocket(m, s);
         case CUTTHROAT -> cutthroat(m, onBefore, onAfter, s);
      };
   }

   private static EightBall.Foul basicFoul(EightBall.Shot s) {
      return s.scratch ? EightBall.Foul.SCRATCH : (s.firstContact < 0 ? EightBall.Foul.NO_CONTACT : EightBall.Foul.NONE);
   }

   private static boolean madeCall(EightBall.Shot s) {
      return s.firstContact > 0 && s.callPocket >= 0 && s.pocketOf[s.firstContact] == s.callPocket;
   }

   private static int[] noPoints(int players) {
      return new int[players];
   }

   private static Rules.Outcome eightBall(Rules.Match m, boolean[] onBefore, EightBall.Shot s) {
      int me = m.shooter();
      int them = m.opponent(me);
      EightBall.Outcome o = EightBall.judge(me, them, m.group(), onBefore, s);
      int winner = o.winner();
      Rules.Note note = Rules.Note.NONE;
      if (m.type() == GameType.AMERICAN_8 && winner == me && s.pocketOf[8] != s.callPocket) {
         winner = them;
         note = Rules.Note.WRONG_POCKET_EIGHT;
      }

      List<Integer> respot = new ArrayList<>();
      boolean rebreak = false;
      if (o.respotEight()) {
         if (m.type() == GameType.ENGLISH_8) {
            rebreak = true;
         } else {
            respot.add(8);
            note = Rules.Note.EIGHT_RESPOTTED;
         }
      }

      boolean again = o.continueTurn() && !rebreak;
      int next = winner >= 0 ? -1 : (!rebreak && !again ? them : me);
      return new Rules.Outcome(
         o.foul(),
         winner,
         o.assigned(),
         again,
         next,
         rebreak ? EightBall.BallInHand.KITCHEN : o.ballInHand(),
         respot,
         false,
         noPoints(m.players()),
         rebreak,
         note,
         List.of()
      );
   }

   private static Rules.Outcome lowestFirst(Rules.Match m, boolean[] onBefore, EightBall.Shot s) {
      int me = m.shooter();
      int them = m.opponent(me);
      boolean ten = m.type() == GameType.TEN;
      int money = ten ? 10 : 9;
      EightBall.Foul foul = basicFoul(s);
      if (foul == EightBall.Foul.NONE && s.firstContact != lowest(onBefore)) {
         foul = EightBall.Foul.WRONG_BALL;
      }

      boolean called = ten && !s.isBreak;
      boolean made = called ? madeCall(s) : !s.pocketed.isEmpty();
      List<Integer> respot = new ArrayList<>();
      int winner = -1;
      Rules.Note note = Rules.Note.NONE;
      if (s.pocketOf[money] >= 0) {
         boolean wins = foul == EightBall.Foul.NONE && (!ten || called && made && s.firstContact == money);
         if (wins) {
            winner = me;
         } else {
            respot.add(money);
            note = Rules.Note.BALL_RESPOTTED;
         }
      }

      boolean again = foul == EightBall.Foul.NONE && winner < 0 && made;
      if (called && !made && foul == EightBall.Foul.NONE && !s.pocketed.isEmpty() && note == Rules.Note.NONE) {
         note = Rules.Note.CALL_MISSED;
      }

      return new Rules.Outcome(
         foul,
         winner,
         null,
         again,
         winner >= 0 ? -1 : (again ? me : them),
         foul != EightBall.Foul.NONE ? EightBall.BallInHand.ANYWHERE : EightBall.BallInHand.NONE,
         respot,
         false,
         noPoints(m.players()),
         false,
         note,
         List.of()
      );
   }

   private static Rules.Outcome straight(Rules.Match m, EightBall.Shot s) {
      int me = m.shooter();
      int them = m.opponent(me);
      EightBall.Foul foul = basicFoul(s);
      boolean made = !s.isBreak && foul == EightBall.Foul.NONE && madeCall(s);
      int[] points = noPoints(m.players());
      List<Integer> respot = new ArrayList<>();
      Rules.Note note = Rules.Note.NONE;
      if (made) {
         points[me] = s.pocketed.size();
      } else {
         respot.addAll(s.pocketed);
         if (foul == EightBall.Foul.NONE && !s.isBreak && !s.pocketed.isEmpty()) {
            note = Rules.Note.CALL_MISSED;
         }
      }

      if (foul != EightBall.Foul.NONE) {
         points[me] = -Math.min(1, m.score()[me]);
      }

      int winner = m.score()[me] + points[me] >= m.target() ? me : -1;
      return new Rules.Outcome(
         foul,
         winner,
         null,
         made,
         winner >= 0 ? -1 : (made ? me : them),
         foul == EightBall.Foul.SCRATCH ? EightBall.BallInHand.KITCHEN : EightBall.BallInHand.NONE,
         respot,
         false,
         points,
         false,
         note,
         List.of()
      );
   }

   private static Rules.Outcome onePocket(Rules.Match m, EightBall.Shot s) {
      int me = m.shooter();
      int them = m.opponent(me);
      EightBall.Foul foul = basicFoul(s);
      int mine = ownPocket(m.teamOf(me));
      int theirs = ownPocket(m.teamOf(them));
      int[] points = noPoints(m.players());
      List<Integer> respot = new ArrayList<>();
      boolean scored = false;

      for (int b : s.pocketed) {
         int pocket = s.pocketOf[b];
         if (pocket == mine && foul == EightBall.Foul.NONE) {
            points[me]++;
            scored = true;
         } else if (pocket == theirs) {
            points[them]++;
         } else {
            respot.add(b);
         }
      }

      boolean restore = false;
      if (foul != EightBall.Foul.NONE && m.score()[me] > 0) {
         points[me] = -1;
         restore = true;
      }

      int target = m.target();
      int winner = m.score()[me] + points[me] >= target ? me : (m.score()[them] + points[them] >= target ? them : -1);
      boolean again = foul == EightBall.Foul.NONE && scored;
      return new Rules.Outcome(
         foul,
         winner,
         null,
         again,
         winner >= 0 ? -1 : (again ? me : them),
         foul == EightBall.Foul.SCRATCH ? EightBall.BallInHand.KITCHEN : EightBall.BallInHand.NONE,
         respot,
         restore,
         points,
         false,
         Rules.Note.NONE,
         List.of()
      );
   }

   private static Rules.Outcome bank(Rules.Match m, EightBall.Shot s) {
      int me = m.shooter();
      int them = m.opponent(me);
      EightBall.Foul foul = basicFoul(s);
      boolean made = !s.isBreak && foul == EightBall.Foul.NONE && madeCall(s) && s.banked[s.firstContact];
      int[] points = noPoints(m.players());
      List<Integer> respot = new ArrayList<>();

      for (int b : s.pocketed) {
         if (!made || b != s.firstContact) {
            respot.add(b);
         }
      }

      Rules.Note note = Rules.Note.NONE;
      if (made) {
         points[me] = 1;
      } else if (foul == EightBall.Foul.NONE && !s.isBreak && !s.pocketed.isEmpty()) {
         note = madeCall(s) ? Rules.Note.NOT_BANKED : Rules.Note.CALL_MISSED;
      }

      boolean restore = false;
      if (foul != EightBall.Foul.NONE && m.score()[me] > 0) {
         points[me] = -1;
         restore = true;
      }

      int winner = m.score()[me] + points[me] >= m.target() ? me : -1;
      return new Rules.Outcome(
         foul,
         winner,
         null,
         made,
         winner >= 0 ? -1 : (made ? me : them),
         foul == EightBall.Foul.SCRATCH ? EightBall.BallInHand.KITCHEN : EightBall.BallInHand.NONE,
         respot,
         restore,
         points,
         false,
         note,
         List.of()
      );
   }

   private static Rules.Outcome cutthroat(Rules.Match m, boolean[] onBefore, boolean[] onAfter, EightBall.Shot s) {
      int me = m.shooter();
      int n = m.players();
      EightBall.Group[] g = m.group();
      EightBall.Foul foul = basicFoul(s);
      boolean[] on = (boolean[])onAfter.clone();
      List<Integer> respot = new ArrayList<>();
      if (foul != EightBall.Foul.NONE) {
         for (int seat = 0; seat < n; seat++) {
            if (seat != me && in(g[seat], onBefore)) {
               for (int b = 1; b < 16; b++) {
                  if (g[seat].has(b) && !on[b]) {
                     respot.add(b);
                     on[b] = true;
                     break;
                  }
               }
            }
         }
      }

      List<Integer> out = new ArrayList<>();
      int alive = 0;
      int last = -1;

      for (int seatx = 0; seatx < n; seatx++) {
         if (in(g[seatx], on)) {
            alive++;
            last = seatx;
         } else if (in(g[seatx], onBefore)) {
            out.add(seatx);
         }
      }

      int winner = alive == 1 ? last : (alive == 0 ? me : -1);
      boolean again = foul == EightBall.Foul.NONE && !s.pocketed.isEmpty() && in(g[me], on);
      int next = -1;
      if (winner < 0) {
         next = me;
         if (!again) {
            do {
               next = (next + 1) % n;
            } while (!in(g[next], on) && next != me);
         }
      }

      return new Rules.Outcome(
         foul,
         winner,
         null,
         again,
         next,
         foul != EightBall.Foul.NONE ? EightBall.BallInHand.ANYWHERE : EightBall.BallInHand.NONE,
         respot,
         false,
         noPoints(m.players()),
         false,
         Rules.Note.NONE,
         out
      );
   }

   public record Match(GameType type, int shooter, EightBall.Group[] group, int[] score, int target, int players, MatchFormat format) {
      public Match(GameType type, int shooter, EightBall.Group[] group, int[] score, int target) {
         this(type, shooter, group, score, target, type.players, MatchFormat.defaultFor(type));
      }

      public int teamOf(int seat) {
         return this.format.teamOf(seat);
      }

      public int opponent(int seat) {
         return this.format.opponent(seat);
      }
   }

   public static enum Note {
      NONE,
      CALL_MISSED,
      NOT_BANKED,
      EIGHT_RESPOTTED,
      BALL_RESPOTTED,
      WRONG_POCKET_EIGHT;
   }

   public record Outcome(
      EightBall.Foul foul,
      int winner,
      EightBall.Group assigned,
      boolean continueTurn,
      int next,
      EightBall.BallInHand ballInHand,
      List<Integer> respot,
      boolean restore,
      int[] points,
      boolean rebreak,
      Rules.Note note,
      List<Integer> out
   ) {
   }
}

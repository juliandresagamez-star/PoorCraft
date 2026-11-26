package dev.ash.pooltable.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EightBall {
   private EightBall() {
   }

   public static EightBall.Group groupOf(int ball) {
      return ball >= 1 && ball <= 7 ? EightBall.Group.SOLIDS : (ball >= 9 && ball <= 15 ? EightBall.Group.STRIPES : EightBall.Group.NONE);
   }

   public static int remaining(EightBall.Group group, boolean[] on) {
      int n = 0;

      for (int b = 1; b < 16; b++) {
         if (on[b] && group.has(b)) {
            n++;
         }
      }

      return n;
   }

   public static EightBall.Outcome judge(int shooter, EightBall.Group[] groups, boolean[] onBefore, EightBall.Shot shot) {
      return judge(shooter, 1 - shooter, groups, onBefore, shot);
   }

   public static EightBall.Outcome judge(int shooter, int opponent, EightBall.Group[] groups, boolean[] onBefore, EightBall.Shot shot) {
      EightBall.Group mine = groups[shooter];
      boolean open = mine == EightBall.Group.NONE;
      boolean clearedBefore = !open && remaining(mine, onBefore) == 0;
      EightBall.Foul foul = EightBall.Foul.NONE;
      if (shot.scratch) {
         foul = EightBall.Foul.SCRATCH;
      } else if (shot.firstContact < 0) {
         foul = EightBall.Foul.NO_CONTACT;
      } else if (!shot.isBreak) {
         int first = shot.firstContact;
         boolean legal = open ? first != 8 : (clearedBefore ? first == 8 : groupOf(first) == mine);
         if (!legal) {
            foul = EightBall.Foul.WRONG_BALL;
         }
      }

      EightBall.BallInHand inHand = foul == EightBall.Foul.NONE
         ? EightBall.BallInHand.NONE
         : (shot.isBreak ? EightBall.BallInHand.KITCHEN : EightBall.BallInHand.ANYWHERE);
      boolean eight = shot.pocketed.contains(8);
      if (eight && !shot.isBreak) {
         boolean win = foul == EightBall.Foul.NONE && clearedBefore;
         return new EightBall.Outcome(foul, win ? shooter : opponent, null, false, false, EightBall.BallInHand.NONE);
      } else {
         List<Integer> objects = new ArrayList<>();

         for (int b : shot.pocketed) {
            if (b != 8) {
               objects.add(b);
            }
         }

         EightBall.Group assigned = null;
         if (open && !shot.isBreak && foul == EightBall.Foul.NONE && !objects.isEmpty()) {
            assigned = groupOf(objects.get(0));
         }

         EightBall.Group target = assigned != null ? assigned : mine;
         boolean pottedOwn = false;

         for (int bx : objects) {
            if (target == EightBall.Group.NONE || groupOf(bx) == target) {
               pottedOwn = true;
               break;
            }
         }

         return new EightBall.Outcome(foul, -1, assigned, foul == EightBall.Foul.NONE && pottedOwn, eight, inHand);
      }
   }

   public static enum BallInHand {
      NONE,
      KITCHEN,
      ANYWHERE;
   }

   public static enum Foul {
      NONE,
      SCRATCH,
      NO_CONTACT,
      WRONG_BALL;
   }

   public static enum Group {
      NONE,
      SOLIDS,
      STRIPES,
      LOW,
      MID,
      HIGH,
      CUT_2_A,
      CUT_2_B,
      CUT_4_A,
      CUT_4_B,
      CUT_4_C,
      CUT_4_D,
      CUT_5_A,
      CUT_5_B,
      CUT_5_C,
      CUT_5_D,
      CUT_5_E;

      public EightBall.Group other() {
         return this == SOLIDS ? STRIPES : (this == STRIPES ? SOLIDS : NONE);
      }

      public boolean has(int ball) {
         return switch (this) {
            case NONE -> false;
            case SOLIDS -> ball >= 1 && ball <= 7;
            case STRIPES -> ball >= 9 && ball <= 15;
            case LOW -> ball >= 1 && ball <= 5;
            case MID -> ball >= 6 && ball <= 10;
            case HIGH -> ball >= 11 && ball <= 15;
            case CUT_2_A -> ball >= 1 && ball <= 8;
            case CUT_2_B -> ball >= 9 && ball <= 15;
            case CUT_4_A -> ball >= 1 && ball <= 4;
            case CUT_4_B -> ball >= 5 && ball <= 8;
            case CUT_4_C -> ball >= 9 && ball <= 12;
            case CUT_4_D -> ball >= 13 && ball <= 15;
            case CUT_5_A -> ball >= 1 && ball <= 3;
            case CUT_5_B -> ball >= 4 && ball <= 6;
            case CUT_5_C -> ball >= 7 && ball <= 9;
            case CUT_5_D -> ball >= 10 && ball <= 12;
            case CUT_5_E -> ball >= 13 && ball <= 15;
         };
      }
   }

   public record Outcome(EightBall.Foul foul, int winner, EightBall.Group assigned, boolean continueTurn, boolean respotEight, EightBall.BallInHand ballInHand) {
   }

   public static final class Shot {
      public boolean isBreak;
      public int firstContact = -1;
      public boolean scratch;
      public final List<Integer> pocketed = new ArrayList<>();
      public final int[] pocketOf = new int[16];
      public final boolean[] banked = new boolean[16];
      private final boolean[] cushioned = new boolean[16];
      public int callPocket = -1;

      public Shot() {
         Arrays.fill(this.pocketOf, -1);
      }

      public void cushion(int ball, double x, double z) {
         if (!Rules.inJaws(x, z)) {
            this.cushioned[ball] = true;
         }
      }

      public void ballHit(int a, int b) {
         if (this.firstContact < 0 && (a == 0 || b == 0)) {
            this.firstContact = a == 0 ? b : a;
         }
      }

      public void pocketed(int ball) {
         this.pocketed(ball, -1);
      }

      public void pocketed(int ball, int pocket) {
         this.pocketOf[ball] = pocket;
         this.banked[ball] = this.cushioned[ball];
         if (ball == 0) {
            this.scratch = true;
         } else {
            this.pocketed.add(ball);
         }
      }
   }
}

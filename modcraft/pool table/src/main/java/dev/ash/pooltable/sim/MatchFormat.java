package dev.ash.pooltable.sim;

import java.util.Locale;

public enum MatchFormat {
   INDIVIDUAL_2(2, false, 0),
   INDIVIDUAL_3(3, false, 0),
   INDIVIDUAL_4(4, false, 0),
   INDIVIDUAL_5(5, false, 0),
   TEAMS_1V1(2, true, 1),
   TEAMS_2V2(4, true, 2),
   TEAMS_2V3(5, true, 2);

   private final int players;
   private final boolean teams;
   private final int teamSize;

   MatchFormat(int players, boolean teams, int teamSize) {
      this.players = players;
      this.teams = teams;
      this.teamSize = teamSize;
   }

   public int players() {
      return this.players;
   }

   public boolean teams() {
      return this.teams;
   }

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public boolean supports(GameType type) {
      return type == GameType.CUTTHROAT
         ? this == INDIVIDUAL_3 || this == INDIVIDUAL_4 || this == INDIVIDUAL_5
         : this == INDIVIDUAL_2 || this.teams;
   }

   public int teamOf(int seat) {
      return this.teams ? (seat < this.teamSize ? 0 : 1) : seat;
   }

   public int opponent(int seat) {
      int next = (seat + 1) % this.players;
      while (this.teamOf(next) == this.teamOf(seat)) {
         next = (next + 1) % this.players;
      }
      return next;
   }

   public int nextTeamSeat(int team, int[] cursors) {
      int teamPlayers = team == 0 ? this.teamSize : this.players - this.teamSize;
      int offset = Math.floorMod(cursors[team], teamPlayers);
      int firstSeat = team == 0 ? 0 : this.teamSize;
      int seat = firstSeat + offset;
      cursors[team] = (offset + 1) % teamPlayers;
      return seat;
   }

   public static MatchFormat defaultFor(GameType type) {
      return type == GameType.CUTTHROAT ? INDIVIDUAL_3 : INDIVIDUAL_2;
   }

   public static EightBall.Group cutthroatGroup(int players, int seat) {
      return switch (players) {
         case 2 -> seat == 0 ? EightBall.Group.CUT_2_A : EightBall.Group.CUT_2_B;
         case 3 -> switch (seat) {
            case 0 -> EightBall.Group.LOW;
            case 1 -> EightBall.Group.MID;
            default -> EightBall.Group.HIGH;
         };
         case 4 -> switch (seat) {
            case 0 -> EightBall.Group.CUT_4_A;
            case 1 -> EightBall.Group.CUT_4_B;
            case 2 -> EightBall.Group.CUT_4_C;
            default -> EightBall.Group.CUT_4_D;
         };
         case 5 -> switch (seat) {
            case 0 -> EightBall.Group.CUT_5_A;
            case 1 -> EightBall.Group.CUT_5_B;
            case 2 -> EightBall.Group.CUT_5_C;
            case 3 -> EightBall.Group.CUT_5_D;
            default -> EightBall.Group.CUT_5_E;
         };
         default -> throw new IllegalArgumentException("Unsupported Cutthroat player count: " + players);
      };
   }

   public static MatchFormat nextFor(GameType type, MatchFormat current) {
      MatchFormat[] options = type == GameType.CUTTHROAT
         ? new MatchFormat[]{INDIVIDUAL_3, INDIVIDUAL_4, INDIVIDUAL_5}
         : new MatchFormat[]{INDIVIDUAL_2, TEAMS_1V1, TEAMS_2V2, TEAMS_2V3};

      for (int i = 0; i < options.length; i++) {
         if (options[i] == current) {
            return options[(i + 1) % options.length];
         }
      }

      return options[0];
   }
}

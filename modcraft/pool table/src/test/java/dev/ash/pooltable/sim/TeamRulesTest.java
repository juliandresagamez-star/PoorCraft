package dev.ash.pooltable.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TeamRulesTest {
   @Test
   void cutthroatEliminatesAnEmptyGroupAndSkipsItsTurnForThreeToFivePlayers() {
      for (int players = 3; players <= 5; players++) {
         EightBall.Group[] groups = new EightBall.Group[players];
         boolean[] before = new boolean[16];
         for (int seat = 0; seat < players; seat++) {
            groups[seat] = MatchFormat.cutthroatGroup(players, seat);
            before[firstBall(groups[seat])] = true;
         }
         int lostBall = firstBall(groups[1]);
         boolean[] after = before.clone();
         after[lostBall] = false;
         EightBall.Shot shot = new EightBall.Shot();
         shot.firstContact = firstBall(groups[0]);

         Rules.Outcome outcome = Rules.judge(
            new Rules.Match(GameType.CUTTHROAT, 0, groups, new int[players], 8, players, format(players)),
            before,
            after,
            shot
         );

         assertEquals(-1, outcome.winner());
         assertEquals(2, outcome.next());
         assertEquals(1, outcome.out().getFirst());
      }
   }

   @Test
   void wrongEightBallWinnerIsTheOpposingTeam() {
      MatchFormat format = MatchFormat.TEAMS_2V3;
      EightBall.Group[] groups = {
         EightBall.Group.SOLIDS,
         EightBall.Group.SOLIDS,
         EightBall.Group.STRIPES,
         EightBall.Group.STRIPES,
         EightBall.Group.STRIPES
      };
      Rules.Match match = new Rules.Match(GameType.AMERICAN_8, 0, groups, new int[5], 8, 5, format);
      EightBall.Shot shot = new EightBall.Shot();
      shot.firstContact = 1;
      shot.pocketed(8, 0);
      boolean[] onBefore = new boolean[16];
      onBefore[1] = true;
      onBefore[9] = true;

      Rules.Outcome outcome = Rules.judge(match, onBefore, onBefore, shot);

      assertEquals(2, outcome.winner());
   }

   @Test
   void teamTurnsHaveOpposingRepresentativesForBothTeamFormats() {
      assertEquals(2, MatchFormat.TEAMS_2V2.opponent(0));
      assertEquals(0, MatchFormat.TEAMS_2V2.opponent(3));
      assertEquals(2, MatchFormat.TEAMS_2V3.opponent(1));
      assertEquals(0, MatchFormat.TEAMS_2V3.opponent(4));
   }

   private static int firstBall(EightBall.Group group) {
      for (int ball = 1; ball <= 15; ball++) {
         if (group.has(ball)) {
            return ball;
         }
      }
      throw new AssertionError("Group has no balls: " + group);
   }

   private static MatchFormat format(int players) {
      return switch (players) {
         case 3 -> MatchFormat.INDIVIDUAL_3;
         case 4 -> MatchFormat.INDIVIDUAL_4;
         default -> MatchFormat.INDIVIDUAL_5;
      };
   }

   @Test
   void onePocketUsesTheTeamPocketAndScoresTheTeamRepresentative() {
      Rules.Match match = new Rules.Match(
         GameType.ONE_POCKET,
         0,
         new EightBall.Group[5],
         new int[5],
         1,
         5,
         MatchFormat.TEAMS_2V3
      );
      EightBall.Shot shot = new EightBall.Shot();
      shot.firstContact = 1;
      shot.pocketed(1, Rules.ownPocket(match.teamOf(0)));

      Rules.Outcome outcome = Rules.judge(match, new boolean[16], new boolean[16], shot);

      assertEquals(1, outcome.points()[0]);
      assertEquals(0, outcome.winner());
   }
}

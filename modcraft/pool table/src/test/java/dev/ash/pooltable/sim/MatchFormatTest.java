package dev.ash.pooltable.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ash.pooltable.block.GameState;
import org.junit.jupiter.api.Test;

class MatchFormatTest {
   @Test
   void formatsReportConfiguredPlayerCountsAndAvailability() {
      assertEquals(2, MatchFormat.INDIVIDUAL_2.players());
      assertEquals(3, MatchFormat.INDIVIDUAL_3.players());
      assertEquals(4, MatchFormat.INDIVIDUAL_4.players());
      assertEquals(5, MatchFormat.INDIVIDUAL_5.players());
      assertEquals(2, MatchFormat.TEAMS_1V1.players());
      assertEquals(4, MatchFormat.TEAMS_2V2.players());
      assertEquals(5, MatchFormat.TEAMS_2V3.players());

      assertTrue(MatchFormat.INDIVIDUAL_5.supports(GameType.CUTTHROAT));
      assertFalse(MatchFormat.TEAMS_2V3.supports(GameType.CUTTHROAT));
      assertTrue(MatchFormat.TEAMS_2V3.supports(GameType.AMERICAN_8));
   }

   @Test
   void teamTurnsAlternateSidesAndRotatePlayersWithinEachTeam() {
      GameState state = new GameState();
      state.format = MatchFormat.TEAMS_2V3;
      state.resetTeamCursors(0);

      assertEquals(2, state.nextTurn(0, false));
      assertEquals(1, state.nextTurn(2, false));
      assertEquals(3, state.nextTurn(1, false));
      assertEquals(0, state.nextTurn(3, false));
      assertEquals(4, state.nextTurn(0, false));
      assertEquals(1, state.nextTurn(4, false));

      state.format = MatchFormat.TEAMS_2V2;
      state.resetTeamCursors(0);
      assertEquals(2, state.nextTurn(0, false));
      assertEquals(1, state.nextTurn(2, false));
      assertEquals(3, state.nextTurn(1, false));
   }

   @Test
   void teamPointsAreSharedByEveryMember() {
      for (MatchFormat format : new MatchFormat[]{MatchFormat.TEAMS_2V2, MatchFormat.TEAMS_2V3}) {
         GameState state = new GameState();
         state.format = format;
         state.applyPoints(new int[]{3, 0, 0, 0, 0});

         assertEquals(3, state.score[0]);
         assertEquals(3, state.score[1]);
         for (int seat = 2; seat < format.players(); seat++) {
            assertEquals(0, state.score[seat]);
         }
      }
   }

   @Test
   void individualCutthroatPartitionsEveryBallAndKeepsTheOriginalThreePlayerGroups() {
      assertEquals(EightBall.Group.LOW, MatchFormat.cutthroatGroup(3, 0));
      assertEquals(EightBall.Group.MID, MatchFormat.cutthroatGroup(3, 1));
      assertEquals(EightBall.Group.HIGH, MatchFormat.cutthroatGroup(3, 2));

      for (int players = 3; players <= 5; players++) {
         for (int ball = 1; ball <= 15; ball++) {
            int owners = 0;
            for (int seat = 0; seat < players; seat++) {
               if (MatchFormat.cutthroatGroup(players, seat).has(ball)) {
                  owners++;
               }
            }
            assertEquals(1, owners, "ball " + ball + " must have one owner with " + players + " players");
         }
      }
   }
}

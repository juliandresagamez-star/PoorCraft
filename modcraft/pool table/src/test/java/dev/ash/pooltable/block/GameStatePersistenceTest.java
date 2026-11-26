package dev.ash.pooltable.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.ash.pooltable.sim.GameType;
import dev.ash.pooltable.sim.MatchFormat;
import dev.ash.pooltable.sim.PoolBot;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameStatePersistenceTest {
   @Test
   void savesFiveSeatsAndFormatForReconnects() {
      UUID returningPlayer = UUID.randomUUID();
      GameState state = new GameState();
      state.type = GameType.CUTTHROAT;
      state.format = MatchFormat.INDIVIDUAL_5;
      for (int seat = 0; seat < GameState.SEATS; seat++) {
         state.seat[seat] = seat == 4 ? returningPlayer : UUID.randomUUID();
         state.name[seat] = "Player " + (seat + 1);
      }
      state.turn = 4;
      state.phase = GameState.Phase.PLAY;

      GameState restored = new GameState();
      restored.load(state.save());

      assertEquals(MatchFormat.INDIVIDUAL_5, restored.format);
      assertEquals(returningPlayer, restored.seat[4]);
      assertEquals("Player 5", restored.name[4]);
      assertEquals(4, restored.turn);
      assertEquals(GameState.Phase.PLAY, restored.phase);
   }

   @Test
   void savesAndRestoresTeamTurnRotation() {
      GameState state = new GameState();
      state.format = MatchFormat.TEAMS_2V3;
      state.resetTeamCursors(0);
      assertEquals(2, state.nextTurn(0, false));

      GameState restored = new GameState();
      restored.load(state.save());

      assertEquals(1, restored.nextTurn(2, false));
   }

   @Test
   void removesDuplicatePlayersButKeepsMultipleBotSeats() {
      UUID duplicate = UUID.randomUUID();
      GameState state = new GameState();
      state.format = MatchFormat.TEAMS_2V2;
      state.seat[0] = duplicate;
      state.seat[1] = duplicate;
      state.seat[2] = GameState.BOT;
      state.seat[3] = GameState.BOT;
      state.bot[2] = PoolBot.Level.EASY;
      state.bot[3] = PoolBot.Level.HARD;

      GameState restored = new GameState();
      restored.load(state.save());

      assertEquals(duplicate, restored.seat[0]);
      assertNull(restored.seat[1]);
      assertEquals(GameState.BOT, restored.seat[2]);
      assertEquals(GameState.BOT, restored.seat[3]);
   }
}

package dev.ash.pooltable.block;

import dev.ash.pooltable.sim.EightBall;
import dev.ash.pooltable.sim.GameType;
import dev.ash.pooltable.sim.MatchFormat;
import dev.ash.pooltable.sim.PoolBot;
import dev.ash.pooltable.sim.Rules;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public final class GameState {
   public static final UUID BOT = UUID.nameUUIDFromBytes("pooltable:bot".getBytes(StandardCharsets.UTF_8));
   public static final int SEATS = 5;
   public static final int[] STRAIGHT_TARGETS = new int[]{25, 50, 100, 150};
   public GameType type = GameType.AMERICAN_8;
   public MatchFormat format = MatchFormat.INDIVIDUAL_2;
   public int target = STRAIGHT_TARGETS[0];
   public final UUID[] seat = new UUID[SEATS];
   public final PoolBot.Level[] bot = new PoolBot.Level[SEATS];
   public final String[] name = new String[SEATS];
   public final int[] wins = new int[SEATS];
   public final EightBall.Group[] group = new EightBall.Group[SEATS];
   public final int[] score = new int[SEATS];
   public GameState.Phase phase = GameState.Phase.PRACTICE;
   public final int[] teamCursor = new int[2];
   public int turn;
   public int nextBreaker;
   public int winner = -1;
   public EightBall.BallInHand ballInHand = EightBall.BallInHand.ANYWHERE;
   public String message = "";
   public String[] messageArgs = new String[0];

   public GameState() {
      Arrays.fill(this.name, "");
      Arrays.fill(this.group, EightBall.Group.NONE);
   }

   public int players() {
      return this.format.players();
   }

   public int teamOf(int seat) {
      return this.format.teamOf(seat);
   }

   public boolean teams() {
      return this.format.teams();
   }

   public int nextTurn(int current, boolean keepTurn) {
      if (keepTurn) {
         return current;
      }
      if (!this.teams()) {
         return (current + 1) % this.players();
      }
      return this.format.nextTeamSeat(1 - this.teamOf(current), this.teamCursor);
   }

   public void resetTeamCursors(int breaker) {
      Arrays.fill(this.teamCursor, 0);
      if (this.teams()) {
         int team = this.teamOf(breaker);
         int teamSize = 0;
         int position = 0;
         for (int seat = 0; seat < this.players(); seat++) {
            if (this.teamOf(seat) == team) {
               if (seat == breaker) {
                  position = teamSize;
               }
               teamSize++;
            }
         }
         this.teamCursor[team] = (position + 1) % teamSize;
      }
   }

   public boolean inGame() {
      return this.phase == GameState.Phase.BREAK || this.phase == GameState.Phase.PLAY;
   }

   public int seatOf(UUID id) {
      for (int s = 0; s < this.players(); s++) {
         if (id.equals(this.seat[s])) {
            return s;
         }
      }

      return -1;
   }

   public boolean seated(UUID id) {
      return this.seatOf(id) >= 0;
   }

   public boolean hasBot() {
      for (int s = 0; s < this.players(); s++) {
         if (this.bot[s] != null) {
            return true;
         }
      }

      return false;
   }

   public PoolBot.Level botLevel() {
      for (int s = 0; s < this.players(); s++) {
         if (this.bot[s] != null) {
            return this.bot[s];
         }
      }

      return null;
   }

   public boolean player(int s) {
      return this.seat[s] != null && this.bot[s] == null;
   }

   public boolean full() {
      for (int s = 0; s < this.players(); s++) {
         if (this.seat[s] == null) {
            return false;
         }
      }

      return true;
   }

   public boolean mayShoot(UUID id) {
      return switch (this.phase) {
         case PRACTICE -> true;
         case BREAK, PLAY -> id.equals(this.seat[this.turn]);
         case OVER -> false;
      };
   }

   public int pointsToWin() {
      return this.type.target(this.target);
   }

   public Rules.Match match(int shooter) {
      return new Rules.Match(this.type, shooter, this.group.clone(), this.score.clone(), this.pointsToWin(), this.players(), this.format);
   }

   public void applyPoints(int[] points) {
      if (points.length < this.players()) {
         throw new IllegalArgumentException("Expected points for " + this.players() + " players");
      }

      int[] teamPoints = new int[SEATS];
      for (int seat = 0; seat < this.players(); seat++) {
         teamPoints[this.teamOf(seat)] += points[seat];
      }
      for (int seat = 0; seat < this.players(); seat++) {
         this.score[seat] += teamPoints[this.teamOf(seat)];
      }
   }

   public void say(String key, String... args) {
      this.message = key;
      this.messageArgs = args;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();

      for (int i = 0; i < SEATS; i++) {
         CompoundTag s = new CompoundTag();
         if (this.seat[i] != null) {
            s.store("Id", UUIDUtil.CODEC, this.seat[i]);
         }

         s.putString("Name", this.name[i]);
         s.putInt("Wins", this.wins[i]);
         s.putString("Group", this.group[i].name());
         s.putInt("Score", this.score[i]);
         if (this.bot[i] != null) {
            s.putString("Bot", this.bot[i].name());
         }

         tag.put("Seat" + i, s);
      }

      tag.putString("Type", this.type.name());
      tag.putString("Format", this.format.name());
      tag.putInt("Target", this.target);
      tag.putString("Phase", this.phase.name());
      tag.putInt("Turn", this.turn);
      tag.putInt("NextBreaker", this.nextBreaker);
      tag.putInt("TeamCursor0", this.teamCursor[0]);
      tag.putInt("TeamCursor1", this.teamCursor[1]);
      tag.putInt("Winner", this.winner);
      tag.putString("BallInHand", this.ballInHand.name());
      tag.putString("Msg", this.message);
      ListTag args = new ListTag();

      for (String a : this.messageArgs) {
         args.add(StringTag.valueOf(a));
      }

      tag.put("MsgArgs", args);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.type = parse(GameType.class, tag.getStringOr("Type", ""), GameType.AMERICAN_8);
      this.format = parse(MatchFormat.class, tag.getStringOr("Format", ""), MatchFormat.defaultFor(this.type));
      if (!this.format.supports(this.type)) {
         this.format = MatchFormat.defaultFor(this.type);
      }
      int storedTarget = tag.getIntOr("Target", STRAIGHT_TARGETS[0]);
      this.target = Arrays.stream(STRAIGHT_TARGETS).anyMatch(target -> target == storedTarget) ? storedTarget : STRAIGHT_TARGETS[0];

      Set<UUID> seen = new HashSet<>();
      for (int i = 0; i < SEATS; i++) {
         CompoundTag s = tag.getCompoundOrEmpty("Seat" + i);
         this.seat[i] = (UUID)s.read("Id", UUIDUtil.CODEC).orElse(null);
         this.name[i] = s.getStringOr("Name", "");
         this.wins[i] = s.getIntOr("Wins", 0);
         this.group[i] = parse(EightBall.Group.class, s.getStringOr("Group", ""), EightBall.Group.NONE);
         this.score[i] = s.getIntOr("Score", 0);
         this.bot[i] = s.contains("Bot") ? parse(PoolBot.Level.class, s.getStringOr("Bot", ""), PoolBot.Level.MEDIUM) : null;
         if (BOT.equals(this.seat[i]) && this.bot[i] == null) {
            this.seat[i] = null;
            this.name[i] = "";
         } else if (!BOT.equals(this.seat[i])) {
            this.bot[i] = null;
         }
         if (this.seat[i] != null && !BOT.equals(this.seat[i]) && !seen.add(this.seat[i])) {
            this.seat[i] = null;
            this.name[i] = "";
            this.bot[i] = null;
         }
      }

      this.turn = Math.floorMod(tag.getIntOr("Turn", 0), this.players());
      this.nextBreaker = Math.floorMod(tag.getIntOr("NextBreaker", 0), this.players());
      this.teamCursor[0] = Math.max(0, tag.getIntOr("TeamCursor0", 0));
      this.teamCursor[1] = Math.max(0, tag.getIntOr("TeamCursor1", 0));
      int storedWinner = tag.getIntOr("Winner", -1);
      this.winner = storedWinner >= 0 && storedWinner < this.players() ? storedWinner : -1;
      this.phase = parse(GameState.Phase.class, tag.getStringOr("Phase", ""), GameState.Phase.PRACTICE);
      this.ballInHand = parse(EightBall.BallInHand.class, tag.getStringOr("BallInHand", ""), EightBall.BallInHand.NONE);
      if (this.phase == GameState.Phase.OVER && this.winner < 0) {
         this.phase = GameState.Phase.PRACTICE;
         this.ballInHand = EightBall.BallInHand.ANYWHERE;
      }
      this.message = tag.getStringOr("Msg", "");
      ListTag args = tag.getListOrEmpty("MsgArgs");
      this.messageArgs = new String[args.size()];

      for (int i = 0; i < args.size(); i++) {
         this.messageArgs[i] = args.getStringOr(i, "");
      }
      for (int i = this.players(); i < SEATS; i++) {
         this.seat[i] = null;
         this.bot[i] = null;
         this.name[i] = "";
         this.group[i] = EightBall.Group.NONE;
         this.wins[i] = 0;
         this.score[i] = 0;
      }
      if (this.inGame() && !this.full()) {
         this.phase = GameState.Phase.PRACTICE;
         this.winner = -1;
         this.ballInHand = EightBall.BallInHand.ANYWHERE;
      }
   }

   private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
      try {
         return Enum.valueOf(type, name);
      } catch (IllegalArgumentException var4) {
         return fallback;
      }
   }

   @Override
   public String toString() {
      return this.type
         + " "
         + this.phase
         + " turn="
         + this.turn
         + " seats="
         + Arrays.toString((Object[])this.name)
         + " bot="
         + Arrays.toString((Object[])this.bot)
         + " groups="
         + Arrays.toString((Object[])this.group)
         + " score="
         + Arrays.toString(this.score)
         + " inHand="
         + this.ballInHand
         + " winner="
         + this.winner
         + " wins="
         + Arrays.toString(this.wins)
         + " msg="
         + this.message;
   }

   public static enum Phase {
      PRACTICE,
      BREAK,
      PLAY,
      OVER;
   }
}

package dev.ash.pooltable.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.ash.pooltable.PoolConfig;
import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.net.AimSyncPayload;
import dev.ash.pooltable.net.Net;
import dev.ash.pooltable.net.ShotPayload;
import dev.ash.pooltable.net.TableActionPayload;
import dev.ash.pooltable.sim.EightBall;
import dev.ash.pooltable.sim.GameType;
import dev.ash.pooltable.sim.MatchFormat;
import dev.ash.pooltable.sim.PoolBot;
import dev.ash.pooltable.sim.PoolSim;
import dev.ash.pooltable.sim.Rules;
import dev.ash.pooltable.sim.TableDims;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PoolTableBlockEntity extends BlockEntity {
   public static final int MAX_SHOT_TICKS = 1200;
   public static final double REACH = 6.0;
   private static final Set<PoolTableBlockEntity> CLIENT_TABLES = Collections.newSetFromMap(new WeakHashMap<>());
   private static final Set<PoolTableBlockEntity> SERVER_TABLES = Collections.newSetFromMap(new WeakHashMap<>());
   @Nullable
   private Set<PoolTableBlockEntity> registry;
   public final PoolSim sim = new PoolSim();
   public final GameState game = new GameState();
   public final List<Integer> tray = new ArrayList<>();
   private boolean moving;
   private int shotId;
   private int shotTicks;
   private DyeColor cloth = DyeColor.GREEN;
   @Nullable
   private EightBall.Shot shot;
   private int runSeat = -1;
   private boolean[] onBefore = new boolean[16];
   private static final int BOT_THINK = 20;
   private static final int BOT_AIM = 24;
   private static final int BOT_DRAW = 16;
   private static final int BOT_HOLD = 4;
   private static final ExecutorService BOT_THREAD = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Pool Table bot");
      t.setDaemon(true);
      return t;
   });
   @Nullable
   private CompletableFuture<PoolBot.Plan> botThinking;
   @Nullable
   private PoolBot.Plan botPlan;
   private long[] botBoard = new long[0];
   private int botFor = -1;
   private int botTicks;
   private int botCall = -1;
   private float botFrom;
   public final TableVisuals visuals = new TableVisuals();
   @Nullable
   private CompoundTag pendingFinal;
   private int pendingWait;
   private int soundBudget;
   private boolean pathSettled;
   private final PoolSim.Events serverEvents = new PoolSim.Events() {
      {
         Objects.requireNonNull(PoolTableBlockEntity.this);
      }

      @Override
      public void ballHit(int a, int b, double speed) {
         if (PoolTableBlockEntity.this.shot != null) {
            PoolTableBlockEntity.this.shot.ballHit(a, b);
         }
      }

      @Override
      public void cushionHit(int ball, double speed) {
         if (PoolTableBlockEntity.this.shot != null) {
            PoolTableBlockEntity.this.shot.cushion(ball, PoolTableBlockEntity.this.sim.x[ball], PoolTableBlockEntity.this.sim.z[ball]);
         }
      }

      @Override
      public void pocketed(int ball, int pocket) {
         if (PoolTableBlockEntity.this.shot != null) {
            PoolTableBlockEntity.this.shot.pocketed(ball, pocket);
         }

         if (ball != 0) {
            PoolTableBlockEntity.this.tray.add(ball);
         }
      }
   };
   private final PoolSim.Events clientEvents = new PoolSim.Events() {
      {
         Objects.requireNonNull(PoolTableBlockEntity.this);
      }

      @Override
      public void ballHit(int a, int b, double speed) {
         if (speed > 0.06 && PoolTableBlockEntity.this.soundBudget-- > 0) {
            this.sound(
               PoolTable.BALL_HIT.get(), a, (float)Mth.clamp(speed / 3.5, 0.08, 1.0), 0.92F + PoolTableBlockEntity.this.level.getRandom().nextFloat() * 0.16F
            );
         }
      }

      @Override
      public void cushionHit(int ball, double speed) {
         if (speed > 0.15 && PoolTableBlockEntity.this.soundBudget-- > 0) {
            this.sound(
               PoolTable.CUSHION.get(), ball, (float)Mth.clamp(speed / 4.0, 0.08, 0.9), 0.9F + PoolTableBlockEntity.this.level.getRandom().nextFloat() * 0.2F
            );
         }
      }

      @Override
      public void pocketed(int ball, int pocket) {
         PoolTableBlockEntity.this.visuals
            .drops
            .add(
               new TableVisuals.Drop(
                  ball, pocket, PoolTableBlockEntity.this.sim.x[ball], PoolTableBlockEntity.this.sim.z[ball], PoolTableBlockEntity.this.level.getGameTime()
               )
            );
         double[] p = TableDims.POCKETS;
         Vec3 at = PoolTableBlockEntity.this.frame().toWorld(p[pocket * 4], 0.8125, p[pocket * 4 + 1]);
         PoolTable.clientHooks
            .playSound(PoolTable.POCKET.get(), at.x, at.y, at.z, 0.9F, 0.95F + PoolTableBlockEntity.this.level.getRandom().nextFloat() * 0.1F);
         if (ball != 0) {
            PoolTableBlockEntity.this.tray.add(ball);
         }
      }

      private void sound(SoundEvent event, int ball, float volume, float pitch) {
         Vec3 at = PoolTableBlockEntity.this.frame().toWorld(PoolTableBlockEntity.this.sim.x[ball], 0.8875, PoolTableBlockEntity.this.sim.z[ball]);
         PoolTable.clientHooks.playSound(event, at.x, at.y, at.z, volume, pitch);
      }
   };
   private static final MapCodec<CompoundTag> WHOLE = MapCodec.assumeMapUnsafe(CompoundTag.CODEC);

   public PoolTableBlockEntity(BlockPos pos, BlockState state) {
      super(PoolTable.TABLE_BE.get(), pos, state);
      this.sim.rack(new Random(pos.asLong()));
   }

   public TableFrame frame() {
      return TableFrame.of(this.worldPosition, this.getBlockState());
   }

   public boolean moving() {
      return this.moving;
   }

   public int shotId() {
      return this.shotId;
   }

   public DyeColor clothColor() {
      return this.cloth;
   }

   public EightBall.BallInHand ballInHand() {
      if (this.game.phase == GameState.Phase.PRACTICE) {
         return PoolConfig.practiceBallInHand() ? EightBall.BallInHand.ANYWHERE : EightBall.BallInHand.NONE;
      } else {
         return this.game.ballInHand;
      }
   }

   public void onLoad() {
      if (this.level != null) {
         this.registry = this.level.isClientSide() ? CLIENT_TABLES : SERVER_TABLES;
         this.registry.add(this);
      }

      if (this.level != null && this.level.isClientSide()) {
         this.sim.recordPath(true);
         this.sim.settlePath();
      }
   }

   public void setRemoved() {
      super.setRemoved();
      if (this.registry != null) {
         this.registry.remove(this);
      }
   }

   public void onChunkUnloaded() {
      if (this.registry != null) {
         this.registry.remove(this);
      }
   }

   public static Iterable<PoolTableBlockEntity> clientTables() {
      return CLIENT_TABLES;
   }

   @Nullable
   public static PoolTableBlockEntity nearest(Level level, Vec3 pos, double range) {
      PoolTableBlockEntity best = null;
      double bestD = range * range;

      for (PoolTableBlockEntity be : level.isClientSide() ? CLIENT_TABLES : SERVER_TABLES) {
         if (be.level == level && !be.isRemoved()) {
            double d = distanceSqr(be.frame().bounds(), pos);
            if (d <= bestD) {
               bestD = d;
               best = be;
            }
         }
      }

      return best;
   }

   private static double distanceSqr(AABB box, Vec3 p) {
      double dx = Math.max(0.0, Math.max(box.minX - p.x, p.x - box.maxX));
      double dy = Math.max(0.0, Math.max(box.minY - p.y, p.y - box.maxY));
      double dz = Math.max(0.0, Math.max(box.minZ - p.z, p.z - box.maxZ));
      return dx * dx + dy * dy + dz * dz;
   }

   public boolean inReach(Player player, double range) {
      return !this.isRemoved() && distanceSqr(this.frame().bounds(), player.getEyePosition()) <= range * range;
   }

   public void shoot(ServerPlayer player, float angle, float power, float spin, int call) {
      if (!this.moving
         && this.game.mayShoot(player.getUUID())
         && this.sim.on[0]
         && this.inReach(player, 6.0)
         && Float.isFinite(angle)
         && Float.isFinite(power)
         && Float.isFinite(spin)) {
         this.strike(angle, power, spin, call);
      }
   }

   private void strike(float angle, float power, float spin, int call) {
      power = Mth.clamp(power, 0.0F, 1.0F);
      spin = Mth.clamp(spin, -1.0F, 1.0F);
      this.shot = new EightBall.Shot();
      this.shot.isBreak = this.game.phase == GameState.Phase.BREAK;
      if (this.game.inGame() && Rules.callNeeded(this.game.type, this.game.turn, this.game.group, this.sim.on, this.shot.isBreak)) {
         this.shot.callPocket = call >= 0 && call < 6 ? call : Rules.aimedPocket(this.sim, angle);
      }

      this.onBefore = (boolean[])this.sim.on.clone();
      this.sim.strike(angle, power, spin);
      this.moving = true;
      this.shotId++;
      this.shotTicks = 0;
      if (this.game.phase != GameState.Phase.PRACTICE) {
         this.game.ballInHand = EightBall.BallInHand.NONE;
      }

      this.setChanged();
      Net.toTracking((ServerLevel)this.level, this.worldPosition, new ShotPayload(this.worldPosition, this.shotId, this.sim.save(), angle, power, spin));
   }

   public void placeCue(ServerPlayer player, double x, double z) {
      if (!this.moving) {
         if (this.game.mayShoot(player.getUUID()) && this.inReach(player, 6.0) && this.cueMayGo(x, z)) {
            this.sim.place(0, x, z);
         }

         this.sync();
      }
   }

   private boolean cueMayGo(double x, double z) {
      EightBall.BallInHand hand = this.ballInHand();
      return hand != EightBall.BallInHand.NONE
         && Double.isFinite(x)
         && Double.isFinite(z)
         && (hand != EightBall.BallInHand.KITCHEN || !(z > -1.093749))
         && this.sim.isFree(0, x, z);
   }

   public void relayAim(ServerPlayer player, float angle, float power, int call) {
      if (!this.moving && this.game.mayShoot(player.getUUID()) && this.inReach(player, 6.0) && Float.isFinite(angle)) {
         this.relay(player, player.getId(), angle, power, call);
      }
   }

   private void relay(@Nullable ServerPlayer from, int id, float angle, float power, int call) {
      AimSyncPayload out = new AimSyncPayload(this.worldPosition, id, angle, Mth.clamp(power, 0.0F, 1.0F), Mth.clamp(call, -1, 5));

      for (ServerPlayer p : ((ServerLevel)this.level).getChunkSource().chunkMap.getPlayers(ChunkPos.containing(this.worldPosition), false)) {
         if (p != from) {
            Net.toPlayer(p, out);
         }
      }
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, PoolTableBlockEntity be) {
      if (!be.moving) {
         be.botTick();
      } else {
         be.shotTicks++;
         boolean still = be.sim.tick(be.serverEvents);
         if (!still || be.shotTicks >= 1200) {
            for (int i = 0; i < 16; i++) {
               if (be.sim.on[i]) {
                  be.sim.place(i, be.sim.x[i], be.sim.z[i]);
               }
            }

            be.moving = false;
            be.finishShot();
         }
      }
   }

   private void botTick() {
      int s = this.game.turn;
      PoolBot.Level lvl = this.game.inGame() ? this.game.bot[s] : null;
      if (lvl != null && this.sim.on[0] && !this.anyoneAway()) {
         if (this.botThinking != null && (this.botFor != s || !Arrays.equals(this.botBoard, this.sim.save()))) {
            this.botReset();
         }

         if (this.botThinking == null) {
            PoolSim copy = new PoolSim();
            copy.copyFrom(this.sim);
            Rules.Match match = this.game.match(s);
            boolean isBreak = this.game.phase == GameState.Phase.BREAK;
            EightBall.BallInHand hand = this.game.ballInHand;
            Random random = new Random(this.level.getRandom().nextLong());
            this.botThinking = CompletableFuture.supplyAsync(() -> PoolBot.plan(copy, match, isBreak, hand, lvl, random), BOT_THREAD);
            this.botBoard = this.sim.save();
            this.botFor = s;
            this.botTicks = 0;
         } else {
            this.botTicks++;
            if (this.botPlan == null) {
               if (this.botTicks < 20 || !this.botThinking.isDone()) {
                  return;
               }

               this.botPlan = this.botThinking.exceptionally(e -> {
                  LogUtils.getLogger().error("The pool table bot couldn't work out a shot", e);
                  return new PoolBot.Plan(false, 0.0, 0.0, 0.0F, 0.3F, 0.0F, -1);
               }).join();
               if (this.botPlan.place() && this.cueMayGo(this.botPlan.placeX(), this.botPlan.placeZ())) {
                  this.sim.place(0, this.botPlan.placeX(), this.botPlan.placeZ());
                  this.sync();
                  this.botBoard = this.sim.save();
               }

               this.botFrom = this.botPlan.angle() + (this.level.getRandom().nextFloat() - 0.5F) * 1.4F;
               this.botCall = Rules.callNeeded(this.game.type, s, this.game.group, this.sim.on, this.game.phase == GameState.Phase.BREAK)
                  ? (this.botPlan.call() >= 0 ? this.botPlan.call() : Rules.aimedPocket(this.sim, this.botPlan.angle()))
                  : -1;
               this.botTicks = 0;
            }

            float t = Math.min(1.0F, this.botTicks / 24.0F);
            float angle = this.botFrom + (this.botPlan.angle() - this.botFrom) * t * t * (3.0F - 2.0F * t);
            float power = this.botPlan.power() * Mth.clamp((this.botTicks - 24) / 16.0F, 0.0F, 1.0F);
            if (this.botTicks % 2 == 0) {
               this.relay(null, -1, angle, power, this.botCall);
            }

            if (this.botTicks >= 44) {
               PoolBot.Plan plan = this.botPlan;
               this.botReset();
               this.strike(plan.angle(), plan.power(), plan.spin(), this.botCall);
            }
         }
      } else {
         this.botReset();
      }
   }

   private void botReset() {
      this.botThinking = null;
      this.botPlan = null;
      this.botFor = -1;
   }

   private boolean anyoneAway() {
      for (int s = 0; s < this.game.players(); s++) {
         ServerPlayer p = this.game.player(s) ? ((ServerLevel)this.level).getServer().getPlayerList().getPlayer(this.game.seat[s]) : null;
         if (p != null && (p.level() != this.level || !this.inReach(p, 32.0))) {
            return true;
         }
      }

      return false;
   }

   private void finishShot() {
      EightBall.Shot s = this.shot;
      this.shot = null;
      if (this.game.inGame() && s != null) {
         this.judge(s);
      } else if (!this.sim.on[0]) {
         this.sim.spot(0, 0.0, -1.09375);
         this.game.say("msg.practice_scratch");
      } else if (this.objectBallsLeft() == 0) {
         this.game.say("msg.practice_cleared");
      }

      this.sync();
   }

   private int objectBallsLeft() {
      int n = 0;

      for (int i = 1; i < 16; i++) {
         n += this.sim.on[i] ? 1 : 0;
      }

      return n;
   }

   private void judge(EightBall.Shot s) {
      int me = this.game.turn;
      Rules.Outcome o = Rules.judge(this.game.match(me), this.onBefore, this.sim.on, s);
      this.awards(me, s, o);
      if (o.rebreak()) {
         this.startGame(me);
         this.game.say("msg.rebreak", this.game.name[me]);
      } else {
         this.game.applyPoints(o.points());

         boolean cueMoved = Rules.settle(this.game.type, this.sim, this.tray, o, new Random(this.level.getRandom().nextLong()));
         if (o.note() == Rules.Note.EIGHT_RESPOTTED) {
            this.tellPlayers(PoolTable.text("msg.eight_respot"));
         } else if (o.note() == Rules.Note.BALL_RESPOTTED && !o.respot().isEmpty()) {
            this.tellPlayers(PoolTable.text("msg.ball_respot", o.respot().get(0)));
         }

         if (o.winner() >= 0) {
            int w = o.winner();
            this.endGame(
               w,
               w == me
                  ? "msg.win"
                  : (o.note() == Rules.Note.WRONG_POCKET_EIGHT ? "msg.lose_call" : (o.foul() != EightBall.Foul.NONE ? "msg.lose_foul" : "msg.lose_early")),
               this.game.name[me]
            );
         } else {
            if (o.assigned() != null) {
               for (int seat = 0; seat < this.game.players(); seat++) {
                  this.game.group[seat] = this.game.teamOf(seat) == this.game.teamOf(me) ? o.assigned() : o.assigned().other();
               }
            }

            this.game.phase = GameState.Phase.PLAY;
            this.game.ballInHand = cueMoved ? EightBall.BallInHand.KITCHEN : o.ballInHand();
            this.game.turn = this.game.teams() ? this.game.nextTurn(me, o.continueTurn()) : o.next();
            String next = this.game.name[this.game.turn];
            if (o.foul() != EightBall.Foul.NONE) {
               this.game
                  .say(
                     "msg.foul." + o.foul().name().toLowerCase(Locale.ROOT) + (this.game.ballInHand == EightBall.BallInHand.NONE ? ".plain" : ""),
                     this.game.name[me],
                     next
                  );
            } else if (!o.out().isEmpty()) {
               this.game.say("msg.out", this.game.name[o.out().get(0)], next);
            } else if (o.assigned() != null) {
               this.game.say("msg.groups", this.game.name[me], this.groupKey(o.assigned()));
            } else if (o.continueTurn()) {
               this.game.say("msg.again", this.game.name[me]);
            } else if (o.note() == Rules.Note.CALL_MISSED) {
               this.game.say("msg.call_missed", this.game.name[me], next);
            } else if (o.note() == Rules.Note.NOT_BANKED) {
               this.game.say("msg.not_banked", this.game.name[me], next);
            } else {
               this.game.say("msg.turn", next);
            }
         }
      }
   }

   private void awards(int me, EightBall.Shot s, Rules.Outcome o) {
      MinecraftServer server = ((ServerLevel)this.level).getServer();
      UUID shooter = this.game.seat[me];
      if (s.isBreak) {
         PoolAdvancements.award(server, shooter, "break");
         this.runSeat = me;
      }

      boolean potted = false;

      for (int ball : s.pocketed) {
         potted |= ball != 0;
      }

      if (potted && o.foul() == EightBall.Foul.NONE) {
         PoolAdvancements.award(server, shooter, "pot");
      }

      int w = o.winner();
      if (w < 0) {
         if (o.next() != me) {
            this.runSeat = -1;
         }
      } else {
         UUID winner = this.game.seat[w];
         boolean opponent = false;
         boolean expert = false;

         for (int seat = 0; seat < this.game.players(); seat++) {
            if (this.game.teamOf(seat) != this.game.teamOf(w) && this.game.seat[seat] != null && !this.game.seat[seat].equals(winner)) {
               opponent = true;
               expert |= this.game.bot[seat] == PoolBot.Level.EXPERT;
            }
         }

         if (opponent) {
            PoolAdvancements.award(server, winner, "win");
            if (expert) {
               PoolAdvancements.award(server, winner, "beat_expert");
            }

            if (w == me && this.runSeat == me) {
               PoolAdvancements.award(server, winner, "break_and_run");
            }

            if (w == me && s.isBreak && this.game.type == GameType.NINE) {
               PoolAdvancements.award(server, winner, "golden_break");
            }
         }

         this.runSeat = -1;
      }
   }

   private String groupKey(EightBall.Group group) {
      String name = group.name().toLowerCase(Locale.ROOT);
      return "#pooltable.group." + (this.game.type == GameType.ENGLISH_8 ? "english." : "") + name;
   }

   private void endGame(int winner, String key, String loser) {
      this.game.phase = GameState.Phase.OVER;
      this.game.winner = winner;
      for (int seat = 0; seat < this.game.players(); seat++) {
         if (this.game.teamOf(seat) == this.game.teamOf(winner)) {
            this.game.wins[seat]++;
         }
      }
      this.game.ballInHand = EightBall.BallInHand.NONE;
      StringBuilder score = new StringBuilder();

      if (this.game.teams()) {
         score.append(this.game.wins[0]).append(" - ").append(this.game.wins[this.game.format == MatchFormat.TEAMS_1V1 ? 1 : 2]);
      } else {
         for (int s = 0; s < this.game.players(); s++) {
            score.append(s > 0 ? " - " : "").append(this.game.wins[s]);
         }
      }

      if (key.equals("msg.win")) {
         this.game.say(key, this.game.name[winner], score.toString());
      } else {
         this.game.say(key, loser, this.game.name[winner], score.toString());
      }

      Component line = PoolTable.text(this.game.message, (Object[])this.game.messageArgs);

      for (Player p : this.level.players()) {
         if (p.distanceToSqr(this.frame().centre()) < 576.0) {
            p.sendSystemMessage(line);
         }
      }
   }

   private void tellPlayers(Component message) {
      for (UUID id : this.game.seat) {
         if (id != null && this.level.getPlayerByUUID(id) instanceof ServerPlayer p) {
            p.sendOverlayMessage(message);
         }
      }
   }

   public void action(ServerPlayer player, TableActionPayload.Action action) {
      if (this.inReach(player, 12.0)) {
         switch (action) {
            case SIT_1:
               this.sit(player, 0);
               break;
            case SIT_2:
               this.sit(player, 1);
               break;
            case SIT_3:
               this.sit(player, 2);
               break;
            case SIT_4:
               this.sit(player, 3);
               break;
            case SIT_5:
               this.sit(player, 4);
               break;
            case LEAVE:
               this.leave(player);
               break;
            case RACK:
               this.rack(player);
               break;
            case CONCEDE:
               this.concede(player);
               break;
            case BOT_EASY:
               this.bot(player, PoolBot.Level.EASY);
               break;
            case BOT_MEDIUM:
               this.bot(player, PoolBot.Level.MEDIUM);
               break;
            case BOT_HARD:
               this.bot(player, PoolBot.Level.HARD);
               break;
            case BOT_EXPERT:
               this.bot(player, PoolBot.Level.EXPERT);
               break;
            case REMOVE_BOT:
               this.removeBot(player);
               break;
            case GAME_NEXT:
               this.nextGame(player);
               break;
            case TARGET_NEXT:
               this.nextTarget(player);
               break;
            case FORMAT_NEXT:
               this.nextFormat(player);
         }

         this.sync();
      }
   }

   private void sit(ServerPlayer player, int s) {
      UUID id = player.getUUID();
      int occupiedSeat = this.game.seatOf(id);
      if (s < this.game.players() && occupiedSeat != s && (!this.game.inGame() || occupiedSeat < 0)) {
         if (this.game.seat[s] != null) {
            ServerPlayer holder = player.level().getServer().getPlayerList().getPlayer(this.game.seat[s]);
            if (holder != null && holder.level() == this.level && this.inReach(holder, 32.0)) {
               player.sendOverlayMessage(PoolTable.text("msg.seat_taken", this.game.name[s]));
               return;
            }
         }

         if (occupiedSeat >= 0) {
            this.game.seat[occupiedSeat] = null;
            this.game.name[occupiedSeat] = "";
         }
         this.game.seat[s] = id;
         this.game.name[s] = player.getGameProfile().name();
         this.game.bot[s] = null;
         Arrays.fill(this.game.wins, 0);
         if (this.game.phase == GameState.Phase.PRACTICE && this.game.full()) {
            this.startGame(0);
         } else {
            this.game.say("msg.join", this.game.name[s]);
         }
      }
   }

   private void leave(ServerPlayer player) {
      int s = this.game.seatOf(player.getUUID());
      if (s >= 0) {
         String name = this.game.name[s];

         for (int i = 0; i < GameState.SEATS; i++) {
            if (player.getUUID().equals(this.game.seat[i])) {
               this.game.seat[i] = null;
               this.game.name[i] = "";
            }
         }

         Arrays.fill(this.game.wins, 0);
         this.toPractice();
         this.game.say("msg.left", name);
      }
   }

   private boolean mayManage(ServerPlayer player) {
      if (this.game.seated(player.getUUID())) {
         return true;
      } else {
         for (int s = 0; s < this.game.players(); s++) {
            if (this.game.player(s)) {
               return false;
            }
         }

         return true;
      }
   }

   private void bot(ServerPlayer player, PoolBot.Level lvl) {
      if (this.mayManage(player)) {
         if (this.game.hasBot()) {
            if (this.game.botLevel() != lvl) {
               for (int s = 0; s < GameState.SEATS; s++) {
                  if (this.game.bot[s] != null) {
                     this.game.bot[s] = lvl;
                  }
               }

               this.game.say("msg.bot_level", "#pooltable.bot." + lvl.key());
            }
         } else if (!this.moving) {
            UUID id = player.getUUID();
            int mine = this.game.seatOf(id);

            for (int sx = 0; sx < this.game.players() && mine < 0; sx++) {
               mine = this.game.seat[sx] == null ? sx : -1;
            }

            int free = 0;

            for (int sx = 0; sx < this.game.players(); sx++) {
               free += sx == mine || this.game.seat[sx] != null && !id.equals(this.game.seat[sx]) ? 0 : 1;
            }

            if (mine >= 0 && free != 0) {
               this.game.seat[mine] = id;
               this.game.name[mine] = player.getGameProfile().name();
               int bots = 0;

               for (int sx = 0; sx < this.game.players(); sx++) {
                  if (sx != mine && (this.game.seat[sx] == null || id.equals(this.game.seat[sx]))) {
                     this.game.seat[sx] = GameState.BOT;
                     bots++;
                     this.game.name[sx] = bots == 1 ? "Bot" : "Bot " + bots;
                     this.game.bot[sx] = lvl;
                  }
               }

               Arrays.fill(this.game.wins, 0);
               this.startGame(mine);
            } else {
               player.sendOverlayMessage(PoolTable.text("msg.bot_no_seat"));
            }
         }
      }
   }

   private void removeBot(ServerPlayer player) {
      if (this.game.hasBot() && this.mayManage(player)) {
         this.botReset();

         for (int s = 0; s < GameState.SEATS; s++) {
            if (this.game.bot[s] != null) {
               this.game.seat[s] = null;
               this.game.name[s] = "";
               this.game.bot[s] = null;
            }
         }

         Arrays.fill(this.game.wins, 0);
         this.toPractice();
         this.game.say("msg.left", "Bot");
      }
   }

   private void nextGame(ServerPlayer player) {
      if (!this.moving && !this.game.inGame() && this.mayManage(player)) {
         MatchFormat previousFormat = this.game.format;
         this.game.type = this.game.type.next();
         if (!this.game.format.supports(this.game.type)) {
            this.game.format = MatchFormat.defaultFor(this.game.type);
         }
         if (this.game.format != previousFormat) {
            Arrays.fill(this.game.wins, 0);
         }

         for (int s = this.game.players(); s < GameState.SEATS; s++) {
            this.game.seat[s] = null;
            this.game.name[s] = "";
            this.game.bot[s] = null;
         }

         this.toPractice();
         if (this.game.full()) {
            this.game.phase = GameState.Phase.OVER;
         }

         this.sim.rack(this.game.type, new Random(this.level.getRandom().nextLong()));
         this.tray.clear();
         this.playRack();
         this.game.say(this.game.full() ? "msg.game_ready" : "msg.game", "#pooltable.game." + this.game.type.key());
      }
   }

   private void nextFormat(ServerPlayer player) {
      if (this.moving || this.game.inGame() || !this.mayManage(player)) {
         return;
      }
      MatchFormat next = MatchFormat.nextFor(this.game.type, this.game.format);
      this.game.format = next;
      for (int seat = this.game.players(); seat < GameState.SEATS; seat++) {
         this.game.seat[seat] = null;
         this.game.name[seat] = "";
         this.game.bot[seat] = null;
         this.game.group[seat] = EightBall.Group.NONE;
         this.game.score[seat] = 0;
         this.game.wins[seat] = 0;
      }
      Arrays.fill(this.game.wins, 0);
      Arrays.fill(this.game.teamCursor, 0);
      this.toPractice();
      if (this.game.full()) {
         this.startGame(0);
         this.game.say("msg.format_ready", "#pooltable.format." + next.key());
      } else {
         this.game.say("msg.format", "#pooltable.format." + next.key());
      }
   }

   private void nextTarget(ServerPlayer player) {
      if (this.game.type == GameType.STRAIGHT && !this.game.inGame() && this.mayManage(player)) {
         int[] targets = GameState.STRAIGHT_TARGETS;
         int i = 0;

         while (i < targets.length && targets[i] != this.game.target) {
            i++;
         }

         this.game.target = targets[(i + 1) % targets.length];
         this.game.say("msg.target", String.valueOf(this.game.target));
      }
   }

   private void toPractice() {
      this.game.phase = GameState.Phase.PRACTICE;
      Arrays.fill(this.game.group, EightBall.Group.NONE);
      Arrays.fill(this.game.score, 0);
      this.game.winner = -1;
      this.game.ballInHand = EightBall.BallInHand.ANYWHERE;
   }

   private void rack(ServerPlayer player) {
      if (!this.moving) {
         if (this.game.phase == GameState.Phase.PRACTICE) {
            this.sim.rack(this.game.type, new Random(this.level.getRandom().nextLong()));
            this.tray.clear();
            this.playRack();
            this.game.say("msg.rack", player.getGameProfile().name());
         } else if (this.game.seated(player.getUUID())) {
            this.startGame(this.game.nextBreaker);
         }
      }
   }

   private void concede(ServerPlayer player) {
      int s = this.game.seatOf(player.getUUID());
      if (this.game.inGame() && s >= 0 && !this.moving) {
         int winner = this.game.teams()
            ? this.game.match(s).opponent(s)
            : (this.game.type == GameType.CUTTHROAT ? this.cutthroatConcedeWinner(s) : this.game.match(s).opponent(s));
         this.endGame(winner, "msg.concede", this.game.name[s]);
      }
   }

   private int cutthroatConcedeWinner(int concedingSeat) {
      int winner = -1;
      int most = -1;
      for (int seat = 0; seat < this.game.players(); seat++) {
         int remaining = EightBall.remaining(this.game.group[seat], this.sim.on);
         if (seat != concedingSeat && remaining > most) {
            most = remaining;
            winner = seat;
         }
      }
      return winner >= 0 ? winner : (concedingSeat + 1) % this.game.players();
   }

   private void startGame(int breaker) {
      this.sim.rack(this.game.type, new Random(this.level.getRandom().nextLong()));
      this.tray.clear();
      this.game.phase = GameState.Phase.BREAK;
      this.game.turn = breaker;
      this.game.nextBreaker = (breaker + 1) % this.game.players();
      Arrays.fill(this.game.group, EightBall.Group.NONE);
      if (this.game.type == GameType.CUTTHROAT) {
         for (int seat = 0; seat < this.game.players(); seat++) {
            this.game.group[seat] = MatchFormat.cutthroatGroup(this.game.players(), seat);
         }
      }
      this.game.resetTeamCursors(breaker);
      Arrays.fill(this.game.score, 0);
      this.game.winner = -1;
      this.game.ballInHand = EightBall.BallInHand.KITCHEN;
      this.game.say("msg.break", this.game.name[breaker]);
      this.playRack();
   }

   private void playRack() {
      Vec3 c = this.frame().toWorld(0.0, 0.8125, 1.09375);
      this.level.playSound(null, c.x, c.y, c.z, PoolTable.RACK.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
   }

   public void setClothColor(DyeColor color) {
      this.cloth = color;
      this.sync();
   }

   private void sync() {
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
      }
   }

   public void clientShot(int id, long[] state, float angle, float power, float spin) {
      this.pendingFinal = null;
      this.sim.load(state);
      this.moving = true;
      this.shotId = id;
      this.shotTicks = 0;
      this.pathSettled = false;
      if (this.game.phase != GameState.Phase.PRACTICE) {
         this.game.ballInHand = EightBall.BallInHand.NONE;
      }

      this.visuals.strikeTime = this.level.getGameTime();
      this.visuals.strikeAngle = angle;
      this.visuals.strikePull = 0.05F + power * 0.3F;
      this.visuals.strikeSpin = spin;
      this.visuals.strikeX = this.sim.x[0];
      this.visuals.strikeZ = this.sim.z[0];
      this.visuals.aimTime = -1000.0;
      Vec3 at = this.frame().toWorld(this.sim.x[0], 0.8875, this.sim.z[0]);
      PoolTable.clientHooks.playSound(PoolTable.CUE_HIT.get(), at.x, at.y, at.z, 0.35F + 0.65F * power, 0.9F + this.level.getRandom().nextFloat() * 0.2F);
   }

   public static void clientTick(Level level, BlockPos pos, BlockState state, PoolTableBlockEntity be) {
      be.tickClient();
   }

   private void tickClient() {
      this.soundBudget = 5;
      this.visuals.prune(this.level.getGameTime());
      if (this.moving) {
         boolean still = this.sim.tick(this.clientEvents);
         this.pathSettled = false;
         if (!still || this.pendingFinal != null && ++this.pendingWait > 60) {
            this.moving = false;
         }
      }

      if (!this.moving) {
         if (this.pendingFinal != null) {
            CompoundTag tag = this.pendingFinal;
            this.pendingFinal = null;
            this.applyBalls(tag);
         }

         if (!this.pathSettled) {
            this.sim.settlePath();
            this.pathSettled = true;
         }
      }
   }

   protected void saveAdditional(ValueOutput output) {
      super.saveAdditional(output);
      output.store(WHOLE, this.writeTag());
   }

   protected void loadAdditional(ValueInput input) {
      super.loadAdditional(input);
      input.read(WHOLE).ifPresent(this::readTag);
   }

   private CompoundTag writeTag() {
      CompoundTag tag = new CompoundTag();
      tag.putLongArray("Balls", this.sim.save());
      tag.putBoolean("Moving", this.moving);
      tag.putInt("ShotId", this.shotId);
      tag.putInt("ShotTicks", this.shotTicks);
      tag.putIntArray("Tray", this.tray.stream().mapToInt(Integer::intValue).toArray());
      tag.put("Game", this.game.save());
      tag.putString("Cloth", this.cloth.getName());
      if (this.shot != null) {
         CompoundTag s = new CompoundTag();
         s.putBoolean("Break", this.shot.isBreak);
         s.putInt("First", this.shot.firstContact);
         s.putBoolean("Scratch", this.shot.scratch);
         s.putIntArray("Potted", this.shot.pocketed.stream().mapToInt(Integer::intValue).toArray());
         s.putIntArray("Pockets", this.shot.pocketOf);
         int banks = 0;

         for (int i = 0; i < 16; i++) {
            banks |= this.shot.banked[i] ? 1 << i : 0;
         }

         s.putInt("Banked", banks);
         s.putInt("Call", this.shot.callPocket);
         int mask = 0;

         for (int i = 0; i < 16; i++) {
            mask |= this.onBefore[i] ? 1 << i : 0;
         }

         s.putInt("OnBefore", mask);
         tag.put("Shot", s);
      }

      return tag;
   }

   private void readTag(CompoundTag tag) {
      String oldMessage = this.game.message + String.join("|", this.game.messageArgs);
      this.game.load(tag.getCompoundOrEmpty("Game"));
      DyeColor newCloth = DyeColor.byName(tag.getStringOr("Cloth", ""), DyeColor.GREEN);
      boolean clothChanged = newCloth != this.cloth;
      this.cloth = newCloth;
      boolean client = this.level != null && this.level.isClientSide();
      if (!client || !this.moving || tag.getIntOr("ShotId", 0) != this.shotId) {
         this.applyBalls(tag);
      } else if (!tag.getBooleanOr("Moving", false)) {
         this.pendingFinal = tag.copy();
         this.pendingWait = 0;
      }

      if (tag.contains("Shot")) {
         CompoundTag s = tag.getCompoundOrEmpty("Shot");
         this.shot = new EightBall.Shot();
         this.shot.isBreak = s.getBooleanOr("Break", false);
         this.shot.firstContact = s.getIntOr("First", 0);
         this.shot.scratch = s.getBooleanOr("Scratch", false);

         for (int b : s.getIntArray("Potted").orElse(new int[0])) {
            this.shot.pocketed.add(b);
         }

         int[] pockets = s.getIntArray("Pockets").orElse(new int[0]);
         int banks = s.getIntOr("Banked", 0);

         for (int i = 0; i < 16; i++) {
            this.shot.pocketOf[i] = i < pockets.length ? pockets[i] : -1;
            this.shot.banked[i] = (banks & 1 << i) != 0;
         }

         this.shot.callPocket = s.getIntOr("Call", -1);
         int mask = s.getIntOr("OnBefore", 0);

         for (int i = 0; i < 16; i++) {
            this.onBefore[i] = (mask & 1 << i) != 0;
         }
      }

      if (client) {
         if (clothChanged) {
            PoolTable.clientHooks.clothChanged(this);
         }

         if (!oldMessage.equals(this.game.message + String.join("|", this.game.messageArgs))) {
            this.visuals.messageTime = this.level.getGameTime();
         }
      }
   }

   private void applyBalls(CompoundTag tag) {
      this.sim.load(tag.getLongArray("Balls").orElse(new long[0]));
      this.moving = tag.getBooleanOr("Moving", false);
      this.shotId = tag.getIntOr("ShotId", 0);
      this.shotTicks = tag.getIntOr("ShotTicks", 0);
      this.tray.clear();

      for (int b : tag.getIntArray("Tray").orElse(new int[0])) {
         this.tray.add(b);
      }

      this.sim.settlePath();
      this.pathSettled = true;
   }

   public CompoundTag getUpdateTag(Provider registries) {
      CompoundTag tag = this.writeTag();
      tag.remove("Shot");
      return tag;
   }

   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}

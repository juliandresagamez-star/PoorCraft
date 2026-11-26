package dev.ash.pooltable.client;

import dev.ash.pooltable.PoolClientConfig;
import dev.ash.pooltable.PoolConfig;
import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.CueRackBlock;
import dev.ash.pooltable.block.GameState;
import dev.ash.pooltable.block.PoolTableBlock;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.block.TableFrame;
import dev.ash.pooltable.client.compat.ClientShim;
import dev.ash.pooltable.net.AimPayload;
import dev.ash.pooltable.net.Net;
import dev.ash.pooltable.net.PlaceCuePayload;
import dev.ash.pooltable.net.ShootPayload;
import dev.ash.pooltable.sim.EightBall;
import dev.ash.pooltable.sim.PoolSim;
import dev.ash.pooltable.sim.Rules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public final class ClientAim {
   public static final double CHARGE_SECONDS = 3.0;
   public static final double FINE = 0.25;
   private static final double TILT_SENSITIVITY = 0.6;
   private static final double SMOOTH_SECONDS = 0.06;
   private static final double GHOST_SMOOTH_SECONDS = 0.08;
   private static final float MIN_TILT = 8.0F;
   private static final float MAX_TILT = 80.0F;
   private static final double VR_STEEP = 75.0;
   @Nullable
   private static PoolTableBlockEntity table;
   private static ClientAim.Stage stage = ClientAim.Stage.IDLE;
   private static boolean armed;
   public static double angle;
   private static double aimTarget;
   public static float tilt = 25.0F;
   private static float tiltTarget = 25.0F;
   public static float spin;
   public static float power;
   private static boolean cancelled;
   private static int sendCooldown;
   private static float sentAngle = Float.NaN;
   private static float sentPower;
   private static int sentCall = -1;
   private static int callChoice = -1;
   private static long lastNanos;
   private static double handAngle = Double.NaN;
   private static int cueSlot = -1;
   private static double chargeSeconds;
   private static boolean wasCharging;
   static boolean worldPass;
   public static boolean ghostShown;
   public static boolean ghostValid;
   public static double ghostX;
   public static double ghostZ;
   public static double ghostTargetX;
   public static double ghostTargetZ;

   private ClientAim() {
   }

   @Nullable
   public static PoolTableBlockEntity table() {
      return table != null && !table.isRemoved() ? table : null;
   }

   public static ClientAim.Stage stage() {
      return stage;
   }

   static boolean holdingCue(Player p) {
      return p.getMainHandItem().is(PoolTable.CUE.get());
   }

   public static boolean mayAct(PoolTableBlockEntity be, Player p) {
      return !be.moving() && be.game.mayShoot(p.getUUID());
   }

   public static boolean canAim() {
      Player p = Minecraft.getInstance().player;
      PoolTableBlockEntity be = table();
      return be != null && p != null && mayAct(be, p) && be.sim.on[0];
   }

   public static boolean canPlace() {
      Player p = Minecraft.getInstance().player;
      PoolTableBlockEntity be = table();
      return be != null && p != null && mayAct(be, p) && be.ballInHand() != EightBall.BallInHand.NONE;
   }

   public static boolean aiming() {
      return stage == ClientAim.Stage.AIM && canAim();
   }

   static boolean cameraAllowed(PoolTableBlockEntity be) {
      return PoolClientConfig.aimCamera() && be.game.phase != GameState.Phase.BREAK && !VrInput.active();
   }

   public static ClientAim.View view() {
      PoolTableBlockEntity be = table();
      if (be == null) {
         return ClientAim.View.NONE;
      } else {
         return switch (stage) {
            case IDLE -> ClientAim.View.NONE;
            case PLACE -> ClientAim.View.PLACE;
            case AIM -> cameraAllowed(be) ? ClientAim.View.AIM_CAMERA : ClientAim.View.AIM_EYES;
         };
      }
   }

   public static boolean charging() {
      LocalPlayer p = Minecraft.getInstance().player;
      return p != null && p.isUsingItem() && p.getUseItem().is(PoolTable.CUE.get()) && table() != null;
   }

   public static PoolConfig.Guide guide() {
      PoolConfig.Guide server = PoolConfig.guide();
      PoolConfig.Guide own = PoolClientConfig.aimGuide();
      return own.ordinal() > server.ordinal() ? own : server;
   }

   public static double sensitivity(boolean fine) {
      double base = view() == ClientAim.View.AIM_CAMERA ? PoolClientConfig.aimSensitivity() : 1.0;
      return base * (fine ? 0.25 : 1.0);
   }

   private static boolean fineHeld() {
      return Minecraft.getInstance().options.keyShift.isDown();
   }

   static void tick(Minecraft mc) {
      LocalPlayer p = mc.player;
      if (p != null) {
         vrHotbar(p);
      }

      if (p != null && mc.level != null && holdingCue(p)) {
         table = pick(mc, p);
         if (table != null && mayAct(table, p)) {
            if (stage == ClientAim.Stage.PLACE && !canPlace()) {
               stage = ClientAim.Stage.IDLE;
            }

            if (stage == ClientAim.Stage.AIM && !canAim()) {
               stage = ClientAim.Stage.IDLE;
            }

            if (stage == ClientAim.Stage.AIM && !armed && !mc.options.keyUse.isDown()) {
               armed = true;
            }

            if (aiming()) {
               int call = call();
               if (--sendCooldown <= 0
                  && (Float.isNaN(sentAngle) || Math.abs(sentAngle - angle) > 0.002 || Math.abs(sentPower - power) > 0.02 || sentCall != call)) {
                  sentAngle = (float)angle;
                  sentPower = power;
                  sentCall = call;
                  sendCooldown = 2;
                  Net.toServer(new AimPayload(table.getBlockPos(), sentAngle, sentPower, call));
               }
            }
         } else {
            stage = ClientAim.Stage.IDLE;
         }
      } else {
         table = null;
         stage = ClientAim.Stage.IDLE;
      }
   }

   static void tickStart(Minecraft mc) {
      if (mc.player != null) {
         vrHotbar(mc.player);
      }

      if (charging()) {
         boolean clicked = false;

         while (mc.options.keyAttack.consumeClick()) {
            clicked = true;
         }

         if (clicked && mc.gameMode != null) {
            cancelled = true;
            armed = false;
            mc.gameMode.releaseUsingItem(mc.player);
         }
      }
   }

   @Nullable
   private static PoolTableBlockEntity pick(Minecraft mc, LocalPlayer p) {
      if (stage != ClientAim.Stage.IDLE && table != null && !table.isRemoved() && table.inReach(p, 7.0)) {
         return table;
      } else {
         HitResult hit = mc.hitResult;
         if (hit instanceof BlockHitResult bhr && hit.getType() == Type.BLOCK) {
            PoolTableBlockEntity be = PoolTableBlock.master(mc.level, bhr.getBlockPos(), mc.level.getBlockState(bhr.getBlockPos()));
            if (be != null) {
               return be;
            }
         }

         return table != null && !table.isRemoved() && table.getLevel() == mc.level && table.inReach(p, 7.0)
            ? table
            : PoolTableBlockEntity.nearest(mc.level, p.getEyePosition(), 6.0);
      }
   }

   private static void stepUp() {
      LocalPlayer p = Minecraft.getInstance().player;
      PoolTableBlockEntity be = table();
      AimCamera.stopWatching();
      stage = ClientAim.Stage.AIM;
      armed = false;
      if (p != null) {
         cueSlot = p.getInventory().getSelectedSlot();
      }

      if (p != null && be != null) {
         double hand = handAngle(be);
         angle = aimTarget = Double.isNaN(hand) ? be.frame().localAngle(p.getViewVector(1.0F)) : hand;
         tilt = tiltTarget = Mth.clamp(p.getXRot(), 12.0F, 55.0F);
      }

      handAngle = Double.NaN;
      sentAngle = Float.NaN;
      callChoice = -1;
   }

   public static boolean callNeeded() {
      PoolTableBlockEntity be = table();
      return be != null && be.game.inGame() && Rules.callNeeded(be.game.type, be.game.turn, be.game.group, be.sim.on, be.game.phase == GameState.Phase.BREAK);
   }

   public static int call() {
      PoolTableBlockEntity be = table();
      if (aiming() && be != null && callNeeded()) {
         return callChoice >= 0 ? callChoice : Rules.aimedPocket(be.sim, angle);
      } else {
         return -1;
      }
   }

   static boolean pickKey() {
      if (callNeeded() && aiming()) {
         callChoice = (Math.max(call(), -1) + 1) % 6;
         LocalPlayer p = Minecraft.getInstance().player;
         if (p != null) {
            p.playSound((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.6F);
         }

         return true;
      } else {
         return false;
      }
   }

   static boolean useKey() {
      LocalPlayer p = Minecraft.getInstance().player;
      PoolTableBlockEntity be = table();
      if (p == null || be == null || !holdingCue(p) || !mayAct(be, p)) {
         return false;
      } else if (stage == ClientAim.Stage.IDLE && lookingAtRack()) {
         return false;
      } else {
         switch (stage) {
            case IDLE:
               boolean practice = be.game.phase == GameState.Phase.PRACTICE;
               if (!canPlace() || practice && !fineHeld() && be.sim.on[0]) {
                  if (canAim()) {
                     stepUp();
                  }
               } else {
                  pickUp(be);
               }

               return true;
            case PLACE:
               double[] spot = freeSpot(be, ghostX, ghostZ);
               if (spot != null) {
                  Net.toServer(new PlaceCuePayload(be.getBlockPos(), spot[0], spot[1]));
                  be.sim.place(0, spot[0], spot[1]);
                  be.sim.settlePath();
                  ghostX = spot[0];
                  ghostZ = spot[1];
                  ghostShown = false;
                  VrInput.pulse(0.03F, 0.3F);
                  stepUp();
               } else {
                  p.playSound(SoundEvents.DISPENSER_FAIL, 0.4F, 1.4F);
               }

               return true;
            default:
               return !armed;
         }
      }
   }

   private static boolean lookingAtRack() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level != null
         && mc.hitResult instanceof BlockHitResult hit
         && hit.getType() == Type.BLOCK
         && mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof CueRackBlock;
   }

   static boolean attackKey() {
      if (AimCamera.watching()) {
         AimCamera.stopWatching();
         return true;
      } else {
         LocalPlayer p = Minecraft.getInstance().player;
         if (stage != ClientAim.Stage.IDLE && p != null && holdingCue(p)) {
            stage = ClientAim.Stage.IDLE;
            return true;
         } else {
            return false;
         }
      }
   }

   public static boolean turn(double dx, double dy) {
      if (AimCamera.watching() && AimCamera.active()) {
         AimCamera.orbit(dx, dy);
         return true;
      } else if (aiming() && !VrInput.active()) {
         boolean fine = fineHeld();
         aimTarget = aimTarget - Math.toRadians(dx * 0.15) * sensitivity(fine);
         if (view() != ClientAim.View.AIM_CAMERA) {
            return false;
         } else {
            tiltTarget = Mth.clamp(tiltTarget + (float)(dy * 0.15 * 0.6 * (fine ? 0.25 : 1.0)), 8.0F, 80.0F);
            return true;
         }
      } else {
         return false;
      }
   }

   static void released(int ticks) {
      if (cancelled) {
         cancelled = false;
      } else {
         PoolTableBlockEntity be = table();
         if (be != null && stage == ClientAim.Stage.AIM && ticks >= 3 && canAim()) {
            Net.toServer(new ShootPayload(be.getBlockPos(), (float)angle, power, spin, call()));
            VrInput.pulse(0.04F + 0.08F * power, 0.35F + 0.65F * power);
            sentAngle = Float.NaN;
            if (view() == ClientAim.View.AIM_CAMERA) {
               AimCamera.watch(be);
            }
         }
      }
   }

   public static boolean scrolled(double delta) {
      if (AimCamera.watching() && delta != 0.0) {
         AimCamera.zoom(delta);
         return true;
      } else if (aiming() && delta != 0.0) {
         spin = Mth.clamp(spin + (delta > 0.0 ? 0.25F : -0.25F), -1.0F, 1.0F);
         return true;
      } else {
         return false;
      }
   }

   private static void vrHotbar(LocalPlayer p) {
      if (stage == ClientAim.Stage.AIM && cueSlot >= 0 && VrInput.active()) {
         int now = p.getInventory().getSelectedSlot();
         int step = Math.floorMod(now - cueSlot, 9);
         if ((step == 1 || step == 8) && p.getInventory().getItem(cueSlot).is(PoolTable.CUE.get()) && aiming()) {
            p.getInventory().setSelectedSlot(cueSlot);
            scrolled(step == 8 ? 1.0 : -1.0);
         }
      }
   }

   static boolean freezeMovement() {
      return aiming() && view() == ClientAim.View.AIM_CAMERA;
   }

   static void frame(float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      PoolTableBlockEntity be = table();
      long now = System.nanoTime();
      double elapsed = lastNanos == 0L ? 0.0 : (now - lastNanos) / 1.0E9;
      lastNanos = now;
      if (p != null && be != null && mc.level != null) {
         double dt = Math.min(elapsed, 0.1);
         vrHotbar(p);
         followHand(be);
         double k = 1.0 - Math.exp(-dt / 0.06);
         angle = angle + Mth.wrapDegrees(Math.toDegrees(aimTarget - angle)) * Math.PI / 180.0 * k;
         tilt = tilt + (tiltTarget - tilt) * (float)k;
         boolean charging = charging();
         if (!charging || !wasCharging) {
            chargeSeconds = 0.0;
         } else if (!mc.isPaused()) {
            chargeSeconds = chargeSeconds + Math.min(elapsed, 0.5);
         }

         wasCharging = charging;
         power = charging ? (float)Math.min(chargeSeconds / 3.0, 1.0) : 0.0F;
         if (stage != ClientAim.Stage.PLACE) {
            ghostShown = false;
         } else {
            Vec3 look = p.getViewVector(partialTick);
            Vec3 eye = p.getEyePosition(partialTick);
            Vec3 pointing = VrInput.handDir();
            if (pointing != null) {
               look = pointing;
               Vec3 hand = VrInput.handPos();
               eye = hand != null ? hand : eye;
            }

            if (look.y < -0.02) {
               TableFrame f = be.frame();
               double planeY = f.master().getY() + 0.8125 + 0.075;
               Vec3 at = eye.add(look.scale((planeY - eye.y) / look.y));
               double[] spot = freeSpot(be, f.localX(at), f.localZ(at));
               if (spot != null) {
                  ghostTargetX = spot[0];
                  ghostTargetZ = spot[1];
               }
            }

            double g = 1.0 - Math.exp(-dt / 0.08);
            ghostX = ghostX + (ghostTargetX - ghostX) * g;
            ghostZ = ghostZ + (ghostTargetZ - ghostZ) * g;
            ghostShown = true;
            ghostValid = freeSpot(be, ghostX, ghostZ) != null;
         }
      } else {
         power = 0.0F;
         wasCharging = false;
      }
   }

   private static void followHand(PoolTableBlockEntity be) {
      double now = aiming() ? handAngle(be) : Double.NaN;
      if (!Double.isNaN(now) && !Double.isNaN(handAngle)) {
         aimTarget = aimTarget + Mth.wrapDegrees(Math.toDegrees(now - handAngle)) * Math.PI / 180.0 * (fineHeld() ? 0.25 : 1.0);
      }

      handAngle = now;
   }

   private static double handAngle(PoolTableBlockEntity be) {
      double[] hand = VrInput.hand();
      return hand != null && !(Math.abs(Math.toDegrees(hand[1])) > 75.0)
         ? be.frame().localAngle(new Vec3(-Math.sin(hand[0]), 0.0, Math.cos(hand[0])))
         : Double.NaN;
   }

   private static void pickUp(PoolTableBlockEntity be) {
      AimCamera.stopWatching();
      stage = ClientAim.Stage.PLACE;
      LocalPlayer p = Minecraft.getInstance().player;
      cueSlot = p != null ? p.getInventory().getSelectedSlot() : -1;
      double[] start = be.sim.on[0] ? new double[]{be.sim.x[0], be.sim.z[0]} : new double[]{0.0, -1.09375};
      double[] spot = freeSpot(be, start[0], start[1]);
      if (spot == null) {
         spot = start;
      }

      ghostX = ghostTargetX = spot[0];
      ghostZ = ghostTargetZ = spot[1];
      ghostShown = true;
      ghostValid = true;
   }

   @Nullable
   static double[] freeSpot(PoolTableBlockEntity be, double x, double z) {
      PoolSim sim = be.sim;
      double r = 0.075;
      double m = 0.002;
      double maxZ = be.ballInHand() == EightBall.BallInHand.KITCHEN ? -1.09375 : 2.1875 - r - m;

      for (int pass = 0; pass < 12; pass++) {
         x = Mth.clamp(x, -1.1875 + r + m, 1.1875 - r - m);
         z = Mth.clamp(z, -2.1875 + r + m, maxZ);
         boolean clear = true;

         for (int i = 1; i < 16; i++) {
            if (sim.on[i]) {
               double dx = x - sim.x[i];
               double dz = z - sim.z[i];
               double d = Math.sqrt(dx * dx + dz * dz);
               double need = 2.0 * r + 0.004;
               if (d < need) {
                  clear = false;
                  if (d < 1.0E-6) {
                     dx = 1.0;
                     dz = 0.0;
                     d = 1.0;
                  }

                  x = sim.x[i] + dx / d * need;
                  z = sim.z[i] + dz / d * need;
               }
            }
         }

         if (clear && sim.isFree(0, x, z)) {
            return new double[]{x, z};
         }
      }

      return null;
   }

   static boolean hideHand() {
      return stage != ClientAim.Stage.IDLE && table() != null && ClientShim.get().screen() == null;
   }

   static boolean isAiming(Player p) {
      Minecraft mc = Minecraft.getInstance();
      if (p == mc.player) {
         return hideHand();
      } else if (mc.level == null) {
         return false;
      } else {
         for (PoolTableBlockEntity be : PoolTableBlockEntity.clientTables()) {
            if (be.getLevel() == mc.level && !be.moving() && be.visuals.aimPlayer == p.getId() && mc.level.getGameTime() - be.visuals.aimTime < 20.0) {
               return true;
            }
         }

         return false;
      }
   }

   static void reset() {
      table = null;
      stage = ClientAim.Stage.IDLE;
      cancelled = false;
      spin = 0.0F;
      power = 0.0F;
      wasCharging = false;
      handAngle = Double.NaN;
      AimCamera.reset();
   }

   public static enum Stage {
      IDLE,
      PLACE,
      AIM;
   }

   public static enum View {
      NONE,
      PLACE,
      AIM_EYES,
      AIM_CAMERA;
   }
}
